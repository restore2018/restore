package hk.polyu.comp.jaid.fixer.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import hk.polyu.comp.jaid.fixer.config.Config;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.util.FileUtil;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.MONITORED_STATES;
import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.STACK_DIFF;

/**
 * Created by Max PEI.
 */
public class LoggingService {

    private static LoggingService service;

    public static void initLogging(Config config) throws Exception {
        service = new LoggingService(config);
        addFileLogger(FixerOutput.LogFile.FILE, Level.toLevel(getCurrentLoggingLevel().toString()));
        addFileLogger(FixerOutput.LogFile.PLAUSIBLE_LOG, Level.DEBUG);
        addFileLogger(FixerOutput.LogFile.MONITORED_EXPS, Level.DEBUG);
        addFileLogger(FixerOutput.LogFile.ALL_STATE_SNAPSHOT, Level.DEBUG);
        addFileLogger(FixerOutput.LogFile.SUSPICIOUS_STATE_SNAPSHOT, Level.DEBUG);

        addFileLogger(FixerOutput.LogFile.EVALUATED_FIX_ACTION, Level.DEBUG);
        addFileLogger(FixerOutput.LogFile.COMPILATION_ERRORS, Level.DEBUG);
        addFileLogger(FixerOutput.LogFile.LOCATION_SORT_LOG, Level.DEBUG);
        addFileLogger(FixerOutput.LogFile.GIVEN_NUMBER_LOCATION_SORT_LOG, Level.DEBUG);
        addFileLogger(FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG, Level.DEBUG);
    }

    public static LogLevel getCurrentLoggingLevel() {
        return service.config.getLogLevel();
    }

    public static boolean shouldLogError() {
        return LogLevel.ERROR.isNotHigherThan(getCurrentLoggingLevel());
    }

    public static boolean shouldLogWarn() {
        return LogLevel.WARN.isNotHigherThan(getCurrentLoggingLevel());
    }

    public static boolean shouldLogInfo() {
        return LogLevel.INFO.isNotHigherThan(getCurrentLoggingLevel());
    }

    public static boolean shouldLogDebug() {
        return LogLevel.DEBUG.isNotHigherThan(getCurrentLoggingLevel());
    }

    public static boolean shouldLogTrace() {
        return LogLevel.TRACE.isNotHigherThan(getCurrentLoggingLevel());
    }

    public static void close() {
        for (Logger logger : service.loggerList) {
            logger.detachAndStopAllAppenders();
        }
    }

    private List<Logger> loggerList;
    private Logger stateLogger;
    private Config config;


    LoggingService(Config config) throws Exception {
        // Remove the default 'ROOT' logger.
        removeAllExistingLogger();

        this.config = config;
        this.loggerList = new ArrayList<>();
        addConsoleLogger(config);
        addConciseLogger(MONITORED_STATES);
        addConciseLogger(STACK_DIFF);
    }

    public static void error(String message) {
        for (Logger logger : service.loggerList)
            if (logger.getAppender(FixerOutput.LogFile.FILE.name()) != null
                    || logger.getAppender(ConsoleLoggerName) != null)
                logger.error(message);
    }

    public static void errorAll(String message) {
        for (Logger logger : service.loggerList)
            logger.error(message);
    }

    public static void warn(String message) {
        for (Logger logger : service.loggerList)
            if (logger.getAppender(FixerOutput.LogFile.FILE.name()) != null
                    || logger.getAppender(ConsoleLoggerName) != null)
                logger.warn(message);
    }

    public static void warnAll(String message) {
        for (Logger logger : service.loggerList)
            logger.warn(message);
    }

    public static void warnFileOnly(String message, FixerOutput.LogFile logFileName) {
        for (Logger logger : service.loggerList) {
            if (logger.getAppender(logFileName.name()) != null) {
                logger.warn(message);
                break;
            }
        }
    }

    public static void info(String message) {
        for (Logger logger : service.loggerList)
            if (logger.getAppender(FixerOutput.LogFile.FILE.name()) != null
                    || logger.getAppender(ConsoleLoggerName) != null)
                logger.info(message);
    }

    public static void infoAll(String message) {
        for (Logger logger : service.loggerList)
            logger.info(message);
    }

    public static void infoFileOnly(String message, FixerOutput.LogFile logFileName) {
        for (Logger logger : service.loggerList) {
            if (logger.getAppender(logFileName.name()) != null) {
                logger.info(message);
                break;
            }
        }
    }

    public static void debugFileOnly(String message, FixerOutput.LogFile logFileName) {
        for (Logger logger : service.loggerList) {
            if (logger.getAppender(logFileName.name()) != null) {
                logger.debug(message);
                break;
            }
        }
    }

    public static void debug(String message) {
        for (Logger logger : service.loggerList)
            if (logger.getAppender(FixerOutput.LogFile.FILE.name()) != null)
                logger.debug(message);
    }

    public static void logStateForDebug(String message) {
        service.stateLogger.debug(message);
    }

    public static void debugAll(String message) {
        for (Logger logger : service.loggerList)
            logger.debug(message);
    }

    public static void trace(String message) {
        for (Logger logger : service.loggerList)
            if (logger.getAppender(FixerOutput.LogFile.FILE.name()) != null
                    || logger.getAppender(ConsoleLoggerName) != null)
                logger.trace(message);
    }

    public static void traceAll(String message) {
        for (Logger logger : service.loggerList)
            logger.trace(message);
    }


    protected void removeAllExistingLogger() throws Exception {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<Logger> loggers = loggerContext.getLoggerList();
        for (Logger logger : loggers) {
            logger.detachAndStopAllAppenders();
        }
    }

    public static void addFileLogger(FixerOutput.LogFile logFileName, Level logLevel) {
        if (!isExistingLogger(logFileName)) {
            FileUtil.ensureEmptyFile(logFileName.getLogFilePath());

            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

            FileAppender fileAppender = new FileAppender();
            fileAppender.setContext(loggerContext);
            fileAppender.setName(logFileName.name());
            fileAppender.setFile(logFileName.getLogFilePath().toString());
            fileAppender.setEncoder(getPatternLayoutEncoder());
            fileAppender.setAppend(false);
            fileAppender.start();

            Logger logger = loggerContext.getLogger(logFileName.name());
            logger.detachAndStopAllAppenders();
            logger.addAppender(fileAppender);
            logger.setLevel(logLevel);

            service.loggerList.add(logger);
        }
    }

    private static boolean isExistingLogger(FixerOutput.LogFile logFileName) {
        for (Logger logger : service.loggerList) {
            if (logger.getAppender(logFileName.name()) != null)
                return true;
        }
        return false;
    }

    public static void removeExtraLogger(FixerOutput.LogFile logFileName) {
        for (Logger logger : service.loggerList) {
            if (logger.getAppender(logFileName.name()) != null) {
                logger.detachAndStopAllAppenders();
                break;
            }
        }
    }

    protected void addConsoleLogger(Config config) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        ConsoleAppender consoleAppender = new ConsoleAppender();
        consoleAppender.setContext(loggerContext);
        consoleAppender.setName(ConsoleLoggerName);
        consoleAppender.setEncoder(getPatternLayoutEncoder());
        consoleAppender.start();

        Logger logger = loggerContext.getLogger(ConsoleLoggerName);
        logger.detachAndStopAllAppenders();
        logger.addAppender(consoleAppender);
        logger.setLevel(Level.valueOf(config.getLogLevel().toString()));

        loggerList.add(logger);
    }

    public void addConciseLogger(FixerOutput.LogFile logFileName) throws Exception {
        FileUtil.ensureEmptyFile(logFileName.getLogFilePath());

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(loggerContext);
        fileAppender.setName(logFileName.name());
        fileAppender.setFile(logFileName.getLogFilePath().toString());
        fileAppender.setEncoder(getSimplePatternLayoutEncoder());
        fileAppender.setAppend(false);
        fileAppender.start();

        stateLogger = loggerContext.getLogger(logFileName.name());
        stateLogger.detachAndStopAllAppenders();
        stateLogger.addAppender(fileAppender);
        stateLogger.setLevel(Level.DEBUG);

        loggerList.add(stateLogger);
    }

    protected static PatternLayoutEncoder getPatternLayoutEncoder() {
        // Do not share encoders between appenders!
        // Always start encoders before use!

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.setPattern("%-24date [%-5thread] %-5level - %msg%n");
        encoder.start();

        return encoder;
    }

    protected static PatternLayoutEncoder getSimplePatternLayoutEncoder() {
        // Do not share encoders between appenders!
        // Always start encoders before use!

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.setPattern("%msg%n");
        encoder.start();
        return encoder;
    }

    public static final String STD_OUTPUT_START = "=== STD OUTPUT START ===";
    public static final String STD_OUTPUT_END = "=== STD OUTPUT END ===";
    public static final String STD_OUTPUT_IS_KILLED = "=== IS KILLED ===";

    public static final String ConsoleLoggerName = "CONS";


}
