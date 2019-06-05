package hk.polyu.comp.jaid.util;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestUlamDistance  {
    @Test
    public  void test(){
        List a=Stream.of(1,2,3,4,5,6).collect(Collectors.toList());
        List b=Stream.of(2,5,3,1,4,6).collect(Collectors.toList());
        int k=CommonUtils.ulamDistance(a,b);
        System.out.println(k);
    }

    @Test
    public  void test1(){
        List a=Stream.of(1,2,3,2,4,4,1).collect(Collectors.toList());
        List b=Stream.of(2,1,4,2,1,2,4).collect(Collectors.toList());
        int k=CommonUtils.ulamDistance(a,b);
        System.out.println(k);
    }
}
