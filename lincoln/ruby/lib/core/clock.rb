require 'lib/types/exception/update_exception'
class Clock < ObjectTable 
	@@PRIMARY_KEY = Key.new(0)
	@@TABLENAME = TableName.new(GLOBALSCOPE, "clock")
	
	class Field
	  LOCATION = 0
	  CLOCK = 1
  end
  
	@@SCHEMA = [String, Integer] # location, clockvalue
	
	def initialize(context, location)
		super(context, @@TABLENAME, @@PRIMARY_KEY,  TypeList.new(@@SCHEMA))
		@location = location;
		@clock = 0
	end
	
	def current()
		@clock
	end
	
	def insert_tup(tuple)
#	  # require 'ruby-debug'; debugger
		time = tuple.values[Field::CLOCK]
		if (time < @clock) then
#		  require 'ruby-debug'; debugger
			raise UpdateException, "Invalid clock time " +  time.to_s + " current clock value = " + @clock.to_s
		end
		@clock = time
#		puts "XXXXXXXXXX CLOCK TIME SET TO #{time}  XXXXXXXXX" 
#		require 'ruby-debug'; debugger if time == 3
		return super(tuple)
	end
	
	def time(t)
		me = TupleSet.new(name)
		me << Tuple.new(@location, t)
		return me
	end
end
