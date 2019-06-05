package hk.polyu.comp.jaid.fixer.config;

import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.fixer.ranking.AbsRankingCal;
import hk.polyu.comp.jaid.java.JavaEnvironment;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;

/**
 * Created by Max PEI.
 */
public class Config {

    private LogLevel logLevel;

    private ExperimentControl experimentControl;

    public enum SnippetConstructionStrategy {BASIC, COMPREHENSIVE}

    ;

    private SnippetConstructionStrategy snippetConstructionStrategy;

    public SnippetConstructionStrategy getSnippetConstructionStrategy() {
        return snippetConstructionStrategy;
    }

    public void setSnippetConstructionStrategy(SnippetConstructionStrategy snippetConstructionStrategy) {
        this.snippetConstructionStrategy = snippetConstructionStrategy;
    }

    private JavaEnvironment javaEnvironment;
    private JavaProject javaProject;
    private String methodToFix;

    public String getMethodToFix() {
        return methodToFix;
    }

    public void setMethodToFix(String methodToFix) {
        this.methodToFix = methodToFix;
    }

    public String getFaultyClassName() {
        return getMethodToFix().substring(getMethodToFix().indexOf('@') + 1, getMethodToFix().length());
    }

    public String getFaultyMethodSignature() {
        return getMethodToFix().substring(0, getMethodToFix().indexOf('@'));
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public JavaProject getJavaProject() {
        return javaProject;
    }

    public void setJavaProject(JavaProject javaProject) {
        this.javaProject = javaProject;
    }

    public ExperimentControl getExperimentControl() {
        return experimentControl;
    }

    public void setExperimentControl(ExperimentControl experimentControl) {
        this.experimentControl = experimentControl;
    }


    public JavaEnvironment getJavaEnvironment() {
        return javaEnvironment;
    }

    public void setJavaEnvironment(JavaEnvironment javaEnvironment) {
        this.javaEnvironment = javaEnvironment;
    }

    public static class ExperimentControl {
        private AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm;
        private AbsRankingCal.RankingAlgorithm rankingAlgorithm;
        private int maxPassingTestNumber = -1;
        private boolean enableSecondValidation = false;


        public ExperimentControl(String sbflAlgorithm, String rankingAlgorithm) {
            if (sbflAlgorithm == null || sbflAlgorithm.trim().length() == 0)
                this.sbflAlgorithm = AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix;
            else if (sbflAlgorithm.trim().toLowerCase().equals(KEY_WORD_ALL))
                this.sbflAlgorithm = null;
            else
                this.sbflAlgorithm = AbsSuspiciousnessAlgorithm.SbflAlgorithm.get(sbflAlgorithm);

            if (rankingAlgorithm == null || rankingAlgorithm.trim().length() == 0)
                this.rankingAlgorithm = AbsRankingCal.RankingAlgorithm.NoRank;
            else if (rankingAlgorithm.trim().toLowerCase().equals(KEY_WORD_ALL))
                this.rankingAlgorithm = null;
            else
                this.rankingAlgorithm = AbsRankingCal.RankingAlgorithm.get(rankingAlgorithm);
        }

        public AbsSuspiciousnessAlgorithm.SbflAlgorithm getSbflAlgorithm() {
            return sbflAlgorithm;
        }

        public AbsRankingCal.RankingAlgorithm getRankingAlgorithm() {
            return rankingAlgorithm;
        }

        public int getMaxPassingTestNumber() {
            return maxPassingTestNumber;
        }

        public void setMaxPassingTestNumber(int maxPassingTestNumber) {
            this.maxPassingTestNumber = maxPassingTestNumber;
        }

        public boolean isEnableSecondValidation() {
            return enableSecondValidation;
        }

        public void enableSecondValidation() {
            this.enableSecondValidation = true;
        }
    }

    public static final int MAXIMUM_STATE_SNAPSHOTS = 1500;
    private static final int MONITOR_TEST_BATCH_LOCATION_ETM = 600000;
    private static final int TEST_THRESHOLD_LOCATION_ETM = 600000;
    private static final String KEY_WORD_ALL = "all";

    public static int BATCH_SIZE = 50;// The number of fixed method in a class is BATCH_SIZE*BATCH_SIZE
    public static int TEST_BATCH = 50;// The maximum number of executed tests in tester during M3.
    // Updated according to the valid location size and ETM size, default is 50.
    public static int TEST_THRESHOLD = 50;// Updated according to the valid location size and ETM size, default is 50.

    public static void updateTestParameters(int valid_location_number, int etm_number) {
        TEST_BATCH = MONITOR_TEST_BATCH_LOCATION_ETM / valid_location_number / etm_number;
        TEST_THRESHOLD = TEST_THRESHOLD_LOCATION_ETM / valid_location_number / etm_number;
    }
}

