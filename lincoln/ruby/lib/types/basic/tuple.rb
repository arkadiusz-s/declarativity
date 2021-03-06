require 'lib/types/basic/schema'
require 'lib/types/basic/tuple'
require 'lib/types/basic/tuple_set'
require 'lib/lang/plan/variable'
require 'lib/lang/plan/dont_care'
require 'lib/lang/plan/value'
require 'lib/types/table/table_name'
require 'lib/types/table/index'

require "rubygems"

class Tuple
  include Comparable

  @@idGen = 0
  def initialize(*values)
    init
    @values = Array.new
    values.each {|v| @values << v}
    @values
  end

  def clone
    t = super
    t.values = @values.clone
    # can't freeze here.  Ruby clone copies the freeze state, so this would mean that
    # the next call to clone would return a frozen t.values.  Not nice.
    #    @values.freeze  
    # require 'ruby-debug'; debugger if t.values.frozen?
    return t
  end

  def init
    @schema = Schema.new(nil, nil)
    @count = 1
    @tid = @@idGen
    @@idGen += 1
  end

  attr_accessor :tid, :count, :schema, :values

  def append(var, val)
    var = var.clone
    var.position = schema.size
    @schema << var
    # require 'ruby-debug'; debugger if @values.frozen?
    @values << val
  end

  def to_s
    out = "<"
    if @values.size > 0
      @values.each_with_index do |value, i|
        out += ", " unless i == 0 
        #out += (value.nil? ? "nil".to_s : value.to_s) 
        if value.nil? 
          out += "nil"
        elsif (value.class <= UnsortedTupleSet) || (value.class <= Index) || (value.class <= Table)
          # stop; enough is enough
          out += "#{value.class}:#{value.object_id.to_s}(#{value.size} tuples)"
        else
          if value.class <= String
            out += '"' + value.to_s + '"'
          elsif value.class <= Float
            out += sprintf("%.2f", value)
          else
            out += value.to_s
          end
        end
      end
    else
      out += @tid.to_s
    end
    return out + ">"
  end

  def schema=(s)
    if s.size != size
      # require 'ruby-debug'; debugger
      err = "Schema assignment does not match tuple arity!  schema " + s.to_s+" (vs. tuple values ["
      values.each {|v| err <<  v.to_s + ", " }
      err[err.length-1] = "]" 
      err << ": length "+size.to_s+")"
      raise err
    end
    @schema = s
  end

  def <=>(o)
    return -1 if size != o.size

    0.upto(@values.size - 1) do |i|
      if (@values[i].nil? || o.values[i].nil?)
        if @values[i] != o.values[i]
          return -1
        end
      elsif (@values[i] <=> o.values[i]) != 0
        return @values[i] <=> o.values[i]
      end
    end
    return 0
  end

  def ==(o)
    (o.class == Tuple) && ((o <=> self) == 0)
  end

  def hash
    return to_s.hash
  end

  def size
    return @values.length
  end
  
  def int_value(i)
    return values[i]
  end
  
  def name_pos(i)
    p = @schema.variable_set[i].position
    if p.nil?
      ## require 'ruby-debug'; debugger
      print "SCHEMA: #{@schema}\n"
      print "TUPLE: #{values}\n"
      # require 'ruby-debug'; debugger
      raise("field "+i.to_s+" does not exist in tuple") 
    end
    return p
  end    
  def name_value(i)
    return values[name_pos(i)]
  end

# try not to call this; the class check is slow
  def value(i)
    if i.class <= Numeric
      return int_value(i)
    else
      return name_value(i)
    end
  end  

  def set_value(i, value)
    if i.class <= Numeric
      @values[i] = value
    elsif i.class == Variable
      pos = @schema.position(i.name)
      if pos.nil? or pos < 0
        append(i, value)
      else
        set_value(pos, value)
      end
    end
  end

  def tuple_type(name)
    @schema.schema_type(name)
  end

  def timestamp=(value)
    @timestamp = value
  end

  def timestamp
    @timestamp
  end

  def join(inner)
    jointup = Tuple.new

    # take care of all join variables first
    @schema.variables.each do |v|
      continue if v.class <= DontCare

      if inner.schema.contains(v)
        outerval = name_value(v.name)
        innerval = inner.value(v.name)
        if (outerval.nil? or innerval.nil?)
          jointup.append(v, nil) if outerval == innerval
        elsif value(v.name) != inner.name_value(v.name)
          return nil # Tuples don't join
        else
          jointup.append(v, value(v.name))
        end
      else
        jointup.append(v, value(v.name))
      end
    end

    # Append any variables from the inner that do not match join
    # variable.
    inner.schema.variables.each do |v|
      if v.class <= DontCare
        continue
      elsif not jointup.schema.contains(v)
        jointup.append(v, inner.name_value(v.name))
      end
    end

    return jointup
  end
end
