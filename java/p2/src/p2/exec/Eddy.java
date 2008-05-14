package p2.exec;

import java.util.List;

import p2.lang.plan.Predicate;
import p2.types.basic.Schema;
import p2.types.basic.TupleSet;
import p2.types.operator.Operator;
import p2.types.operator.Projection;
import p2.types.table.Key;
import p2.types.table.ObjectTable;

public class Eddy extends Query {

	private Projection head;
	
	private List<Operator> body;

	public Eddy(String program, String rule, Boolean delete, 
			    Predicate input, Projection output, 
			    List<Operator> body) {
		super(program, rule, delete, input, output.predicate());
		this.head = output;
		this.body = body;
	}
	
	public TupleSet evaluate(TupleSet input) {
		
		return null;
	}
}
