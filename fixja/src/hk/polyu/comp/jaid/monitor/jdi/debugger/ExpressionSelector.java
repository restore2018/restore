package hk.polyu.comp.jaid.monitor.jdi.debugger;

import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.tools.example.debug.expr.ExpressionParser;
import com.sun.tools.example.debug.expr.ParseException;
import com.sun.tools.jdi.ObjectReferenceImpl;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.tester.TesterConfig;
import org.eclipse.jdt.core.dom.*;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;
import static hk.polyu.comp.jaid.monitor.jdi.debugger.Utils4Debugger.*;
import static hk.polyu.comp.jaid.util.CommonUtils.isSubExp;

public class ExpressionSelector extends AbstractDebuggerLauncher {

    private Map<Integer, LineLocation> validLocations;
    private MethodDeclarationInfoCenter infoCenter;
    private Set<ExpressionToMonitor> expressionsToCheck;

    private TestRequest passingTest, failingTest;
    private boolean hasFoundExpressionWithSideEffect;


    Set<ExpressionToMonitor> invalidExpressionsForLine = new HashSet<>();
    Map<LineLocation, Set<ExpressionToMonitor>> invalidExpressions = new HashMap<>();

    public ExpressionSelector(JavaProject project, LogLevel logLevel, long timeoutPerTest, FailureHandling failureHandling,
                              Set<LineLocation> validLocations) {
        super(project, logLevel, timeoutPerTest, failureHandling);

        this.validLocations = validLocations.stream().collect(Collectors.toMap(LineLocation::getLineNo, Function.identity()));
        this.infoCenter = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter();
        this.expressionsToCheck = this.infoCenter.getAllEnrichedEtmWithinMethod().stream().filter(ExpressionToMonitor::hasMethodInvocation).collect(Collectors.toSet());
        this.enableAssertAgent = true;

        selectTests();
    }

    // ======================================== Operations

    public void doSelection() {
        setFoundExpressionWithSideEffect(false);
        launch();
    }

    // ======================================== Getters and Setters

    public boolean hasFoundExpressionWithSideEffect() {
        return hasFoundExpressionWithSideEffect;
    }

    public void setFoundExpressionWithSideEffect(boolean hasFoundExpressionWithSideEffect) {
        this.hasFoundExpressionWithSideEffect = hasFoundExpressionWithSideEffect;
    }

    // ======================================== Override

    @Override
    protected List<TestRequest> testsToRunList() {
        return Arrays.asList(passingTest, failingTest);
    }

    @Override
    protected String argumentsForTester() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(TesterConfig.ACTIVE_IS_MONITOR_MODE).append(" ").append(Boolean.TRUE).append(" ")
                .append(super.argumentsForTester());
        return sb.toString();
        //TODO: Some potential improvements to support recursive call and reduce the side-effect of this mechanism:
        //a. enable ACTIVE_IS_MONITOR_MODE (set Tester.IS_MONITOR_MODE to true ) before evaluate an expression and disable it immediately after that
        //b. disable MTF breakpoints when evaluating expressions and enable them before resume.

    }

    @Override
    protected Path getLogPath() {
        return FixerOutput.getPre4ExpTestResultsLogFilePath();
    }

    @Override
    protected void registerBreakpointForMonitoring(ReferenceType referenceType, boolean shouldEnable) throws AbsentInformationException, ClassNotLoadedException {
        if (!referenceType.name().equals(Tester.class.getName())) {
            Method methodToMonitor = getMethodToMonitorFromType(referenceType);
            getBreakpointRequestsForMonitoring().addAll(addBreakPointToAllLocationsInMethod(methodToMonitor, shouldEnable));
        } else {
            setMtfEntryAndExitLocationBreakpoint(referenceType, getBreakpointRequestsForMonitoring());
        }

    }

    @Override
    protected void processTestStart(BreakpointEvent breakpointEvent) {
        super.processTestStart(breakpointEvent);
        nbrStackFramesAtMethodEntry = -1;
        if (!getBreakpointRequestsForMonitoring().isEmpty()) {
            getBreakpointRequestsForMonitoring().forEach(x -> x.enable());
        }
        invalidExpressions = new HashMap<>();
    }

    @Override
    protected void processTestEnd(BreakpointEvent breakpointEvent) {
        super.processTestEnd(breakpointEvent);

        if (!getBreakpointRequestsForMonitoring().isEmpty()) {
            getBreakpointRequestsForMonitoring().forEach(x -> x.disable());
        }

        if (getCurrentTestResult().wasSuccessful() &&
                getCurrentTestResult().getExitStates().stream()
                        .filter(exitState -> exitState.isHasException())
                        .collect(Collectors.toSet()).size() == 0) {
            //Remove  invalid ETMs in valid locations
            for (LineLocation l : invalidExpressions.keySet()) {
                getProject().getMethodToMonitor().getMethodDeclarationInfoCenter()
                        .getLocationExpressionMap().get(l).removeAll(invalidExpressions.get(l));
            }
        }
    }

    private int nbrStackFramesAtMethodEntry;

    @Override
    protected void processMonitorLocation(BreakpointEvent breakpointEvent) throws AbsentInformationException {
        getBreakpointRequestsForMonitoring().forEach(x -> x.disable());//disable all breakpoints to prevent blocking.

        ExpressionParser.GetFrame getFrame = getFrameGetter(breakpointEvent.thread(), 0, null);
        if (getMtfEntryLocationBreakpoint().equals(breakpointEvent.location()) && nbrStackFramesAtMethodEntry == -1) {
            nbrStackFramesAtMethodEntry = safeGetNbrStackFrames(breakpointEvent);
        } else if (validLocations.containsKey(breakpointEvent.location().lineNumber())) {
            LineLocation lineLocation = validLocations.get(breakpointEvent.location().lineNumber());
            invalidExpressionsForLine = new HashSet<>();
            Set<ExpressionToMonitor> othersETM = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter()
                    .getLocationExpressionMap().get(lineLocation).stream().filter(e -> !expressionsToCheck.contains(e)).collect(Collectors.toSet());
            //for expressions that contains method invocation, check its validity and if it is side-effect free
            for (ExpressionToMonitor exp : expressionsToCheck) {
                if (!exp.hasChangedState()) {
                    if (hasSurelySideEffectByFields(getVirtualMachine(), getFrame, exp)) {
                        exp.setChangedState(true);
                        setFoundExpressionWithSideEffect(true);
                        infoCenter.getExpressionsToMonitorWithSideEffect().add(exp);

                        if (shouldLogDebug()) {
                            LoggingService.debugAll("Expression with side effect: " + exp.getText());
                        }
                    }
                }
            }
            // for others check the validity
            for (ExpressionToMonitor etm : othersETM) {
                if (isSpecialCase(lineLocation, etm) || etm.isInvokeMTF()) invalidExpressionsForLine.add(etm);

                DebuggerEvaluationResult debuggerEvaluationResult = evaluate(getVirtualMachine(), getFrame, etm);
                if (debuggerEvaluationResult.hasSyntaxError())
                    invalidExpressionsForLine.add(etm);
            }
            invalidExpressions.put(lineLocation, invalidExpressionsForLine);
        } else if (getMtfExitLocationBreakpoint().equals(breakpointEvent.location()) && nbrStackFramesAtMethodEntry == safeGetNbrStackFrames(breakpointEvent)) {
            nbrStackFramesAtMethodEntry = -1;
        }
        getBreakpointRequestsForMonitoring().forEach(x -> x.enable());//enable breakpoints

    }

    // fixme: is this necessary?
    public boolean isSpecialCase(LineLocation location, ExpressionToMonitor exp) {
        Map<LineLocation, Statement> locationStatementMap = getProject().getMethodToMonitor().getMethodDeclarationInfoCenter().getRelevantLocationStatementMap();
        if (locationStatementMap.containsKey(location)) {
            Statement oldStmt = locationStatementMap.get(location);
            if (oldStmt instanceof ForStatement) {
                ForStatement forStmt = (ForStatement) oldStmt;
                for (Object init : forStmt.initializers()) {
                    if (init instanceof VariableDeclarationExpression) {
                        VariableDeclarationExpression initExp = (VariableDeclarationExpression) init;
                        for (Object o : initExp.fragments()) {
                            VariableDeclarationFragment oexp = (VariableDeclarationFragment) o;
                            if (isSubExp(exp, oexp.getName()))
                                return true;
                        }
                    }
                }
            } else if (oldStmt instanceof EnhancedForStatement) {
                EnhancedForStatement forStmt = (EnhancedForStatement) oldStmt;
                SingleVariableDeclaration initExp = forStmt.getParameter();
                if (isSubExp(exp, initExp.getName()))
                    return true;
            }
            return false;
        }
        return false;
    }

    @Override
    protected void processStepEvent(StepEvent stepEvent) throws AbsentInformationException {

    }

    // ======================================== Implementation details

    /**
     * This method replace the hasSurelySideEffectByGuardEXPs
     * this method sufficiently utilizes the JDI API to get current program states, which reduces the resource and time cost.
     *
     * @param vm
     * @param getFrame
     * @param expToCheck
     * @return
     */
    private boolean hasSurelySideEffectByFields(VirtualMachine vm, ExpressionParser.GetFrame getFrame, ExpressionToMonitor expToCheck) {
        DebuggerEvaluationResult debuggerEvaluationResult;

        Map<ExpressionToMonitor, DebuggerEvaluationResult> preState = new HashMap<>();
        Map<ExpressionToMonitor, DebuggerEvaluationResult> postState = new HashMap<>();

        Set<ExpressionToMonitor> guardExpressions = new HashSet<>();
        expToCheck.getSubExpressions().stream().filter(x -> !x.hasMethodInvocation() && !x.equals(expToCheck)).forEach(guardExpressions::add);
        //Monitor pre states
        for (ExpressionToMonitor exp : guardExpressions) {
            preState.putAll(evaluateExpAndFields(vm, getFrame, exp));
        }

        debuggerEvaluationResult = evaluate(vm, getFrame, expToCheck);
        if (debuggerEvaluationResult.isInvokeMTF()) {
            expToCheck.setInvokeMTF(true);
            return false;
        }
        if (debuggerEvaluationResult.hasSyntaxError()) {
            invalidExpressionsForLine.add(expToCheck);
            return false;
        }

        //Monitor post states
        for (ExpressionToMonitor exp : guardExpressions) {
            postState.putAll(evaluateExpAndFields(vm, getFrame, exp));
        }

        //Compare pre and post states
        if (!preState.keySet().equals(postState.keySet())) return true;
        for (ExpressionToMonitor exp : preState.keySet()) {
            if (!preState.get(exp).equals(postState.get(exp))) {
                return true;
            }
        }
        return false;
    }

    public static Map<ExpressionToMonitor, DebuggerEvaluationResult> evaluateExpAndFields(VirtualMachine vm, ExpressionParser.GetFrame getFrame, ExpressionToMonitor exp) {
        DebuggerEvaluationResult debuggerEvaluationResult = null;
        Map<ExpressionToMonitor, DebuggerEvaluationResult> resultMap = new HashMap<>();
        if (!exp.isFinal()) {
            try {
                Value value = ExpressionParser.evaluate(exp.getText(), vm, getFrame);
                debuggerEvaluationResult = DebuggerEvaluationResult.fromValue(exp.getType(), value);

                //Monitor fields' values
                if (value instanceof ObjectReferenceImpl) {
                    Map<Field, Value> fieldValueMap = ((ObjectReferenceImpl) value).getValues(((ObjectReferenceImpl) value).referenceType().allFields());
                    for (ExpressionToMonitor fieldETM : exp.getFieldsToMonitor().values()) {
                        for (Field fieldJDI : fieldValueMap.keySet()) {
                            if (((FieldAccess) fieldETM.getExpressionAST()).getName().toString().equals(fieldJDI.name())) {
                                try {
                                    DebuggerEvaluationResult fieldResult = DebuggerEvaluationResult.fromValue(fieldETM.getType(), fieldValueMap.get(fieldJDI));
                                    if (!fieldResult.hasSyntaxError() && !fieldResult.hasSemanticError())
                                        resultMap.put(fieldETM, fieldResult);
                                    break;
                                } catch (Exception e) {
                                    LoggingService.debugAll(e.getMessage() + " ; fieldETM: " + fieldETM);
                                }
                            }
                        }
                    }
                }
            } catch (ParseException e) {
                debuggerEvaluationResult = DebuggerEvaluationResult.getDebuggerEvaluationResultSyntaxError();
            } catch (Exception e) {
                if (e instanceof InvocationException) {
                    InvocationException invocationException = (InvocationException) e;
                    if (invocationException.exception().referenceType().name().equals(Tester.MTFIsMonitoredException.class.getName())) {
                        debuggerEvaluationResult = DebuggerEvaluationResult.getInvokeMtfDebuggerEvaluationResult();
                        if (shouldLogDebug()) {
                            LoggingService.debugAll("Expression that invokes MTF: " + exp.getText());
                        }
                    }
                } else if (e instanceof NullPointerException) {
                    debuggerEvaluationResult = getNullExceptionContainedExpResult(exp);
                } else {
                    debuggerEvaluationResult = DebuggerEvaluationResult.getDebuggerEvaluationResultSemanticError();
                }
            }
            resultMap.put(exp, debuggerEvaluationResult);
        }
        return resultMap;
    }


    private void selectTests() {
        if(getProject().getFailingTests().size()>0)
            failingTest=getProject().getFailingTests().get(0);
        else
            throw new IllegalStateException("No failing test.");
        if(getProject().getPassingTests().size()>0)
            passingTest=getProject().getPassingTests().get(0);
    }


}
