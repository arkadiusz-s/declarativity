require 'lib/exec/query'
require 'lib/types/exception/p2_runtime_exception'
class BasicQuery < Query

  def initialize(program, rule, isPublic, isDelete, event, head, body)
    super(program, rule, isPublic, isDelete, event, head)
    @aggregation = nil
    @body        = body
  end

  def to_s
    String   query = "Basic Query Rule " + rule + 
    ": input " + input.to_s
    @body.each do |oper| 
      query += " -> " + oper.to_s
    end
    query += " -> " + output().to_s
    return query
  end

  def evaluate(inval)
    if (@input.name != inval.name) then
      raise P2RuntimeException, "Query expects input " + inval.name.to_s + 
      " but got input tuples " + @input.name.to_s
    end

    tuples = TupleSet.new(@input.name)
    inval.each do |tuple| 
      tuple = tuple.clone
      tuple.schema = @input.schema.clone
      tuples << (tuple)
    end

    @body.each do |oper| 
      tuples = oper.evaluate(tuples)
    end

    return tuples
  end
end
