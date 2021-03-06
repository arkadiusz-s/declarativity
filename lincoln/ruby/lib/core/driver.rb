require 'lib/types/table/function_table'
require 'lib/types/basic/tuple_set'
require 'lib/types/table/aggregation_table'
require 'lib/types/table/event_table'
require 'lib/types/operator/watch_op'
require 'monitor'

class Driver < Monitor
  Infinity = 1.0/0
  @@threads = []

  class Flusher < TableFunctionTable
    class ScheduleUnit 
      def initialize(tuple)
        @time    = tuple.values[Field::TIME]
        @program = tuple.values[Field::PROGRAM]
        @name    = tuple.values[Field::TABLENAME]
        @insertions = TupleSet.new(@name)
        @deletions = TupleSet.new(@name)
      end

      attr_accessor :insertions, :deletions
      def <<(tuple)
        insertions = tuple.values[Field::INSERTIONS]
        deletions  = tuple.values[Field::DELETIONS]
#        require 'ruby-debug'; debugger unless insertions.nil? or insertions.class <= TupleSet
        @insertions.addAll(insertions) unless insertions.nil? 
        @deletions.addAll(deletions) unless deletions.nil? 
      end

      def tuple
        Tuple.new(@time, @program, @name, @insertions, @deletions)
      end

      def hash
        to_s.hash
      end

      def ==(o)
        return ((o.class <= ScheduleUnit) and (to_s == o.to_s))
      end

      def to_s
        @program + ":" + ":" + @time.to_s + ":" +  @name.to_s
      end
    end

    #  Aggregates the set of tuples into groups
    #  of tuples that are flushed into the same table.
    #  tuples is the set of tuples to be flushed
    #  returns an Aggregated set of tuples.
    def aggregate(tuples)
      units = Hash.new
      tuples.each do |tuple|
        unit = ScheduleUnit.new(tuple)
        units[unit] = unit unless units.has_key?(unit)
 #       require 'ruby-debug'; debugger unless tuple.values[Field::INSERTIONS].nil? or tuple.values[Field::INSERTIONS].class <= TupleSet
        units[unit] << tuple
      end

      aggregate = TupleSet.new(@name)
      units.keys.each do |unit|
        if (unit.insertions.size > 0 || unit.deletions.size > 0)
          aggregate << unit.tuple
        end
      end
      return aggregate
    end

    class Field
      TIME = 0
      PROGRAM = 1
      TABLENAME = 2
      INSERTIONS = 3
      DELETIONS = 4
    end

    @@SCHEMA =  [Integer, String, TableName, TupleSet, TupleSet]

    #  Creates a new flusher table function.
    #  context: The runtime context. 
    def initialize(context)
      super("flusher", TypeList.new(@@SCHEMA))
      @context = context
    end

    def insert(tuples, conflicts)
      delta = TupleSet.new(@name)
      aggregate(tuples).each do |tuple| 
        time       = tuple.values[Field::TIME]
        program    = tuple.values[Field::PROGRAM]
        name       = tuple.values[Field::TABLENAME]
        insertions = tuple.values[Field::INSERTIONS]
 #       require 'ruby-debug'; debugger unless insertions.nil? or insertions.class <= TupleSet
        
        deletions  = tuple.values[Field::DELETIONS]

        insertions = TupleSet.new(name) if insertions.nil?
        deletions  = TupleSet.new(name) if deletions.nil?

        #        # require 'ruby-debug'; debugger if name.name == "insertionQueue"
        next if (insertions.size == 0 and deletions.size == 0)

        #        # require 'ruby-debug'; debugger
        watch = @context.catalog.table(WatchTable.table_name)
        table = @context.catalog.table(name)
        # require 'ruby-debug'; debugger if table.nil?
        if (insertions.size > 0 || table.class <= AggregationTable)
#          require 'ruby-debug'; debugger if table.name.name == 'facts'
#          puts "XXXX Insertions found: #{insertions.to_s}"
          insertions = table.insert(insertions, deletions)

          if (table.class <= AggregationTable)
            watchRemove = watch.watched(program, name, WatchOp::Modifier::ERASE)
            watchRemove.evaluate(deletions) unless (watchRemove.nil?) 
          end
        else
          return TupleSet.new(name) if (table.table_type != Table::TableType::TABLE)
          #           require 'ruby-debug'; debugger
          
#          require 'ruby-debug'; debugger if name == TableName.new('compiler','dependency')

#          puts "XXXX DELETIONS found: #{deletions.to_s}" if !deletions.nil? and deletions.size > 0
          deletions = table.delete(deletions)

          watchRemove = watch.watched(program, name, WatchOp::Modifier::ERASE)
          watchRemove.evaluate(deletions) unless watchRemove.nil?
        end

        if (insertions.size > 0) 
          #         # require 'ruby-debug'; debugger
          watchAdd = watch.watched(program, name, WatchOp::Modifier::ADD)
          watchAdd.evaluate(insertions) unless watchAdd.nil?
        end

#        require 'ruby-debug'; debugger unless insertions.nil? or insertions.class <= TupleSet
        tuple.set_value(Field::INSERTIONS, insertions)
        tuple.set_value(Field::DELETIONS, deletions)
        delta << tuple
      end
      return delta
    end
  end

  #  Table function represents the query processor that evaluates 
  #  tuples using the query objects install by the runtime programs.
  #  The tuple format that this function expects is as follows:
  #  <Time, Program, TableName, Insertions, Deletions>
  #  Time: The evaluation time (usually taken from the system clock).
  #  Program: The program whose query objects are to evaluate the tuples.
  #  TableName: The name of the table to which the tuples refer.
  #  Insertions: Contains a set of tuples that represent the delta set
  #  of an insertion into the respective table.
  #  Deletions: Contains a set of tuples representing the delta set
  #  of a deletion from the respective table.
  #  NOTE: Deletions are not evaluated unless there are no insertions
  #  The return value of this table function contains a set of tuples
  #  that represent the output of the query evaluations. 

  class Evaluator < TableFunctionTable
    class Field
      TIME = 0
      PROGRAM = 1
      TABLENAME = 2
      INSERTIONS = 3
      DELETIONS = 4
    end
    @@SCHEMA =  [Integer, String, TableName, TupleSet, TupleSet]

    #  Creates a new evaluated based on the given runtime.
    #  @param context The runtime context. 
    def initialize(context)
      super("evaluator", TypeList.new(@@SCHEMA))
      @context = context
    end

    def insert(tuples, conflicts)
      delta = TupleSet.new(@name)
      tuples.each do |tuple|
        time       = tuple.values[Field::TIME]
        programName    = tuple.values[Field::PROGRAM]
        name       = tuple.values[Field::TABLENAME]
        insertions = tuple.values[Field::INSERTIONS]
        deletions  = tuple.values[Field::DELETIONS]

        deletions  = TupleSet.new(name) if deletions.nil?

        #        puts "INSERTING into #{name.to_s}"
        program = @context.program(programName)

        if !program.nil?
          result = evaluate(time, program, name, insertions, deletions)
          delta.addAll(result)
        else
          raise("EVALUATOR ERROR: unknown program " + programName + "!")
        end
      end
      return delta
    end

    #  Evaluates the given insertions/deletions against the program queries.
    #  @param time The current time.
    #  @param program The program whose queries will evaluate the tuples.
    #  @param name The name of the table to which the tuples refer.
    #  @param insertions A delta set of tuple insertions.
    #  @param deletions A delta set of tuple deletions.
    #  @return The result of the query evaluation. NOTE: if insertions.size > 0 then
    #  deletions will not be evaluated but rather deferred until a call is made with
    #  insertions.size == 0.
    #  @throws UpdateException On evaluation error.
    def evaluate(time, program, name, insertions, deletions) 
      continuations = Hash.new

      watch = @context.catalog.table(WatchTable.table_name)
      watchInsert = watch.watched(program.name, name, WatchOp::Modifier::INSERT)
      watchDelete = watch.watched(program.name, name, WatchOp::Modifier::DELETE)

      #      # require 'ruby-debug'; debugger if name.to_s == 'runtime::insertionQueue'
      querySet = program.get_queries(name)
      #      querySet.each { |q| puts "tuple from #{name} triggers #{q.rule}"}

      #      program.dump_queries

      return TupleSet.new(name) if (querySet.nil?) # Done

      if (insertions.size > 0) 
        # We're not going to deal with the deletions yet.
        continuation(continuations, time, program.name, Predicate::Event::DELETE, deletions)

        querySet.each do |query|
          if (query.event != Predicate::Event::DELETE)
            if (!watchInsert.nil?)
              watchInsert.rule = query.rule
              watchInsert.evaluate(insertions)
            end
            #            puts "evaluating #{query.to_s} on #{insertions.size} tuples"
            result = query.evaluate(insertions)
            next if (result.size == 0)

            if (query.isDelete)
              continuation(continuations, time, program.name, Predicate::Event::DELETE, result)
            else 
              continuation(continuations, time, program.name, Predicate::Event::INSERT, result)
            end
          end
        end
      elsif (deletions.size > 0)
        querySet.each do |query|
          output = @context.catalog.table(query.output.name)
          if (query.event == Predicate::Event::DELETE or
            (output.table_type == Table::TableType::TABLE and query.event != Predicate::Event::INSERT))
            if (!watchDelete.nil?) 
              watchDelete.rule = query.rule
              watchDelete.evaluate(deletions)
            end
            result = query.evaluate(deletions)
            next if (result.size == 0)

            if (!query.isDelete && output.table_type == Table::TableType::EVENT) 
              # Query is not a delete and it's output type is an event.
              continuation(continuations, time, program.name, Predicate::Event::INSERT, result)
            elsif (output.table_type == Table::TableType::TABLE)
              continuation(continuations, time, program.name, Predicate::Event::DELETE, result)
            else
              raise "Query " + query + " is trying to delete from table " + output.name + "?"
            end
          end
        end
      end

      delta = TupleSet.new(name)
      continuations.values.each do |continuation|
        ins  = continuation.values[Field::INSERTIONS]
        raise "insertions not a TupleSet" unless ins.nil? or ins.class <= TupleSet
        dels = continuation.values[Field::DELETIONS]
        delta << continuation if (ins.size > 0 || dels.size > 0)
      end

      return delta
    end

    # Helper routine that packages up result tuples grouped by (time, program, tablename).
    # @param continuations Hashtable containing the tuple groups
    # @param time Current time.
    # @param program Program that evaluated the tuples.
    # @param event Indicates whether the tuples are insertions or deletions.
    # @param result The result tuples taken from the output of a query.
    def continuation(continuations, time, program, event, result)
      key = program.to_s + "." + result.name.to_s

      if (!continuations.has_key?(key))
        tuple = Tuple.new(time, program, result.name, TupleSet.new(result.name), TupleSet.new(result.name))
        continuations[key] = tuple
      end

      if (event == Predicate::Event::INSERT)
        insertions = continuations[key].values[Field::INSERTIONS]
#        require 'ruby-debug'; debugger unless insertions.nil? or insertions.class <= TupleSet
        insertions.addAll(result)
      else
        deletions = continuations[key].values[Field::DELETIONS]
        deletions.addAll(result)
      end
    end

  end
  # Tasks are used to inject tuples into the schedule.
  class Task 
    attr_accessor :insertions, :deletions, :program, :name
  end

  # Creates a new driver.
  # @param context The runtime context.
  # @param schedule The schedule table.
  # @param clock The system clock table.
  def initialize(context, schedule, clock)
    @tasks = Array.new
    @schedule = schedule
    @clock = clock
    @evaluator = Evaluator.new(context)
    @flusher = Flusher.new(context)
    @context = context

    context.catalog.register(@evaluator)
    context.catalog.register(@flusher)
    # important to call super on Monitor initialization
    super()
    @cond_var = self.new_cond
  end

  # Set the runtime program.
  # @param runtime The runtime program. 
  attr_accessor :runtime, :cond_var, :context

  # * Add a task to the task queue. This will be evaluated
  # * on the next clock tick.
  # * @param task The task to be added.
  def task(task)
#    require 'ruby-debug'; debugger if task.name.name == 'compiler'
    @tasks << task
  end

  # * The main driver loop.
  # * The loop is responsible for 1. updating the clock 2. scheduling tasks.
  # * Driver algorithm:
  # *  loop forever {
  # *    update to new clock value;
  # *    evaluate (insertion of clock value);
  # *    evaluate (all tasks);
  # *    evaluate (deletion of clock value);
  # *  }
  def run_driver
    time = @clock.time(0)
    while 1
      synchronize do
        #        puts("============================ INSERTING TIME #{time.tups[0].to_s} ===================")
        #        # require 'ruby-debug'; debugger
        evaluate(runtime.name, time.name, time, nil) # Clock insert new time
        print("========================     EVALUATE SCHEDULE TIME #{@clock.current}    =========================\n")
        #        print("============================     #{@tasks.size} TASKS     ===========================\n")
        #        print("===========================     #{@schedule.cardinality} SCHED QUEUE     ========================\n")

        # Evaluate task queue
        @tasks.each_with_index do |task, i| 
          #          # require 'ruby-debug'; debugger
          #          print "======================== TASK #{i}: #{task.name} ========================\n"
          #          puts "    Insertions: #{task.insertions.tups[0].to_s}"
          #          puts "    Deletions: #{task.deletions.tups[0].to_s}"
          evaluate(task.program, task.name, task.insertions, task.deletions)
        end
        @tasks.clear # Clear task queue.
        evaluate(runtime.name, time.name, nil, time) # Clock delete current
        print("========================== ======================== ===========================\n");

        # Check for new tasks or schedules, if none wait.
        @cond_var.wait_while {@tasks.size == 0 && @schedule.cardinality == 0}
        if (@schedule.cardinality > 0)
          time = @clock.time(@schedule.min)
        else 
          time = @clock.time(@clock.current + 1)
        end
      end
    end
  end

  # * Helper function that calls the flusher and evaluator table functions.
  # * This function will evaluate the passed in tuples to fixedpoint (until
  # * no further tuples exist to evaluate).
  # * @param program The program that should be executed.
  # * @param name The table name to which the tuples refer.
  # * @param insertions The set of insertions to evaluate.
  # * @param deletions The set of deletions to evaluate.
  # * @throws UpdateException
  def evaluate(program, name, insertions, deletions)
    insert = TupleSet.new("insert")
    delete = TupleSet.new("delete")
    insert << Tuple.new(@clock.current, program, name, insertions, deletions)
    # Evaluate until nothing remains.
    while (insert.size > 0 || delete.size > 0) do
      delta = nil
      while (insert.size > 0) do
        delta = @flusher.insert(insert, nil)
        delta = @evaluator.insert(delta, nil)
        insert.clear
        split(delta, insert, delete)
      end

      while(delete.size > 0) do
        delta = @flusher.insert(delete, nil)
        delta = @evaluator.insert(delta, nil)
        delete.clear
        split(delta, insert, delete)
      end
    end
  end

  def split(tuples, insertions, deletions)
    tuples.each do |tuple| 
      insert = tuple.clone;
      delete = tuple.clone;
      insert.set_value(Evaluator::Field::INSERTIONS, tuple.values[Evaluator::Field::INSERTIONS])
      insert.set_value(Evaluator::Field::DELETIONS, nil)
      delete.set_value(Evaluator::Field::INSERTIONS, nil)
      delete.set_value(Evaluator::Field::DELETIONS, tuple.values[Evaluator::Field::DELETIONS])

      insertions << insert
#      require 'ruby-debug'; debugger unless insertions.nil? or insertions.class <= TupleSet
      deletions << delete
    end
  end
end
