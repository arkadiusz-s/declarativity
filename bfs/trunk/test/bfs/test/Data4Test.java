package bfs.test;

import org.junit.Test;

public class Data4Test extends DataCommon {
    private static final String TEST_FILE = "/usr/share/dict/words";

	@Test(timeout=32000)
    public void test1() throws Exception  {
        test(TEST_FILE, 3, 1, 9);
        //check_files();

        /* victimization of individual chunks.  requires deletion deltas, which
           are not yet implemented
        Victim v = new Victim(this.datanodes.size());
        v.pick_victim();
        System.out.println("victim: dir "+ v.getDir() + ", chunk " + v.getChunk());        
        v.do_victim();
        */

        killDataNode(0);

        try {
        Thread.sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        //check_files();
        cleanupAll();
    }

	public static void main(String[] args) throws Exception {
		Data4Test t = new Data4Test();
		t.test1();
	}
}
