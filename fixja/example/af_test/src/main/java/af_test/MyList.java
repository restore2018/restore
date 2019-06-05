package af_test;

import com.google.java.contract.Requires;
import java.util.ArrayList;

public class MyList {

    private  ArrayList<String> storage;

    private int index = 0;

    public  MyList(int a_size) {
		assert(a_size > 0);
        storage = new ArrayList<String>(a_size);
    }

    public MyList duplicate(int n) {
        int to_be_copied = 0, counter = 0;
        MyList result = new MyList(storage.size());
        to_be_copied = Math.min(n, this.count() - index);
        while (counter < to_be_copied) {
            result.extend(this.item());
            forth();
            counter++;
        }
        return result;
    }

    public void start() {
        index = 0;
    }

    private void forth() {
        index++;
    }

    public boolean after() {
        return index >= storage.size() ;
    }

    public String item() {
        return storage.get(index);
    }

    public boolean has(String a_item) {
        return storage.contains(a_item);
    }

    public boolean off() {
        return storage.isEmpty();
    }

    public int count() {
        return storage.size();
    }

    public void extend(String a_item) {
        storage.add(a_item);
    }

    public void remove() {
        storage.remove(index);
    }

}
