package hk.polyu.comp.jaid.fixer.ranking;

import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.jdi.debugger.QloseRanking;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.util.MappingRuntimeLocationByCode;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class QloseRankingCal extends AbsRankingCal {
    protected QloseRankingCal(JavaProject project, List<FixAction> validFixes, List<TestExecutionResult> testResults) {
        super(project, validFixes, testResults);
        passingOriginalTestResults = new HashMap<>();
    }

    private Map<String, TestExecutionResult> passingOriginalTestResults;
    private boolean isPublicFieldsOnly = false;

    @Override
    public void rank() {
        dynamicRankFixActions();
        scoreCalculation();
    }

    @Override
    public void launchingAFix(List<FixAction> currentBatch, FixAction fixAction, int currentIdx) {
        QloseRanking qloseRanking = new QloseRanking(project, Session.getSession().getConfig().getLogLevel(), 0, project.getTimeoutPerTest() * 500, FailureHandling.CONTINUE);
        qloseRanking.launchingFixAction(currentBatch, currentIdx);

    }


    @Override
    public void afterOneBatch(List<FixAction> currentBatch) {
        //Mapping runtime batch location in each program state to original LineLocation
        MappingRuntimeLocationByCode mapper = null;
        try {
            mapper = new MappingRuntimeLocationByCode(project.getMethodToMonitor(), project.getSourceFileWithAllFixes());
            for (int j = 0; j < currentBatch.size(); j++) {
                for (TestExecutionResult testExecutionResult : currentBatch.get(j).getTestExecutionResults()) {
                    for (ProgramState programState : testExecutionResult.getObservedStates()) {
                        programState.setLocation(mapper.mapping(programState.getRuntimeLocation()));
                    }
                    Set<ProgramState> toBeRemove = testExecutionResult.getObservedStates()
                            .stream().filter(x -> x.getLocation() == null).collect(Collectors.toSet());
                    testExecutionResult.getObservedStates().removeAll(toBeRemove);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void scoreCalculation() {
        passingOriginalTestResults = originalTestResults.stream().filter(x -> x.wasSuccessful()).collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity()));
        for (FixAction validFix : validFixes) {
            int semScore = 0;
            for (TestExecutionResult fixedResult : validFix.getTestExecutionResults()) {
                String testClassMethod = fixedResult.getTestClassAndMethod();
                if (passingOriginalTestResults.keySet().contains(testClassMethod)) {
                    List<ProgramState> original = passingOriginalTestResults.get(testClassMethod).getObservedStates();
                    List<ProgramState> fixed = fixedResult.getObservedStates();
                    semScore += getOneTestScore(original, fixed);
                }
            }
            validFix.setQloseScore(semScore);
        }

    }

    private int getOneTestScore(List<ProgramState> original, List<ProgramState> fixed) {
        int exeLengthDiff = Math.abs(original.size() - fixed.size());
        int minLength = Math.min(original.size(), fixed.size());
        int valueDiff = 0, locationDiff = 0, concreteDiff = 0;
        boolean isValueDiff = false, isLocationDiff = false;
        for (int iStep = 0; iStep < minLength; iStep++) {
            ProgramState oState = original.get(iStep), fState = fixed.get(iStep);
            if (!oState.getLocation().equals(fState.getLocation())) isLocationDiff = true;
            Set<ExpressionToMonitor> validETM = getValidVars(oState.getJdiApiEtmValMap().keySet(), fState.getJdiApiEtmValMap().keySet());
            for (ExpressionToMonitor etm : validETM) {
                DebuggerEvaluationResult or = oState.getJdiApiEtmValMap().getOrDefault(etm, null);
                DebuggerEvaluationResult fr = fState.getJdiApiEtmValMap().getOrDefault(etm, null);
                if (!(or == fr || (or != null && fr != null && or.equals(fr)))) {
                    isValueDiff = true;
                    break;
                }
            }
            if (isLocationDiff) locationDiff += 1;
            if (isValueDiff) valueDiff += 1;
            if (isLocationDiff || isValueDiff) concreteDiff += 1;
        }
        return concreteDiff + exeLengthDiff;
    }

    private Set<ExpressionToMonitor> getValidVars(Set<ExpressionToMonitor> oETM, Set<ExpressionToMonitor> fETM) {
        Set<ExpressionToMonitor> result = new HashSet();
        result.addAll(oETM);
        result.addAll(fETM);
        if (isPublicFieldsOnly)
            return result.stream().filter(x -> x.isPublic()).collect(Collectors.toSet());
        return result;
    }
}
