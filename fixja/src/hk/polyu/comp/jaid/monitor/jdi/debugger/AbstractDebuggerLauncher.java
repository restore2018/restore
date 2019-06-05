package hk.polyu.comp.jaid.monitor.jdi.debugger;

import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import com.sun.tools.example.debug.expr.ExpressionParser;
import hk.polyu.comp.jaid.ast.MethodUtil;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.tester.TesterConfig;
import hk.polyu.comp.jaid.util.LogUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;
import static hk.polyu.comp.jaid.monitor.jdi.debugger.Utils4Debugger.getFrameGetter;
import static hk.polyu.comp.jaid.tester.Tester.TIME_OUT_STATE;
import static hk.polyu.comp.jaid.tester.TraceFormat.RESULT_GET_FAILURE_MSG;


public abstract class AbstractDebuggerLauncher {


    public static final String TEST_RESULT_RUN_TIME_METHOD_NAME = "getRunTime";
    public static final String TEST_RESULT_WAS_SUCCESSFUL_METHOD_NAME = "wasSuccessful";

    // In-parameters
    private JavaProject project;
    private LogLevel logLevel;
    private long timeoutPerTest;
    private FailureHandling failureHandling;

    // Debugger state
    private VirtualMachine virtualMachine;
    private boolean hasVirtualMachineTerminated;
    private List<BreakpointRequest> breakpointRequestsForMonitoring;

    // Test results
    private List<TestExecutionResult> testResults;
    protected TestExecutionResult currentTestResult;

    // Test related state
    private boolean testStarted;
    private int nbrStackFramesAtTestEntry;
    private Location testStartBreakpoint;
    private Location testEndBreakpoint;
    private Location mtfEntryLocationBreakpoint;
    private Location mtfExitLocationBreakpoint;
    private Location timeoutLocationBreakpoint;
    private Location assertInvocationLocationBreakpoint;
    private List<Thread> outputCollectingThreads;
    boolean isTimeout = false, isFirstTime = true;


    // Enable agent to divide exit state list with assertions
    protected boolean enableAssertAgent = false;


    /**
     * Constructor.
     */
    public AbstractDebuggerLauncher(JavaProject project,
                                    LogLevel logLevel,
                                    long timeoutPerTest,
                                    FailureHandling failureHandling
    ) {
        this.project = project;
        this.logLevel = logLevel;
        this.timeoutPerTest = timeoutPerTest;
        this.failureHandling = failureHandling;
        initDebugger();
    }

    protected void initDebugger() {
        this.setVirtualMachineTerminated(false);
        virtualMachine = null;
        outputCollectingThreads = new LinkedList<>();
        testStarted = false;
        breakpointRequestsForMonitoring = null;
    }

    /**
     * Launch the debugger
     */
    public void launch() {
        while (isTimeout || isFirstTime) {
            if (!isTimeout) prepareForLaunch();
            else initDebugger();

            try {
                LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
                Map<String, Connector.Argument> argumentMap = prepareLaunchArguments(launchingConnector);
                virtualMachine = launchingConnector.launch(argumentMap);
                isTimeout = false;

                registerClassPrepareRequest();
                registerClassPrepareRequestForTester();

                if (shouldLogDebug()) {
                    outputCollectingThreads.add(displayRemoteOutput(getProcess().getInputStream()));
                    outputCollectingThreads.add(displayRemoteOutput(getProcess().getErrorStream()));
                }

                // Enter event loop
                eventLoop();
            } catch (Exception e) {
                e.printStackTrace();
                LoggingService.warn("ADL:" + e.toString());
                for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                    LoggingService.warn(stackTraceElement.toString());
                }
                getVirtualMachine().exit(1);
            } finally {
                isFirstTime = false;
                if (getProcess() != null && getProcess().isAlive()) getProcess().destroy();
                while (getProcess().isAlive()) {
                    try {
                        Thread.currentThread().sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (getProcess().exitValue() == TIME_OUT_STATE) isTimeout = true;
                debuggerFinished();
            }
        }
    }

    // Methods to override

    protected abstract List<TestRequest> testsToRunList();

    protected List<TestRequest> testsToRunListAfterTimeOut() {
        List<String> executedTest = testResults.stream().map(TestExecutionResult::getTestClassAndMethod).collect(Collectors.toList());
        return testsToRunList().stream().filter(x -> !executedTest.contains(x.getTestClassAndMethod())).collect(Collectors.toList());
    }

    protected abstract void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException, ClassNotLoadedException;

    protected abstract void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException;

    protected abstract void processStepEvent(StepEvent stepEvent) throws AbsentInformationException;

    protected abstract Path getLogPath();

    // Getters and Setters

    public JavaProject getProject() {
        return project;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public long getTimeoutPerTest() {
        return timeoutPerTest;
    }

    public FailureHandling getFailureHandling() {
        return failureHandling;
    }

    protected VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    protected Process getProcess() {
        return getVirtualMachine().process();
    }

    protected EventRequestManager getEventRequestManager() {
        return getVirtualMachine().eventRequestManager();
    }

    protected boolean hasVirtualMachineTerminated() {
        return hasVirtualMachineTerminated;
    }

    protected void setVirtualMachineTerminated(boolean flag) {
        hasVirtualMachineTerminated = flag;
    }

    protected Location getTestStartBreakpoint() {
        return testStartBreakpoint;
    }

    private void setTestStartBreakpoint(Location location) {
        testStartBreakpoint = location;
    }

    protected Location getTestEndBreakpoint() {
        return testEndBreakpoint;
    }

    private void setTestEndBreakpoint(Location location) {
        testEndBreakpoint = location;
    }

    public Location getMtfEntryLocationBreakpoint() {
        return mtfEntryLocationBreakpoint;
    }

    public void setMtfEntryLocationBreakpoint(Location mtfEntryLocationBreakpoint) {
        this.mtfEntryLocationBreakpoint = mtfEntryLocationBreakpoint;
    }

    public Location getMtfExitLocationBreakpoint() {
        return mtfExitLocationBreakpoint;
    }

    public void setMtfExitLocationBreakpoint(Location mtfExitLocationBreakpoint) {
        this.mtfExitLocationBreakpoint = mtfExitLocationBreakpoint;
    }

    public Location getTimeoutLocationBreakpoint() {
        return timeoutLocationBreakpoint;
    }

    public void setTimeoutLocationBreakpoint(Location timeoutLocationBreakpoint) {
        this.timeoutLocationBreakpoint = timeoutLocationBreakpoint;
    }

    public Location getAssertInvocationLocationBreakpoint() {
        return assertInvocationLocationBreakpoint;
    }

    public void setAssertInvocationLocationBreakpoint(Location assertInvocationLocationBreakpoint) {
        this.assertInvocationLocationBreakpoint = assertInvocationLocationBreakpoint;
    }

    public List<TestExecutionResult> getResults() {
        return testResults;
    }

    public TestExecutionResult getCurrentTestResult() {
        return currentTestResult;
    }

    public boolean isTestStarted() {
        return testStarted;
    }

    public void setTestStarted(boolean testStarted) {
        this.testStarted = testStarted;
    }

    public int getNbrStackFramesAtTestEntry() {
        return nbrStackFramesAtTestEntry;
    }

    public void setNbrStackFramesAtTestEntry(int nbrStackFramesAtTestEntry) {
        this.nbrStackFramesAtTestEntry = nbrStackFramesAtTestEntry;
    }

    public List<BreakpointRequest> getBreakpointRequestsForMonitoring() {
        if (breakpointRequestsForMonitoring == null) {
            breakpointRequestsForMonitoring = new ArrayList<>();
        }
        return breakpointRequestsForMonitoring;
    }


    protected void prepareForLaunch() {
        newTestResults();
    }

    private void newTestResults() {
        testResults = new LinkedList<>();
    }

    protected void registerClassPrepareRequest() {
        ClassPrepareRequest classPrepareRequest = getEventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(getProject().getMethodToMonitor().getFullQualifiedClassName());
        classPrepareRequest.addCountFilter(1);
        classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        classPrepareRequest.enable();
    }

    protected String argumentsForTester() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(TesterConfig.LOG_FILE_OPT)
                .append(" \"")
                .append(getLogPath())
                .append("\" ");
        sb.append(TesterConfig.LOG_LEVEL_OPT)
                .append(" ")
                .append(getLogLevel().name()).append(' ');
        sb.append(TesterConfig.FAILURE_HANDLING_OPT)
                .append(" ")
                .append(getFailureHandling().name()).append(' ');
        sb.append(TesterConfig.TIMEOUT_PER_TEST_OPT)
                .append(" ")
                .append(getTimeoutPerTest()) // disable internal timer in class Tester //But why??
                .append(' ');
        List<TestRequest> testToRun;
        if (isTimeout) testToRun = testsToRunListAfterTimeOut();
        else testToRun = testsToRunList();
        sb.append(JavaProject.commandLineArgumentForTestsToRun(testToRun));
        return sb.toString();
    }

    protected int safeGetNbrStackFrames(BreakpointEvent breakpointEvent) {
        try {
            return breakpointEvent.thread().frameCount();
        } catch (Exception e) {
            throw new IllegalStateException();
        }
    }

    protected void processTestStart(BreakpointEvent breakpointEvent) {
        initTestResult(breakpointEvent);
        testResults.add(getCurrentTestResult());

    }

    protected void initTestResult(BreakpointEvent breakpointEvent) {
        currentTestResult = null;

        // Always get the stack frame of the caller
        ExpressionParser.GetFrame getFrame = getFrameGetter(breakpointEvent.thread(), 1, Tester.MemberName.TEST_EXECUTION_METHOD_NAME.getName());

        String testClass = "Error", testMethod = "Error";
        try {
            Value value = null;

            value = ExpressionParser.evaluate(Tester.MemberName.TEST_REQUEST_VARIABLE_NAME.getName() + "." + TestRequest.TEST_CLASS_GETTER_NAME + "()", getVirtualMachine(), getFrame);
            testClass = value.toString();
            testClass = testClass.substring(1, testClass.length() - 1);  // Remove the double quotes surrounding the class name.

            value = ExpressionParser.evaluate(Tester.MemberName.TEST_REQUEST_VARIABLE_NAME.getName() + "." + TestRequest.TEST_METHOD_GETTER_NAME + "()", getVirtualMachine(), getFrame);
            testMethod = value.toString();
            testMethod = testMethod.substring(1, testMethod.length() - 1);  // Remove the double quotes surrounding the method name.

        } catch (Exception e) {
            throw new IllegalStateException();
        } finally {
            currentTestResult = new TestExecutionResult(testClass, testMethod);
            if (shouldLogDebug()) {
                LoggingService.debug("Executing test: " + testClass + "." + testMethod);
            }
        }
    }

    ;

    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        updateTestResult(breakpointEvent);
    }

    protected void updateTestResult(BreakpointEvent breakpointEvent) {

        ExpressionParser.GetFrame getFrame = getFrameGetter(breakpointEvent.thread(), 1, Tester.MemberName.TEST_EXECUTION_METHOD_NAME.getName());

        long runTime = -1;
        boolean wasSuccessful = false;
        String failureMsg = "";
        try {
            Value value = null;
            value = ExpressionParser.evaluate(TEST_RESULT_RUN_TIME_METHOD_NAME, getVirtualMachine(), getFrame);
            if (value != null)
                runTime = Long.valueOf(value.toString());
            value = ExpressionParser.evaluate(TEST_RESULT_WAS_SUCCESSFUL_METHOD_NAME, getVirtualMachine(), getFrame);
            if (value != null)
                wasSuccessful = Boolean.valueOf(value.toString());
            if (!wasSuccessful) {
                value = ExpressionParser.evaluate(RESULT_GET_FAILURE_MSG, getVirtualMachine(), getFrame);
                if (value != null)
                    failureMsg = value.toString();
            }
        } catch (Exception e) {
            LoggingService.warn(e.toString());
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                LoggingService.warn(stackTraceElement.toString());
            }
        } finally {
            getCurrentTestResult().setRunTime(runTime);
            getCurrentTestResult().setWasSuccessful(wasSuccessful);
            if (!wasSuccessful) {
                getCurrentTestResult().setFailureMsg(failureMsg);
                if (shouldLogDebug())
                    LoggingService.debug(getCurrentTestResult().toString());
            }
        }
    }

    protected void debuggerFinished() {
        // no need to explicitly terminate outputCollectingThreads
    }

    protected void handleOtherEventType(EventSet eventSet, Event event) {
        eventSet.resume();
    }

    protected BreakpointRequest registerOneBreakpoint(Location location, boolean shouldEnable) {
        BreakpointRequest breakpointRequest = getEventRequestManager()
                .createBreakpointRequest(location);
        breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        if (shouldEnable)
            breakpointRequest.enable();

        return breakpointRequest;
    }

    protected List<BreakpointRequest> registerAllBreakpoint(Collection<Location> locations, boolean shouldEnable) {
        List<BreakpointRequest> requests = new LinkedList<>();
        for (Location location : locations) {
            requests.add(registerOneBreakpoint(location, shouldEnable));
        }
        return requests;
    }

    protected Set<Location> getBreakpointLocations(Method method, Collection<LineLocation> locations) {
        Set<Location> result = new HashSet<>();
        for (LineLocation location : locations) {
            try {
                List<Location> jdiLocations = method.locationsOfLine(location.getLineNo());
                if (jdiLocations.isEmpty())
                    continue;

                Location loc = jdiLocations.get(0);
                result.add(loc);
            } catch (Exception e) {
                throw new IllegalStateException();
            }
        }

        return result;
    }

    protected List<BreakpointRequest> addBreakPointToLocations(Method method, Collection<LineLocation> locations, boolean shouldEnable) throws AbsentInformationException {
        List<BreakpointRequest> requests = new LinkedList<>();

        for (LineLocation location : locations) {
            // Create BreakpointEvent for desired locations
            List<Location> jdiLocations = method.locationsOfLine(location.getLineNo());
            jdiLocations.forEach(jdiL -> {
                BreakpointRequest breakpointRequest = registerOneBreakpoint(jdiL, shouldEnable);
                requests.add(breakpointRequest);
            });
        }
        return requests;
    }

    protected List<BreakpointRequest> addBreakPointToAllLocationsInMethod(Method method, boolean shouldEnable) throws AbsentInformationException {
        List<BreakpointRequest> requests = new LinkedList<>();
        Set<Integer> lineNoSetAlready = new HashSet<>();

        List<Location> jdiLocation = method.allLineLocations();
        for (Location location : jdiLocation) {
            if (!lineNoSetAlready.contains(location.lineNumber())) {
                BreakpointRequest breakpointRequest = registerOneBreakpoint(location, shouldEnable);
                lineNoSetAlready.add(location.lineNumber());
                requests.add(breakpointRequest);
            }
        }
        return requests;
    }

    protected BreakpointRequest addBreakPointToFirstLineInMethod(Method method, boolean shouldEnable) throws AbsentInformationException {
        Set<Integer> lineNoSetAlready = new HashSet<>();

        List<Location> jdiLocation = method.allLineLocations();
        for (Location location : jdiLocation) {
            if (!lineNoSetAlready.contains(location.lineNumber())) {
                BreakpointRequest breakpointRequest = registerOneBreakpoint(location, shouldEnable);
                lineNoSetAlready.add(location.lineNumber());
                return breakpointRequest;
            }
        }
        return null;
    }

    protected Method getMethodToMonitorFromType(ReferenceType referenceType) {
        MethodToMonitor method = getProject().getMethodToMonitor();
        String className = referenceType.name();
        if (!className.equals(method.getFullQualifiedClassName())) throw new IllegalStateException();

        String sig2 = MethodUtil.getMethodSignature(method.getMethodAST());

        // Important: Use qualified return type name to filter out other methods with the same signature
        //              but different return type. Without this, multiple matches are possible.

        org.eclipse.jdt.core.dom.Type returnType = method.getMethodAST().getReturnType2();
        String qualifiedReturnTypeName = returnType.resolveBinding().getErasure().getQualifiedName();
        String binaryReturnTypeName = returnType.resolveBinding().getErasure().getBinaryName();
        List<Method> methods = referenceType.methodsByName(method.getMethodAST().getName().toString());
        List<Method> targetMethods = new ArrayList<>();
        for (Method m : methods) {
            if ((qualifiedReturnTypeName.equals(m.returnTypeName()) || binaryReturnTypeName.equals(m.returnTypeName())) &&
                    (sig2.equals(MethodUtil.getMethodSignature(m)) || sig2.equals(MethodUtil.getMethodSignatureIgnoreDollar(m)))) {
                //Note: The typeName return by JDT and JDI have small differences,which can causes MTF missing and needs special treatment.
                targetMethods.add(m);
            }
        }

        if (targetMethods.isEmpty() || targetMethods.size() > 1) {
            throw new IllegalStateException("Incorrect number of methods to monitor :" + targetMethods.size());
        }

        return targetMethods.get(0);
    }

    // Implementation details

    private void registerClassPrepareRequestForTester() {
        ClassPrepareRequest classPrepareRequest = getEventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(Tester.class.getName());
        classPrepareRequest.addCountFilter(1);
        classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        classPrepareRequest.enable();
    }

    protected Map<String, Connector.Argument> prepareLaunchArguments(LaunchingConnector launchingConnector) throws Exception {
        // Get arguments of the launching connector
        Map<String, Connector.Argument> defaultArguments = launchingConnector.defaultArguments();

        Connector.Argument mainArg = defaultArguments.get("main"); // Set class of main method
        mainArg.setValue(Tester.class.getName() + " " + argumentsForTester());

        Connector.Argument options = defaultArguments.get("options");// Set classpath of target program
        options.setValue(getProject().commandLineStringForAgents(enableAssertAgent) + " -ea -cp \"" + getProject().getClasspathForFixingStr() + "\"");

        Connector.Argument home = defaultArguments.get("home");
        home.setValue(project.getJavaEnvironment().getJdkRootDir().toString());

        LogUtil.logMapForDebug(defaultArguments);
        return defaultArguments;
    }

    private void eventLoop() throws Exception {
        EventQueue eventQueue = getVirtualMachine().eventQueue();
        while (true) {
            if (hasVirtualMachineTerminated() == true) break;
            if (!getVirtualMachine().process().isAlive()) break;

            EventSet eventSet = eventQueue.remove();
            EventIterator eventIterator = eventSet.eventIterator();
            while (eventIterator.hasNext()) {
                if (hasVirtualMachineTerminated() == true) break;
                if (!getVirtualMachine().process().isAlive()) break;
                Event event = eventIterator.next();
                execute(eventSet, event);
            }
        }

    }

    void clearPreviousStep(ThreadReference threadReference) {
        EventRequestManager requestManager = getVirtualMachine().eventRequestManager();
        Iterator requestIterator = requestManager.stepRequests().iterator();

        while (requestIterator.hasNext()) {
            StepRequest request = (StepRequest) requestIterator.next();
            if (request.thread().equals(threadReference)) {
                requestManager.deleteEventRequest(request);
                break;
            }
        }

    }

    void commandStep(LocatableEvent locatableEvent) {
        this.clearPreviousStep(locatableEvent.thread());
        EventRequestManager var4 = getVirtualMachine().eventRequestManager();
        StepRequest var5 = var4.createStepRequest(locatableEvent.thread(), StepRequest.STEP_LINE, StepRequest.STEP_OVER);
        var5.addCountFilter(1);
        var5.enable();
    }

    private void execute(EventSet eventSet, Event event) throws Exception {
//        LoggingService.debugFileOnly(event.toString(), FixerOutput.LogFile.FILE);
        if (event instanceof VMStartEvent) {
            eventSet.resume();
        } else if (event instanceof VMDeathEvent) {
            eventSet.resume();
        } else if (event instanceof ClassPrepareEvent) {
            ClassPrepareEvent classPrepareEvent = (ClassPrepareEvent) event;
            ReferenceType referenceType = classPrepareEvent.referenceType();

            if (referenceType.name().equals(Tester.class.getName())) {
                setTestStartBreakpoint(registerTestBoundaryBreakpoint(referenceType, Tester.MemberName.TEST_START_METHOD_NAME).location());
                setTestEndBreakpoint(registerTestBoundaryBreakpoint(referenceType, Tester.MemberName.TEST_END_METHOD_NAME).location());

            }
            registerBreakpointForMonitoring(classPrepareEvent.referenceType(), isTestStarted());
            eventSet.resume();
        } else if (event instanceof BreakpointEvent) {
            BreakpointEvent breakpointEvent = (BreakpointEvent) event;
            Location location = ((BreakpointEvent) event).location();
            if (getTestStartBreakpoint().equals(location) && !isTestStarted()) {
                setTestStarted(true);
                setNbrStackFramesAtTestEntry(safeGetNbrStackFrames(breakpointEvent));

                processTestStart(breakpointEvent);
            } else if (getTestEndBreakpoint().equals(location) && isTestStarted()
                    && getNbrStackFramesAtTestEntry() == safeGetNbrStackFrames(breakpointEvent)) {
                setTestStarted(false);

                processTestEnd(breakpointEvent);
            } else {
                processMonitorLocation(breakpointEvent);
            }
            eventSet.resume();
        } else if (event instanceof StepEvent) {
            processStepEvent((StepEvent) event);
            eventSet.resume();
        } else if (event instanceof VMDisconnectEvent) {
            setVirtualMachineTerminated(true);
        } else {
            handleOtherEventType(eventSet, event);
        }
    }

    private BreakpointRequest registerTestBoundaryBreakpoint(ReferenceType referenceType, Tester.MemberName testerMemberName) {
        String methodName = testerMemberName.getName();
        List<Method> methods = referenceType.methodsByName(methodName);
        if (methods.isEmpty() || methods.size() > 1)
            throw new IllegalStateException();

        Method testStartMethod = methods.get(0);
        try {
            List<Location> locations = testStartMethod.allLineLocations();
            Location location = locations.get(0);

            return registerOneBreakpoint(location, true);
        } catch (Exception e) {
            throw new IllegalStateException();
        }
    }


    private Thread displayRemoteOutput(final InputStream inputStream) {
        Thread thread = new Thread("Debuggee output") {
            public void run() {
                try {
                    dumpStream(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                }
            }
        };
        thread.setPriority(9);
        thread.start();

        return thread;
    }

    private void dumpStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        int ch;
        try {
            StringBuilder sb = new StringBuilder();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            LoggingService.debug("Debugee output: " + sb.toString());
        } catch (IOException e) {
            String msg = e.getMessage();
            if (!msg.startsWith("Bad file number")) {
                throw e;
            }
        }
    }

    protected void setMtfEntryAndExitLocationBreakpoint(ReferenceType referenceType, List<BreakpointRequest> breakpointRequestList) {
        BreakpointRequest mtfEntryBreakPoint = registerTestBoundaryBreakpoint(referenceType, Tester.MemberName.START_METHOD_TO_MONITOR_METHOD_NAME);
        BreakpointRequest mtfExitBreakPoint = registerTestBoundaryBreakpoint(referenceType, Tester.MemberName.END_METHOD_TO_MONITOR_METHOD_NAME);
        BreakpointRequest timeoutBreakPoint = registerTestBoundaryBreakpoint(referenceType, Tester.MemberName.TIMEOUT_TEST_CASE_METHOD_NAME);
        setMtfEntryLocationBreakpoint(mtfEntryBreakPoint.location());
        setMtfExitLocationBreakpoint(mtfExitBreakPoint.location());
        setTimeoutLocationBreakpoint(timeoutBreakPoint.location());
        breakpointRequestList.add(mtfEntryBreakPoint);
        breakpointRequestList.add(mtfExitBreakPoint);
        breakpointRequestList.add(timeoutBreakPoint);

    }

    protected void setAssertInvocationMonitorBreakpoint(ReferenceType referenceType, List<BreakpointRequest> breakpointRequestList) {
        BreakpointRequest assertInvocationMonitor = registerTestBoundaryBreakpoint(referenceType, Tester.MemberName.ASSERT_INVOCATION_METHOD_NAME);
        setAssertInvocationLocationBreakpoint(assertInvocationMonitor.location());
        breakpointRequestList.add(assertInvocationMonitor);
    }

}
