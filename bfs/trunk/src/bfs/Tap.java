package bfs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jol.core.JolSystem;
import jol.core.Runtime;
import jol.lang.plan.Aggregate;
import jol.lang.plan.Assignment;
import jol.lang.plan.Expression;
import jol.lang.plan.Predicate;
import jol.lang.plan.Rule;
import jol.lang.plan.Term;
import jol.lang.plan.Value;
import jol.lang.plan.Variable;
import jol.types.basic.BasicTupleSet;
import jol.types.basic.Tuple;
import jol.types.basic.TupleSet;
import jol.types.exception.JolRuntimeException;
import jol.types.exception.UpdateException;
import jol.types.table.Table;
import jol.types.table.TableName;
import jol.types.table.Table.Callback;

// abandon all hope, ye who enter here

public class Tap {
    public static void main(String[] args) throws JolRuntimeException, UpdateException, InterruptedException {

        String arg = args[0];
        if (arg.startsWith("-l")) {
            /* listen mode. */
            Tap n = new Tap(5678);
            n.doListen();
        } else if (arg.startsWith("-f")) {
            // it doesn't matter which port we choose
            Tap n = new Tap(7654);
            n.doFinish();
            n.shutdown();
        } else if (arg.startsWith("-r")) {
            System.out.println("ARRR: "+arg);
            String program = args[1];
            String rule = args[2];
            List<String> path = new LinkedList<String>();
            for (int i=3; i < args.length; i++) {
                System.out.println("put " + args[i] + " on stack");
                path.add(args[i]);
            }
            // it doesn't matter which port we choose
            Tap n = new Tap(1234);
            n.doLookup(program, rule, path);
        } else {
            usage();
        }

        System.out.println("DONE\n");
    }

    private static void usage() {
        //System.err.println("Usage: bfs.Tap <olg source>, <sink>");yy
        System.err.println("Usage: bfs.Tap <opt>\n where ops is one of:\n\t-l\tlisten, or\n\t-f finish\n");
        System.exit(1);
    }

    public JolSystem system;
    public String rewrittenProgram;
    private String sink;
    private FileOutputStream watcher;
    private Map defines;
    private List<Rule> ruleList;
    private Map<String, Predicate> predHash;


    public Tap(int port) throws JolRuntimeException, UpdateException {
        this.system = Runtime.create(Runtime.DEBUG_ALL, System.err, port);
        init(sink);
    }

    public Tap(JolSystem s, String sink) {
        /* this class is being instantiated
           in a running JolSystem;
        */
        this.system = s;
        init(sink);
    }

    private void init(String sink) {
        this.sink = sink;
        this.ruleList = new LinkedList<Rule>();
		this.responseQueue = new SimpleQueue<Object>();
        this.predHash = new HashMap<String, Predicate>();
		this.firings = new HashMap<String, Integer>();
        this.rewrittenProgram = "";
        this.defines = new HashMap();
    }


    public static String join(List l, String delim, boolean quotes) {
        String ret = "";
        Iterator i = l.iterator();
        while (i.hasNext()) {
            Object current = i.next();
            String nStr = current.toString();
            if (quotes)
                nStr = "\"" + nStr + "\"";

            ret += nStr;

            if (i.hasNext()) {
                ret += delim;
            }
        }

        return ret;
    }

    public static String join(List l) {
    	return join(l, ",", false);
    }

    public static String sift(List l) {
        List<String> goods = new LinkedList<String>();

        for (Object i : l) {
            if (i.getClass() == jol.lang.plan.Assignment.class) {
                // we do not care about assignments; they are not preconditions, but
                // merely projection (that often use side-effecting (ie sequences) or
                // non-deterministic (ie clocks) functions anyway.

                // we use a separate rewrite to capture the actual projections
            } else {
                String clause = i.toString();
                /* hack attack */
                String res = clause.replace("BOOLEAN","").replace("MATH","").replace("@","").replace(":0","");
                /* super hack */
                //if (!res.equals("") && !res.matches("Invoker:")) {
                if (!res.equals("")) {
                    goods.add(res);
                }
            }
        }

        return join(goods, ",\n\t", false);
    }

    public String header() {

        String head = "program tap;\n" +
            "import jol.core.Runtime;\n" +
            "define(tap_precondition, keys(0,1,2,3), {String, String, String, Long, Integer});\n" +
            "define(tap_send, {String, String, String, String,  Long, Integer});\n" +
            "define(tap_universe, {String, String, String, String, String});\n" +
            "define(tap_chaff, {String, String});\n" +
            "tap_chaff(\"foo\", \"" + Conf.getTapSink() + "\");\n" +
            "watch(tap_precondition, a);\n" +
            "tap_send(@Sink, Program, Rule, Head,  Ts, Id) :- \n" +
            "\ttap_precondition(Program, Rule, Head,  @Ts, Id),\n" +
            rfooter();

        return head;
    }

    public String conjoin(String delim, boolean quotes, String... arg) {
        List<String> l = new LinkedList<String>();
        for (String s : arg) {
            l.add(s);
        }
        return join(l, delim, quotes);
    }
    public boolean isNetworkRule(Rule r) {
        Variable headLoc = r.head().locationVariable();
        if (headLoc != null) {
            for (Term t: r.body()) {
                if (t.getClass() == Predicate.class) {
                    Predicate p = (Predicate)t;
                    Variable pLVar = p.locationVariable();
                    if (pLVar != null) {
                        if (pLVar.toString() != headLoc.toString()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public String precondition(Rule rule) {
        String name = (rule.isDelete ? "delete-" : "") + rule.name;
        String body = sift(rule.body());

        /* if this is a network rule, count that separately. */

        String r =
            "public tap_precondition(" + conjoin(", ", true, rule.program, name, rule.head().name().name) + ",Ts, count<*>) :-\n\t" +
            conjoin(",\n\t", false, body, "Ts := java.lang.System.currentTimeMillis();\n\n");



        return r;
    }


    public String summarize() {
		StringBuilder sb = new StringBuilder();
        for (Rule r : ruleList) {
            String prov = provenance(r);
			//System.out.println("PROV: " + prov);
			sb.append(prov);
        }

		System.out.println("-------------------------PROGRAM:-------------------------\n");

		String finish = "program provenance;\n" + allDefines() + sb.toString();
		return finish;
    }

	public String provHead(Predicate p) {
		return  p.name().scope.toString() + "__" + "prov_" + p.name().name.toString();
	}


	private String makeDefine(List<Expression> args, String name) {

		List<String> schema = new ArrayList<String>();
		for (Expression v : args) {
			if (v.type() != null)
				schema.add(v.type().toString().replace("class ","").replace("interface ",""));
			else
				schema.add("java.lang.Object");
		}
		List<String> keys = new ArrayList<String>();
		for (Integer i=0; i < schema.size(); i++) {
			keys.add(i.toString());
		}
		String key = "keys(" + join(keys, ",", false) + ")";
		String define = "define(" + name + ", " + key + ", {" + join(schema, ", ",false) + ", String});\n";
		String watch = "watch(" + name + ", ae);";
		return define + watch;
	}

	private void checkDefine(List<Expression> args, String name) {
		String define = makeDefine(args, name);

		if (defines.get(name) == null) {
			defines.put(name, define);
		}
	}

	private String allDefines() {
        Set s = this.defines.entrySet();
        Iterator i = s.iterator();
		StringBuilder sb = new StringBuilder();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            String key = (String) me.getKey();
            String value = (String) me.getValue();
			sb.append(value + "\n");
        }
		return sb.toString();
	}

	public String nullType(Assignment ass) {
		Class type = ass.value().type();
		String res;
		if (type == String.class) {
			res = "\"\"";
		} else if (type == int.class) {
			res = "-1";
		} else if (type == Integer.class) {
			res = "-1";
		} else if (type == Float.class) {
			res = "-1.0";
		} else {
			res = "(" + type.toString().replace("class ", "") + ")null";
		}

		return ass.variable().toString() + " := " + res;
	}

    public String provenance(Rule r) {

		List<String> rules = new ArrayList<String>();
		List<String> body = new ArrayList<String>();
		List<String> provenances = new ArrayList<String>();
		int pcnt = 0;
        /* for each element of the rule's body, create a new rule */
        for (Term t : r.body()) {
            if (t.getClass() == Predicate.class) {
                /* new rule */
                Predicate p = (Predicate) t;

                String newName = provHead(p);
				checkDefine(p.arguments(), newName);

                List<Expression> l = new LinkedList<Expression>(p.arguments());
                List<Expression> lSub = new LinkedList<Expression>(p.arguments());
				String prov = "Provenance" + (pcnt++);
				provenances.add(prov);
                l.add(new Variable(null, prov, String.class));
				lSub.add(new Variable(null, "Provenance", String.class));
                if (predHash.containsKey(p.name().name.toString()) && (p.locationVariable() == null)) {
					// this is an edb predicate.
                   	String mini = "public\n" + newName + "(" +
                       	join(lSub, ", ", false) +
                       	") :-\n\t" +
                       	p.toString() + ",\n\t" +
                       	"Provenance := \"|\" + jol.core.Runtime.idgen().toString();\n";
					rules.add(mini);
				}
				body.add(newName + "(" + join(l, ", ", false) + ")");

            } else if (t.getClass() == jol.lang.plan.Assignment.class) {
				Assignment ass = (Assignment)t;
				body.add(nullType(ass));
			} else {
				// skip the assignments; they are not part of the lineage....
				body.add(t.toString().replace("BOOLEAN","").replace("MATH","").replace("@",""));
			}
        }
		List<Expression> args = headArgs(r.head());
		checkDefine(args, provHead(r.head()));
		StringBuilder rewrite = new StringBuilder();
		rewrite.append("public " + provHead(r.head())+ getHeadArgs(args) + join(body, ",\n\t",false));
		rewrite.append("\n { Provenance := " + join(provenances, "+", false) + "; };\n");
		for (String subRule : rules) {
			System.out.println("mini: "+subRule);
			rewrite.append(subRule);
		}
        return rewrite.toString();
    }

	private List<Expression> headArgs(Predicate head) {
		List<Expression> newArgs = new ArrayList<Expression>();
		Map<String, String> stopVars = new HashMap<String, String>();
		for (Expression e : head.arguments()) {
			if (e instanceof Aggregate) {
				Aggregate agg = (Aggregate)e;
				for (Variable v : agg.variables()) {
					stopVars.put(v.toString(), v.toString());
				}
			}
			// by convention, agg variables are always tagged on to the end.
			if (stopVars.get(e.toString()) == null) {
				newArgs.add(e);
			}
		}
		return newArgs;
	}
	private String getHeadArgs(List<Expression> args) {

		List<String> strArgs = new LinkedList<String>();
		for (Expression  e : args) {
			if ((e instanceof Value) && (e.type() == String.class)) {
				strArgs.add("\"" + e.toString() + "\"");
			} else {
				strArgs.add(e.toString());
			}
		}
		return "(" + join(strArgs, ", ", false) + ", Provenance) :-\n\t";
	}

    public String rfooter() {
        String foot = "\tSink := \"" + this.sink + "\";\n";

        return foot;
    }

    public String rewriter(String rname, Rule rule, String sink) {
        String Head1Name = rule.head().name().name;
        String name = (rule.isDelete ? "delete-" : "") + rule.name;
        String head1 = "Ts, \"" + name + "\"";
        String body1Str =  "tap_precondition(@" + head1 + ")";
        String body = sift(rule.body());
        String rule1Str = precondition(rule);
        String rule2Str ="";
        String type = isNetworkRule(rule) ? "N" : "L";
        String rule3Str = "tap_universe(@Sink, " + conjoin(", ", true, rule.program, name, rule.head().name().name, type ) + ") :-\n\ttap_chaff(@Foo),\n" + rfooter();

        return rule1Str + rule2Str + rule3Str;
    }

    public void doRewrite(String program) throws JolRuntimeException, UpdateException {
        doRewrite(program, null);
    }

    protected void finalize() throws IOException {
        this.watcher.close();
    }

    protected void watch() throws IOException {
        Set s = this.defines.entrySet();
        Iterator i = s.iterator();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            String key = (String) me.getKey();
            this.watcher.write(key.getBytes());
        }
    }


    public void provRewriter(String pn, Predicate pred, String type) {
        if (type.equals("E")) {
            predHash.put(pn, pred);
        }
    }

    public void doLookup(String program, String ruleName, List<String> path) throws JolRuntimeException {

        Callback reportCallback = new Callback() {
            @Override
            public void deletion(TupleSet tuples) {}

            @Override
            public void insertion(TupleSet tuples) {
                for (Tuple t : tuples) {
                    String rName = (String) t.value(0);
                    Rule r = (Rule) t.value(1);
                    System.out.println(r.toString());

                }
            }
        };

        this.system.evaluate();
        for (String prog : Conf.corpus) {
            System.out.println("DO: "+prog);
            this.system.install("tap", ClassLoader.getSystemResource(prog));
            this.system.evaluate();

        }

        TableName tblName = new TableName("tap", "tap");
        TupleSet tap = new BasicTupleSet();
        // XXX: hack
        tap.add(new Tuple(ruleName, program));
        this.system.schedule("tap", tblName, tap, null);

        Table table = this.system.catalog().table(new TableName("tap", "query"));
        table.register(reportCallback);

        this.system.evaluate();
}

	public Map<String, Integer> firings;

	public void lReport(String p, String r, String pr, Integer c) {
		firings.put(p+":"+r+"-"+pr, c);
	}
	public Map<String, Integer> getFirings() {
		return firings;
	}

    public void doListen() throws JolRuntimeException{

        this.system.install("tap", ClassLoader.getSystemResource("tap/listen.olg"));
        this.system.evaluate();


        this.system.start();
    }

	private SimpleQueue<Object> responseQueue;

	public void shutdown() {
		this.system.shutdown();
	}

    public void doFinish() throws JolRuntimeException, InterruptedException {

        Callback reportCallback = new Callback() {
            @Override
            public void deletion(TupleSet tuples) {}

            @Override
            public void insertion(TupleSet tuples) {
				for (Tuple t : tuples) {
                    String program = (String) t.value(0);
                    String rule = (String) t.value(1);
                    String pred = (String) t.value(2);
					Integer iCnt = (Integer) t.value(3);
					lReport(program, rule, pred, iCnt);

                }
				responseQueue.put("foo");

            }
        };

        this.system.install("tap", ClassLoader.getSystemResource("tap/listen.olg"));
        this.system.evaluate();

        Table table = this.system.catalog().table(new TableName("tap", "networkFires"));

       	table.register(reportCallback);

        this.system.install("tap", ClassLoader.getSystemResource("tap/tap_done.olg"));

        this.system.start();

		// till we timeout
		Object o = (Object)new String("foo");
		while (o != null) {
			o = responseQueue.get(6000);
			System.out.println("got something...\n");
		}
		System.out.println("GOT\n");

    }

    public void doRewrite(String program, String file) throws JolRuntimeException, UpdateException {

        final String mySink = this.sink;

        rewrittenProgram = header();
        Callback preconditionCallback = new Callback() {
            @Override
            public void deletion(TupleSet tuples) {}

            @Override
            public void insertion(TupleSet tuples) {
                for (Tuple t : tuples) {
                    String program = (String) t.value(0);
                    String ruleName = (String) t.value(1);
                    Rule rule = (Rule) t.value(2);

                    rewrittenProgram += rewriter(ruleName, rule, mySink);
                    ruleList.add(rule);
                }
            }
        };

        Callback provenanceCallback = new Callback() {
            @Override
            public void deletion(TupleSet tuples) {}

            @Override
            public void insertion(TupleSet tuples) {
                for (Tuple t : tuples) {
                    String program = (String) t.value(0);
                    Predicate pred = (Predicate) t.value(1);
                    String predName = (String) t.value(2);
                    String type = (String) t.value(3);

                    //rewrittenProgram += rewriter(ruleName, rule, mySink);
                    provRewriter(predName, pred, type);
                }
            }
        };


        this.system.evaluate();
        this.system.install("tap", ClassLoader.getSystemResource("bfs/tap.olg"));

        this.system.evaluate();

        if (file != null)
            this.system.install(program, ClassLoader.getSystemResource(file));


        this.system.evaluate();
        /* Identify the data directory */
        TableName tblName = new TableName("tap", "tap");
        TupleSet datadir = new BasicTupleSet();
        // XXX: hack
        datadir.add(new Tuple("tcp:localhost:12345", program));
        this.system.schedule("tap", tblName, datadir, null);

        Table t2 = this.system.catalog().table(new TableName("tap", "db"));
        t2.register(provenanceCallback);

        Table table = this.system.catalog().table(new TableName("tap", "rewriteRule"));
        table.register(preconditionCallback);

        this.system.evaluate();
        this.system.evaluate();
        this.system.evaluate();

        String  provProgram = summarize();

		installProgram("tap", rewrittenProgram);
		//installProgram("provenance", provProgram);
    }

	private void installProgram(String name, String program) {
        URL u;
        try {
            File tmp = File.createTempFile(name, "olg");
            /* put our program into a temp file.  wish we didn't have to do this... */
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(program.getBytes());
            fos.close();
            String path = tmp.getAbsolutePath();
            u = new URL("file", "", path);

        	this.system.install("user", u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}
}
