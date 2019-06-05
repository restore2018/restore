package hk.polyu.comp.jaid.fixer.config;

import hk.polyu.comp.jaid.fixer.log.LogLevel;
import hk.polyu.comp.jaid.java.JavaEnvironment;
import hk.polyu.comp.jaid.java.JavaProject;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Created by Max PEI.
 */
public class ConfigBuilder {

    private Config config;

    public Config getConfig() {
        return config;
    }

    public void buildConfig(CommandLine commandLine) {
        Properties properties = null;
        String jaidSettingFileVal = commandLine.getOptionValue(CmdOptions.JAID_SETTING_FILE_OPT, null);
        if (jaidSettingFileVal == null) {
            properties = getPropertiesFromCommandLine(commandLine);
        } else {
            properties = getPropertiesFromFile(Paths.get(jaidSettingFileVal));
        }

        config = buildConfigFromProperties(properties);
    }

    private Properties getPropertiesFromFile(Path jaidSettingFile) {
        Properties properties = new Properties();

        try (FileInputStream in = new FileInputStream(jaidSettingFile.toString())) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Error: Failed to load properties from file.\n\t" + jaidSettingFile.toString());
        }

        return properties;
    }

    private Properties getPropertiesFromCommandLine(CommandLine commandLine) {
        Properties properties = new Properties();
        for (Option opt : commandLine.getOptions()) {
            String propertyID = opt.getArgName();
            properties.setProperty(propertyID, commandLine.getOptionValue(propertyID));
        }

        return properties;
    }

    private Config buildConfigFromProperties(Properties properties) {
        Config config = new Config();

        initJavaEnvironment(config, properties);
        initLogLevel(config, properties);
        initJavaProject(config, properties);
        initMethodToFix(config, properties);
        initSnippetConstructionStrategy(config, properties);
        initExperimentControl(config, properties);
        return config;
    }

    private void initMethodToFix(Config config, Properties properties) {
        config.setMethodToFix(properties.getProperty(CmdOptions.METHOD_TO_FIX_OPT));
    }

    private void initExperimentControl(Config config, Properties properties) {
        Config.ExperimentControl experimentControl = new Config.ExperimentControl
                (properties.getProperty(CmdOptions.SBFL_ALGORITHM), properties.getProperty(CmdOptions.RANKING_ALGORITHM));
        if (properties.getProperty(CmdOptions.MAX_TEST_NO) != null && properties.getProperty(CmdOptions.MAX_TEST_NO).trim().length() > 0)
            experimentControl.setMaxPassingTestNumber(Integer.valueOf(properties.getProperty(CmdOptions.MAX_TEST_NO)));
        if (properties.getProperty(CmdOptions.ENABLE_SECOND_VALIDATION) != null
                && properties.getProperty(CmdOptions.ENABLE_SECOND_VALIDATION).trim().toLowerCase().equals("true"))
            experimentControl.enableSecondValidation();
        config.setExperimentControl(experimentControl);
    }

    private void initJavaProject(Config config, Properties properties) {
        config.setJavaProject(new JavaProject(config.getJavaEnvironment(), properties));
    }

    private void initJavaEnvironment(Config config, Properties properties) {
        String jdkDirVal = properties.getProperty(CmdOptions.JDK_DIR_OPT, null);
        if (jdkDirVal == null)
            throw new IllegalStateException("Error: JDK environment not set (" + CmdOptions.JDK_DIR_OPT + ").");
        else {
            config.setJavaEnvironment(new JavaEnvironment(jdkDirVal));
        }
    }

    private void initLogLevel(Config config, Properties properties) {
        LogLevel level = LogLevel.valueOf(properties.getProperty(CmdOptions.LOG_LEVEL_OPT));
        config.setLogLevel(level);
    }

    private void initSnippetConstructionStrategy(Config config, Properties properties) {
        String strategyStr = properties.getProperty(CmdOptions.SNIPPET_CONSTRUCTION_STRATEGY_OPT);
        if (CmdOptions.SNIPPET_CONSTRUCTION_STRATEGY_COMPREHENSIVE.equals(strategyStr)) {
            config.setSnippetConstructionStrategy(Config.SnippetConstructionStrategy.COMPREHENSIVE);
        } else {
            config.setSnippetConstructionStrategy(Config.SnippetConstructionStrategy.BASIC);
        }
    }

}
