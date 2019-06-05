package hk.polyu.comp.jaid.fixer.config;

import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;
import hk.polyu.comp.jaid.fixer.ranking.AbsRankingCal;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * Created by Max PEI.
 */
public class CmdOptions {

    public final static String JAID_SETTING_FILE_OPT = "JaidSettingFile";
    public final static String JAID_SETTING_FILE_DESC = "Full path to the JAID setting file.";

    public final static String HELP_OPT = "Help";
    public final static String HELP_DESC = "Print the help message.";

    // Java environment

    public final static String JDK_DIR_OPT = "JDKDir";
    public final static String JDK_DIR_DESC = "Path to the JDK installation.";

    // JAID settings

    public final static String LOG_LEVEL_OPT = "LogLevel";
    public final static String LOG_LEVEL_DESC = "Level of information to log. Valid values: OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL.";

    // Project specific settings

    public final static String PROJECT_ROOT_DIR_OPT = "ProjectRootDir";
    public final static String PROJECT_ROOT_DIR_DESC = "Full path to the project root directory. All other relative paths will be resolved against this path";

    public final static String PROJECT_SOURCE_DIR_OPT = "ProjectSourceDir";
    public final static String PROJECT_SOURCE_DIR_DESC = "Paths to the project source directories, separated by semicolon (;).";

    public final static String PROJECT_OUTPUT_DIR_OPT = "ProjectOutputDir";
    public final static String PROJECT_OUTPUT_DIR_DESC = "Path to the project output directories.";

    public final static String PROJECT_TEST_OUTPUT_DIR_OPT = "ProjectTestOutputDir";
    public final static String PROJECT_TEST_OUTPUT_DIR_DESC = "Path to the project test output directories.";

    public final static String PROJECT_LIB_OPT = "ProjectLib";
    public final static String PROJECT_LIB_DESC = "Paths to the project libraries, separated by semicolon (;).";

    public final static String PROJECT_TEST_SOURCE_DIR_OPT = "ProjectTestSourceDir";
    public final static String PROJECT_TEST_SOURCE_DIR_DESC = "Paths to the project test source directories, separated by semicolon (;).";

    public final static String PROJECT_TESTS_TO_INCLUDE_OPT = "ProjectTestsToInclude";
    public final static String PROJECT_TESTS_TO_INCLUDE_DESC = "Tests that should be used for fixing. All tests will be included if not specified. Format: FullyQualifiedClassName;FullyQualifiedClassName#MethodName";

    public final static String PROJECT_TESTS_TO_EXCLUDE_OPT = "ProjectTestsToExclude";
    public final static String PROJECT_TESTS_TO_EXCLUDE_DESC = "Tests that should NOT be used for fixing. No test will be excluded if not specified. Format: FullyQualifiedClassName;FullyQualifiedClassName#MethodName";

    public final static String PROJECT_EXTRA_CLASSPATH_OPT = "ProjectExtraClasspath";
    public final static String PROJECT_EXTRA_CLASSPATH_DESC = "Project extra classpath. Used together with other paths to form the classpath.";

    public final static String METHOD_TO_FIX_OPT = "MethodToFix";
    public final static String METHOD_TO_FIX_DESC = "Method to fix. Format: MethodName(Type1,Type2)@PackageName.ClassName .";

    public final static String ENCODING_OPT = "Encoding";
    public final static String ENCODING_DESC = "Compile SourceCode Encoding (optional).";

    public final static String EXCLUDE_EXP_OPT = "ExpToExclude";
    public final static String EXCLUDE_EXP_DESC = "Expressions should be excluded in the monitor stage (optional).";

    public final static String TIMEOUT_PER_TEST_OPT = "TimeoutPerTest";
    public final static String TIMEOUT_PER_TEST_DESC = "Timeout in milli seconds for each test.";

    public final static String TARGET_JAVA_VERSION_OPT = "TargetJavaVersion";
    public final static String TARGET_JAVA_VERSION_DESC = "Source code target java version(optional, default 1.8).";

    public final static String MAX_TEST_NO = "MaxTestNumber";
    public final static String MAX_TEST_NO_DESC = "The maximum number of passing test used in monitoring and ranking";

    public final static String ENABLE_SECOND_VALIDATION = "EnableSecondValidation";
    public final static String ENABLE_SECOND_VALIDATION_DESC = "This is a special trigger for the function of second validation for ICJ";

    public final static String SBFL_ALGORITHM = "SbflAlgorithm";
    public final static String SBFL_ALGORITHM_DESC = "Selected spectrum based fault localization algorithm (optional, default AutoFix's algorithm)." +
            " Valid values: " + AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix + ","
            + AbsSuspiciousnessAlgorithm.SbflAlgorithm.Tarantula + ","
            + AbsSuspiciousnessAlgorithm.SbflAlgorithm.Barinel + ","
            + AbsSuspiciousnessAlgorithm.SbflAlgorithm.DStar + ","
            + AbsSuspiciousnessAlgorithm.SbflAlgorithm.Ochiai + ","
            + AbsSuspiciousnessAlgorithm.SbflAlgorithm.Op2;

    public final static String RANKING_ALGORITHM = "RankingAlgorithm";
    public final static String RANKING_ALGORITHM_DESC = "Selected patch ranking algorithm (optional, default AutoFix's algorithm)." +
            " Valid values: " + AbsRankingCal.RankingAlgorithm.AutoFix + ","
            + AbsRankingCal.RankingAlgorithm.Qlose;

    public final static String SNIPPET_CONSTRUCTION_STRATEGY_OPT = "SnippetConstructionStrategy";
    public final static String SNIPPET_CONSTRUCTION_STRATEGY_BASIC = "basic";
    public final static String SNIPPET_CONSTRUCTION_STRATEGY_COMPREHENSIVE = "comprehensive";
    public final static String SNIPPET_CONSTRUCTION_STRATEGY_DESC = "Strategy used to construct code snippets (optional). Valid values: "
            + SNIPPET_CONSTRUCTION_STRATEGY_BASIC + ", "
            + SNIPPET_CONSTRUCTION_STRATEGY_COMPREHENSIVE + ".";

    public final static String PROJECT_COMPILATION_COMMAND_OPT = "ProjectCompilationCommand";
    public final static String PROJECT_COMPILATION_COMMAND_DESC = "Command to compile the project. Supported placeholders include "
            + getPlaceHolder(PROJECT_ROOT_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_SOURCE_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_OUTPUT_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_LIB_OPT) + ", "
            + getPlaceHolder(PROJECT_EXTRA_CLASSPATH_OPT) + ", "
            + getPlaceHolder(PROJECT_TEST_SOURCE_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_TEST_OUTPUT_DIR_OPT) + ".";

    public final static String PROJECT_EXECUTION_COMMAND_OPT = "ProjectExecutionCommand";
    public final static String PROJECT_EXECUTION_COMMAND_DESC = "Command to execute the project. Supported placeholders include "
            + getPlaceHolder(PROJECT_ROOT_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_SOURCE_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_OUTPUT_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_LIB_OPT) + ", "
            + getPlaceHolder(PROJECT_EXTRA_CLASSPATH_OPT) + ", "
            + getPlaceHolder(PROJECT_TEST_SOURCE_DIR_OPT) + ", "
            + getPlaceHolder(PROJECT_TEST_OUTPUT_DIR_OPT) + ".";

    public final static String[][] commandLineFlags = {
            {HELP_OPT, HELP_DESC}
    };

    public final static String[][] commandLineOptions = {
            {JAID_SETTING_FILE_OPT, JAID_SETTING_FILE_DESC},

            {JDK_DIR_OPT, JDK_DIR_DESC},

            {LOG_LEVEL_OPT, LOG_LEVEL_DESC},

            {METHOD_TO_FIX_OPT, METHOD_TO_FIX_DESC},

            {SBFL_ALGORITHM, SBFL_ALGORITHM_DESC},
            {RANKING_ALGORITHM, RANKING_ALGORITHM_DESC},
            {MAX_TEST_NO, MAX_TEST_NO_DESC},
            {ENABLE_SECOND_VALIDATION, ENABLE_SECOND_VALIDATION_DESC},


            {PROJECT_ROOT_DIR_OPT, PROJECT_ROOT_DIR_DESC},
            {PROJECT_SOURCE_DIR_OPT, PROJECT_SOURCE_DIR_DESC},
            {PROJECT_OUTPUT_DIR_OPT, PROJECT_OUTPUT_DIR_DESC},
            {PROJECT_LIB_OPT, PROJECT_LIB_DESC},
            {PROJECT_EXTRA_CLASSPATH_OPT, PROJECT_EXTRA_CLASSPATH_DESC},
            {PROJECT_TEST_SOURCE_DIR_OPT, PROJECT_TEST_SOURCE_DIR_DESC},
            {PROJECT_TEST_OUTPUT_DIR_OPT, PROJECT_TEST_OUTPUT_DIR_DESC},
            {PROJECT_TESTS_TO_INCLUDE_OPT, PROJECT_TESTS_TO_INCLUDE_DESC},
            {PROJECT_TESTS_TO_EXCLUDE_OPT, PROJECT_TESTS_TO_EXCLUDE_DESC},
            {ENCODING_OPT, ENCODING_DESC},
            {TARGET_JAVA_VERSION_OPT, TARGET_JAVA_VERSION_DESC},
            {TIMEOUT_PER_TEST_OPT, TIMEOUT_PER_TEST_DESC},

            {EXCLUDE_EXP_OPT, EXCLUDE_EXP_DESC},

            {PROJECT_COMPILATION_COMMAND_OPT, PROJECT_COMPILATION_COMMAND_DESC},
            {PROJECT_EXECUTION_COMMAND_OPT, PROJECT_EXECUTION_COMMAND_DESC}
    };

    public final static String getPlaceHolder(String option) {
        return PLACE_HOLDER_BEGIN + option + PLACE_HOLDER_END;
    }

    public final static String PLACE_HOLDER_BEGIN = "${";
    public final static String PLACE_HOLDER_END = "}";

    private static Options cmdOptions;

    public static Options getCmdOptions() {
        if (cmdOptions == null) {
            cmdOptions = new Options();
            prepareCmdOptions();
        }
        return cmdOptions;
    }

    private static void prepareCmdOptions() {
        for (String[] pair : commandLineOptions) {
            cmdOptions.addOption(Option.builder().longOpt(pair[0]).hasArg(true).desc(pair[1]).build());
        }

        for (String[] pair : commandLineFlags) {
            cmdOptions.addOption(Option.builder().longOpt(pair[0]).hasArg(false).desc(pair[1]).build());
        }
    }

}
