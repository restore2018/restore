package hk.polyu.comp.jaid.fixer;

import ch.qos.logback.classic.Level;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.config.Config;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.jdi.debugger.ExpressionSelector;
import hk.polyu.comp.jaid.monitor.jdi.debugger.LocationSelector;
import hk.polyu.comp.jaid.monitor.jdi.debugger.ProgramStepStateMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.util.LogUtil;
import org.eclipse.jdt.core.dom.Statement;

import java.util.*;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.config.Config.*;
import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.*;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.*;

public class ProgramMonitor {
    Config config;
    List<TestExecutionResult> testResults;

    public ProgramMonitor(Config config) {
        this.config = config;
    }

    public List<TestExecutionResult> getTestResults() {
        if (testResults == null)
            execute();
        return testResults;
    }

    public Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<StateSnapshot>> execute() {
        JavaProject project=config.getJavaProject();

        selectLocationsToMonitor(project);
        List<TestExecutionResult> expSelectorTestResults = filterOutSideEffectExp(project);
        // Build snapshots
        project.getMethodToMonitor().getMethodDeclarationInfoCenter().buildStateSnapshotsWithinMethod();
        List<TestRequest> debuggerTestToRun = project.getValidTestsToRunUnderRange();
        // Program state monitoring
        testResults = monitorProgramStates(project, expSelectorTestResults,debuggerTestToRun);
        return computeStateSnapshotSuspiciousness();
    }

    protected List<TestExecutionResult> selectLocationsToMonitor(JavaProject project) {
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();

        LocationSelector locationSelector = new LocationSelector(project,
                Session.getSession().getConfig().getLogLevel(),
                project.getTimeoutPerTest() * 50, FailureHandling.CONTINUE);
        locationSelector.launch();
        locationSelector.pruneIrrelevantLocationsAndTests();
        project.recordRelevantTests(locationSelector.getRelevantTestResults());

        Set<LineLocation> relevantLocations = locationSelector.getRelevantLocations();
        infoCenter.pruneIrrelevantLocation(relevantLocations);
        LogUtil.logLocationForDebug(relevantLocations);

        return locationSelector.getRelevantTestResults();
    }

    protected List<TestExecutionResult> filterOutSideEffectExp(JavaProject project) {
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();
        boolean hasSideEffectExpressions = true;
        List<TestExecutionResult> expSelectorTestResult = null;

        while (hasSideEffectExpressions) {
            ExpressionSelector selector = new ExpressionSelector(
                    project, Session.getSession().getConfig().getLogLevel(),
                    project.getTimeoutPerTest() * 40 * infoCenter.getRelevantLocationStatementMap().size(),
                    FailureHandling.CONTINUE, infoCenter.getAllLocationStatementMap().keySet());
            selector.doSelection();
            hasSideEffectExpressions = selector.hasFoundExpressionWithSideEffect();
            expSelectorTestResult = selector.getResults();
        }

        infoCenter.retainSideEffectFreeExpressionsToLocation();

        if (shouldLogDebug()) {
            Set<ExpressionToMonitor> sideEffectFreeExpressions = infoCenter.getSideEffectFreeExpressionsToMonitor();
            LoggingService.infoAll("============ AllNonSideEffectETM size::" + sideEffectFreeExpressions.size());
            sideEffectFreeExpressions.stream().forEach(f -> LoggingService.debugFileOnly(f.toString(), MONITORED_EXPS));
        }
        return expSelectorTestResult;
    }

    protected List<TestExecutionResult> monitorProgramStates(JavaProject project, List<TestExecutionResult> testResults
                                                             , List<TestRequest> debuggerTestToRun) {
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();
        Set<ExpressionToMonitor> sideEffectFreeExpressions = infoCenter.getSideEffectFreeExpressionsToMonitor();
        Map<LineLocation, Statement> relevantLocation = infoCenter.getRelevantLocationStatementMap();
        List<TestExecutionResult> testExecutionResults = new ArrayList<>();
        updateTestParameters(relevantLocation.size(), sideEffectFreeExpressions.size());//Important: update test parameters before using them.
        int monitorTestBatch = TEST_BATCH;
        for (int i = 0; i <= debuggerTestToRun.size() / monitorTestBatch; i++) {
            int endIdx = Math.min(debuggerTestToRun.size(), (i + 1) * monitorTestBatch);
            int test_threshold = TEST_THRESHOLD - i * monitorTestBatch;
            test_threshold = test_threshold > 0 ? test_threshold : 0;
            List<TestRequest> testToRunRequest = debuggerTestToRun.subList(i * monitorTestBatch, endIdx);

            ProgramStepStateMonitor programStateMonitor = new ProgramStepStateMonitor(
                    project,
                    Session.getSession().getConfig().getLogLevel(),
                    project.getTimeoutPerTest() * 40 * relevantLocation.size() * sideEffectFreeExpressions.size(),
                    FailureHandling.CONTINUE, relevantLocation.keySet(), testResults, testToRunRequest, test_threshold);
            programStateMonitor.launch();
            testExecutionResults.addAll(programStateMonitor.getResults());

            // Log exit states
            if (shouldLogDebug()) {
                for (TestExecutionResult testExecutionResult : programStateMonitor.getResults()) {
                    LoggingService.debugFileOnly("\nTest: " + testExecutionResult.toString(), MONITORED_STATES);
                    for (ProgramState exitState : testExecutionResult.getExitStates())
                        LoggingService.debugFileOnly(exitState.toString(), MONITORED_STATES);
                }
            }
        }

        LogUtil.logSessionCosting("Finish evaluate program states.");
        return testExecutionResults;
    }

    protected Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<StateSnapshot>> computeStateSnapshotSuspiciousness() {
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<StateSnapshot>> flAlgorithmSnapshotMap = new HashMap<>();
        JavaProject project = config.getJavaProject();
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();

        List<TestExecutionResult> failingTestResults = testResults.stream().filter(x -> !x.wasSuccessful() && !x.getObservedStates().isEmpty()).collect(Collectors.toList());
        Set<StateSnapshot> allSnapsthots = infoCenter.getStateSnapshotsWithinMethod();

        computeLocationDistanceToFailure(failingTestResults.get(0), infoCenter);
        for (StateSnapshot stateSnapshot : allSnapsthots) {
            LineLocation location = stateSnapshot.getLocation();
            double distanceToFailure = infoCenter.getLocationDistanceToFailureMap().getOrDefault(location, (double) LineLocation.getMaximumDistanceToFailure());
            stateSnapshot.setDistanceToFailure(distanceToFailure);
        }
        filterOutSchemas4Snapshot(allSnapsthots, testResults);

        addFileLogger(LOCATION_SCORES, Level.DEBUG);

        AbsSuspiciousnessAlgorithm.SbflAlgorithm flAlgorithm = config.getExperimentControl().getSbflAlgorithm();
        if (flAlgorithm != null)
            flAlgorithmSnapshotMap.put(flAlgorithm, computeSingleSsScore(project, infoCenter, flAlgorithm));
        else
            for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : AbsSuspiciousnessAlgorithm.SbflAlgorithm.values()) {
                flAlgorithmSnapshotMap.put(sbflAlgorithm, computeSingleSsScore(project, infoCenter, sbflAlgorithm));
            }
        if (shouldLogDebug())
            allSnapsthots.forEach(x -> LoggingService.debugFileOnly(x.toString(), ALL_STATE_SNAPSHOT));

        removeExtraLogger(LOCATION_SCORES);
        return flAlgorithmSnapshotMap;
    }


    protected List<StateSnapshot> computeSingleSsScore(JavaProject project, MethodDeclarationInfoCenter infoCenter, AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm) {

        AbsSuspiciousnessAlgorithm flAlgorithm = AbsSuspiciousnessAlgorithm
                .construct(project.getRelevantTestCount(), project.getPassingTests().size(), sbflAlgorithm);

        // computing location suspiciousness score
        for (LineLocation location : infoCenter.getAllLocationStatementMap().keySet()) {
            location.computeSuspiciousness(infoCenter, flAlgorithm);
        }
        //Sorting and logging locations
        List<LineLocation> locationList = new LinkedList<>(infoCenter.getAllLocationStatementMap().keySet());
        locationList.sort((LineLocation s1, LineLocation s2) -> Double.compare(s2.getSuspiciousness(flAlgorithm.getSbflAlgorithm()), s1.getSuspiciousness(flAlgorithm.getSbflAlgorithm())));
        LoggingService.debugFileOnly("SBFL-" + sbflAlgorithm.name(), LOCATION_SCORES);
        locationList.stream().forEach(x -> LoggingService.debugFileOnly(x.richInfoToString() + " :: " + x.getSuspiciousness(sbflAlgorithm), LOCATION_SCORES));

        // computing snapshot suspiciousness score
        for (StateSnapshot stateSnapshot : infoCenter.getStateSnapshotsWithinMethod()) {
            stateSnapshot.computeSuspiciousness(infoCenter, flAlgorithm);
        }
        //Sorting snapshots
        List<StateSnapshot> result = infoCenter.getStateSnapshotsWithinMethod().stream().filter(x -> x.getOccurrenceInFailing() > 0).collect(Collectors.toList());
        result.sort((StateSnapshot s1, StateSnapshot s2) -> Double.compare(s2.getSuspiciousness(flAlgorithm.getSbflAlgorithm()), s1.getSuspiciousness(flAlgorithm.getSbflAlgorithm())));

        result = result.subList(0, Math.min(result.size(), MAXIMUM_STATE_SNAPSHOTS));
        LoggingService.infoAll("Valid snapshots ::" + result.size());

        return result;
    }


    protected void computeLocationDistanceToFailure(TestExecutionResult testExecutionResult, MethodDeclarationInfoCenter infoCenter) {
        Map<LineLocation, Double> map = infoCenter.getLocationDistanceToFailureMap();
        List<ProgramState> states = new ArrayList<>(testExecutionResult.getObservedStates());

        ListIterator iterator = states.listIterator(states.size());
        int distance = 0, maximumDistance = -1;
        while (iterator.hasPrevious()) {
            LineLocation location = ((ProgramState) iterator.previous()).getLocation();
            if (map.containsKey(location))
                continue;

            map.put(location, (double) distance);
            if (maximumDistance < distance)
                maximumDistance = distance;

            distance++;
        }
        LineLocation.setMaximumDistanceToFailure(maximumDistance);
    }

    /**
     * A snapshot should skip schema B,C,D while building fix if it fulfill both condition:
     * 1) snapshot.location is executed only once in a failing test
     * 2) the evaluated result of the expression is not equals to the snapshot value
     * <p>
     * Which means the 'suspicious' is False, therefor, fix actions inserted with BCD schemas makes no change to the MTF
     *
     * @param stateSnapshots
     * @param testResults
     */
    protected void filterOutSchemas4Snapshot(Set<StateSnapshot> stateSnapshots, Collection<TestExecutionResult> testResults) {
        //Group snapshots by location
        Map<LineLocation, List<StateSnapshot>> locationSnapshots = new HashMap<>();
        for (StateSnapshot snapshot : stateSnapshots) {
            LineLocation location = snapshot.getLocation();
            if (locationSnapshots.containsKey(location))
                locationSnapshots.get(location).add(snapshot);
            else {
                List<StateSnapshot> list = new ArrayList<>();
                list.add(snapshot);
                locationSnapshots.put(location, list);
            }
        }
        for (TestExecutionResult result : testResults) {
            if (!result.wasSuccessful()) {
                Map<LineLocation, List<ProgramState>> locationProgramStates = new HashMap<>();
                //Group program states by location
                for (ProgramState programState : result.getObservedStates()) {
                    LineLocation stateL = programState.getLocation();
                    if (locationProgramStates.containsKey(stateL))
                        locationProgramStates.get(stateL).add(programState);
                    else {
                        List<ProgramState> list = new ArrayList<>();
                        list.add(programState);
                        locationProgramStates.put(stateL, list);
                    }
                }
                //Evaluate each snapshots
                for (LineLocation snapshotLocation : locationSnapshots.keySet()) {
                    if (!locationProgramStates.containsKey(snapshotLocation)) continue;
                    if (locationProgramStates.get(snapshotLocation).size() == 1)
                        for (StateSnapshot stateSnapshot : locationSnapshots.get(snapshotLocation)) {
                            DebuggerEvaluationResult evaluationResult =
                                    stateSnapshot.getSnapshotExpression().evaluate(locationProgramStates.get(snapshotLocation).get(0));
                            if (!stateSnapshot.getValue().equals(evaluationResult))
                                stateSnapshot.setShouldSkipSchemaBCD();
                        }
                }
            }
        }
    }
}
