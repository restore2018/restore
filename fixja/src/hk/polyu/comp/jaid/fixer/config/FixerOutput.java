package hk.polyu.comp.jaid.fixer.config;


import java.nio.file.Path;

import static hk.polyu.comp.jaid.util.FileUtil.*;

/**
 * Created by Max PEI.
 */
public class FixerOutput {

    private static Path rootDir;

    public static void init(Path parentDir) {
        rootDir = parentDir.resolve(ROOT_DIR_NAME);

        ensureEmptyDir(rootDir);
        ensureEmptyDir(getTempSourceDirPath());
        ensureEmptyDir(getTempDestDirPath());
        ensureEmptyDir(getFormattedSourceDirPath());

        ensureEmptyFile(getTestCasesListFilePath());
        ensureEmptyFile(getMonitoredTestResultsLogFilePath());
    }

    public static Path getRootDir() {
        return rootDir;
    }


    public static Path getSourceJavaListFilePath() {
        return rootDir.resolve(SOURCE_JAVA_LIST_FILE_NAME);
    }

    public static Path getTestJavaListFilePath() {
        return rootDir.resolve(TEST_JAVA_LIST_FILE_NAME);
    }

    public static Path getTestCasesListFilePath() {
        return rootDir.resolve(TEST_CASES_LIST_FILE_NAME);
    }


    public static Path getTestedMethodsLogFilePath() {
        return rootDir.resolve(EXECUTED_METHODS_LOG_FILE_NAME);
    }


    public static Path getMonitoredTestResultsLogFilePath() {
        return rootDir.resolve(MONITORED_TEST_RESULTS);
    }

    public static Path getPre4LocationTestResultsLogFilePath() {
        return rootDir.resolve(PRETREATMENT_4LOCATION_TEST_RESULTS);
    }

    public static Path getPre4ExpTestResultsLogFilePath() {
        return rootDir.resolve(PRETREATMENT_4EXP_TEST_RESULTS);
    }

    public static Path getFixLogFilePath(long fixId, String otherKeyWord) {
        return rootDir.resolve(TEMP_FIX_DIR_PRE_NAME).resolve(otherKeyWord + fixId + OBSERVED_Fix_LOG_FILE_NAME);
    }

    public static Path getTempSourceDirPath() {
        return rootDir.resolve(TEMP_SOURCE_DIR_NAME);
    }

    public static Path getTempDestDirPath() {
        return rootDir.resolve(TEMP_DEST_DIR_NAME);
    }

    public static Path getTempFixDirPath(int fixHash) {
        return rootDir.resolve(TEMP_FIX_DIR_PRE_NAME).resolve(TEMP_FIX_DIR_POST_NAME + fixHash);
    }

    public static Path getFormattedSourceDirPath() {
        return rootDir.resolve(FORMATTED_SOURCE_DIR_NAME);
    }

    public static Path getTempBatchFixDirPath() {
        return rootDir.resolve(TEMP_FIX_DIR_PRE_NAME);
    }

    private static Path batchFixFilePath;

    public static Path getBatchFixFilePath() {
        return batchFixFilePath;
    }

    public static void setBatchFixFilePath(Path p) {
        batchFixFilePath = p;
    }

    private static final String ROOT_DIR_NAME = "jaid_output";
    private static final String SOURCE_JAVA_LIST_FILE_NAME = "source_java_files.txt";
    private static final String TEST_JAVA_LIST_FILE_NAME = "test_java_files.txt";
    private static final String TEST_CASES_LIST_FILE_NAME = "test_cases_files.txt";
    private static final String FORMATTED_SOURCE_DIR_NAME = "formatted_src";
    private static final String TEMP_SOURCE_DIR_NAME = "tmp_src";
    private static final String TEMP_DEST_DIR_NAME = "tmp_dest";
    private static final String TEMP_FIX_DIR_PRE_NAME = "tmp";
    private static final String TEMP_FIX_DIR_POST_NAME = "fix_src_";
    private static final String OBSERVED_Fix_LOG_FILE_NAME = "_fix.log";

    private static final String EXECUTED_METHODS_LOG_FILE_NAME = "executed_methods.log";
    private static final String MONITORED_TEST_RESULTS = "monitored_test_results.log";
    private static final String PRETREATMENT_4LOCATION_TEST_RESULTS = "pre4location_test_results.log";
    private static final String PRETREATMENT_4EXP_TEST_RESULTS = "pre4exp_test_results.log";


    /**
     * Created by Ls CHEN.
     */
    public enum LogFile {

        FILE("jaid.log"),
        EXE_ERROR_LOG("exe_errors.log"),
        MONITORED_EXPS("monitored_exps.log"),
        MONITORED_STATES("monitored_states.log"),
        STACK_DIFF("stack_diff.log"),
        FAILING_EXCEPTIONS("failing_exceptions.log"),
        EXE_LOCATIONS("exe_locations.log"),
        ALL_STATE_SNAPSHOT("all_snapshots.log"),
        SUSPICIOUS_STATE_SNAPSHOT("suspicious_snapshots.log"),
        SNIPPETS("snippets.log"),
        ALL_FIX_ACTION("raw_fix_actions.log"),
        EVALUATED_FIX_ACTION("evaluated_fix_actions.log"),
        PLAUSIBLE_LOG("plausible_fix_actions.log"),
        SECOND_VALIDATION_LOG("second_validation.log"),
        CANDIDATE_ID_FOR_SBFL("candidate_id_for_sbfl.log"),
        BASIC_BLOCK_MAP("basic_block_map.log"),
        LOCATION_SCORES("location_scores.log"),
        LOCATION_SORT_LOG("location_sort.log"),
        GIVEN_NUMBER_LOCATION_SORT_LOG("location_sort_given_number.log"),
        RANDOM_LOCATION_SORT_LOG("location_sort_random.log"),
        COMPILATION_ERRORS("compilation_errors.log");


        private String logFileName;

        LogFile(String logFileName) {
            this.logFileName = logFileName;
        }

        public Path getLogFilePath() {
            return getRootDir().resolve(this.logFileName);
        }
    }

}
