package hk.polyu.comp.jaid.fixer;

import hk.polyu.comp.jaid.fixer.config.CmdOptions;
import hk.polyu.comp.jaid.fixer.config.Config;
import hk.polyu.comp.jaid.fixer.config.ConfigBuilder;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.util.LogUtil;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Application {

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        try {
            CommandLine commandLine = parseCommandLine(CmdOptions.getCmdOptions(), args);
            if (commandLine.getOptions().length == 0 || commandLine.hasOption(CmdOptions.HELP_OPT)) {
                System.out.println(helpInfo());
                return;
            }
            LogUtil.setStartTime(startTime);
            init(commandLine);
            Fixer fixer = new Fixer();
            fixer.execute();
        } catch (Throwable t) {
            t.printStackTrace();
            LoggingService.warn(t.toString());
            for (StackTraceElement stackTraceElement : t.getStackTrace()) {
                LoggingService.warn(stackTraceElement.toString());
            }
        } finally {
            LogUtil.logSessionCosting("Finished.");
            LoggingService.close();
            System.exit(33);
        }
    }

    public static CommandLine parseCommandLine(Options options, String[] args){
        try{
            CommandLineParser cmdLineParser = new DefaultParser();
            CommandLine commandLine = cmdLineParser.parse(options, args);
            return commandLine;
        }
        catch(ParseException e){
            throw new IllegalStateException();
        }
    }

    protected static void init(CommandLine commandLine) throws Exception {
        Config config = initConfig(commandLine);
        Session.initSession(config);
        LoggingService.initLogging(config);
        LoggingService.infoAll("JAID started ...");
    }

    protected static String helpInfo() {
        String result = "";

        try (StringWriter sWriter = new StringWriter();
             PrintWriter pWriter = new PrintWriter(sWriter)) {
            HelpFormatter usageFormatter = new HelpFormatter();
            usageFormatter.printHelp(pWriter, 80, "java Application", "", CmdOptions.getCmdOptions(), 3, 5, "", true);
            result = sWriter.toString();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        return result;
    }

    private static Config initConfig(CommandLine commandLine) {
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder.buildConfig(commandLine);
        Config config = configBuilder.getConfig();

        return config;
    }


}
