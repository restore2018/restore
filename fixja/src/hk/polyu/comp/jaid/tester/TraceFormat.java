package hk.polyu.comp.jaid.tester;

import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.util.List;

/**
 * Created by Max PEI.
 */
public class TraceFormat {

    public static String getTestStartMessage(TestRequest testRequest) {
        return TEST_START_PREFIX + testRequest.toString();
    }

    public static String getTestStartMessage(String testRequest) {
        return TEST_START_PREFIX + testRequest;
    }

    public static String getTestEndMessage(TestRequest testRequest) {
        return TEST_END_PREFIX + testRequest.toString();
    }

    public static String getTestEndMessage(String testRequest) {
        return TEST_END_PREFIX + testRequest;
    }

    public static String getErrorLaunchingTestPrefix(String testName) {
        return ERROR_LAUNCHING_TEST_PREFIX + testName;
    }

    public static String getTestResultMessage(Result result) {
        if (result == null)
            throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder();
        sb.append(TEST_RESULT_PREFIX).append("\n");
        sb.append("\t").append(RESULT_FIELD_RUN_COUNT).append(result.getRunCount()).append("\n");
        sb.append("\t").append(RESULT_FIELD_IGNORE_COUNT).append(result.getIgnoreCount()).append("\n");
        sb.append("\t").append(RESULT_FIELD_RUN_TIME).append(result.getRunTime()).append("\n");
        sb.append("\t").append(RESULT_FIELD_WAS_SUCCESSFUL).append(result.wasSuccessful()).append("\n");
        sb.append("\t").append(RESULT_FIELD_FAILURE_COUNT).append(result.getFailureCount()).append("\n");
        sb.append(getFailureMsg(result));
        return sb.toString();
    }
    public static String getFailureMsg(Result result){
        StringBuilder sb = new StringBuilder();

        if (result.getFailureCount() != 0) {
            List<Failure> failures = result.getFailures();
            for (Failure failure : failures) {
                sb.append("\t").append(RESULT_FIELD_FAILURE_PREFIX).append(failure.getMessage() == null ? null : failure.getMessage().replace(LINE_CHARACTER, REPLACE_LINE_CHARACTER)).append("\n");

                String trace = failure.getTrace();
                String[] lines = trace.split("\n");
                sb.append("\t\t").append(lines.length).append("\n");
                for (String line : lines) {
                    sb.append("\t\t").append(line).append("\n");
                }
            }
        }
        return sb.toString();
    }

    public static final String ALL_TEST_START = "AllTestStart:";
    public static final String ALL_TEST_END = "AllTestsEnd:";

    public static final String TEST_START_PREFIX = "TestStart:";
    public static final String TEST_END_PREFIX = "TestEnd:";

    public static final String STATE_MONITORING_START_PREFIX = "StateMonitoringStart:";
    public static final String STATE_MONITORING_END_PREFIX = "StateMonitoringEnd:";

    public static final String RESULT_GET_FAILURE_MSG = "hk.polyu.comp.jaid.tester.TraceFormat.getFailureMsg(result)";

    public static final String TEST_RESULT_PREFIX = "DTestResult:";
    public static final String ERROR_LAUNCHING_TEST_PREFIX = "ErrorLaunchingTest:";

    public static final String RESULT_FIELD_RUN_COUNT = "RunCount:";
    public static final String RESULT_FIELD_IGNORE_COUNT = "IgnoreCount:";
    public static final String RESULT_FIELD_RUN_TIME = "RunTime:";
    public static final String RESULT_FIELD_WAS_SUCCESSFUL = "WasSuccessful:";
    public static final String RESULT_FIELD_FAILURE_COUNT = "FailureCount:";
    public static final String RESULT_FIELD_FAILURE_PREFIX = "Failure:";
    public static final String LINE_CHARACTER = "\n";
    public static final String REPLACE_LINE_CHARACTER = "`";

}
