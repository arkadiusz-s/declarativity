package p2.lang.plan;

import p2.types.basic.Tuple;
import p2.types.function.TupleFunction;

public class DontCare extends Variable {
	public final static String DONTCARE = "_";
	public static Long ids = 0L;

	public DontCare(Class type) {
		super("DC" + Long.toString(ids++), type);
	}
	
	@Override
	public TupleFunction function() {
		assert(position() >= 0);
		return super.function();
	}

}
