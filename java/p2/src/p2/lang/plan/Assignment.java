package p2.lang.plan;

import java.util.Set;
import p2.lang.plan.Predicate.Field;
import p2.types.basic.Schema;
import p2.types.basic.Tuple;
import p2.types.basic.TypeList;
import p2.types.exception.UpdateException;
import p2.types.operator.Operator;
import p2.types.operator.Assign;
import p2.types.table.Key;
import p2.types.table.ObjectTable;

public class Assignment extends Term {
	
	public static class AssignmentTable extends ObjectTable {
		public static final Key PRIMARY_KEY = new Key(0,1);
		
		public enum Field {PROGRAM, RULE, POSITION, OBJECT};
		public static final Class[] SCHEMA = { 
			String.class,    // Program name
			String.class,    // Rule name
			Integer.class,   // Rule position
			Assignment.class // Assignment object
		};

		public AssignmentTable() {
			super("assignment", PRIMARY_KEY, new TypeList(SCHEMA));
		}
		
		@Override
		protected boolean insert(Tuple tuple) throws UpdateException {
			Assignment object = (Assignment) tuple.value(Field.OBJECT.ordinal());
			if (object == null) {
				throw new UpdateException("Assignment object null");
			}
			object.program   = (String) tuple.value(Field.PROGRAM.ordinal());
			object.rule      = (String) tuple.value(Field.RULE.ordinal());
			object.position  = (Integer) tuple.value(Field.POSITION.ordinal());
			return super.insert(tuple);
		}
		
		@Override
		protected boolean remove(Tuple tuple) throws UpdateException {
			return super.remove(tuple);
		}
	}
	
	private Variable variable;
	
	private Expression value;
	
	public Assignment(Variable variable, Expression value) {
		this.variable = variable;
		this.value = value;
	}
	
	public String toString() {
		return variable.toString() + " := " + value.toString();
	}

	@Override
	public Set<Variable> requires() {
		return value.variables();
	}
	
	public Variable variable() {
		return this.variable;
	}
	
	public Expression value() {
		return this.value;
	}

	@Override
	public Operator operator() {
		return new Assign(this);
	}

	@Override
	public void set(String program, String rule, Integer position) {
		Tuple me = new Tuple(Program.assignment.name(), program, rule, position, this);
		try {
			Program.assignment.force(me);
		} catch (UpdateException e) {
			e.printStackTrace();
		}
	}

}
