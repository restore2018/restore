package hk.polyu.comp.jaid.tester;

import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.util.FileUtil;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Max PEI.
 */
public class TesterConfig {

    public static final int INVALID_ACTIVE_FIX_ID = -1;

    private String testsToRunInStr;
    private Path logFilePath;
    private boolean shouldQuitUponMaxFailingTests;
    private boolean shouldCommunicateViaStdout;
    private int maxFailingTests;
    private LogLevel logLevel;
    private boolean isMonitorMode=false;
    private FailureHandling failureHandling;
    private long timeoutPerTest;
    private int activeFixIndex = INVALID_ACTIVE_FIX_ID;
    private int batchSize;

    public List<TestRequest> getTestsToRun() {
        return testRequestsFromString(testsToRunInStr);
    }

    public Path getLogFilePath() {
        return logFilePath;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public FailureHandling getFailureHandling() {
        return failureHandling;
    }

    public long getTimeoutPerTest() {
        return timeoutPerTest;
    }

    public int getActiveFixIndex(){ return activeFixIndex; }

    public int getMaxFailingTests() {
        return maxFailingTests;
    }

    public boolean shouldQuitUponMaxFailingTests() {
        return shouldQuitUponMaxFailingTests;
    }

    public boolean shouldCommunicateViaStdout() {
        return shouldCommunicateViaStdout;
    }

    public boolean shouldUseInternalTimer(){
        return getTimeoutPerTest() > 0;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isMonitorMode() {
        return isMonitorMode;
    }

    public TesterConfig(String[] args) {
        if (args.length == 0)
            throw new IllegalArgumentException("Error: Missing command line arguments");

        int index = 0;
        while (index < args.length) {

            switch (args[index]) {
                case TESTS_OPT:
                    testsToRunInStr = args[index + 1];
                    break;
                case TESTS_LIST_FILE_OPT:
                    testsToRunInStr = FileUtil.getFileContent(Paths.get(args[index + 1]), Charset.defaultCharset());
                    break;
                case MAX_FAILING_TESTS_OPT:
                    maxFailingTests = Integer.valueOf(args[index + 1]);
                    break;
                case LOG_FILE_OPT:
                    logFilePath = Paths.get(args[index + 1]);
                    break;
                case LOG_LEVEL_OPT:
                    logLevel = LogLevel.valueOf(args[index + 1]);
                    break;
                case SHOULD_QUIT_UPON_MAX_FAILING_TESTS_OPT:
                    shouldQuitUponMaxFailingTests = Boolean.valueOf(args[index + 1]);
                    break;
                case FAILURE_HANDLING_OPT:
                    failureHandling = FailureHandling.valueOf(args[index + 1]);
                    break;
                case TIMEOUT_PER_TEST_OPT:
                    timeoutPerTest = Long.valueOf(args[index + 1]);
                    break;
                case ACTIVE_FIX_INDEX_OPT:
                    activeFixIndex = Integer.valueOf(args[index + 1]);
                    break;
                case BATCH_SIZE_OPT:
                    batchSize = Integer.valueOf(args[index + 1]);
                    break;
                case ACTIVE_IS_MONITOR_MODE:
                    isMonitorMode = Boolean.valueOf(args[index + 1]);
                    break;
                case SHOULD_COMMUNICATE_VIA_STDOUT_OPT:
                    shouldCommunicateViaStdout = Boolean.valueOf(args[index+1]);
                    break;
            }
            index = index + 2;
        }
    }

    private List<TestRequest> testRequestsFromString(String testsStr) {
        String[] tests = testsStr.split(File.pathSeparator);
        SimpleLogger.debug("Number of tests from command line argument: " + tests.length);

        List<TestRequest> result = Arrays.stream(tests)
                .filter(x -> !x.isEmpty())
                .map(x -> getRequestFromString(x))
                .filter(x -> x.getRequest() != null)
                .collect(Collectors.toList());
        SimpleLogger.debug("Number of test requests identified: " + result.size());

        return result;
    }

    private TestRequest getRequestFromString(String testName) {
        String[] parts = testName.split(TestRequest.CLASS_METHOD_SEPARATOR);
        return new TestRequest(parts[1], parts[0]);
    }

    @Override
    public String toString() {
        return "TesterConfig{" +
                "testsToRunInStr='" + testsToRunInStr + '\'' +
                ", logFilePath=" + logFilePath +
                ", shouldQuitUponMaxFailingTests=" + shouldQuitUponMaxFailingTests +
                ", shouldCommunicateViaStdout=" + shouldCommunicateViaStdout +
                ", maxFailingTests=" + maxFailingTests +
                ", logLevel=" + logLevel +
                ", failureHandling=" + failureHandling +
                ", timeoutPerTest=" + timeoutPerTest +
                ", activeFixIndex=" + activeFixIndex +
                ", isMonitorMode=" + isMonitorMode +
                ", batchSize=" + batchSize +
                '}';
    }

    public final static String TESTS_OPT = "--Tests";
    public final static String TESTS_DESC = "List of tests to launch. Format: FullyQualifiedClassName;FullyQualifiedClassName#MethodName;...";

    public final static String TESTS_LIST_FILE_OPT = "--TestListFile";
    public final static String TESTS_LIST_FILE_DESC = "A file contains the list of tests to launch. Format: FullyQualifiedClassName;FullyQualifiedClassName#MethodName;...";

    public final static String LOG_FILE_OPT = "--LogFile";
    public final static String LOG_FILE_DESC = "Path to the log file.";

    public final static String LOG_LEVEL_OPT = "--LogLevel";
    public final static String LOG_LEVEL_DESC = "Level of information to log. Valid values: OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL.";

    public final static String SHOULD_QUIT_UPON_MAX_FAILING_TESTS_OPT = "--ShouldQuitUponMaxFailingTests";
    public final static String IS_VALIDATING_DESC = "Is tester validating fixes? MaxFailingTests is only effective when validating fixes.";

    public final static String MAX_FAILING_TESTS_OPT = "--MaxFailingTests";
    public final static String MAX_FAILING_TESTS_DESC = "Max number of failing tests before stopping the evaluation";

    public final static String FAILURE_HANDLING_OPT = "--FailureHandling";
    public final static String FAILURE_HANDLING_DESC = "What to do upon test failure. Valid values: continue, break.";

    public final static String TIMEOUT_PER_TEST_OPT = "--TimeoutPerTest";
    public final static String TIMEOUT_PER_TEST_DESC = "Timeout in milliseconds per test.";

    public final static String ACTIVE_FIX_INDEX_OPT = "--ActiveFixIndex";
    public final static String ACTIVE_FIX_ID_DESC = "Active fix Index.";

    public final static String ACTIVE_IS_MONITOR_MODE = "--IsMonitorMode";
    public final static String ACTIVE_IS_MONITOR_MODE_DESC = "Whether the Tester is launch by debugger with breackpoint in MTF.";

    public final static String BATCH_SIZE_OPT = "--BatchSize";
    public final static String BATCH_SIZE_DESC = "Maximum number of fixes to include in a batch.";

    public final static String SHOULD_COMMUNICATE_VIA_STDOUT_OPT = "--ShouldCommunicateViaStdout";

}
