package hk.polyu.comp.restore;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.AccessWatchpointRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.ModificationWatchpointRequest;
import com.sun.jdi.request.WatchpointRequest;
import hk.polyu.comp.jaid.ast.BasicBlock;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import hk.polyu.comp.jaid.monitor.jdi.debugger.AbstractDebuggerLauncher;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public class LocationSelectorRES extends AbstractDebuggerLauncher {

    private List<LineLocation> locationsCoveredByTest;
    private Map<TestExecutionResult, List<LineLocation>> allLocationsCovered;
    private Set<LineLocation> relevantLocations;
    private Set<String> visitedFields;

    private int nbrStackFramesAtMethodEntry;
    private List<WatchpointRequest> watchpointRequests;


    public LocationSelectorRES(JavaProject project, LogLevel logLevel, long timeoutPerTest, FailureHandling failureHandling) {
        super(project, logLevel, timeoutPerTest, failureHandling);

        allLocationsCovered = new HashMap<>();
        visitedFields = new HashSet<>();
        watchpointRequests = new LinkedList<>();
        this.enableAssertAgent = true;

    }

    protected void initDebugger() {
        super.initDebugger();
        locationsCoveredByTest = null;
        watchpointRequests = null;
    }


    // Getters and Setters

    public Set<LineLocation> getRelevantLocations() {
        return relevantLocations;
    }

    public Map<TestExecutionResult, List<LineLocation>> getAllLocationsCovered() {
        return allLocationsCovered;
    }

    public Map<TestRequest, List<BasicBlock>> getSortedBbForTests(List<BasicBlock> basicBlocks) {
        Map<TestRequest, List<BasicBlock>> sortedBbForTest = new LinkedHashMap<>();
        for (TestExecutionResult result : getAllLocationsCovered().keySet()) {
            if (getRelevantTestResults().contains(result)) {
                TestRequest testRequest = getProject().getValidTestsToRun().stream().filter(x -> result.isEqualRequest(x)).collect(Collectors.toList()).get(0);
                Map<BasicBlock, Integer> bbFrequency = new LinkedHashMap<>();
                basicBlocks.forEach(x -> bbFrequency.put(x, 0));
                List<LineLocation> coveredLines = getAllLocationsCovered().get(result);
                List<BasicBlock> coveredBbs = BasicBlock.getCorrespondingBb(basicBlocks, coveredLines);
                for (BasicBlock coveredBb : coveredBbs) {
                    bbFrequency.put(coveredBb, bbFrequency.get(coveredBb) + 1);
                }
                List<BasicBlock> sortedBb = getSortedBb(bbFrequency);
                sortedBbForTest.put(testRequest, sortedBb);
            }
        }
        return sortedBbForTest;
    }

    public static List<BasicBlock> getSortedBb(Map<BasicBlock, Integer> map) {
        List<Map.Entry<BasicBlock, Integer>> list = new LinkedList<>(map.entrySet());
        list.sort(Comparator.comparing(e -> (e.getValue())));
        List<BasicBlock> result = new ArrayList<>();
        for (Map.Entry<BasicBlock, Integer> entry : list) {
            result.add(entry.getKey());
        }
        return result;
    }

    public int getNbrStackFramesAtMethodEntry() {
        return nbrStackFramesAtMethodEntry;
    }

    public void setNbrStackFramesAtMethodEntry(int nbrStackFramesAtMethodEntry) {
        this.nbrStackFramesAtMethodEntry = nbrStackFramesAtMethodEntry;
    }

    // Operations

    public void pruneIrrelevantLocationsAndTests() {
        relevantLocations = new HashSet<>();
        for (TestExecutionResult result : getAllLocationsCovered().keySet()) {
            if (!result.wasSuccessful()) {
                relevantLocations.addAll(getAllLocationsCovered().get(result));
            }
        }

        // prune irrelevant tests that cover none of the relevant locations
        // fixme: is the definition of the irrelevant tests correct?
        Set<TestExecutionResult> testsToRemove = new HashSet<>();
        for (TestExecutionResult result : getAllLocationsCovered().keySet()) {
            Set<LineLocation> commonLocations = new HashSet<>(relevantLocations);
            commonLocations.retainAll(getAllLocationsCovered().get(result));
            if (commonLocations.isEmpty()) {
                testsToRemove.add(result);
            }
            // prune timeout tests
            if (result.getRunTime() == -1) {
                LoggingService.infoAll("Test removed due to timeout: " + result.getTestClassAndMethod());
                testsToRemove.add(result);
                if (!commonLocations.isEmpty()) getProject().addTimeoutTests(result.getTestClassAndMethod());
            }
        }
        testsToRemove.forEach(x -> getAllLocationsCovered().remove(x));
    }

    public List<TestExecutionResult> getRelevantTestResults() {
        return new ArrayList<>(getAllLocationsCovered().keySet());
    }

    // Override

    @Override
    protected List<TestRequest> testsToRunList() {
        return getProject().getValidTestsToRun();
    }

    @Override
    protected Path getLogPath() {
        return FixerOutput.getPre4LocationTestResultsLogFilePath();
    }

    @Override
    protected void debuggerFinished() {
        getProject().getMethodToMonitor().setAccessedFields(getVisitedFieldsString());
        super.debuggerFinished();
    }

    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException, ClassNotLoadedException {
        if (!referenceType.name().equals(Tester.class.getName())) {

            Method methodToMonitor = getMethodToMonitorFromType(referenceType);
            MethodDeclarationInfoCenter infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();

            getBreakpointRequestsForMonitoring().addAll(registerAllBreakpoint(getBreakpointLocations(methodToMonitor, infoCenter.getRelevantLocationStatementMap().keySet()), shouldEnable));
            // Prepare extra breakpoint requests for evaluate field visit (R/W) and method exit.
            prepareFieldVisitEventRequests(referenceType);
        } else {
            setMtfEntryAndExitLocationBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
        }
    }

    @Override
    protected void handleOtherEventType(EventSet eventSet, Event event) {
        if (event instanceof WatchpointEvent) {
            WatchpointEvent wEvent = (WatchpointEvent) event;
            Field field = wEvent.field();
            visitedFields.add(field.toString());
        }
        eventSet.resume();
    }

    @Override
    protected void processTestStart(BreakpointEvent breakpointEvent) {
        super.processTestStart(breakpointEvent);

        locationsCoveredByTest = new ArrayList<>();
        getAllLocationsCovered().put(getCurrentTestResult(), locationsCoveredByTest);

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(EventRequest::enable);
    }

    @Override
    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        super.processTestEnd(breakpointEvent);

        if (!getBreakpointRequestsForMonitoring().isEmpty())
            getBreakpointRequestsForMonitoring().forEach(EventRequest::disable);
    }

    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        if (breakpointEvent.location().equals(getMtfEntryLocationBreakpoint())) {
            setNbrStackFramesAtMethodEntry(safeGetNbrStackFrames(breakpointEvent));
            watchpointRequests.forEach(EventRequest::enable);
        } else if (breakpointEvent.location().equals(getMtfExitLocationBreakpoint())) {
            if (getNbrStackFramesAtMethodEntry() == safeGetNbrStackFrames(breakpointEvent))
                watchpointRequests.forEach(EventRequest::disable);
        } else if (breakpointEvent.location().equals(getTimeoutLocationBreakpoint())) {
            watchpointRequests.forEach(EventRequest::disable);
            getBreakpointRequestsForMonitoring().forEach(EventRequest::disable);
        } else {
            MethodToMonitor methodToMonitor = getProject().getMethodToMonitor();
            LineLocation location = LineLocation.newLineLocation(methodToMonitor, breakpointEvent.location().lineNumber());
            locationsCoveredByTest.add(location);
        }
    }

    @Override
    protected void processStepEvent(StepEvent stepEvent) throws AbsentInformationException {

    }

    // Implementation details

    private void prepareFieldVisitEventRequests(ReferenceType referenceType) {
        List<Field> fields = referenceType.allFields();
        watchpointRequests = new LinkedList<>();
        for (Field field : fields) {
            // fixme: it's more precise to also use instance filter, when available.
            // fixme: For that purpose, we need to distinguish instance methods from static ones.
            AccessWatchpointRequest awRequest = getEventRequestManager().createAccessWatchpointRequest(field);
            awRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            watchpointRequests.add(awRequest);

            ModificationWatchpointRequest mwRequest = getEventRequestManager().createModificationWatchpointRequest(field);
            mwRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            watchpointRequests.add(mwRequest);
        }
    }

    private Set<String> getVisitedFieldsString() {
        return visitedFields;
    }


}
