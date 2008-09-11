require "lib/types/table/table_name"

class UnsortedTupleSet 
  include Comparable
  include Enumerable
  
  @@ids = 0
  def initialize(name, *tuples)
    @id = "TupleSet:" + @@ids.to_s
    @@ids += 1
#    @tups = Array.new
    @tups = Hash.new
    @name = name
#    tuples.each {|t| @tups << t} unless tuples[0] == nil
    tuples.each {|t| @tups[t.hash] = t} unless tuples[0] == nil
  end
  
  def ts_id
    @id
  end
  
  attr_reader :name
  
  def tups
    return @tups.values
  end

  def size
    @tups.length
  end
  
  def to_s
    @id ###+ super
  end
  
  def hash
    @id.hash
  end
  
  def delete(tup)
    @tups.delete(tup.hash)
  end
  
  def clear
#    @tups = Array.new
    @tups = Hash.new
  end
  
  def <<(o)
    case o.class.name
#      when 'TupleSet': o.each {|t| @tups << t }
      when 'TupleSet': o.each {|t| @tups[t.hash]  = t}
      when 'Array': o.each {|t| @tups[t.hash]  = t}
#      when 'Tuple': @tups << o
      when 'Tuple':
         @tups[o.hash] = o
      else 
	puts o.inspect	
	raise "inserting a " + o.class.name + " object into TupleSet"
    end
  end
  
  def ==(o)
    if o.class == TupleSet
      return o.ts_id == @id
    else
      return false
    end
  end
  
  # The only meaningful response to this
  # method is to determine if the two sets
  # are equal.
  def <=>(o)
    return (self == o) ? 0 : 1
  end

  def ordered_array(*col_names)
    out = @tups.values.sort do |a,b| 
      diff = 0
      col_names.each do |c|
        ac = a.value(c)
        bc = b.value(c)
        raise("can't order by unknown column set #{c}") if (ac.nil? or bc.nil?)
        diff = (ac <=> bc)
        break unless diff == 0
      end
      diff
    end
    return out
  end  
  
  def order_by(*col_names)
    ordered_array(*col_names).each  {|t| yield t}
  end
  
  def each
    @tups.values.each do |t|
      yield t
    end
  end
end

class TupleSet < UnsortedTupleSet
  def initialize(name, *tuples)
    # this ordered list will point to hash keys in the superclass
    @positions = Array.new
    @back_ptrs = Hash.new
    super(name,nil)
    unless tuples[0] == nil
	tuples.each { |t| self << t } 
    end
  end

  def delete(tup)
	res = super(tup)
	if res.eql?(tup) then
		@positions.delete_at(@back_ptrs[tup.hash])
	end
	return res
  end

  def clear
    @back_ptrs = Hash.new
    @positions = Array.new
  end

  def <<(o)
	@positions << o.hash
	@back_ptrs[o.hash] = @positions.length-1
	super(o)
  end

  def each # return them in order of position!
    #raise "uh oh tupleset!" unless @positions.length == @tups.length
    #raise "uh oh tupleset!" unless @positions.length == @tup_pos.length
    @positions.each do |p|
      yield @tups[p]
    end
  end
end
