require 'rubygems'
require 'bud'

require './lpair'

module KvsProtocol
  state do
    channel :kvput, [:reqid] => [:@addr, :key, :value]
    channel :kvget, [:reqid] => [:@addr, :key, :client_addr]
    channel :kvget_response, [:reqid] => [:@addr, :value]
  end
end

# Simple KVS replica in which we just merge together all the proposed values for
# a given key. This is reasonable when the intent is to store a monotonically
# increasing lmap of keys.
class MergeMapKvsReplica
  include Bud
  include KvsProtocol

  state do
    lmap :kv_store
  end

  bloom :write do
    kv_store <= kvput {|c| {c.key => c.value}}
  end

  bloom :read do
    kvget_response <~ kvget {|c| [c.reqid, c.client_addr, kv_store.at(c.key)]}
  end

  # bloom :logging do
  #   stdio <~ kvput {|c| ["kvput: #{c.inspect} @ #{ip_port}"]}
  #   stdio <~ kvget {|c| ["kvget: #{c.inspect} @ #{ip_port}"]}
  # end
end

# Design notes:
#
# Inspired by Dynamo. Each key is associated with a <vector-clock, value>
# pair. A read returns this pair.
#
# To issue a write, a client must supply a <vector-clock, value> pair. The
# vector-clock represents the most recent version of this key read by the
# client, if any (if the client is doing a "blind write", they can use the empty
# clock). The write is received by a replica, which _coordinates_ applying the
# write to the rest of the system. First, the coordinator increments its own
# position in the client-submitted vector clock. It then merges the resulting
# <vector-clock, value> pair into its own database, according to this logic:
# versions are preferred according to the partial order over VCs, and for
# incomparable VCs (concurrent versions) a user-supplied merge method is
# invoked; the resulting merged value is assigned the merged VC of the two input
# values. Since we do conflict resolution at the server side, each replica
# stores at most one <vector-clock, value> associated with any key (unlike in
# Dynamo).
#
# Writes are propagated between replicas via the same logic, except that a
# replica accepting a write from another replica does not bump its own position
# in the write's vector clock. Write progagation could either be done actively
# (coordinator doesn't return success to the client until W replicas have been
# written) or passively (replicas perioidically merge their databases).
#
# Note that we maintain a vector clock at each node and increment it for _every_
# kvput; it is this clock that we use to stamp inbound kvputs. An alternative
# approach would be to just increment the previous logical clock for the
# originating node found in the kvput's VC. Unclear which approach is better;
# the current approach provides a hint about cross-key causality.
class VectorClockKvsReplica < MergeMapKvsReplica
  state do
    lmap :my_vc
    lmap :next_vc
  end

  bootstrap do
    my_vc <= { ip_port => Bud::MaxLattice.new(0) }
  end

  bloom :write do
    # Increment local VC on every kvput
    next_vc <= my_vc
    next_vc <= kvput { {ip_port => my_vc.at(ip_port) + 1} }
    my_vc <+ next_vc

    # On every kvput, merge incremented VC with kvput's VC
    kv_store <= kvput {|m| {m.key => m.value.apply_fst(:merge, next_vc)}}
  end
end

class KvsClient
  include Bud
  include KvsProtocol

  def initialize(addr)
    @req_id = 0
    @addr = addr
    super()
  end

  # XXX: Probably not thread-safe.
  def read(key)
    req_id = make_req_id
    r = sync_callback(:kvget, [[req_id, @addr, key, ip_port]], :kvget_response)
    r.each do |t|
      if t[0] == req_id
        return t.value
      end
    end
    raise
  end

  def write(key, val)
    sync_do {
      kvput <~ [[make_req_id, @addr, key, val]]
    }
  end

  private
  def make_req_id
    @req_id += 1
    "#{ip_port}_#{@req_id}"
  end
end
