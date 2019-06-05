package hk.polyu.comp.restore;

import hk.polyu.comp.jaid.fixer.config.CmdOptions;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.util.LogUtil;
import org.apache.commons.cli.CommandLine;

public class Application extends hk.polyu.comp.jaid.fixer.Application {
    public static int snapshotProcessStep = 1;
    public static String faultLocatizationMethod = "FL";
    public static boolean isOnlyUseFailedTest = true;
    public static boolean isSpectrumBased = true;
    public static boolean isForMoreFix = true;
    public static boolean isRestore = true;
    public static boolean isForRQ4 = false;
    public static boolean isForBetterFix = true;

    public static void main(String[] args) {
        if (args.length > 2 && args[2].equals("0"))
            isRestore = false;

        if (args.length > 3 && args[3].equals("0"))
            isForMoreFix = false;

        long startTime = System.currentTimeMillis();
        try {
            CommandLine commandLine = parseCommandLine(CmdOptions.getCmdOptions(), args);
            if (commandLine.getOptions().length == 0 || commandLine.hasOption(CmdOptions.HELP_OPT)) {
                System.out.println(helpInfo());
                return;
            }
            LogUtil.setStartTime(startTime);
            init(commandLine);
            FixerRES fixerRES = new FixerRES();
            fixerRES.execute();
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
}
