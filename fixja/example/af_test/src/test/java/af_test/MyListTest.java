package af_test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MyListTest {
    MyList myList;

    @Before
    public void before() throws Exception {
        myList = new MyList(5);
    }

    @After
    public void after() throws Exception {
    }

    @Test
    public void testDuplicateNotEmpty() throws Exception {
        myList.extend("a");
        myList.extend("b");
        myList.extend("c");
        myList.extend("d");
        MyList dupList = myList.duplicate(3);
    }

    @Test
    public void testDuplicateEmpty() throws Exception {
        myList.duplicate(1);
    }

}
