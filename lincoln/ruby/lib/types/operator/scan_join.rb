require 'lib/types/operator/join'
require 'lib/types/table/table'

class ScanJoin < Join 
	def initialize(context, predicate, input)
		super(context, predicate, input)
		@table = context.catalog.table(predicate.name)
		if @table.nil?
		  raise "ScanJoin initialization failed: couldn't find table " + predicate.name.to_s 
	  end
	end
	
	def to_s
		return "NEST LOOP JOIN: PREDICATE[" + @predicate.to_s  + "]"
	end
	
	def evaluate(tuples) #TupleSet
		result = TupleSet.new("tmp", nil)
		innerTuples = @table.tuples
		tuples.each do |outer| 
		  innerTuples.each do |inner|
				inner.schema = @predicate.schema.clone
				
				if validate(outer, inner)
					join = outer.join(inner)
					if (!join.nil?) 
						result << join
					end
				end
			end
		end

		return result
	end

end
