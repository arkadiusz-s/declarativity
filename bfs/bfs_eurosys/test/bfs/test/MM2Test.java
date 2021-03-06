package bfs.test;

import org.junit.Test;

public class MM2Test extends TestCommon {
    @Test(timeout=28000)
    public void test2() throws Exception {
        startMany("localhost:5500", "localhost:5502", "localhost:5503");

        shellCreate("/foo");
        /* kill one of the masters */
        killMaster(0,1);

        shellCreate("/bar");
        assertTrue(shellLs("/", "foo", "bar"));

        shutdown();
    }

    public static void main(String[] args) throws Exception {
        MM2Test t = new MM2Test();
        t.test2();
    }
}
