package p2.core;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import p2.exec.Query;
import p2.lang.plan.Predicate;
import p2.lang.plan.Program;
import p2.types.basic.Tuple;
import p2.types.basic.TupleSet;
import p2.types.basic.TypeList;
import p2.types.exception.P2RuntimeException;
import p2.types.exception.UpdateException;
import p2.types.table.Key;
import p2.types.table.ObjectTable;
import p2.types.table.Table;

public class Driver implements Runnable {
	
	public static class DriverTable extends ObjectTable {
		public static final Key PRIMARY_KEY = new Key(0,1);

		public enum Field{PROGRAM, TUPLENAME, TUPLESET};
		public static final Class[] SCHEMA =  {
			String.class,   // Program name
			String.class,   // Tuple name
			TupleSet.class  // TupleSet to evaluate
		};

		public DriverTable() {
			super("driver", PRIMARY_KEY, new TypeList(SCHEMA));
		}
		
		protected boolean insert(Tuple tuple) throws UpdateException {
			java.lang.System.err.println("DRIVER TUPLE: " + tuple);
			String   name   = (String) tuple.value(Field.PROGRAM.ordinal());
			TupleSet tuples = (TupleSet) tuple.value(Field.TUPLESET.ordinal());
			p2.core.System.driver().evaluate(System.program(name), tuples);
			return true; // Do not store this tuple.
		}
	}

	/** The schedule queue. */
	private Program runtime;
	
	private Schedule schedule;
	
	private Clock clock;
	
	public Driver(Program runtime, Schedule schedule, Clock clock) {
		this.runtime = runtime;
		this.schedule = schedule;
		this.clock = clock;
	}
	

	public void run() {
		final TupleSet scheduleInsertions = new TupleSet(schedule.name());
		schedule.register(new Table.Callback() {
			public void deletion(TupleSet tuples) { /* Don't care. */ }
			public void insertion(TupleSet tuples) {
				java.lang.System.err.println("SCHEDULE INSERTION HANDLER " + tuples);
				synchronized (schedule) {
					scheduleInsertions.addAll(tuples);
					schedule.notify();
				}
			}
		});
		
		TupleSet factSchedule = new TupleSet(schedule.name());
		for (TupleSet fact : this.runtime.facts().values()) {
			Table table = Table.table(fact.name());
			try {
				if (!table.isEvent()) {
					TupleSet delta = table.insert(fact);
					if (delta.size() > 0) {
						factSchedule.add(
							new Tuple(schedule.name(), clock.current(), runtime.name(), 
									  delta.name(), Predicate.EventModifier.INSERT.toString(), delta));
					}
				}
				else {
					factSchedule.add(
						new Tuple(schedule.name(), clock.current(), runtime.name(), 
								  fact.name(), Predicate.EventModifier.NONE.toString(), fact));
				}
			} catch (UpdateException e) {
				e.printStackTrace();
				java.lang.System.exit(0);
			}
		}
		
		if (factSchedule.size() > 0) {
			try {
				schedule.insert(factSchedule);
			} catch (UpdateException e) {
				e.printStackTrace();
				java.lang.System.exit(1);
			}
		}
		
		Hashtable<String, TupleSet> insertions = new Hashtable<String, TupleSet>();
		while (true) {
			synchronized (schedule) {
				if (schedule.size() == 0) {
					try {
						java.lang.System.err.println("Nothing scheduled at this time.");
						schedule.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
						java.lang.System.exit(1);
					}
				}
			}
			
			try {
				assert(this.clock.current() < schedule.min());
				
				// java.lang.System.err.println("CURRENT CLOCK " + clock.current() + " MIN SCHEDULED TIME " + schedule.min());
				/* Evaluate queries that run off the start clock. */
				if (this.clock.current() < schedule.min()) {
					TupleSet clock = this.clock.set(schedule.min());
					evaluate(this.runtime, clock); // Eval new clock
				}
				
				/* Schedule until nothing left in this clock. */
				while (scheduleInsertions.size() > 0) {
					java.lang.System.err.println("EXECUTE SCHEDULE INSERTIONS: " + scheduleInsertions);
					TupleSet scheduled = new TupleSet(schedule.name());
					scheduled.addAll(scheduleInsertions);
					scheduleInsertions.clear();
					Hashtable<String, TupleSet> continuations = evaluate(this.runtime, scheduled);
					TupleSet reschedule = new TupleSet(schedule.name());
					for (TupleSet continuation : continuations.values()) {
						reschedule.add(new Tuple(schedule.name(), clock.current(), runtime.name(),
								                 continuation.name(), 
								                 Predicate.EventModifier.INSERT.toString(),
								                 continuation));
					}
					schedule.insert(reschedule);
				}
			} catch (UpdateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/** 
	 * Run fixpoint evaluation for a single strata. 
	 */
	private Hashtable<String, TupleSet> evaluate(Program program, TupleSet insertion) {
		Hashtable<String, Set<Query>> queries       = program.queries();
		Hashtable<String, Table>      tables        = program.tables();
		Hashtable<String, TupleSet>   deletions     = new Hashtable<String, TupleSet>();
		Hashtable<String, TupleSet>   continuations = new Hashtable<String, TupleSet>();
		
		try {
			evaluate(program, insertion, deletions, continuations);
		} catch (P2RuntimeException e) {
			e.printStackTrace();
			java.lang.System.exit(1);
		}

		java.lang.System.err.println("APPLY DELETIONS: " + deletions.keySet());
		while (deletions.size() > 0) {
			Hashtable<String, TupleSet> delta = new Hashtable<String, TupleSet>();
			
			for (String name : deletions.keySet()) {
				Table table = tables.get(name);
				try {
					java.lang.System.err.println("REMOVE FROM TABLE " + name + " TUPLES " + deletions.get(name));
					TupleSet deletionDelta = table.remove(deletions.get(name));
					if (deletionDelta.size() > 0) {
						Set<Query> querySet = queries.get(name);
						for (Query query : querySet) {
							TupleSet result = query.evaluate(deletionDelta);
							if (tables.containsKey(result.name())) {
								/* These tuples must be delete in order
								 * to provide proper view maintenance. */
								update(delta, result); 
							}
						}
					}
				} catch (UpdateException e) {
					e.printStackTrace();
					java.lang.System.exit(1);
				} catch (P2RuntimeException e) {
					e.printStackTrace();
					java.lang.System.exit(1);
				}
			}
			deletions = delta;
		}
		return continuations;
	}
	
	/**
	 * Fixed point evaluation of all events in the event set.
	 * @throws P2RuntimeException 
	 */
	private void evaluate(Program program, 
			              TupleSet tuples, 
			              Hashtable<String, TupleSet> deletions,
			              Hashtable<String, TupleSet> continuations) throws P2RuntimeException {
		Hashtable<String, Set<Query>> queries = program.queries(); 
		Hashtable<String, Table>      tables  = program.tables();
		
		java.lang.System.err.println("EVALUATING PROGRAM " + program.name() + " on tupleset " + tuples);
		if (!queries.containsKey(tuples.name())) {
			// TODO log unknown tuple set
			java.lang.System.err.println("Unknown tuple set " + tuples.name() + 
					                     " in program " + program);
			return;
		}
		
		while (!tuples.isEmpty()) {
			TupleSet delta = new TupleSet(tuples.name());
			Set<Query> querySet = queries.get(tuples.name());
			for (Query query : querySet) {
				TupleSet result = query.evaluate(tuples);
				if (result.size() == 0) continue;
				java.lang.System.err.println("============================");
				java.lang.System.err.println("======= EVALUATE ===========");
				java.lang.System.err.println("QUERY: " + query);
				java.lang.System.err.println("INPUT: " + tuples);
				java.lang.System.err.println("OUTPUT: " + result);
				if (result.size() == 0) continue;
				
				if (tables.containsKey(result.name())) {
					if (query.delete()) {
						update(deletions, result);
					}
					else {
						try {
							TupleSet conflicts = tables.get(result.name()).conflict(result);
							result = tables.get(result.name()).insert(result);
							if (delta.name().equals(result.name())) {
								delta.addAll(result); // Keep evaluating recursive rule.
							}
							else {
								if (!continuations.containsKey(result.name())) {
									continuations.put(result.name(), result);
								}
								else {
									continuations.get(result.name()).addAll(result);
								}
							}
							update(deletions, conflicts);
						} catch (UpdateException e) {
							e.printStackTrace();
							java.lang.System.exit(0);
						}
					}
				}
				else {
					java.lang.System.err.println("EVALUATE EVENT RESULT: " + result);
					/* Fixpoint evaluation on event */
					evaluate(program, result, deletions, continuations);
				}
			}
			tuples.clear();
			tuples.addAll(delta);
		}
		
	}

	private void update(Hashtable<String, TupleSet> buffer, TupleSet result) {
		if (buffer.containsKey(result.name())) {
			buffer.get(result.name()).addAll(result);
		}
		else {
			buffer.put(result.name(), result);
		}
	}
}
