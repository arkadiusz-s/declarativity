package p2.lang.plan;

import java.util.Set;

import p2.types.function.TupleFunction;

public abstract class Expression {
	
	private xtc.tree.Location location;
	
	private int position;
	
	public void location(xtc.tree.Location location) {
		this.location = location;
	}
	
	public xtc.tree.Location location() {
		return this.location;
	}

	@Override
	public abstract String toString();
	
	public int position() {
		return this.position;
	}
	
	public void position(int position) {
		this.position = position;
	}
	
	/**
	 * @return The java type of the expression value.
	 */
	public abstract Class type();
	
	public abstract Set<Variable> variables();
	
	public abstract TupleFunction function();
	
}
