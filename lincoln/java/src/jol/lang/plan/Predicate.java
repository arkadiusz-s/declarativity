package jol.lang.plan;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jol.core.Runtime;
import jol.types.basic.Schema;
import jol.types.basic.Tuple;
import jol.types.basic.TypeList;
import jol.types.exception.UpdateException;
import jol.types.operator.AntiScanJoin;
import jol.types.operator.IndexJoin;
import jol.types.operator.Operator;
import jol.types.operator.ScanJoin;
import jol.types.table.HashIndex;
import jol.types.table.Index;
import jol.types.table.Key;
import jol.types.table.ObjectTable;
import jol.types.table.Table;
import jol.types.table.TableName;

public class Predicate extends Term implements Iterable<Expression> {
	public enum Field{PROGRAM, RULE, POSITION, EVENT, OBJECT};
	public static class PredicateTable extends ObjectTable {
		public static final TableName TABLENAME = new TableName(GLOBALSCOPE, "predicate");
		public static final Key PRIMARY_KEY = new Key(0,1,2);
		
		public static final Class[] SCHEMA =  {
			String.class,     // program name
			String.class,     // rule name
			Integer.class,    // position
			String.class,     // Event
			Predicate.class   // predicate object
		};

		public PredicateTable(Runtime context) {
			super(context, TABLENAME, PRIMARY_KEY, new TypeList(SCHEMA));
		}
		
		@Override
		protected boolean insert(Tuple tuple) throws UpdateException {
			Predicate object = (Predicate) tuple.value(Field.OBJECT.ordinal());
			if (object == null) {
				throw new UpdateException("Predicate object null");
			}
			object.program   = (String) tuple.value(Field.PROGRAM.ordinal());
			object.rule      = (String) tuple.value(Field.RULE.ordinal());
			object.position  = (Integer) tuple.value(Field.POSITION.ordinal());
			return super.insert(tuple);
		}
		
		@Override
		protected boolean delete(Tuple tuple) throws UpdateException {
			return super.delete(tuple);
		}
	}
	
	public enum Event{NONE, INSERT, DELETE};
	
	public Runtime context;
	
	private boolean notin;
	
	private TableName name;
	
	private Event event;
	
	private Arguments arguments;
	
	private Schema schema;
	
	public Predicate(Runtime context, boolean notin, TableName name, Event event, List<Expression> arguments) {
		super();
		this.context = context;
		this.notin = notin;
		this.name = name;
		this.event = event;
		this.arguments = new Arguments(this, arguments);
	}
	
	public Predicate(Runtime context, boolean notin, TableName name, Event event, Schema schema) {
		super();
		this.context = context;
		this.notin = notin;
		this.name = name;
		this.event = event;
		this.schema = schema;
		this.arguments = new Arguments(this, schema.variables());
	}
	
	protected Predicate(Predicate pred) {
		super();
		this.context   = pred.context;
		this.notin     = pred.notin;
		this.name      = pred.name;
		this.event     = pred.event;
		this.arguments = pred.arguments;
		this.schema    = pred.schema;
	}
	
	public Schema schema() {
		return schema;
	}
	
	public boolean notin() {
		return this.notin;
	}
	
	public Event event() {
		return this.event;
	}
	
	void event(Event event) {
		this.event = event;
	}
	
	public TableName name() {
		return this.name;
	}
	
	public Variable locationVariable() {
		for (Expression argument : this) {
			if (argument instanceof Variable && ((Variable) argument).loc()) {
				return (Variable) argument;
			}
		}
		return null;
	}
	
	public boolean containsAggregation() {
		for (Expression e : arguments) {
			if (e instanceof Aggregate) {
				return true;
			}
		}
		return false;
	}

	/**
	 * An iterator over the predicate arguments.
	 */
	public Iterator<Expression> iterator() {
		return this.arguments.iterator();
	}
	
	public Expression argument(Integer i) {
		return this.arguments.get(i);
	}
	
	public List<Expression> arguments() {
		return this.arguments;
	}
	
	void arguments(List<Expression> arguments) {
		this.arguments = new Arguments(this, arguments);
	}
	
	@Override
	public String toString() {
		assert(schema.size() == arguments.size());
		String value = (notin ? "notin " : "") + name + "(";
		if (arguments.size() == 0) {
			return value + ")";
		}
		value += arguments.get(0).toString();
		for (int i = 1; i < arguments.size(); i++) {
			value += ", " + arguments.get(i);
		}
		return value + ")";
	}

	@Override
	public Set<Variable> requires() {
		Set<Variable> variables = new HashSet<Variable>();
		for (Expression<?> arg : arguments) {
			if (!(arg instanceof Variable)) {
				variables.addAll(arg.variables());
			}
		}
		return variables;
	}

	@Override
	public Operator operator(Runtime context, Schema input) {
		/* Determine the join and lookup keys. */
		Key lookupKey = new Key();
		Key indexKey  = new Key();
		for (Variable var : this.schema.variables()) {
			if (input.contains(var)) {
				indexKey.add(var.position());
				lookupKey.add(input.variable(var.name()).position());
			}
		}
		
		if (notin) {
			return new AntiScanJoin(context, this, input);
		}
		
		Table table = context.catalog().table(this.name);
		Index index = null;
		if (indexKey.size() > 0) {
			if (table.primary().key().equals(indexKey)) {
				index = table.primary();
			}
			else if (table.secondary().containsKey(indexKey)) {
				index = table.secondary().get(indexKey);
			}
			else {
				index = new HashIndex(context, table, indexKey, Index.Type.SECONDARY);
				table.secondary().put(indexKey, index);
			}
		}
		
		if (index != null) {
			return new IndexJoin(context, this, input, lookupKey, index);
		}
		else {
			return new ScanJoin(context, this, input);
		}
	}
	
	@Override
	public void set(Runtime context, String program, String rule, Integer position) throws UpdateException {
		context.catalog().table(PredicateTable.TABLENAME).force(new Tuple(program, rule, position, event.toString(), this));
		
		this.schema = new Schema(name());
		for (Expression arg : arguments) {
			if (arg instanceof Variable) {
				this.schema.append((Variable) arg);
			}
			else {
				this.schema.append(new DontCare(arg.type()));
			}
		}
	}
	
	public String dotName() {
		return name().scope + name().name;
	}
	
	public String toDot() {
		String dot = "\n" + dotName() + " [shape=ellipse, label = \"";
		dot += name() + 
		       schema().toString() +
		       "\" ];\n";
		return dot;
	}
}
