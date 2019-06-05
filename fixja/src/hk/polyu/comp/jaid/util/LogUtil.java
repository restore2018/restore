package hk.polyu.comp.jaid.util;

import ch.qos.logback.classic.Level;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.COMPILATION_ERRORS;
import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.PLAUSIBLE_LOG;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.addFileLogger;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

/**
 * Created by liushanchen on 16/10/6.
 */
public class LogUtil {
    private static long startTime;

    /**
     * Output the items within the collection to a specific log
     *
     * @param objectCollection the collection to be logged
     * @throws Exception
     */
    public static void logCollectionForDebug(Collection objectCollection, FixerOutput.LogFile logFileName, boolean removeLogger) {
        if (shouldLogDebug()) {
            addFileLogger(logFileName, Level.INFO);
            LoggingService.infoFileOnly(logFileName.name() + " size:" + objectCollection.size(), logFileName);
            objectCollection.stream().forEach(f ->
                    LoggingService.infoFileOnly(f.toString(), logFileName));
            if (removeLogger)
                LoggingService.removeExtraLogger(logFileName);
        }
    }

    /**
     * Output the items within the map to a specific log
     *
     * @param objectCollection the map to be logged
     * @throws Exception
     */
    public static void logMapForDebug(Map objectCollection, FixerOutput.LogFile logFileName) {
        addFileLogger(logFileName, Level.DEBUG);
        LoggingService.debugFileOnly(logFileName.name() + " size:" + objectCollection.size(), logFileName);
        objectCollection.forEach((k, v) -> {
            StringBuilder vStr = new StringBuilder();
            if (v instanceof Collection) {
                ((Collection) v).forEach(x -> vStr.append(x.toString()));
            } else vStr.append(v.toString());
            LoggingService.debugFileOnly(k.toString() + " : " + vStr.toString(), logFileName);
        });
//                LoggingService.removeExtraLogger(logFileName);
    }

    /**
     * Output items within a map
     *
     * @param objectCollection the map to be logged
     */
    public static void logMapForDebug(Map objectCollection) {
        if (shouldLogDebug()) {
            objectCollection.forEach((k, v) ->
                    LoggingService.debug(k.toString() + "\n" + v.toString()));
        }
    }

    public static void logCompilationErrorForDebug(DiagnosticCollector<JavaFileObject> diagnosticCollector) {
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticCollector.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                StringBuilder sb = new StringBuilder("Source: ")
                        .append(diagnostic.getSource().getName())
                        .append(" :: ").append(diagnostic.getLineNumber())
                        .append("\nMSG: ").append(diagnostic.getMessage(null));
                LoggingService.debugFileOnly(sb.toString(), COMPILATION_ERRORS);
            }
        }
    }

    public static void logLocationForDebug(Set<LineLocation> locations) {
        if (shouldLogDebug()) {
            FixerOutput.LogFile locationLog = FixerOutput.LogFile.EXE_LOCATIONS;

            FileUtil.ensureEmptyFile(locationLog.getLogFilePath());
            try (PrintWriter writer = new PrintWriter(locationLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())) {
                for (LineLocation l : locations) {
                    writer.println(l.toString());
                }
                writer.println("Valid locations.size:" + locations.size());
                writer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        LoggingService.infoAll("Valid locations.size::" + locations.size());
    }

    public static void logEvaluatedSnapshotsForDebug(List<StateSnapshot> snapshots) {
        if (shouldLogDebug()) {
            FixerOutput.LogFile snapshotLog = FixerOutput.LogFile.SUSPICIOUS_STATE_SNAPSHOT;

            FileUtil.ensureEmptyFile(snapshotLog.getLogFilePath());
            try (PrintWriter writer = new PrintWriter(snapshotLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())) {
                for (StateSnapshot snapshot : snapshots) {
                    writer.println(snapshot + " --> " + snapshot.getSuspiciousness());
                }
                writer.println("Valid snapshots.size:" + snapshots.size());
                writer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        LoggingService.infoAll("Valid snapshots.size::" + snapshots.size());
    }

    public static void logSnippetsForDebug(Map<StateSnapshot, Set<Snippet>> snippetMap) {
        int size = 0;
        for (StateSnapshot snapshot : snippetMap.keySet()) {
            size += snippetMap.get(snapshot).size();
        }
        if (shouldLogDebug()) {
//            addFileLogger(FixerOutput.LogFile.SNIPPETS, Level.DEBUG);
            FixerOutput.LogFile snippetLog = FixerOutput.LogFile.SNIPPETS;

            FileUtil.ensureEmptyFile(snippetLog.getLogFilePath());
            try {
                try (PrintWriter writer = new PrintWriter(snippetLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())) {
                    for (StateSnapshot snapshot : snippetMap.keySet()) {
                        writer.println("<<<<");
                        writer.println(snapshot.toString());
                        writer.println(">>>>");
                        Set<Snippet> snippets = snippetMap.get(snapshot);
                        for (Snippet snippet : snippets) {
                            writer.print(snippet.toString());
                            writer.print("-----");
                        }
                    }
                    //                for (Map.Entry<StateSnapshot, Set<Snippet>> snippetEntry : snippetMap.entrySet()) {
                    //                    writer.println(snippetEntry.getKey() + " --> " + snippetEntry.getValue());
                    //                    size += snippetEntry.getValue().size();
                    //                }
                    writer.println("Valid snippets:" + size);
                    writer.close();

                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        LoggingService.infoAll("Valid snippets::" + size);

    }

    public static void logFixActionsForDebug(Collection<FixAction> fixActions) throws Exception {
        if (shouldLogDebug()) {
            FixerOutput.LogFile fixActionLog = FixerOutput.LogFile.ALL_FIX_ACTION;
            FileUtil.ensureEmptyFile(fixActionLog.getLogFilePath());
            try (PrintWriter writer = new PrintWriter(fixActionLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())) {
                for (FixAction fixAction : fixActions) {
                    writer.println(fixAction.toString());
                }
                writer.close();
            }
        }
        LoggingService.infoAll("Generated fixactions::" + fixActions.size());
    }

    public static void setStartTime(long startTime) {
        LogUtil.startTime = startTime;
    }

    public static void logSessionCosting(String msg) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long duration = System.currentTimeMillis() - startTime;

        duration = duration / 1000;
        LoggingService.infoAll(new StringBuilder(msg)
                .append("   CostingTime: ")
                .append(duration / (60 * 60)).append(":")
                .append(duration / (60) % 60).append(":")
                .append(duration % 60).append(";    ")
                .append("UsedMemory: ").append(usedMemory / (1024 * 1024)).append("MB")
                .toString());
    }

    public static void logSessionCostingMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        LoggingService.info(new StringBuilder("UsedMemory: ")
                .append(usedMemory / (1024 * 1024)).append("MB")
                .toString());
    }


    public static void logRankedFix(List<FixAction> validFixes) {
        int fix_rank = 0;

        List<FixAction> rankedValidFixes = validFixes.stream().sorted((FixAction action1, FixAction action2) -> Double.compare(action2.getQloseScore(), action1.getQloseScore()))
                .collect(Collectors.toList());
        LoggingService.infoFileOnly("========== Ranked by Qlose Score ============:: " + rankedValidFixes.size(), PLAUSIBLE_LOG);
        fix_rank = 0;
        for (FixAction action : rankedValidFixes) {
            LoggingService.infoFileOnly("Fix rank :: " + fix_rank++ + "; Valid fix ::" + action.getFixId() + "; QloseScore:: " + action.getQloseScore(), PLAUSIBLE_LOG);
        }

        rankedValidFixes = validFixes.stream().sorted((FixAction action1, FixAction action2) -> Double.compare(action2.getLocationScore(), action1.getLocationScore()))
                .collect(Collectors.toList());
        LoggingService.infoFileOnly("========== Ranked by Location Score ============:: " + rankedValidFixes.size(), PLAUSIBLE_LOG);
        fix_rank = 0;
        for (FixAction action : rankedValidFixes) {
            LoggingService.infoFileOnly("Fix rank :: " + fix_rank++ + "; Valid fix ::" + action.getFixId() + "; LocationScore:: " + action.getLocationScore(), PLAUSIBLE_LOG);
        }

        rankedValidFixes = validFixes.stream().sorted((FixAction action1, FixAction action2) -> Double.compare(action2.getPassingImpact(), action1.getPassingImpact()))
                .collect(Collectors.toList());
        LoggingService.infoFileOnly("========== Ranked by PassingImpact Score ============:: " + rankedValidFixes.size(), PLAUSIBLE_LOG);
        fix_rank = 0;
        for (FixAction action : rankedValidFixes) {
            LoggingService.infoFileOnly("Fix rank :: " + fix_rank++ + "; Valid fix ::" + action.getFixId() + "; PassingImpactScore:: " + action.getPassingImpact() + "; diff::" + action.getPassingImpactFLDiff(), PLAUSIBLE_LOG);
        }

        rankedValidFixes = validFixes.stream().sorted((FixAction action1, FixAction action2) -> Double.compare(action2.getPassingImpactF(), action1.getPassingImpactF()))
                .collect(Collectors.toList());
        LoggingService.infoFileOnly("========== Ranked by PassingImpactFirst Score ============:: " + rankedValidFixes.size(), PLAUSIBLE_LOG);
        fix_rank = 0;
        for (FixAction action : rankedValidFixes) {
            LoggingService.infoFileOnly("Fix rank :: " + fix_rank++ + "; Valid fix ::" + action.getFixId() + "; PassingImpactFirstScore:: " + action.getPassingImpactF(), PLAUSIBLE_LOG);
        }
    }
}
