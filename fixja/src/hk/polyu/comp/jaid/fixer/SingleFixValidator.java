package hk.polyu.comp.jaid.fixer;

import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.test.ResultReader;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.tester.TesterConfig;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static hk.polyu.comp.jaid.fixer.config.Config.BATCH_SIZE;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.*;

/**
 * Created by Max PEI.
 */
public class SingleFixValidator {

    protected JavaProject project;
    protected LogLevel logLevel;
    protected int maxFailingTests;
    protected long timeoutPerTest;
    protected FailureHandling failureHandling;

    protected FixAction currentFixAction;
    protected int fixActionIndex;
    protected List<TestRequest> tests;

    private DefaultExecutor exec;
    private ExecuteWatchdog watchdog;

    private List<TestExecutionResult> currentFixResults;

    public static int isKillNum = 0;

    public SingleFixValidator(JavaProject project, LogLevel logLevel, int maxFailingTests, long timeoutPerTest,
                              FailureHandling failureHandling, List<TestRequest> testForValidation) {
        this.project = project;
        this.logLevel = logLevel;
        this.maxFailingTests = maxFailingTests;
        this.timeoutPerTest = timeoutPerTest;
        this.failureHandling = failureHandling;
        this.tests = testForValidation;
    }

    public void validate(List<FixAction> fixActions, int fixActionIndex) {
        this.fixActionIndex = fixActionIndex;
        this.currentFixAction = fixActions.get(fixActionIndex);
        this.currentFixResults = new LinkedList<>();
        this.currentFixAction.setTestExecutionResults(currentFixResults);

        try {
            if (shouldLogDebug())
                LoggingService.debug("Validating fix " + currentFixAction.getFixId() + " at index " + fixActionIndex + " using " + tests.size() + " tests.");
            execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void secondValidate(List<FixAction> fixActions, int fixActionIndex) {
        this.fixActionIndex = fixActionIndex;
        this.currentFixAction = fixActions.get(fixActionIndex);
        this.currentFixResults = new LinkedList<>();
        this.currentFixAction.setSecondValidationResults(currentFixResults);

        try {
            if (shouldLogDebug())
                LoggingService.debug("Second-Validating fix " + currentFixAction.getFixId() + " at index " + fixActionIndex + " using " + tests.size() + " tests.");
            execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ====================================== Implementation details

    private boolean execute() throws Exception {

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();

        exec = new DefaultExecutor();
        exec.setWorkingDirectory(project.getRootDir().toFile());

        watchdog = new ExecuteWatchdog(timeoutPerTest * tests.size() + 5000);
        exec.setWatchdog(watchdog);

        CommandLine commandLine = CommandLine.parse(buildCommandLineStr());
        if (shouldLogDebug()) {
            debug(commandLine.toString());
        }

        exec.execute(commandLine, resultHandler);
        resultHandler.waitFor();

        if (watchdog.killedProcess()) {
            // it was killed on purpose by the watchdog
            isKillNum ++;
            info(LoggingService.STD_OUTPUT_IS_KILLED);
            info(currentFixAction.toString());
        }

        ResultReader resultReader = new ResultReader();
        List<TestExecutionResult> results = resultReader.getNewResults(getLogPath());
        currentFixResults.addAll(results);

        return true;
    }

    private String buildCommandLineStr() throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append(project.getJavaEnvironment().getJvmPath().toString().replace(".exe", "") + " ").append(" ")
                .append(project.commandLineStringForAgents(true))
                // Enable assertions which are often used in tests to express the oracle.
                .append("-ea ")
                .append("-cp \"" + project.getClasspathForFixingStr() + "\" ");

        // Entry class for test execution
        sb.append(Tester.class.getName()).append(' ');

        // Arguments
        sb.append(TesterConfig.LOG_FILE_OPT).append(" \"").append(getLogPath()).append("\" ")
                .append(TesterConfig.LOG_LEVEL_OPT).append(" ").append(logLevel.name()).append(" ")
                .append(TesterConfig.FAILURE_HANDLING_OPT).append(" ").append(failureHandling.name()).append(" ")
                .append(TesterConfig.TIMEOUT_PER_TEST_OPT).append(" ").append(timeoutPerTest).append(" ")
                .append(TesterConfig.MAX_FAILING_TESTS_OPT).append(" ").append(maxFailingTests).append(" ")
                .append(TesterConfig.SHOULD_QUIT_UPON_MAX_FAILING_TESTS_OPT).append(" ").append(true).append(" ")
                .append(TesterConfig.SHOULD_COMMUNICATE_VIA_STDOUT_OPT).append(" ").append(true).append(" ")
                .append(TesterConfig.BATCH_SIZE_OPT).append(" ").append(BATCH_SIZE).append(" ")
                .append(TesterConfig.ACTIVE_FIX_INDEX_OPT).append(" ").append(this.fixActionIndex).append(" ")
                .append(TesterConfig.ACTIVE_FIX_INDEX_OPT).append(" ").append(this.fixActionIndex).append(" ")
                .append(TesterConfig.BATCH_SIZE_OPT).append(" ").append(BATCH_SIZE).append(" ");

        sb.append(JavaProject.commandLineArgumentForTestsToRun(tests));

        return sb.toString();
    }

    private Path getLogPath() {
        return FixerOutput.getFixLogFilePath(currentFixAction.getFixId(), "validate");
    }


}

