require 'rubygems'
require 'bud'
require 'test/test_lib'
require 'test/cart_workloads'

require 'lib/disorderly_cart'
require 'lib/destructive_cart'



module Remember
  include Anise
  annotator :declare
  def state
    super
    table :memo, ['client', 'server', 'session', 'item', 'cnt']
  end

  declare 
  def memm
    memo <= response_msg.map{|r| r }
  end
end


class BCS < Bud
  include BestEffortMulticast
  include ReplicatedDisorderlyCart
  include CartClient
  include Remember
end

class DCR < Bud
  include CartClientProtocol
  include CartClient
  include CartProtocol
  include DestructiveCart
  include ReplicatedKVS
  include BestEffortMulticast
  include Remember
end

class DummyDC < Bud
  include CartClientProtocol
  include CartClient
  include CartProtocol
  include DestructiveCart
  include BasicKVS
  include Remember

  def state
    super
    table :members, ['peer']
    channel :tickler, ['myself']
  end
end

class BCSC < Bud
  include CartClient
  def state
    super
    table :cli_resp_mem, ['@client', 'server', 'session', 'item', 'cnt']
  end

  declare 
  def memmy
    cli_resp_mem <= response_msg.map{|r| r }
  end
end

class TestCart < TestLib
  include CartWorkloads

  def test_disorderly_cart
    program = BCS.new('localhost', 23765, {'dump' => true})
    #program = DummyDC.new('localhost', 23765, {'dump' => true})
    #program = DCR.new('localhost', 23765, {'dump' => true, 'scoping' => true})

    program.run_bg
    sleep 1
    run_cart(program)
    advance(program)

    # PAA
    advance(program)
    advance(program)
    advance(program)

    program.memo.each {|m| puts "MEMO: #{m.inspect}" }


    assert_equal(2, program.memo.length)
    program.memo.each do |a|
      print "item: #{a.inspect}\n"
      if a.item == "beer"
        assert_equal(3, a.cnt)
      elsif a.item == "diapers"
        assert_equal(1, a.cnt)
      else
        assert_error("incorrect item #{a.item} in cart")
      end
    end

    # the checkout message is redelivered!
    addy = "#{program.ip}:#{program.port}"
    send_channel(program.ip, program.port, "checkout_msg", [addy, addy, 1234, 133])
    advance(program)
 
    pcnt = 0
    program.memo.each do |a|
      pcnt = a.cnt if a.item == "papers"
    end 

    # undesirable but consistent that a 2nd checkout message should produce a revised manifest.
    assert_equal(3, program.memo.length)
    assert_equal(1, pcnt)
    
  end

end
