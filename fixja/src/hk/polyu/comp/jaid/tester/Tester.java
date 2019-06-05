package hk.polyu.comp.jaid.tester;

import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static hk.polyu.comp.jaid.fixer.log.LogLevel.DEBUG;
import static hk.polyu.comp.jaid.fixer.log.LogLevel.INFO;

/**
 * Created by Max PEI.
 */
public class Tester {

    // ATTENTION:
    // Method name: TEST_START_METHOD_NAME
    // This method should have at least one statement in its body,
    // and should be called before each test is executed inside
    // method with TEST_EXECUTION_METHOD_NAME;
    private static void startTest(String message, TesterConfig config, LogLevel level) {
        conditionalLog(message, config, level);
    }

    // ATTENTION:
    // Method name: TEST_END_METHOD_NAME
    // This method should have at least one statement in its body,
    // and should be called before each test is executed inside
    // method with TEST_EXECUTION_METHOD_NAME;
    private static void endTest(String message, TesterConfig config, LogLevel level) {
        conditionalLog(message, config, level);
    }

    private static void conditionalLog(String message, TesterConfig config, LogLevel level) {
        if (SimpleLogger.shouldLog(level)) {
            SimpleLogger.log(message, level);
        } else if (config.shouldUseInternalTimer()) {
            SimpleLogger.log(message, SimpleLogger.currentLevel());
        }
    }

    // ATTENTION:
    // Method name: GET_BATCH_INDEX_METHOD_NAME
    public static int getBatchIndex() {
        return activeFixIndex / batchSize;
    }

    // ATTENTION:
    // Method name: GET_INDEX_INSIDE_BATCH_METHOD_NAME
    public static int getIndexInsideBatch() {
        return activeFixIndex % batchSize;
    }

    public static final String JAID_KEY_WORD = "__JAID_VAR";
    public static final String HAS_EXCEPTION = "hasException" + JAID_KEY_WORD;
    public static final String EXCEPTION_CLASS_TYPE = "exceptionClassType" + JAID_KEY_WORD;
    public static final String THROWABLE = "t" + JAID_KEY_WORD;

    public static boolean isExecutingMTF = false;
    public static boolean isMonitorMode = false;

    public static int startMethodToMonitor() {
        return 1;
    }

    public static int endMethodToMonitor() {
        return 2;
    }

    public static int testIsTimeout() {
        return 3;
    }

    public static int assertInvoked(String methodName) {
        return 4;
    }

    private static TesterConfig testerConfig;
    private static int activeFixIndex;
    private static int batchSize;

    public static void main(String[] args) {
        testerConfig = new TesterConfig(args);

        isMonitorMode = testerConfig.isMonitorMode();
        activeFixIndex = testerConfig.getActiveFixIndex();
        batchSize = testerConfig.getBatchSize();

        SimpleLogger.start(testerConfig.getLogFilePath(), testerConfig.getLogLevel());
        SimpleLogger.debug(Arrays.toString(args));
        SimpleLogger.debug(testerConfig.toString());
        SimpleLogger.info(TraceFormat.ALL_TEST_START);

        executeTests(testerConfig.getTestsToRun());

        SimpleLogger.info(TraceFormat.ALL_TEST_END);
        SimpleLogger.end();
    }

    public static final String SEPERATOR_SYMBOL = ";";
    public static final String EQUAL_SYMBOL = "=";
    public static final String PREFIX_MARKER = "<<!>>";
    public static final String TEST_START_PREFIX = PREFIX_MARKER + "TestStart";
    public static final String TEST_END_PREFIX = PREFIX_MARKER + "TestEnd";
    public static final String FIX_INDEX_PREFIX = "FixIndex";
    public static final String TEST_REQUEST_PREFIX = "TestRequest";
    public static final String WAS_SUCCESSFUL_PREFIX = "WasSuccessful";
    public static final String RUN_TIME_PREFIX = "RunTime";
    public static final int TIME_OUT_STATE = 77;

    public static void executeTests(List<TestRequest> requests) {
        int nbrFailingTests = 0;
        JUnitCore jUnitCore = new JUnitCore();

        StringBuilder testStartMessageBuilder = new StringBuilder();
        StringBuilder testEndMessageBuilder = new StringBuilder();
        StringBuilder testErrorMessageBuilder = new StringBuilder();

        String fixIndexStr = FIX_INDEX_PREFIX + EQUAL_SYMBOL + activeFixIndex + SEPERATOR_SYMBOL;
        ExecutorService executor = null;
        if (testerConfig.shouldUseInternalTimer()) {
            executor = Executors.newSingleThreadExecutor();
        }
        boolean isTimeout = false;
        Future<Result> future = null;
        for (TestRequest request : requests) {
            testStartMessageBuilder.setLength(0);
            testEndMessageBuilder.setLength(0);
            testErrorMessageBuilder.setLength(0);

            String requestStr = TEST_REQUEST_PREFIX + EQUAL_SYMBOL + request.getTestClassAndMethod() + SEPERATOR_SYMBOL;
            testStartMessageBuilder.append(TEST_START_PREFIX).append(SEPERATOR_SYMBOL)
                    .append(fixIndexStr)
                    .append(requestStr);
            testEndMessageBuilder.append(TEST_END_PREFIX).append(SEPERATOR_SYMBOL)
                    .append(fixIndexStr)
                    .append(requestStr);

            Result result = null;
            try {
                startTest(testStartMessageBuilder.toString(), testerConfig, DEBUG);

                if (testerConfig.shouldUseInternalTimer()) {
                    //The task may be executed once it is submitted. Therefore, a task should be submitted after `startTest` is invoked.
                    future = executor.submit(new TestTask(jUnitCore, request));
                    result = future.get(testerConfig.getTimeoutPerTest(), TimeUnit.MILLISECONDS);
                } else {
                    result = jUnitCore.run(request.getRequest());
                }
                SimpleLogger.debug(TraceFormat.getTestResultMessage(result));

            } catch (Exception e) {
                SimpleLogger.debug("E: " + e.toString());

                try {
                    if (testerConfig.shouldUseInternalTimer() && future != null) {
                        //This statement doesn't kill the timeout test thread in time.
//                        SimpleLogger.debug("cancel:" + future.cancel(true));
                        //Still, this code cannot guarantees that the timeout test thread is killed, but it is better.
                        executor.shutdownNow();
                        testIsTimeout();

                        SimpleLogger.debug("isShutdown:" + executor.isShutdown());
                        SimpleLogger.debug("isTerminated:" + executor.isTerminated());


                        isTimeout = true;
                    }
                } catch (Exception e1) {
                    SimpleLogger.debug("E1: " + e1.toString());
                }

                if (SimpleLogger.shouldLog(DEBUG)) {
                    testErrorMessageBuilder.append(e.toString());
                    for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                        testErrorMessageBuilder.append(stackTraceElement.toString());
                    }
                }
            } finally {
                boolean wasSuccessful = result != null && result.wasSuccessful();
                long getRunTime = result != null ? result.getRunTime() : -1;
                if (wasSuccessful) {
                    testEndMessageBuilder
                            .append(WAS_SUCCESSFUL_PREFIX).append(EQUAL_SYMBOL).append(true).append(SEPERATOR_SYMBOL)
                            .append(RUN_TIME_PREFIX).append(EQUAL_SYMBOL).append(getRunTime).append(SEPERATOR_SYMBOL);
                } else {
                    nbrFailingTests++;
                    testEndMessageBuilder.append(WAS_SUCCESSFUL_PREFIX).append(EQUAL_SYMBOL).append(false).append(SEPERATOR_SYMBOL);
                    if (result != null) {
                        testEndMessageBuilder.append(RUN_TIME_PREFIX).append(EQUAL_SYMBOL).append(getRunTime).append(SEPERATOR_SYMBOL);
                    }
                }

                endTest(testEndMessageBuilder.toString(), testerConfig, DEBUG);

                if (SimpleLogger.shouldLog(DEBUG) && testErrorMessageBuilder.length() > 0) {
                    SimpleLogger.debug(testErrorMessageBuilder.toString());
                }

                if (testerConfig.shouldQuitUponMaxFailingTests() && nbrFailingTests > testerConfig.getMaxFailingTests()) {
                    SimpleLogger.log("MaxFailingTests reached. Skipping the remaining tests.", INFO);
                    System.exit(1);
                    break;
                }

                if (!wasSuccessful && testerConfig.getFailureHandling().equals(FailureHandling.BREAK)) {
                    SimpleLogger.log("Test failed. Skipping the remaining tests.", INFO);
                    System.exit(1);
                    break;
                }
                if (isTimeout) System.exit(TIME_OUT_STATE);
            }
        }
        if (testerConfig.shouldUseInternalTimer() && executor != null) {
            executor.shutdownNow();
        }
    }

    static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    SimpleLogger.debug("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            SimpleLogger.debug("ie: " + ie.toString());

            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public static void logRemote(String message, LogLevel level) {
        System.out.println(message);
        System.out.flush();
    }

    public static class TestTask implements Callable<Result> {
        private final JUnitCore jUnitCore;
        private final TestRequest request;

        public TestTask(JUnitCore jUnitCore, TestRequest request) {
            this.jUnitCore = jUnitCore;
            this.request = request;
        }

        @Override
        public Result call() throws Exception {
            return jUnitCore.run(request.getRequest());
        }
    }

    public static class MTFIsMonitoredException extends RuntimeException {
        static final String msg = "MTF is monitored";

        public MTFIsMonitoredException() {
            super(msg);
        }
    }

    public enum MemberName {
        TEST_START_METHOD_NAME("startTest"),
        TEST_END_METHOD_NAME("endTest"),
        START_METHOD_TO_MONITOR_METHOD_NAME("startMethodToMonitor"),
        END_METHOD_TO_MONITOR_METHOD_NAME("endMethodToMonitor"),
        TIMEOUT_TEST_CASE_METHOD_NAME("testIsTimeout"),
        ASSERT_INVOCATION_METHOD_NAME("assertInvoked"),
        TEST_EXECUTION_METHOD_NAME("executeTests"),
        TEST_RESULT_VARIABLE_NAME("result"),
        TEST_REQUEST_VARIABLE_NAME("request"),
        GET_BATCH_INDEX_METHOD_NAME("getBatchIndex"),
        GET_INDEX_INSIDE_BATCH_METHOD_NAME("getIndexInsideBatch"),
        IS_Executing_MTF("isExecutingMTF"),
        IS_MONITOR_MODE("isMonitorMode");
        private String methodName;

        MemberName(String methodName) {
            this.methodName = methodName;
        }

        public String getName() {
            return methodName;
        }
    }
}
