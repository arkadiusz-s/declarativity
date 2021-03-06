require 'lib/lang/plan/clause'
require 'lib/types/table/object_table'
#require 'lib/types/operator/watch_op'
class WatchClause < Clause
	def initialize(location, name, modifier)
#	  # require 'ruby-debug'; debugger
		super(location)
    @name = name
		@modifier = modifier
	end
	
	def to_s
		return "watch(" + @name + ", " + @modifier + ")."
	end

	def set(context, program)
		###Compiler.watch.force(Tuple.new(program, name, modifier, Watch.new(program, null, name, modifier)))
#		# require 'ruby-debug'; debugger
#  puts("installing watch on [#{program}, #{@name}, #{@modifier}]") if program == 'path'
		context.catalog.table(WatchTable.table_name).force(Tuple.new(program, @name, @modifier, WatchOp.new(context, program, nil, @name, @modifier)))
	end
end
