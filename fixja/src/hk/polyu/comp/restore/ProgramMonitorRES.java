package hk.polyu.comp.restore;

import ch.qos.logback.classic.Level;
import hk.polyu.comp.jaid.ast.BasicBlock;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.ProgramMonitor;
import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.fixer.config.Config;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.util.LogUtil;

import java.util.*;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.config.Config.MAXIMUM_STATE_SNAPSHOTS;
import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.*;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.*;
import static hk.polyu.comp.jaid.util.CommonUtils.ulamDistance;
import static hk.polyu.comp.jaid.util.LogUtil.logMapForDebug;

public class ProgramMonitorRES extends ProgramMonitor {
    Config config;
    List<TestExecutionResult> testResults;
    LocationSelectorRES locationSelector;

    public LocationSelectorRES getLocationSelector() {
        return locationSelector;
    }

    public ProgramMonitorRES(Config config) {
        super(config);
        this.config = config;
    }

    public List<TestExecutionResult> getTestResults() {
        if (testResults == null)
            execute(false);
        return testResults;
    }

    private List<TestRequest> getRandomTestForStateMonitor(JavaProject project) {
        List<TestRequest> FailingRequests = new ArrayList<>(project.getFailingTests());
        Set<TestRequest> randomPassingRequest = new HashSet<>();
        Random random = new Random();
        while (randomPassingRequest.size() < FailingRequests.size()
                && randomPassingRequest.size() < project.getPassingTests().size()) {
            randomPassingRequest.add(project.getPassingTests().get(random.nextInt(project.getPassingTests().size())));
        }
        FailingRequests.addAll(randomPassingRequest);
        FailingRequests.forEach(x -> LoggingService.infoAll(x.toString()));

        return FailingRequests;
    }

    private List<TestRequest> getNearestTestForStateMonitor(JavaProject project) {
        List<BasicBlock> basicBlocks = project.getMethodToMonitor().getMethodDeclarationInfoCenter().getExecutableBasicBlocks();
        Map<TestRequest, List<BasicBlock>> sortedBbForTest = getLocationSelector().getSortedBbForTests(basicBlocks);
        LoggingService.infoFileOnly("\n", BASIC_BLOCK_MAP);
        logMapForDebug(sortedBbForTest, BASIC_BLOCK_MAP);
        List<TestRequest> failingTestRequest = new ArrayList<>(project.getFailingTests());
        List<TestRequest> passingTestRequest = new ArrayList<>(project.getPassingTests());
        Map<TestRequest, Integer> testUlamScoreMap = new HashMap<>();
        int maxScoreForAll = 0;
        for (TestRequest passingTest : passingTestRequest) {
            List<BasicBlock> passingBb = sortedBbForTest.get(passingTest);
            int maxUlamScore = 0;
            for (TestRequest failingTest : failingTestRequest) {
                List<BasicBlock> failingBb = sortedBbForTest.get(failingTest);
                int ulamScore = ulamDistance(failingBb, passingBb);
                if (ulamScore > maxUlamScore) maxUlamScore = ulamScore;
                if (ulamScore > maxScoreForAll) maxScoreForAll = ulamScore;
            }
            testUlamScoreMap.put(passingTest, maxUlamScore);
        }
        LoggingService.infoFileOnly("\n", BASIC_BLOCK_MAP);
        testUlamScoreMap.forEach((k, v) -> LoggingService.infoFileOnly(k.toString() + ": " + v, BASIC_BLOCK_MAP));

        int finalMaxScoreForAll = maxScoreForAll;
        Map<TestRequest, Integer> filteredTestUlamScoreMap = testUlamScoreMap.entrySet().stream()
                .filter(x -> x.getValue() == finalMaxScoreForAll)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<TestRequest> selectedTest = new ArrayList<>(filteredTestUlamScoreMap.keySet());
        selectedTest.addAll(failingTestRequest);
        LoggingService.infoAll("selected tests" + ": " + selectedTest.size());
        return selectedTest;
    }

    public Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<StateSnapshot>> execute(boolean isSelectAllStateSnapshot) {
        JavaProject project = config.getJavaProject();

        selectLocationsToMonitor(project);
        List<TestExecutionResult> expSelectorTestResults = filterOutSideEffectExp(project);
        // Build snapshots
        project.getMethodToMonitor().getMethodDeclarationInfoCenter().buildStateSnapshotsWithinMethod();
        List<TestRequest> debuggerTestToRun = getNearestTestForStateMonitor(project);
        // Program state monitoring
        testResults = monitorProgramStates(project, expSelectorTestResults, debuggerTestToRun);

        return computeLocationSnapshotSuspiciousness(isSelectAllStateSnapshot);
    }

    protected List<TestExecutionResult> selectLocationsToMonitor(JavaProject project) {
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();

        locationSelector = new LocationSelectorRES(project,
                Session.getSession().getConfig().getLogLevel(),
                project.getTimeoutPerTest() * 40, FailureHandling.CONTINUE);
        locationSelector.launch();
        locationSelector.pruneIrrelevantLocationsAndTests();
        project.recordRelevantTests(locationSelector.getRelevantTestResults());

        Set<LineLocation> relevantLocations = locationSelector.getRelevantLocations();
        infoCenter.pruneIrrelevantLocation(relevantLocations);
//        locationSelector.recordingVisitedLocations();
        LogUtil.logLocationForDebug(relevantLocations);

        return locationSelector.getRelevantTestResults();
    }


    private Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<StateSnapshot>> computeLocationSnapshotSuspiciousness(boolean isSelectAllStateSnapshot) {
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<StateSnapshot>> flAlgorithmSnapshotMap = new HashMap<>();
        JavaProject project = config.getJavaProject();
        MethodDeclarationInfoCenter infoCenter = project.getMethodToMonitor().getMethodDeclarationInfoCenter();
        Set<StateSnapshot> allSnapsthots = infoCenter.getStateSnapshotsWithinMethod();

        filterOutSchemas4Snapshot(allSnapsthots, testResults);
        addFileLogger(LOCATION_SCORES, Level.DEBUG);

        AbsSuspiciousnessAlgorithm.SbflAlgorithm flAlgorithm = config.getExperimentControl().getSbflAlgorithm();
        if (flAlgorithm != null)
            flAlgorithmSnapshotMap.put(flAlgorithm, computeSingleSsScore(project, infoCenter, flAlgorithm, isSelectAllStateSnapshot));
        else
            for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : AbsSuspiciousnessAlgorithm.SbflAlgorithm.values()) {
                flAlgorithmSnapshotMap.put(sbflAlgorithm, computeSingleSsScore(project, infoCenter, sbflAlgorithm, isSelectAllStateSnapshot));
            }
        if (shouldLogDebug())
            allSnapsthots.forEach(x -> LoggingService.debugFileOnly(x.toString(), ALL_STATE_SNAPSHOT));

        removeExtraLogger(LOCATION_SCORES);
        return flAlgorithmSnapshotMap;
    }

    private List<StateSnapshot> computeSingleSsScore(JavaProject project, MethodDeclarationInfoCenter infoCenter, AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm, boolean isSelectAllStateSnapshot) {
        // computing
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
        locationList.stream().forEach(x -> LoggingService.debugFileOnly(x.toString() + " :: " + x.getSuspiciousness(sbflAlgorithm), LOCATION_SCORES));

        // computing snapshot suspiciousness score
        applyLocationOccurrenceToSnapShots(infoCenter);
        for (StateSnapshot stateSnapshot : infoCenter.getStateSnapshotsWithinMethod()) {
            stateSnapshot.computeSuspiciousness(infoCenter, flAlgorithm);
        }

        //Sorting
        List<StateSnapshot> result = infoCenter.getStateSnapshotsWithinMethod().stream().filter(x -> x.getOccurrenceInFailing() > 0).collect(Collectors.toList());
        result.sort((StateSnapshot s1, StateSnapshot s2) -> Double.compare(s2.getSuspiciousness(flAlgorithm.getSbflAlgorithm()), s1.getSuspiciousness(flAlgorithm.getSbflAlgorithm())));
        if (!isSelectAllStateSnapshot) {
            result = result.subList(0, Math.min(result.size(), MAXIMUM_STATE_SNAPSHOTS));
        }
        //result = result.subList(0, Math.min(result.size(), MAXIMUM_STATE_SNAPSHOTS));
        LoggingService.infoAll("Valid snapshots ::" + result.size());

        return result;
    }

    /**
     * Special treatment for using location occurrence to calculate snapshot suspiciousness
     *
     * @param infoCenter
     */
    private void applyLocationOccurrenceToSnapShots(MethodDeclarationInfoCenter infoCenter) {
        for (StateSnapshot stateSnapshot : infoCenter.getStateSnapshotsWithinMethod()) {
            int failingCount = stateSnapshot.getLocation().getOccurrenceInFailing();
            for (int i = 0; i < failingCount; i++) {
                stateSnapshot.increaseOccurrenceInFailingNoDup();
                stateSnapshot.increaseOccurrenceInFailing();
            }
            int passingCount = stateSnapshot.getLocation().getOccurrenceInPassing();
            for (int i = 0; i < passingCount; i++) {
                stateSnapshot.increaseOccurrenceInPassingNoDup();
                stateSnapshot.increaseOccurrenceInPassing();
            }
        }
    }

}
