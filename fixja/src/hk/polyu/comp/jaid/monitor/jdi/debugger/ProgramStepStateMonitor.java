package hk.polyu.comp.jaid.monitor.jdi.debugger;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.tools.example.debug.expr.ExpressionParser;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.monitor.jdi.debugger.Utils4Debugger.evaluate;
import static hk.polyu.comp.jaid.monitor.jdi.debugger.Utils4Debugger.getFrameGetter;

public class ProgramStepStateMonitor extends AbstractDebuggerLauncher {

    private Map<Integer, LineLocation> allLocations;
    private Set<LineLocation> validLocations;
    private List<TestRequest> debuggerTestToRun;
    private int test_threshold;

    private int nbrStackFramesAtMethodEntry;
    private List<String> failingTestList;


    /**
     * Important Note: To reduce the memory usage at this stage,
     * 1. mapping the observed program states to snapshots after each test.
     * 2. if valid test size larger than TEST_THRESHOLD,
     * the ObservedStates of the passing test result will be deleted after calculation.
     *
     * @param project
     * @param logLevel
     * @param timeoutPerTest
     * @param failureHandling
     * @param validLocations
     * @param lastTestResults
     */
    public ProgramStepStateMonitor(JavaProject project, LogLevel logLevel, long timeoutPerTest,
                                   FailureHandling failureHandling, Set<LineLocation> validLocations,
                                   List<TestExecutionResult> lastTestResults, List<TestRequest> testToRunRequests, int test_threshold) {
        super(project, logLevel, timeoutPerTest, failureHandling);
        this.failingTestList = lastTestResults.stream().filter(x -> !x.wasSuccessful()).map(TestExecutionResult::getTestClassAndMethod).collect(Collectors.toList());
        this.allLocations = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter().getAllLocationStatementMap().keySet()
                .stream().collect(Collectors.toMap(LineLocation::getLineNo, Function.identity()));
        this.validLocations = validLocations;
        this.enableAssertAgent = true;
        this.debuggerTestToRun = testToRunRequests;
        this.test_threshold = test_threshold;
    }


    // ============================================== Override

    @Override
    protected List<TestRequest> testsToRunList() {
        return debuggerTestToRun;
    }

    @Override
    protected Path getLogPath() {
        return FixerOutput.getMonitoredTestResultsLogFilePath();
    }

    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException {
        if (referenceType.name().equals(Tester.class.getName())) {
            setMtfEntryAndExitLocationBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
        }
    }

    @Override
    protected void processTestStart(BreakpointEvent breakpointEvent) {

        super.processTestStart(breakpointEvent);
        nbrStackFramesAtMethodEntry = -1;

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(EventRequest::enable);
    }

    @Override
    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        super.processTestEnd(breakpointEvent);

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(EventRequest::disable);

        //mapping after each test end
        getProject().getMethodToMonitor().getMethodDeclarationInfoCenter().mappingObservedStatesToSnapshots(getCurrentTestResult());
        if ((getResults().size() - failingTestList.size()) > test_threshold && getCurrentTestResult().wasSuccessful())
            getCurrentTestResult().removeObservedStates();
    }


    /**
     * For validLocation: Using JdbEvaluate method to monitor valid expressions
     * For entryExitLocation: stop and record frame count
     * For otherLocation: skip
     *
     * @param breakpointEvent
     */
    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) {

        Location location = breakpointEvent.location();
        if (getMtfEntryLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == -1) {
            nbrStackFramesAtMethodEntry = safeGetNbrStackFrames(breakpointEvent);
            commandStep(breakpointEvent);
        } else if (getMtfExitLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == safeGetNbrStackFrames(breakpointEvent)) {
            nbrStackFramesAtMethodEntry = -1;
        } else if (getAssertInvocationLocationBreakpoint() != null && getAssertInvocationLocationBreakpoint().equals(location)) {
            getCurrentTestResult().addDividerFramesStack();
        }
    }

    @Override
    protected void processStepEvent(StepEvent stepEvent) {
        getBreakpointRequestsForMonitoring().forEach(EventRequest::disable);//disable all breakpoints to prevent blocking.
        //This instruction is really time consuming
        int lineNo = stepEvent.location().lineNumber();
        if (stepEvent.location().method().name().startsWith(getProject().getMethodToMonitor().getSimpleName())) {
            if (validLocations.contains(allLocations.get(lineNo))) {
                getCurrentTestResult().getObservedLocations().add(lineNo);
                //Monitoring program states for snapshots
                ProgramState state = monitoringProgramStates(stepEvent);
                getCurrentTestResult().getObservedStates().add(state);
            }
            commandStep(stepEvent);
        }

        getBreakpointRequestsForMonitoring().forEach(EventRequest::enable);//enable breakpoints
    }

    private ProgramState monitoringProgramStates(StepEvent stepEvent) {
        int lineNo = stepEvent.location().lineNumber();
        LineLocation lineLocation = allLocations.get(lineNo);
        ProgramState state = new ProgramState(lineLocation);

        MethodDeclarationInfoCenter infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();
        //Using JDI API to monitor local variables and fields
        Utils4Debugger.monitorThreadCurrentFrame(infoCenter, stepEvent.thread(), state);
        //Using JDB evaluate other expressions (including method invocations)
        ExpressionParser.GetFrame getFrame = getFrameGetter(stepEvent.thread(), 0, null);
        Set<ExpressionToMonitor> etmToEvaluate = infoCenter.getLocationExpressionMap().get(lineLocation).stream()
                .filter(x -> !state.getExpressionToValueMap().containsKey(x)).collect(Collectors.toSet());
//            Set<ExpressionToMonitor> etmToEvaluate = infoCenter.getNearByLocationExpressionToMonitor(lineLocation).stream()
//                    .filter(x -> !state.getExpressionToValueMap().containsKey(x)).collect(Collectors.toSet());
        for (ExpressionToMonitor etm : etmToEvaluate) {
            if (etm.isInvokeMTF() || etm.isFinal()) continue;
            DebuggerEvaluationResult debuggerEvaluationResult = evaluate(getVirtualMachine(), getFrame, etm);
            if (debuggerEvaluationResult.hasSyntaxError())
                continue;
            if (!debuggerEvaluationResult.hasSemanticError() && !debuggerEvaluationResult.hasSyntaxError())
                state.extend(etm, debuggerEvaluationResult);
        }
        return state;

    }

}
