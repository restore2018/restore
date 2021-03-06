package hk.polyu.comp.jaid.monitor.jdi.debugger;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.tools.example.debug.expr.ExpressionParser;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotExpression;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.FramesStack;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.ImpactComparator.retainMethodImpact;
import static hk.polyu.comp.jaid.monitor.jdi.debugger.Utils4Debugger.evaluate;
import static hk.polyu.comp.jaid.monitor.jdi.debugger.Utils4Debugger.getFrameGetter;

public class ProgramBreakPointStateMonitor extends AbstractDebuggerLauncher {

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
    public ProgramBreakPointStateMonitor(JavaProject project, LogLevel logLevel, long timeoutPerTest,
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
        if (!referenceType.name().equals(Tester.class.getName())) {
            Method methodToMonitor = getMethodToMonitorFromType(referenceType);
            // register breakpoint for valid locations of MTF
            getBreakpointRequestsForMonitoring().addAll(addBreakPointToLocations(methodToMonitor,validLocations, shouldEnable));
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

        //Only keep MethodImpact, remove other unchange states.
//        retainMethodImpact(getCurrentTestResult());
        //mapping after each test end
        mappingObservedStatesToSnapshots();
        if ((getResults().size() - failingTestList.size()) > test_threshold && getCurrentTestResult().wasSuccessful())
            getCurrentTestResult().removeObservedStates();
    }

    private void mappingObservedStatesToSnapshots() {
        MethodDeclarationInfoCenter infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();
        Set<LineLocation> relatedLocation = new HashSet<>();
        Set<StateSnapshot> relatedStateSnapshots = new HashSet<>();

        for (ProgramState oneObservedState : getCurrentTestResult().getObservedStates()) {
            LineLocation location = oneObservedState.getLocation();
            relatedLocation.add(location);

            if (!infoCenter.getRelevantLocationStatementMap().containsKey(location))
                continue;
            for (StateSnapshotExpression expressionToMonitor : infoCenter.getStateSnapshotExpressionsWithinMethod()) {
                DebuggerEvaluationResult evaluationResult = expressionToMonitor.evaluate(oneObservedState);
                if (evaluationResult.hasSemanticError() || evaluationResult.hasSyntaxError())
                    continue;

                if (!(evaluationResult instanceof DebuggerEvaluationResult.BooleanDebuggerEvaluationResult))
                    throw new IllegalStateException();
                boolean booleanEvaluationResult = ((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) evaluationResult).getValue();

                StateSnapshot stateSnapshot = infoCenter.getStateSnapshot(location, expressionToMonitor, booleanEvaluationResult);
                if (stateSnapshot != null) {//if snapshot is null means it is not valid at that location
                    relatedStateSnapshots.add(stateSnapshot);
                    if (getCurrentTestResult().wasSuccessful())
                        stateSnapshot.increaseOccurrenceInPassing();
                    else
                        stateSnapshot.increaseOccurrenceInFailing();
                }
            }
        }
        // record location coverage information
        for (LineLocation lineLocation : relatedLocation) {
            if (getCurrentTestResult().wasSuccessful())
                lineLocation.increasingOccurrenceInPassing();
            else
                lineLocation.increasingOccurrenceInFailing();
        }

    }



    /**
     * For validLocation: Using JDIAPI and JdbEvaluate to monitor valid expressions and their fields in CURRENT frame at the location (put into result.observedLocations)
     * For entryExitLocation: Disable Using JdiApi to monitor local variable and their fields in ALL frames in the thread (put in to result.framesStackList)
     * For otherLocation: Not stop at all
     *
     * @param breakpointEvent
     * @throws AbsentInformationException
     */
    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        getBreakpointRequestsForMonitoring().forEach(x -> x.disable());//disable all breakpoints to prevent blocking.

        Location location = breakpointEvent.location();
        if (getMtfEntryLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == -1) {
            nbrStackFramesAtMethodEntry = safeGetNbrStackFrames(breakpointEvent);
        } else if (getMtfExitLocationBreakpoint().equals(location) && nbrStackFramesAtMethodEntry == safeGetNbrStackFrames(breakpointEvent)) {
            nbrStackFramesAtMethodEntry = -1;
        } else if (getAssertInvocationLocationBreakpoint().equals(location)) {
            getCurrentTestResult().addDividerFramesStack();
        } else if (validLocations.contains(allLocations.get(location.lineNumber()))) {
            getCurrentTestResult().getObservedLocations().add(location.lineNumber());
            //Monitoring program states for snapshots
            ProgramState state = monitoringProgramStates(breakpointEvent);
            getCurrentTestResult().getObservedStates().add(state);
        }
        getBreakpointRequestsForMonitoring().forEach(x -> x.enable());//enable breakpoints
    }

    @Override
    protected void processStepEvent(StepEvent stepEvent) throws AbsentInformationException {

    }


    private ProgramState monitoringProgramStates(BreakpointEvent breakpointEvent) {
        int lineNo = breakpointEvent.location().lineNumber();
        LineLocation lineLocation = allLocations.get(lineNo);
        ProgramState state = new ProgramState(lineLocation);

        MethodDeclarationInfoCenter infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();
        //Using JDI API to monitor local variables and fields
        Utils4Debugger.monitorThreadCurrentFrame(infoCenter, breakpointEvent.thread(), state);
        //Using JDB evaluate other expressions (including method invocations)
        if (validLocations.contains(lineLocation)) {
            ExpressionParser.GetFrame getFrame = getFrameGetter(breakpointEvent.thread(), 0, null);
            Set<ExpressionToMonitor> etmToEvaluate = infoCenter.getLocationExpressionMap().get(lineLocation).stream()
                    .filter(x -> !state.getExpressionToValueMap().containsKey(x)).collect(Collectors.toSet());
//            Set<ExpressionToMonitor> etmToEvaluate = infoCenter.getNearByLocationExpressionToMonitor(lineLocation).stream()
//                    .filter(x -> !state.getExpressionToValueMap().containsKey(x)).collect(Collectors.toSet());
            for (ExpressionToMonitor etm : etmToEvaluate) {
                if (etm.isInvokeMTF() || etm.isFinal()) continue;
                //FOR debugging closure 18\31 only
//                LoggingService.debugFileOnly(lineNo + " M-etm:" + etm.toString(), FixerOutput.LogFile.FILE);
                DebuggerEvaluationResult debuggerEvaluationResult = evaluate(getVirtualMachine(), getFrame, etm);
                if (debuggerEvaluationResult.hasSyntaxError())
                    continue;
                if (!debuggerEvaluationResult.hasSemanticError() && !debuggerEvaluationResult.hasSyntaxError())
                    state.extend(etm, debuggerEvaluationResult);
            }
        }
        return state;


    }

}
