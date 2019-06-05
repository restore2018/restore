package hk.polyu.comp.jaid.monitor.jdi.debugger;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.BreakpointRequest;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.FixedMethodNameFormatter;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import hk.polyu.comp.jaid.monitor.state.FramesStack;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.tester.TesterConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static hk.polyu.comp.jaid.fixer.ImpactComparator.retainMethodImpact;
import static hk.polyu.comp.jaid.fixer.config.Config.BATCH_SIZE;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

public class JaidRanking extends AbstractDebuggerLauncher {

    private int maxFailingTests;
    private List<FixAction> fixActions;
    private int fixActionIndex;
    private FixAction currentFixAction;
    private boolean shouldMonitorExitState;
    private List<TestRequest> debuggerTestToRun;

    private int nbrStackFramesAtMethodEntry;

    public JaidRanking(JavaProject project,
                       LogLevel logLevel,
                       int maxFailingTests,
                       long timeoutPerTest,
                       FailureHandling failureHandling) {
        super(project, logLevel, timeoutPerTest, failureHandling);

        this.maxFailingTests = maxFailingTests;
        this.enableAssertAgent = true;
        this.debuggerTestToRun = getProject().getValidTestsToRunUnderRange();
    }

    // todo:override prepareLaunchArguments

    public void validateFixAction(List<FixAction> fixActions, int fixActionIndex, boolean shouldMonitorExitState) {
        this.fixActions = fixActions;
        this.fixActionIndex = fixActionIndex;
        this.shouldMonitorExitState = shouldMonitorExitState;

        boolean hasError = false;
        String msg = "";
        currentFixAction = this.fixActions.get(this.fixActionIndex);

        if (shouldLogDebug()) {
            LoggingService.debug(this.getClass().getName() + " of fix #" + this.getCurrentFixAction().getFixId() + " starting...");
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
                    LoggingService.error(this.getClass().getName() + " of fix #" + currentFixAction.getFixId() + " failed with message: " + msg);
                else
                    LoggingService.debug(this.getClass().getName() + " of fix #" + currentFixAction.getFixId() + " succeeded, "
                            + (currentFixAction.getSuccessfulTestExecutionResultsCount() == debuggerTestToRun.size() ? "" : "not ")
                            + "valid");
            }
        }
    }

    public int getTestSize() {
        return debuggerTestToRun.size();
    }

    public FixAction getCurrentFixAction() {
        return currentFixAction;
    }

    protected void prepareForLaunch() {
        super.prepareForLaunch();
        this.currentFixAction.setTestExecutionResults(getResults());
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

    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException {
        if (!referenceType.name().equals(Tester.class.getName())) {

            if (shouldMonitorExitState) {
                MethodToMonitor methodToMonitor = this.getCurrentFixAction().getLocation().getContextMethod();
                FixedMethodNameFormatter methodNameFormatter = new FixedMethodNameFormatter();
                String fixedMethodToMonitorName = methodNameFormatter.getFixedMethodName(methodToMonitor.getMethodAST().getName().getIdentifier(), this.fixActionIndex);

                List<Method> methods = referenceType.methodsByName(fixedMethodToMonitorName);
                if (methods.size() != 1) throw new IllegalStateException("Unexpected number of fixed method!");
                Method fixedMethod = methods.get(0);

                // Monitor all locations of MTF
                getBreakpointRequestsForMonitoring().addAll(addBreakPointToAllLocationsInMethod(fixedMethod, shouldEnable));
            }
        } else {
            setMtfEntryAndExitLocationBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
            setAssertInvocationMonitorBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
        }
    }

    @Override
    protected List<TestRequest> testsToRunList() {
        return debuggerTestToRun;
    }

    protected Path getLogPath() {
        return FixerOutput.getFixLogFilePath(this.currentFixAction.getFixId(), "rank");
    }


    protected void processTestStart(BreakpointEvent breakpointEvent) {
        super.processTestStart(breakpointEvent);

        nbrStackFramesAtMethodEntry = -1;

        if (this.shouldMonitorExitState) {
            if (!getBreakpointRequestsForMonitoring().isEmpty())
                getBreakpointRequestsForMonitoring().forEach(x -> x.enable());
        }
    }

    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        super.processTestEnd(breakpointEvent);

        if (this.shouldMonitorExitState) {
            if (!getBreakpointRequestsForMonitoring().isEmpty())
                getBreakpointRequestsForMonitoring().forEach(x -> x.disable());
            //Only keep MethodImpact, remove other unchange states.
            retainMethodImpact(getCurrentTestResult());
        }
    }


    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        getBreakpointRequestsForMonitoring().forEach(x -> x.disable());//disable all breakpoints to prevent blocking.

        Location location = breakpointEvent.location();

        int lineNo = location.lineNumber();

        if (getMtfEntryLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == -1) {
            nbrStackFramesAtMethodEntry = safeGetNbrStackFrames(breakpointEvent);
            Utils4Debugger.monitorThreadMultipleFrames(getProject(), breakpointEvent.thread(), getCurrentTestResult(), FramesStack.StackTag.ENTRY);

        } else if (getMtfExitLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == safeGetNbrStackFrames(breakpointEvent)) {
            nbrStackFramesAtMethodEntry = -1;
            Utils4Debugger.monitorThreadMultipleFrames(getProject(), breakpointEvent.thread(), getCurrentTestResult(), FramesStack.StackTag.EXIT);

//            monitorExitState(breakpointEvent);
        } else if (getAssertInvocationLocationBreakpoint().equals(location)) {
            //divide the exit states results
            getCurrentTestResult().addDividerFramesStack();
        } else {
            //TODO:record the executed statement inside try block only.
            getCurrentTestResult().getObservedLocations().add(lineNo);
        }
        getBreakpointRequestsForMonitoring().forEach(x -> x.enable());//enable breakpoints

    }

    @Override
    protected void processStepEvent(StepEvent stepEvent) throws AbsentInformationException {

    }


}
