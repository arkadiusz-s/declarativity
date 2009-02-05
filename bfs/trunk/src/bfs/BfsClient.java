package bfs;

import java.util.Random;

import jol.core.JolSystem;
import jol.core.Runtime;
import jol.types.basic.Tuple;
import jol.types.basic.TupleSet;
import jol.types.exception.JolRuntimeException;
import jol.types.exception.UpdateException;
import jol.types.table.Table;
import jol.types.table.TableName;
import jol.types.table.Table.Callback;

/**
 * This class provides the BoomFS client API. It communicates with both master
 * nodes, to obtain metadata, and data nodes, to read and write data.
 */
public class BfsClient {
	private int currentMaster;
    private Random rand;
	private SimpleQueue responseQueue;
	private JolSystem system;

	public BfsClient(int port) {
        this.rand = new Random();
        this.currentMaster = 0;
        this.responseQueue = new SimpleQueue();

		try {
	        /* this shouldn't be a static member at all... */
	        Conf.setSelfAddress("tcp:localhost:" + port);

			this.system = Runtime.create(port);

	        this.system.install("bfs_global", ClassLoader.getSystemResource("bfs/bfs_global.olg"));
	        this.system.evaluate();
	        this.system.install("bfs", ClassLoader.getSystemResource("bfs/bfs.olg"));
	        this.system.evaluate();

	        updateMasterAddr();
	        this.system.start();
		} catch (JolRuntimeException e) {
			throw new RuntimeException(e);
		} catch (UpdateException e) {
			throw new RuntimeException(e);
		}
	}

	public void createFile() {
		;
	}

	public boolean delete(final String path) {
        final int requestId = generateId();

        // Register callback to listen for responses
        Callback responseCallback = new Callback() {
            @Override
            public void deletion(TupleSet tuples) {}

            @Override
            public void insertion(TupleSet tuples) {
                for (Tuple t : tuples) {
                    Integer tupRequestId = (Integer) t.value(1);

                    if (tupRequestId.intValue() == requestId) {
                        Boolean success = (Boolean) t.value(3);
                        System.out.println("Remove of file \"" + path + "\": " +
                                           (success.booleanValue() ? "succeeded" : "failed"));
                        responseQueue.put(success);
                        break;
                    }
                }
            }
        };
        Table responseTbl = registerCallback(responseCallback, "response");

        // Create and insert the request tuple
        TableName tblName = new TableName("bfs", "start_request");
        TupleSet req = new TupleSet(tblName);
        req.add(new Tuple(Conf.getSelfAddress(), requestId, "Rm", path));
        try {
        	this.system.schedule("bfs", tblName, req, null);
        } catch (UpdateException e) {
        	throw new RuntimeException(e);
        }

        Boolean success = (Boolean) waitForResponse(Conf.getFileOpTimeout());
        responseTbl.unregister(responseCallback);

        if (success == null || success.booleanValue() == false)
        	return false;
        else
        	return true;
	}

	public boolean rename(String oldPath, String newPath) {
		return false;
	}

    private Table registerCallback(Callback callback, String tableName) {
        Table table = this.system.catalog().table(new TableName("bfs", tableName));
        table.register(callback);
        return table;
    }

    private Object waitForResponse(long timeout) {
        while (this.currentMaster < Conf.getNumMasters()) {
            Object result = this.responseQueue.get(timeout);
            if (result != null)
                return result;

            System.out.println("Master " + this.currentMaster + " timed out. Retry?");
            this.currentMaster++;
            updateMasterAddr();
        }

        return null;
    }

    private void updateMasterAddr() {
        TupleSet master = new TupleSet();
        master.add(new Tuple(Conf.getSelfAddress(),
                             Conf.getMasterAddress(this.currentMaster)));
        try {
            this.system.schedule("bfs", MasterTable.TABLENAME, master, null);
            this.system.evaluate();
        } catch (UpdateException e) {
            throw new RuntimeException(e);
        } catch (JolRuntimeException e) {
        	throw new RuntimeException(e);
        }
    }

    private int generateId() {
        return rand.nextInt();
    }
}
