package jol.types.table;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jol.core.Runtime;
import jol.types.basic.BasicTupleSet;
import jol.types.basic.ConcurrentTupleSet;
import jol.types.basic.Tuple;
import jol.types.basic.TupleSet;
import jol.types.exception.BadKeyException;

public class ConcurrentHashIndex extends Index {

	/** A map containing the set of tuples with the same key. */
	private Map<Tuple, TupleSet> map;

	/**
	 * Create a new hash index. All tuples currently in
	 * the table will be added to the index by this constructor.
	 * @param context The runtime context.
	 * @param table The table that is to be indexed.
	 * @param key The key used to index the table.
	 * @param type The index type.
	 */
	public ConcurrentHashIndex(Runtime context, Table table, Key key, Type type) {
		super(context, table, key, type, true);
		this.map = new ConcurrentHashMap<Tuple, TupleSet>();

		for (Tuple t : table.tuples()) {
			insert(t);
		}
	}

	@Override
	public String toString() {
		String out = "Index " + table().name() + "\n";
		if (map != null) {
			out += map.toString() + "\n";
		}
		return out;
	}

	@Override
	protected void insert(Tuple t) {
		Tuple key = key().project(t);
		TupleSet tuples = this.map.get(key);

		if (tuples != null) {
		    tuples.add(t);
		} else {
			tuples = new ConcurrentTupleSet();
			tuples.refCount(false);
			tuples.add(t);
			this.map.put(key, tuples);
		}
	}

	@Override
	public TupleSet lookupByKey(Tuple key) throws BadKeyException {
		if (key.size() != key().size() && key().size() > 0) {
			throw new BadKeyException("Key had wrong number of columns.  " +
					"Saw: " + key.size() + " expected: " + key().size() + " key: " + key().toString());
		}

		TupleSet tuples = this.map.get(key);
		if (tuples != null)
		    return tuples;
		else
		    return new BasicTupleSet();
	}

	@Override
	protected void remove(Tuple t) {
		Tuple key = key().project(t);

		TupleSet tuples = this.map.get(key);
		if (tuples != null) {
		    tuples.remove(t);

		    if (tuples.isEmpty())
		        this.map.remove(key);
		}
	}

	@Override
	public Iterator<Tuple> iterator() {
		Set<Tuple> tuples = new HashSet<Tuple>();
		for (TupleSet set : this.map.values()) {
			tuples.addAll(set);
		}
		return tuples.iterator();
	}

}
