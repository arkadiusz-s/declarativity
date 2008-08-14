require 'lib/lang/plan/variable'
class Aggregate < Variable 
	@@STAR = '*'
	
	def initialize(name, function, type)
		super(name, type)
		@function = function
	end
	
	def clone
		Aggregate.new(name, function, type)
	end

	def to_s
		@function.to_s + "<" + super + ">"
	end
	
	def functionName
		@function
	end
	
	def function
	  # set up a TupleFunction and send it a lambda for evaluate 
		doit = lambda do |t|
      return (name == @@STAR ? t.id : t.value(name))
    end
    
    r_lam = lambda do
      return @type
    end

    tmpClass = Class.new(TupleFunction)

    tmpClass.send :define_method, :evaluate do |tuple|
      doit.call(tuple)
    end
    tmpClass.send :define_method, :returnType do 
      rlam.call
    end
  
	  return tmpClass.new
  end
end

