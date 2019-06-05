package hk.polyu.comp.jaid.monitor.jdi.debugger;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.BreakpointRequest;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.FixedMethodNameFormatter;
import hk.polyu.comp.jaid.fixer.BatchFixInstrumentor;
import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.LightLocation;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.tester.TesterConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hk.polyu.comp.jaid.fixer.config.Config.BATCH_SIZE;
import static hk.polyu.comp.jaid.fixer.config.Config.TEST_THRESHOLD;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

public class QloseRanking extends AbstractDebuggerLauncher {

    private int maxFailingTests;
    private List<FixAction> fixActions;
    private int fixActionIndex;
    private FixAction currentFixAction;

    private int nbrStackFramesAtMethodEntry;
    private List<TestRequest> debuggerTestToRun;


    public QloseRanking(JavaProject project,
                        LogLevel logLevel,
                        int maxFailingTests,
                        long timeoutPerTest,
                        FailureHandling failureHandling) {
        super(project, logLevel, timeoutPerTest, failureHandling);

        this.maxFailingTests = maxFailingTests;
        this.enableAssertAgent = true;
        this.debuggerTestToRun = getDebuggerTestToRun();

    }


    public FixAction getCurrentFixAction() {
        return currentFixAction;
    }

    private List<TestRequest> getDebuggerTestToRun() {
        int maxPassingTest = TEST_THRESHOLD;
        int configMaxPassingTest = Session.getSession().getConfig().getExperimentControl().getMaxPassingTestNumber();
        if (configMaxPassingTest > 0 && maxPassingTest > 0)
            maxPassingTest = Math.min(configMaxPassingTest, maxPassingTest);
        else if (maxPassingTest <= 0 && configMaxPassingTest > 0)
            maxPassingTest = configMaxPassingTest;

        if (maxPassingTest > 0 && getProject().getFailingTests().size() > 0)
            return Stream.concat(getProject().getFailingTests().stream(), getProject().getPassingTests().stream().limit(maxPassingTest)
            ).collect(Collectors.toList());
        else
            return getProject().getFailingTests();
    }

    /**
     * Compare two test control number, only execute smallest set of test
     *
     * @return
     * @throws Exception
     */
    @Override
    protected List<TestRequest> testsToRunList() {

        return debuggerTestToRun;
    }

    @Override
    protected Path getLogPath() {
        return FixerOutput.getFixLogFilePath(this.currentFixAction.getFixId(), "rank");
    }


    protected String argumentsForTester() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(TesterConfig.ACTIVE_FIX_INDEX_OPT).append(" ").append(this.fixActionIndex).append(" ")
                .append(TesterConfig.BATCH_SIZE_OPT).append(" ").append(BATCH_SIZE).append(" ")
                .append(TesterConfig.MAX_FAILING_TESTS_OPT).append(" ").append(this.maxFailingTests).append(" ")
                .append(TesterConfig.SHOULD_QUIT_UPON_MAX_FAILING_TESTS_OPT).append(" ").append(true).append(" ")
                .append(super.argumentsForTester());
        return sb.toString();
    }

    protected void prepareForLaunch() {
        super.prepareForLaunch();
        this.currentFixAction.setTestExecutionResults(getResults());
    }

    public void launchingFixAction(List<FixAction> fixActions, int fixActionIndex) {
        this.fixActions = fixActions;
        this.fixActionIndex = fixActionIndex;

        boolean hasError = false;
        String msg = "";
        currentFixAction = this.fixActions.get(this.fixActionIndex);

        if (shouldLogDebug()) {
            LoggingService.debug(this.getClass().getName() + "Launching  fix #" + this.getCurrentFixAction().getFixId() + "...");
        }
        try {
            launch();
        } catch (Exception e) {
            e.printStackTrace();
            hasError = true;
            msg = e.toString();
        } finally {
            if (shouldLogDebug()) {
                if (hasError)
                    LoggingService.error(this.getClass().getName() + "of fix #" + currentFixAction.getFixId() + " failed with message: " + msg);
                else
                    LoggingService.debug(this.getClass().getName() + "of fix #" + currentFixAction.getFixId() + " succeeded, "
                            + (currentFixAction.getSuccessfulTestExecutionResultsCount() == debuggerTestToRun.size() ? "" : "not ")
                            + "valid");
            }
        }
    }


    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException {
        if (!referenceType.name().equals(Tester.class.getName())) {

            MethodToMonitor methodToMonitor = this.getCurrentFixAction().getLocation().getContextMethod();
            FixedMethodNameFormatter methodNameFormatter = new FixedMethodNameFormatter();
            String fixedMethodToMonitorName = methodNameFormatter.getFixedMethodName(methodToMonitor.getMethodAST().getName().getIdentifier(), this.fixActionIndex);

            List<Method> methods = referenceType.methodsByName(fixedMethodToMonitorName);
            if (methods.size() != 1) throw new IllegalStateException("Unexpected number of fixed method!");

            // stepping start from the MtfEntryLocation, no need to register breakpoint for any locations
        } else {
            setMtfEntryAndExitLocationBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
            setAssertInvocationMonitorBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
        }
    }


    @Override
    protected void processTestStart(BreakpointEvent breakpointEvent) {
        super.processTestStart(breakpointEvent);

        nbrStackFramesAtMethodEntry = -1;

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(x -> x.enable());
    }

    @Override
    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        super.processTestEnd(breakpointEvent);

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(x -> x.disable());
    }

    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        getBreakpointRequestsForMonitoring().forEach(x -> x.disable());//disable all breakpoints to prevent blocking.
        Location location = breakpointEvent.location();
        if (getMtfEntryLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == -1) {
            nbrStackFramesAtMethodEntry = safeGetNbrStackFrames(breakpointEvent);

            //Using JDI API to monitor local variables and fields
            ProgramState state = new ProgramState(LightLocation.constructLightLocation(breakpointEvent.location()));
            MethodDeclarationInfoCenter infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();
            Utils4Debugger.monitorThreadCurrentFrame(infoCenter, breakpointEvent.thread(), state);
            getCurrentTestResult().getObservedStates().add(state);

            commandStep(breakpointEvent);
        } else if (getAssertInvocationLocationBreakpoint().equals(location)) {
            //divide the exit states results
            getCurrentTestResult().addDividerFramesStack();
        }
        getBreakpointRequestsForMonitoring().forEach(x -> x.enable());//enable breakpoints

    }

    @Override
    protected void processStepEvent(StepEvent stepEvent) throws AbsentInformationException {
        getBreakpointRequestsForMonitoring().forEach(x -> x.disable());//disable all breakpoints to prevent blocking.
        if (stepEvent.location().method().name().startsWith(getProject().getMethodToMonitor().getSimpleName() + "__")) {
            Location location = stepEvent.location();
            int lineNo = location.lineNumber();
            getCurrentTestResult().getObservedLocations().add(lineNo);

            //Using JDI API to monitor local variables and fields
            ProgramState state = new ProgramState(LightLocation.constructLightLocation(stepEvent.location()));
            MethodDeclarationInfoCenter infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();
            Utils4Debugger.monitorThreadCurrentFrame(infoCenter, stepEvent.thread(), state);
            getCurrentTestResult().getObservedStates().add(state);

            commandStep(stepEvent);
        }
        getBreakpointRequestsForMonitoring().forEach(x -> x.enable());//enable breakpoints
    }


}
