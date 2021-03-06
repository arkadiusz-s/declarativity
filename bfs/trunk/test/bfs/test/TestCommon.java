package bfs.test;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import jol.types.exception.JolRuntimeException;
import jol.types.exception.UpdateException;
import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;

import bfs.BFSFileInfo;
import bfs.Conf;
import bfs.DataNode;
import bfs.Master;
import bfs.Shell;

public class TestCommon {
    protected List<List<Master>> partitions;
    protected List<DataNode> datanodes;
    protected Shell shell;

    @Before
    public void doNothing() {
    }

    @After
    public void shutdown() {
        for (List<Master> masters: partitions) {
        	for (Master m : masters) {
        		m.stop();
        	}
        }
        if (this.datanodes != null) {
            for (DataNode d : this.datanodes) {
                d.shutdown();
            }
        }
        System.out.println("shutdown complete");
    }

    protected boolean shellLs(String dir, String... list) throws JolRuntimeException, UpdateException {
        Shell shell = new Shell();
        boolean ret = findInLs(shell, dir, list);
        shell.shutdown();
        return ret;
    }

    protected int shellLsCnt(String dir) throws JolRuntimeException, UpdateException {
        Shell shell = new Shell();
        int ret = lsCnt(shell, dir);
        shell.shutdown();
        return ret;
    }

    protected void killMaster(int partition, int index) {
        this.partitions.get(partition).get(index).stop();
    }

    protected void killDataNode(int index) {
        this.datanodes.get(index).shutdown();
    }

    protected void shellCreate(String name) throws JolRuntimeException, UpdateException {
        Shell shell = new Shell();
        createFile(shell, name);
        shell.shutdown();
    }

    protected void shellRm(String name) throws JolRuntimeException, UpdateException {
        Shell shell = new Shell();
        rmFile(shell, name);
        shell.shutdown();
    }

    protected void assertTrue(boolean b) {
        safeAssert("", b);
    }
    protected void safeAssert(boolean b) {
        safeAssert("", b);
    }

    protected void safeAssert(String m, boolean b) {
        if (!b) {
            System.out.println("Failed Assertion: " + m);
            shutdown();
        }
        Assert.assertTrue(m, b);
    }

    protected int lsCnt(Shell shell, String dir) throws JolRuntimeException, UpdateException {
        Set<BFSFileInfo> list = lsFile(shell, dir);
        return list.size();
    }

    protected boolean findInLs(Shell shell, String dir, String... files) throws JolRuntimeException,
            UpdateException {
        Set<BFSFileInfo> listing = lsFile(shell, dir);

        // obviously not an efficient way to do this.
        for (String item : files) {
        	boolean success = false;

        	for (BFSFileInfo fInfo : listing) {
        		if (fInfo.getName().equals(item)) {
        			success = true;
        			break;
        		}
        	}
        	if (!success)
        		return false;
        }
        return true;
    }

    protected void startManyPartitioned(String[][] args) throws JolRuntimeException, UpdateException {
	      List<List<String>> a = new ArrayList<List<String>>();
	      for(int i = 0; i < args.length; i++) {
	    	  a.add(new LinkedList<String>());
	    	  for(int j = 0; j < args[i].length; j++) {
	    		  a.get(i).add(args[i][j]);
	    	  }
	      }
	      Conf.setNewMasterList(a);

	      this.partitions = new ArrayList<List<Master>>();
	      for(int i = 0; i < Conf.getNumPartitions(); i++) {
	      	this.partitions.add(new LinkedList<Master>());
	        for (int j = 0; j < Conf.getNumMasters(i); j++) {
	            Master m = new Master(i, j);
	            System.out.println("starting master ("+i+","+j+"): "+ m);
	            m.start();
	            this.partitions.get(i).add(m);
	        }
	      }
    }

    protected void startMany(String... args) throws JolRuntimeException, UpdateException {
        this.partitions = new ArrayList<List<Master>>();
        List<List<String>> a = new ArrayList<List<String>>();
        a.add(new ArrayList<String>());
        for(String arg:args) {a.get(0).add(arg); }
        Conf.setNewMasterList(a);
        for(int i = 0; i < Conf.getNumPartitions(); i++) {
        	this.partitions.add(new LinkedList<Master>());
	        for (int j = 0; j < Conf.getNumMasters(i); j++) {
	            Master m = new Master(i, j);
	            m.start();
	            this.partitions.get(i).add(m);
	        }
        }
    }

    protected void cleanup(String dir) {
        File file = new File(dir);
        if (file.exists()) {
            try {
                File chunks = new File(file, "chunks");
                for (File f : chunks.listFiles()) {
                    f.delete();
                }
                File checksums = new File(file, "checksums");
                for (File f : checksums.listFiles()) {
                    f.delete();
                }
                chunks.delete();
                checksums.delete();
                file.delete();
            } catch (Exception e) {
                throw new RuntimeException("couldn't cleanup dir " + dir, e);
            }
        }
    }

    protected void startManyDataNodes(String... args) throws JolRuntimeException,
            UpdateException {
        Conf.setNewDataNodeList(args.length);
        assert (args.length == Conf.getNumDataNodes());
        this.datanodes = new LinkedList<DataNode>();
        startManyDataNodes(0, args);
    }

    protected void startManyDataNodes(int indx, String... args) throws JolRuntimeException,
            UpdateException {

        for (int i = indx; i < (args.length + indx); i++) {
            cleanup(args[i-indx]);
            DataNode d = new DataNode(i, args[i-indx]);
            System.out.println("new DATANODE " + d.getPort());
            d.start();
            this.datanodes.add(d);
        }
    }

    protected Set<BFSFileInfo> lsFile(Shell shell, String path) throws UpdateException,
            JolRuntimeException {
        List<String> argList = new LinkedList<String>();
        argList.add(path);
        return shell.doListFiles(argList);
    }

    protected void appendFile(Shell shell, String name, InputStream s)
            throws UpdateException, JolRuntimeException {
        List<String> argList = new LinkedList<String>();
        argList.add(name);
        shell.doAppend(argList, s);
    }

    protected void createFile(Shell shell, String name) throws UpdateException,
            JolRuntimeException {
        List<String> argList = new LinkedList<String>();
        argList.add(name);
        shell.doCreateFile(argList, false);
    }

    protected void createDir(Shell shell, String path) throws UpdateException, JolRuntimeException {
    	List<String> argList = new LinkedList<String>();
    	argList.add(path);
    	shell.doCreateFile(argList, true);
    }

    protected void rmFile(Shell shell, String name) throws UpdateException,
            JolRuntimeException {
        List<String> argList = new LinkedList<String>();
        argList.add(name);
        shell.doRemove(argList);
    }
}
