package hk.polyu.comp.jaid.fixer;

import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.FramesStack;
import hk.polyu.comp.jaid.monitor.state.ProgramState;

import java.util.*;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.STACK_DIFF;

public class ImpactComparator {


    public static void retainMethodImpact(TestExecutionResult testExecutionResult) {
        List<FramesStack> stackList = testExecutionResult.getFramesStackList();
        List<Integer> dividerRecorder = getDividerIndex(stackList);
        List<FramesStack.DiffFrameStack> methodImpactStackList = new ArrayList<>();
        if (dividerRecorder.size() > 0) {
            //If the number of divider is equal, compare MethodImpact for each assertion
            int startIdx = 0;
            for (int i = 0; i < dividerRecorder.size(); i++) {
                FramesStack entryStack = getEntryStackLast(stackList, startIdx, dividerRecorder.get(i));
                FramesStack exitStack = getExitStackLast(stackList, startIdx, dividerRecorder.get(i));
                FramesStack.DiffFrameStack methodImpact = getMethodImpact(entryStack, exitStack);
                methodImpactStackList.add(methodImpact);
                startIdx = dividerRecorder.get(i);
            }
            if (dividerRecorder.get(dividerRecorder.size() - 1) < stackList.size() - 1) {
                FramesStack entryStack = getEntryStackLast(stackList, startIdx, stackList.size() - 1);
                FramesStack exitStack = getExitStackLast(stackList, startIdx, stackList.size() - 1);
                FramesStack.DiffFrameStack methodImpact = getMethodImpact(entryStack, exitStack);
                methodImpactStackList.add(methodImpact);
            }
        } else {
            FramesStack entryStack = getEntryStackLast(stackList, 0, stackList.size() - 1);
            FramesStack exitStack = getExitStackLast(stackList, 0, stackList.size() - 1);
            FramesStack.DiffFrameStack methodImpact = getMethodImpact(entryStack, exitStack);
            methodImpactStackList.add(methodImpact);
        }
        retainMethodImpactFirst(testExecutionResult);//do this before removing the original data
        testExecutionResult.setMethodImpact(methodImpactStackList);
    }

    public static void retainMethodImpactFirst(TestExecutionResult testExecutionResult) {
        List<FramesStack> stackList = testExecutionResult.getFramesStackList();
        List<Integer> dividerRecorder = getDividerIndex(stackList);
        List<FramesStack.DiffFrameStack> methodImpactStackList = new ArrayList<>();
        if (dividerRecorder.size() > 0) {
            //If the number of divider is equal, compare MethodImpact for each assertion
            int startIdx = 0;
            for (int i = 0; i < dividerRecorder.size(); i++) {
                FramesStack entryStack = getEntryStackFirst(stackList, startIdx, dividerRecorder.get(i));
                FramesStack exitStack = getExitStackFirst(stackList, startIdx, dividerRecorder.get(i));
                FramesStack.DiffFrameStack methodImpact = getMethodImpact(entryStack, exitStack);
                methodImpactStackList.add(methodImpact);
                startIdx = dividerRecorder.get(i);
            }
            if (dividerRecorder.get(dividerRecorder.size() - 1) < stackList.size() - 1) {
                FramesStack entryStack = getEntryStackFirst(stackList, startIdx, stackList.size() - 1);
                FramesStack exitStack = getExitStackFirst(stackList, startIdx, stackList.size() - 1);
                FramesStack.DiffFrameStack methodImpact = getMethodImpact(entryStack, exitStack);
                methodImpactStackList.add(methodImpact);
            }
        } else {
            FramesStack entryStack = getEntryStackFirst(stackList, 0, stackList.size() - 1);
            FramesStack exitStack = getExitStackFirst(stackList, 0, stackList.size() - 1);
            FramesStack.DiffFrameStack methodImpact = getMethodImpact(entryStack, exitStack);
            methodImpactStackList.add(methodImpact);
        }
        testExecutionResult.setMethodImpactFirst(methodImpactStackList);
    }

    public static void retainFixImpact(TestExecutionResult oResult, TestExecutionResult nResult) {
        if (!oResult.getTestClassAndMethod().equals(nResult.getTestClassAndMethod()))
            throw new IllegalStateException("Test result not match while computing SideChange score.");

        List<FramesStack.DiffFrameStack> oStackList = oResult.getMethodImpact();
        List<FramesStack.DiffFrameStack> nStackList = nResult.getMethodImpact();
        List<FramesStack.DiffFrameStack> fixImpact = new ArrayList<>();
        if (oStackList.size() == nStackList.size()) {
            //If the number of divider is equal, compare MethodImpact for each assertion
            for (int i = 0; i < oStackList.size(); i++) {
                fixImpact.add(getFixImpact(oStackList.get(i), nStackList.get(i)));
            }
        } else {
            //otherwise, only compare the last MethodImpact.
            fixImpact.add(getFixImpact(oStackList.get(oStackList.size() - 1)
                    , nStackList.get(oStackList.size() - 1)));
        }
        nResult.setFixImpact(fixImpact);
        retainFixImpactFirst(oResult, nResult);
    }

    public static void retainFixImpactFirst(TestExecutionResult oResult, TestExecutionResult nResult) {
        if (!oResult.getTestClassAndMethod().equals(nResult.getTestClassAndMethod()))
            throw new IllegalStateException("Test result not match while computing SideChange score.");

        List<FramesStack.DiffFrameStack> oStackList = oResult.getMethodImpactFirst();
        List<FramesStack.DiffFrameStack> nStackList = nResult.getMethodImpactFirst();
        List<FramesStack.DiffFrameStack> fixImpact = new ArrayList<>();
        if (oStackList.size() == nStackList.size()) {
            //If the number of divider is equal, compare MethodImpact for each assertion
            for (int i = 0; i < oStackList.size(); i++) {
                fixImpact.add(getFixImpact(oStackList.get(i), nStackList.get(i)));
            }
        } else {
            //otherwise, only compare the last MethodImpact.
            fixImpact.add(getFixImpact(oStackList.get(oStackList.size() - 1)
                    , nStackList.get(oStackList.size() - 1)));
        }
        nResult.setFixImpactFirst(fixImpact);
    }

    public static double computeSideChangeFromFixImpact(TestExecutionResult oResult, TestExecutionResult nResult) {
        if (!oResult.getTestClassAndMethod().equals(nResult.getTestClassAndMethod()))
            throw new IllegalStateException("Test result not match while computing SideChange score.");
        LoggingService.debugFileOnly("\n ============Start " + oResult.getTestClassAndMethod()
                + "[" + oResult.wasSuccessful() + "->" + nResult.wasSuccessful() + "]", STACK_DIFF);

        List<FramesStack.DiffFrameStack> oStackList = oResult.getMethodImpact();
        List<FramesStack.DiffFrameStack> nStackList = nResult.getMethodImpact();
        double score = 0;
        if (nResult.getFixImpact() != null)
            if (oStackList.size() == nStackList.size()) {
                //If the number of divider is equal, compare MethodImpact for each assertion
                List<Double> passingScore = new ArrayList<Double>();
                for (int i = 0; i < nResult.getFixImpact().size(); i++) {
                    passingScore.add(
                            calculateScore(
                                    countAllState(oStackList.get(i), nStackList.get(i)),
                                    nResult.getFixImpact().get(i)));
                }
                score = passingScore.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
            } else {
                //otherwise, only compare the last MethodImpact.
                int last = Math.min(Math.min(oStackList.size() - 1, nStackList.size() - 1), nResult.getFixImpactFirst().size() - 1);
                score = calculateScore(
                        countAllState(oStackList.get(last), nStackList.get(last)),
                        nResult.getFixImpact().get(last));
            }
        LoggingService.debugFileOnly("\n ============End " + oResult.getTestClassAndMethod(), STACK_DIFF);

        return score;
    }

    public static double computeSideChangeFromFixImpactFirst(TestExecutionResult oResult, TestExecutionResult nResult) {
        if (!oResult.getTestClassAndMethod().equals(nResult.getTestClassAndMethod()))
            throw new IllegalStateException("Test result not match while computing SideChange score.");
        LoggingService.debugFileOnly("\n ============FirstStart " + oResult.getTestClassAndMethod()
                + "[" + oResult.wasSuccessful() + "->" + nResult.wasSuccessful() + "]", STACK_DIFF);

        List<FramesStack.DiffFrameStack> oStackList = oResult.getMethodImpactFirst();
        List<FramesStack.DiffFrameStack> nStackList = nResult.getMethodImpactFirst();
        double score = 0;
        if (nResult.getFixImpactFirst() != null)
            if (oStackList.size() == nStackList.size()) {
                //If the number of divider is equal, compare MethodImpact for each assertion
                List<Double> passingScore = new ArrayList<Double>();
                for (int i = 0; i < nResult.getFixImpactFirst().size(); i++) {
                    passingScore.add(
                            calculateScore(
                                    countAllState(oStackList.get(i), nStackList.get(i)),
                                    nResult.getFixImpactFirst().get(i)));
                }
                score = passingScore.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
            } else {
                //otherwise, only compare the last MethodImpact.
                int last = Math.min(Math.min(oStackList.size() - 1, nStackList.size() - 1), nResult.getFixImpactFirst().size() - 1);
                score = calculateScore(
                        countAllState(oStackList.get(last), nStackList.get(last)),
                        nResult.getFixImpactFirst().get(last));
            }
        LoggingService.debugFileOnly("\n ============FirstEnd " + oResult.getTestClassAndMethod(), STACK_DIFF);

        return score;
    }


    /**
     * Return the first entry FramesStack before the divider
     *
     * @param framesStacks
     * @param startIdx
     * @param dividerIdx
     * @return
     */
    private static FramesStack getEntryStackFirst(List<FramesStack> framesStacks, int startIdx, int dividerIdx) {
        if (dividerIdx >= 0)
            for (int i = startIdx; i <= dividerIdx; i++) {
                if (framesStacks.get(i).getTag().equals(FramesStack.StackTag.ENTRY))
                    return framesStacks.get(i);
            }
        throw new IllegalStateException("Cannot find EntryStack.");
    }

    private static FramesStack getEntryStackLast(List<FramesStack> framesStacks, int startIdx, int dividerIdx) {
        if (dividerIdx >= 0)
            for (int i = dividerIdx; i >= startIdx; i--) {
                if (framesStacks.get(i).getTag().equals(FramesStack.StackTag.ENTRY))
                    return framesStacks.get(i);
            }
        throw new IllegalStateException("Cannot find EntryStack.");
    }

    /**
     * Return the first exit FramesStack before the divider
     *
     * @param framesStacks
     * @param startIdx
     * @param dividerIdx
     * @return
     */
    private static FramesStack getExitStackFirst(List<FramesStack> framesStacks, int startIdx, int dividerIdx) {
        if (dividerIdx >= 0)
            for (int i = startIdx; i <= dividerIdx; i++) {
                if (framesStacks.get(i).getTag().equals(FramesStack.StackTag.EXIT))
                    return framesStacks.get(i);
            }
        throw new IllegalStateException("Cannot find ExitStack.");
    }

    private static FramesStack getExitStackLast(List<FramesStack> framesStacks, int startIdx, int dividerIdx) {
        if (dividerIdx >= 0)
            for (int i = dividerIdx; i >= startIdx; i--) {
                if (framesStacks.get(i).getTag().equals(FramesStack.StackTag.EXIT))
                    return framesStacks.get(i);
            }
        throw new IllegalStateException("Cannot find EntryStack.");
    }


    /**
     * Compare two methodImpact to get fixImpact
     *
     * @param oMethodImpact
     * @param nMethodImpact
     * @return The difference between the entryStack and exitStack, store in a framesStack
     */
    private static FramesStack.DiffFrameStack getFixImpact(FramesStack.DiffFrameStack oMethodImpact, FramesStack.DiffFrameStack nMethodImpact) {
        FramesStack.DiffFrameStack fixImpact = new FramesStack.DiffFrameStack(nMethodImpact.getFrameCount());
        for (ProgramState oFrame : oMethodImpact.getFrameStateList()) {
            //Special treatment for MTF
            for (ProgramState nFrame : nMethodImpact.getFrameStateList()) {
                if (oFrame.getRuntimeLocation().equalInstrumentedMethod(nFrame.getRuntimeLocation())) {
                    fixImpact.extendFrame(getDiff(oFrame, nFrame));
                    break;
                }
            }
        }
        return fixImpact;
    }

    /**
     * Compare entry and exit stack to get state change (methodImpact)
     *
     * @param entryStack
     * @param exitStack
     * @return The difference between the entryStack and exitStack, store in a framesStack
     */
    private static FramesStack.DiffFrameStack getMethodImpact(FramesStack entryStack, FramesStack exitStack) {
        if (entryStack.getFrameCount() != exitStack.getFrameCount()
                || entryStack.getFrameStateList().size() != exitStack.getFrameStateList().size())
            throw new IllegalStateException("EntryStack and ExitStack do not match.");
        FramesStack.DiffFrameStack methodImpact = new FramesStack.DiffFrameStack(exitStack.getFrameCount());
        for (int i = 0; i < entryStack.getFrameStateList().size(); i++) { //Compare each corresponding frame in the two stacks
            ProgramState entryFrame = entryStack.getFrameStateList().get(i);
            ProgramState exitFrame = exitStack.getFrameStateList().get(i);
            if (entryFrame.getRuntimeLocation().equalMethod(exitFrame.getRuntimeLocation())) {
                methodImpact.extendFrame(getDiff(entryFrame, exitFrame));
            }
        }
        return methodImpact;
    }

    private static ProgramState.DiffFrameState getDiff(ProgramState entryFrame, ProgramState exitFrame) {
        ProgramState.DiffFrameState diff = new ProgramState.DiffFrameState(exitFrame.getRuntimeLocation());
        if (entryFrame instanceof ProgramState.DiffFrameState && exitFrame instanceof ProgramState.DiffFrameState) {
            diff.putAllVarDiff(getDiff(((ProgramState.DiffFrameState) entryFrame).getChangedJdiApiEtmValMap(),
                    ((ProgramState.DiffFrameState) exitFrame).getChangedJdiApiEtmValMap()));
        } else {
            diff.putAllVarDiff(getDiff(entryFrame.getJdiApiEtmValMap(), exitFrame.getJdiApiEtmValMap()));
        }
        return diff;

    }


    /**
     * Compare entryMap and exitMap, record all the different expression (with last value)
     *
     * @param entryMap
     * @param exitMap
     * @return
     */
    private static List<Map<ExpressionToMonitor, DebuggerEvaluationResult>> getDiff(
            Map<ExpressionToMonitor, DebuggerEvaluationResult> entryMap,
            Map<ExpressionToMonitor, DebuggerEvaluationResult> exitMap) {
        List<Map<ExpressionToMonitor, DebuggerEvaluationResult>> diff = new ArrayList<>();
        Map<ExpressionToMonitor, DebuggerEvaluationResult> originalExpVal = new HashMap<>();
        Map<ExpressionToMonitor, DebuggerEvaluationResult> changedExpVal = new HashMap<>();
        for (ExpressionToMonitor expressionToMonitor : entryMap.keySet()) {
            //If an exp appear in both map, compare the value, other wise, it is a diff
            if (exitMap.containsKey(expressionToMonitor)) {
                if (entryMap.get(expressionToMonitor) != null && !entryMap.get(expressionToMonitor).equals(exitMap.get(expressionToMonitor))) {
                    originalExpVal.put(expressionToMonitor, entryMap.get(expressionToMonitor));
                    changedExpVal.put(expressionToMonitor, exitMap.get(expressionToMonitor));
                } else if (exitMap.get(expressionToMonitor) != null && !exitMap.get(expressionToMonitor).equals(exitMap.get(expressionToMonitor))) {
                    originalExpVal.put(expressionToMonitor, entryMap.get(expressionToMonitor));
                    changedExpVal.put(expressionToMonitor, exitMap.get(expressionToMonitor));
                }
            } else {
                // Special treatment for JAID_VARs
                originalExpVal.put(expressionToMonitor, entryMap.get(expressionToMonitor));
                changedExpVal.put(expressionToMonitor, exitMap.get(expressionToMonitor));
            }
        }
        for (ExpressionToMonitor expressionToMonitor : exitMap.keySet()) {
            if (!entryMap.containsKey(expressionToMonitor)) {
                originalExpVal.put(expressionToMonitor, entryMap.get(expressionToMonitor));
                changedExpVal.put(expressionToMonitor, exitMap.get(expressionToMonitor));
            }
        }
        diff.add(originalExpVal);
        diff.add(changedExpVal);
        return diff;

    }


    /**
     * Calculate (accumulating the difference recorded in fixImpact) the "passing score" according to the fixImpact (and Log the impact)
     * The less impact to the passing test, the higher "passing score" is.
     *
     * @param fixImpact
     * @return
     */
    private static double calculateScore(int allStateCount, FramesStack.DiffFrameStack fixImpact) {
        final int[] diffCount = {0};
        LoggingService.debugFileOnly("\n" + fixImpact.toString(), STACK_DIFF);
        for (ProgramState frameState : fixImpact.getFrameStateList()) {
            LoggingService.debugFileOnly(frameState.toString(), STACK_DIFF);
            ProgramState.DiffFrameState methodImpactDiff = (ProgramState.DiffFrameState) frameState;
            Map<ExpressionToMonitor, DebuggerEvaluationResult>
                    changedLocalVariableValueMap = methodImpactDiff.getChangedJdiApiEtmValMap(),
                    localVariableValueMap = methodImpactDiff.getJdiApiEtmValMap();

            LoggingService.debugFileOnly("LocalVar", STACK_DIFF);
            if (changedLocalVariableValueMap.size() != localVariableValueMap.size())
                throw new IllegalStateException("LocalVar Value size in MethodImpactDiff not match");
            for (ExpressionToMonitor expressionToMonitor : localVariableValueMap.keySet()) {
                diffCount[0]++;
                LoggingService.debugFileOnly(" " + expressionToMonitor
                        + "\n [" + localVariableValueMap.get(expressionToMonitor) + " -> "
                        + changedLocalVariableValueMap.get(expressionToMonitor) + "]", STACK_DIFF);
            }
        }
        return (allStateCount - diffCount[0] + 1) / (double) (allStateCount + diffCount[0] + 1);
    }

    private static int countAllState(FramesStack oStack, FramesStack nStack) {
        int allState = 0;
        Set allStateInFrame = new HashSet<ExpressionToMonitor>();
        for (ProgramState oFrame : oStack.getFrameStateList()) {
            //Special treatment for MTF
            for (ProgramState nFrame : nStack.getFrameStateList()) {
                if (oFrame.getRuntimeLocation().equalInstrumentedMethod(nFrame.getRuntimeLocation())) {
                    allStateInFrame.addAll(oFrame.getJdiApiEtmValMap().keySet());
                    allStateInFrame.addAll(nFrame.getJdiApiEtmValMap().keySet());
                    allState += allStateInFrame.size();
                    allStateInFrame.clear();
                    break;
                }
            }
        }
        return allState;
    }


    private static List getDividerIndex(List<FramesStack> framesStacks) {
        List dividerRecorder = new ArrayList<Integer>();
        for (int i = 0; i < framesStacks.size(); i++) {
            if (framesStacks.get(i).equals(FramesStack.divider))
                dividerRecorder.add(i);
        }
        return dividerRecorder;

    }
}
