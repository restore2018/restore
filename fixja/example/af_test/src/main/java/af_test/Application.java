package af_test;

public class Application {
    static int  n = 3;
    static int size = 4;
    public static void main(String[] arg) {

        MyList myList = new MyList(size);
        myList.extend("a");
        myList.extend("b");
        myList.extend("c");
        myList.extend("d");
        System.out.println("size:"+myList.count());
        MyList dupList=myList.duplicate(n);
        System.out.println("size:"+dupList.count());
    }
}
