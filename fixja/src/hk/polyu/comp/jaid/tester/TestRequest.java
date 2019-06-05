package hk.polyu.comp.jaid.tester;

import org.junit.runner.Request;

/**
 * Created by Max.
 */
public class TestRequest{
    public static final String TEST_CLASS_GETTER_NAME = "getTestClass";
    public static final String TEST_METHOD_GETTER_NAME = "getTestMethod";

    private Request request;
    private String testClass;
    private String testMethod;

    public TestRequest(String testClass, String testMethod){
        this.testClass = testClass;
        this.testMethod = testMethod;
    }

    public Request getRequest() {
        if(request == null){
            try {
                this.request = Request.method(Class.forName(this.testClass), testMethod);
            }
            catch(ClassNotFoundException e){
                e.printStackTrace();
            }
        }
        return request;
    }

    public String getTestClassAndMethod(){
        return testClass + "." + testMethod;
    }

    public String getTestClass() {
        return testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestRequest that = (TestRequest) o;

        return getTestClass() == that.getTestClass() && getTestMethod() == that.getTestMethod();
    }

    @Override
    public int hashCode() {
        int result = getTestClass().hashCode();
        result = 31 * result + getTestMethod().hashCode();
        return result;
    }


    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append(testMethod).append(CLASS_METHOD_SEPARATOR).append(testClass);
        return sb.toString();
    }

    public static String requestToString(String method,String classString){
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(CLASS_METHOD_SEPARATOR).append(classString);
        return sb.toString();
    }

    public static TestRequest fromString(String s){
        String[] parts = s.split(CLASS_METHOD_SEPARATOR);
        return new TestRequest(parts[1], parts[0]);
    }

    public static final String CLASS_METHOD_SEPARATOR = "@";
}
