package hk.polyu.comp.jaid.monitor.jdi.debugger;

import com.sun.jdi.*;
import com.sun.tools.example.debug.expr.ExpressionParser;
import com.sun.tools.example.debug.expr.ParseException;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LightLocation;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.FramesStack;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

public class Utils4Debugger {

    /**
     * Monitor all related frames of the thread
     * (Used while monitoring exit states only)
     *
     * @param javaProject
     * @param threadReference
     * @param testExecutionResult
     * @param stackTag
     */
    public static void monitorThreadMultipleFrames(JavaProject javaProject, ThreadReference threadReference, TestExecutionResult testExecutionResult, FramesStack.StackTag stackTag) {
        Set<String> allDeclaredClass = javaProject.getAllDeclaredClass();
        boolean createNewETM = true;
        MethodDeclarationInfoCenter infoCenter = javaProject.getMethodToMonitor().getMethodDeclarationInfoCenter();
        try {
            FramesStack framesStack = new FramesStack(threadReference.uniqueID(), stackTag, threadReference.frameCount());
            int originalFrameCount = threadReference.frameCount();
            for (int i = 0; i < originalFrameCount; i++) {
                if (threadReference.frameCount() != originalFrameCount)
                    throw new IllegalStateException("Frame count changed after check Collector size!");
                StackFrame stackFrame = getFrameGetter(threadReference, i, null).get();
                if (allDeclaredClass.contains(stackFrame.location().declaringType().name())) {
                    ProgramState frameState = new ProgramState(LightLocation.constructLightLocation(stackFrame.location()));
                    Map<LocalVariable, Value> localVariableValueMap = stackFrame.getValues(stackFrame.visibleVariables());
                    //monitor all visible local variables
                    frameState.extendLocalVariables(infoCenter, localVariableValueMap, createNewETM);
                    //monitor fields of reference type local variables
                    frameState.extendLocalVarFields(monitorRefLocalVarFieldsWithSizeValue(infoCenter, localVariableValueMap, threadReference, createNewETM));
                    //Monitor this ObjectsFields and collectors size ()
                    stackFrame = getFrameGetter(threadReference, i, null).get();//the stackFrame could become invalid due to (method invocation) monitor Collector size
                    if (stackFrame.thisObject() != null) {
                        Map<Field, Value> fieldValueMap = stackFrame.thisObject().getValues(stackFrame.thisObject().referenceType().allFields());
                        frameState.extendFields(infoCenter, fieldValueMap, "this", createNewETM);
                        for (Field field : fieldValueMap.keySet()) {
                            frameState.putAllFields(
                                    monitorCollectorSize(infoCenter, CommonUtils.getRefFieldAccess("this", field.name()), fieldValueMap.get(field), threadReference, createNewETM));
                        }
                    }
                    framesStack.extendFrame(frameState);
                }
            }
            testExecutionResult.getFramesStackList().add(framesStack);
        } catch (Exception e) {
            e.printStackTrace();
            LoggingService.debug(e.toString());
        }
    }

    /**
     * Monitor local variables, and valid references fields including this.fields (in current frame)
     * used while monitoring locations within MTF
     *
     * @param infoCenter
     * @param threadReference
     * @param programState    not null
     */
    public static void monitorThreadCurrentFrame(MethodDeclarationInfoCenter infoCenter, ThreadReference threadReference, ProgramState programState) {
        boolean createNewETM = false;
        if (programState == null) throw new IllegalArgumentException("Program state is null");
        try {
            int originalFrameCount = threadReference.frameCount();
            if (threadReference.frameCount() != originalFrameCount)
                throw new IllegalStateException("Frame count changed after check Collector size!");
            StackFrame stackFrame = getFrameGetter(threadReference, 0, null).get();
            Map<LocalVariable, Value> localVariableValueMap = stackFrame.getValues(stackFrame.visibleVariables());
            //monitor all visible local variables
            programState.extendLocalVariables(infoCenter, localVariableValueMap, createNewETM);
            //monitor fields of reference type local variables
            programState.extendLocalVarFields(monitorRefLocalVarFields(infoCenter, localVariableValueMap, threadReference));
            //Monitor this ObjectsFields and collectors size ()
            if (stackFrame.thisObject() != null) {
                Map<Field, Value> fieldValueMap = stackFrame.thisObject().getValues(stackFrame.thisObject().referenceType().allFields());
                programState.extendFields(infoCenter, fieldValueMap, "this", createNewETM);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LoggingService.debug(e.toString());
        }
    }

    private static Map<ExpressionToMonitor, Value> monitorRefLocalVarFields(MethodDeclarationInfoCenter infoCenter, Map<LocalVariable, Value> localVariableValueMap, ThreadReference threadReference) {
        //Monitor the fields of valid references

        Map<ExpressionToMonitor, Value> referenceFieldsMap = new HashMap<>();
        for (LocalVariable localVariable : localVariableValueMap.keySet()) {
            if (localVariableValueMap.get(localVariable) instanceof ObjectReference && !localVariable.name().contains(Tester.JAID_KEY_WORD)) {
                ObjectReference reference = (ObjectReference) localVariableValueMap.get(localVariable);
                Map<Field, Value> fieldsMap = reference.getValues(reference.referenceType().allFields());
                for (Field field : fieldsMap.keySet()) {
                    ExpressionToMonitor etm = infoCenter.getExpressionByText(CommonUtils.getRefFieldAccess(localVariable.name(), field.name()));
                    if (etm != null && etm.getType().getName().equals(field.typeName()))
                        referenceFieldsMap.put(etm, fieldsMap.get(field));
                }
            }
        }
        return referenceFieldsMap;
    }


    private static Map<ExpressionToMonitor, Value> monitorRefLocalVarFieldsWithSizeValue(MethodDeclarationInfoCenter infoCenter, Map<LocalVariable, Value> localVariableValueMap, ThreadReference threadReference, boolean createNewETM) {
        //Monitor the fields of valid references

        Map<ExpressionToMonitor, Value> referenceFieldsMap = new HashMap<>();
        for (LocalVariable localVariable : localVariableValueMap.keySet()) {
            if (localVariableValueMap.get(localVariable) instanceof ObjectReference && !localVariable.name().contains(Tester.JAID_KEY_WORD)) {
                ObjectReference reference = (ObjectReference) localVariableValueMap.get(localVariable);
                Map<Field, Value> fieldsMap = reference.getValues(reference.referenceType().allFields());
                for (Field field : fieldsMap.keySet()) {
                    ExpressionToMonitor etm = infoCenter.getExpressionByText(CommonUtils.getRefFieldAccess(localVariable.name(), field.name()), field.typeName(), createNewETM);
                    if (etm != null)
                        referenceFieldsMap.put(etm, fieldsMap.get(field));
                    referenceFieldsMap.putAll(monitorCollectorSize(infoCenter, CommonUtils.getRefFieldAccess(localVariable.name(), field.name()), fieldsMap.get(field), threadReference, createNewETM));
                }
            }
        }
        return referenceFieldsMap;
    }

    private static Map<ExpressionToMonitor, Value> monitorCollectorSize(MethodDeclarationInfoCenter infoCenter, String var, Value value, ThreadReference threadReference, boolean createNewETM) {
        //Monitor the fields of valid references
        Map<ExpressionToMonitor, Value> referenceFieldsMap = new HashMap<>();
        if (value instanceof ObjectReference) {
            ObjectReference refVal = (ObjectReference) value;
            if (refVal.referenceType().methodsByName("size").size() > 0) {
                try {
                    Method invokingSize = refVal.referenceType().methodsByName("size").get(0);
                    Value sizeVal = refVal.invokeMethod(threadReference, invokingSize, new ArrayList<>(), ObjectReference.INVOKE_SINGLE_THREADED);
                    String expName = var + "." + invokingSize.name();
                    ExpressionToMonitor etm = infoCenter.getExpressionByText(expName, invokingSize.returnTypeName(), createNewETM);
                    if (etm != null)
                        referenceFieldsMap.put(etm, sizeVal);
                } catch (Exception e) {
                    e.printStackTrace();
                    LoggingService.debug(e.toString());
                }
            }
        }
        return referenceFieldsMap;
    }


    // Operations

    /**
     * Evaluate string expressions that cannot get from the jdi API directly
     * (the value of local var and fields should get form the JDI API rather than using this method)
     *
     * @param vm
     * @param getFrame
     * @param exp
     * @return
     */
    public static DebuggerEvaluationResult evaluate(VirtualMachine vm, ExpressionParser.GetFrame getFrame, ExpressionToMonitor exp) {
        DebuggerEvaluationResult debuggerEvaluationResult = null;
        try {
            Value value = ExpressionParser.evaluate(exp.getText(), vm, getFrame);
            debuggerEvaluationResult = DebuggerEvaluationResult.fromValue(exp.getType(), value);
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
                    return debuggerEvaluationResult;
                }
            } else if (e instanceof NullPointerException) {
                return getNullExceptionContainedExpResult(exp);
            }
            debuggerEvaluationResult = DebuggerEvaluationResult.getDebuggerEvaluationResultSemanticError();
        }
        return debuggerEvaluationResult;
    }

    protected static DebuggerEvaluationResult getNullExceptionContainedExpResult(ExpressionToMonitor exp) {
        if (!exp.getType().isPrimitive()) {
            return DebuggerEvaluationResult.getReferenceDebuggerEvaluationResultNull();
        }
        if (exp.getExpressionAST() instanceof InfixExpression) {
            InfixExpression infixExp = (InfixExpression) exp.getExpressionAST();
            if (infixExp.getLeftOperand() instanceof NullLiteral ||
                    infixExp.getRightOperand() instanceof NullLiteral) {
                if (infixExp.getOperator().equals(InfixExpression.Operator.EQUALS))
                    return DebuggerEvaluationResult.getBooleanDebugValue(true);
                else if (infixExp.getOperator().toString().equals(InfixExpression.Operator.NOT_EQUALS.toString()))
                    return DebuggerEvaluationResult.getBooleanDebugValue(false);
            }
        }
        return DebuggerEvaluationResult.getDebuggerEvaluationResultSemanticError();
    }

    //todo: (refactoring) extract all static methods to from a new util class
    public static ExpressionParser.GetFrame getFrameGetter(final ThreadReference thread, final int frameIndex, final String expectedMethodName) {
        ExpressionParser.GetFrame getFrame = new ExpressionParser.GetFrame() {
            public StackFrame get() throws IncompatibleThreadStateException {
                StackFrame frame = thread.frame(frameIndex);
                if (expectedMethodName != null && !frame.location().method().name().equals(expectedMethodName)) {
                    throw new IllegalStateException();
                }
                return frame;
            }
        };

        return getFrame;
    }
}
