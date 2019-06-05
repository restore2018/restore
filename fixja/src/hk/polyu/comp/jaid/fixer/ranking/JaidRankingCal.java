package hk.polyu.comp.jaid.fixer.ranking;

import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixer.ImpactComparator;
import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.jdi.debugger.JaidRanking;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.monitor.state.ProgramState;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.MONITORED_STATES;
import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.STACK_DIFF;

public class JaidRankingCal extends AbsRankingCal {

    protected JaidRankingCal(JavaProject project, List<FixAction> validFixes, List<TestExecutionResult> testResults) {
        super(project, validFixes, testResults);
    }

    @Override
    public void rank() {
        dynamicRankFixActions();
        scoreCalculation();
    }

    @Override
    public void launchingAFix(List<FixAction> currentBatch, FixAction fixAction, int currentIdx) {
        //Ranking may fail due to timeout.
        JaidRanking validator = new JaidRanking(project, Session.getSession().getConfig().getLogLevel(), 0, project.getTimeoutPerTest() * 500, FailureHandling.CONTINUE);
        validator.validateFixAction(currentBatch, currentIdx, true);

        if (fixAction.getSuccessfulTestExecutionResultsCount() == validator.getTestSize()) {
            //Check if fix validation result matches fault localization result
            Map<String, TestExecutionResult> testToResultMapBefore = originalTestResults.stream().filter(TestExecutionResult::wasSuccessful).collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity()));
            if (testToResultMapBefore.size() >= validator.getResults().size())
                throw new IllegalStateException();

            Map<String, TestExecutionResult> testToResultMapAfter = validator.getResults().stream().filter(x -> testToResultMapBefore.containsKey(x.getTestClassAndMethod()))
                    .collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity()));
            if (testToResultMapBefore.size() != testToResultMapAfter.size())
                throw new IllegalStateException();
            //Log fix action exit states
            LoggingService.debugFileOnly("\n===== Ranking fix " + currentIdx + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====", MONITORED_STATES);
            for (TestExecutionResult testExecutionResult : validator.getResults()) {
                LoggingService.debugFileOnly("\nTest: " + testExecutionResult.toString(), MONITORED_STATES);
                for (ProgramState exitState : testExecutionResult.getExitStates()) {
                    LoggingService.debugFileOnly(exitState.toString(), MONITORED_STATES);
                }
            }
            double locationScore = locationScoreCalculation(testToResultMapBefore, testToResultMapAfter);
            fixAction.setLocationScore(locationScore);

            retainFixImacts(originalTestResults.stream().collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity())),
                    validator.getResults().stream().collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity())));
        } else {
            LoggingService.errorAll("Failed to rank fix ::" + fixAction.getFixId());
        }
    }

    @Override
    public void afterOneBatch(List<FixAction> currentBatch) {

    }

    public void scoreCalculation() {
        for (FixAction fixAction : validFixes) {
            //Check if fix validation result matches fault localization result
            Map<String, TestExecutionResult> testToResultMapBefore = originalTestResults.stream().filter(TestExecutionResult::wasSuccessful).collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity()));
            if (testToResultMapBefore.size() >= fixAction.getTestExecutionResults().size())
                continue;//throw new IllegalStateException();

            Map<String, TestExecutionResult> testToResultMapAfter = fixAction.getTestExecutionResults().stream().filter(x -> testToResultMapBefore.containsKey(x.getTestClassAndMethod()))
                    .collect(Collectors.toMap(TestExecutionResult::getTestClassAndMethod, Function.identity()));
            if (testToResultMapBefore.size() != testToResultMapAfter.size())
                throw new IllegalStateException();
            double passingImpact = passingImpactCalculation(testToResultMapBefore, testToResultMapAfter);
            double failingImpact = failingImpactCalculation(originalTestResults, fixAction.getTestExecutionResults());
            double passingImpactF = passingImpactCalculationFirst(testToResultMapBefore, testToResultMapAfter);
            double failingImpactF = failingImpactCalculationFirst(originalTestResults, fixAction.getTestExecutionResults());
            LoggingService.debugFileOnly("Fid:" + fixAction.getFixId()
                            + " QloseScore:" + fixAction.getQloseScore()
                            + " passingImpact:" + passingImpact
                            + " failingImpact:" + failingImpact
                    , STACK_DIFF);


            fixAction.setPassingImpact(passingImpact);
            fixAction.setFailingImpact(failingImpact);
            fixAction.setPassingImpactF(passingImpactF);
            fixAction.setFailingImpactF(failingImpactF);
            //new calculate score
        }

    }

    private double locationScoreCalculation(Map<String, TestExecutionResult> testToResultMapBefore, Map<String, TestExecutionResult> testToResultMapAfter) {
        List<Double> scores = new LinkedList<>();
        for (String test : testToResultMapBefore.keySet()) {
            TestExecutionResult beforeResult = testToResultMapBefore.get(test), afterResult = testToResultMapAfter.get(test);
            scores.add(beforeResult.computeLocationScore(afterResult));
        }
        return scores.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
    }

    private double stateScoreCalculation(Map<String, TestExecutionResult> testToResultMapBefore, Map<String, TestExecutionResult> testToResultMapAfter) {
        List<Double> scores = new LinkedList<>();
        for (String test : testToResultMapBefore.keySet()) {
            TestExecutionResult beforeResult = testToResultMapBefore.get(test), afterResult = testToResultMapAfter.get(test);
            scores.add(beforeResult.computeStateScore(afterResult));
        }
        return scores.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
    }

    private void retainFixImacts(Map<String, TestExecutionResult> testToResultMapBefore, Map<String, TestExecutionResult> testToResultMapAfter) {
        for (String test : testToResultMapBefore.keySet()) {
            TestExecutionResult beforeResult = testToResultMapBefore.get(test), afterResult = testToResultMapAfter.get(test);
            ImpactComparator.retainFixImpact(beforeResult, afterResult);
        }
    }

    /**
     * larger score means less state changes
     *
     * @param testToResultMapBefore
     * @param testToResultMapAfter
     * @return
     */
    private double passingImpactCalculation(Map<String, TestExecutionResult> testToResultMapBefore, Map<String, TestExecutionResult> testToResultMapAfter) {
        List<Double> scores = new LinkedList<>();

        for (String test : testToResultMapBefore.keySet()) {
            TestExecutionResult beforeResult = testToResultMapBefore.get(test), afterResult = testToResultMapAfter.get(test);
            scores.add(ImpactComparator.computeSideChangeFromFixImpact(beforeResult, afterResult)
            );
        }
        return scores.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
    }

    /**
     * @param testToResultMapBefore
     * @param testToResultMapAfter
     * @return
     */
    private double failingImpactCalculation(List<TestExecutionResult> testToResultMapBefore, List<TestExecutionResult> testToResultMapAfter) {
        List<Double> scores = new LinkedList<>();
        for (TestExecutionResult testB : testToResultMapBefore) {
            if (!testB.wasSuccessful())
                for (TestExecutionResult testA : testToResultMapAfter) {
                    if (testB.getTestClassAndMethod().equals(testA.getTestClassAndMethod())) {
                        scores.add(ImpactComparator.computeSideChangeFromFixImpact(testB, testA));
                        break;
                    }

                }
        }
        return scores.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
    }

    private double passingImpactCalculationFirst(Map<String, TestExecutionResult> testToResultMapBefore, Map<String, TestExecutionResult> testToResultMapAfter) {
        List<Double> scores = new LinkedList<>();
        for (String test : testToResultMapBefore.keySet()) {
            TestExecutionResult beforeResult = testToResultMapBefore.get(test), afterResult = testToResultMapAfter.get(test);
            scores.add(ImpactComparator.computeSideChangeFromFixImpactFirst(beforeResult, afterResult)
            );
        }
        return scores.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
    }

    private double failingImpactCalculationFirst(List<TestExecutionResult> testToResultMapBefore, List<TestExecutionResult> testToResultMapAfter) {
        List<Double> scores = new LinkedList<>();
        for (TestExecutionResult testB : testToResultMapBefore) {
            if (!testB.wasSuccessful())
                for (TestExecutionResult testA : testToResultMapAfter) {
                    if (testB.getTestClassAndMethod().equals(testA.getTestClassAndMethod())) {
                        scores.add(ImpactComparator.computeSideChangeFromFixImpactFirst(testB, testA));
                        break;
                    }

                }
        }
        return scores.stream().mapToDouble(x -> x).summaryStatistics().getAverage();
    }
}
