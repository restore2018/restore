package hk.polyu.comp.jaid.fixer;

import ch.qos.logback.classic.Level;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.FixActionBuilder;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.SnippetBuilder;
import hk.polyu.comp.jaid.fixer.config.Config;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.fixer.ranking.AbsRankingCal;
import hk.polyu.comp.jaid.java.ClassToFixPreprocessor;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;
import hk.polyu.comp.jaid.test.TestExecutionResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.*;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.*;
import static hk.polyu.comp.jaid.fixer.ranking.AbsRankingCal.RankingAlgorithm.NoRank;
import static hk.polyu.comp.jaid.util.LogUtil.*;

public class Fixer {
    Config config;
    JavaProject javaProject;

    public void execute() throws Exception {

        // Initialization
        config = Session.getSession().getConfig();
        javaProject = config.getJavaProject();
        ClassToFixPreprocessor preprocessor = new ClassToFixPreprocessor(javaProject, config);
        preprocessor.preprocess();
        javaProject.registerMethodToMonitor(config);
        javaProject.initMethodToMonitor();
        javaProject.compile();

        // Monitoring
        ProgramMonitor programMonitor = new ProgramMonitor(config);
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<StateSnapshot>> stateSnapshots = programMonitor.execute();
        List<TestExecutionResult> testResults = programMonitor.getTestResults();
        if (shouldLogDebug()) {
            for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : stateSnapshots.keySet()) {
                LoggingService.debugFileOnly("Generated snapshots - " + sbflAlgorithm.toString() + " :: "
                        + stateSnapshots.get(sbflAlgorithm).size(), SUSPICIOUS_STATE_SNAPSHOT);
                int rank = 0;
                for (StateSnapshot stateSnapshot : stateSnapshots.get(sbflAlgorithm)) {
                    LoggingService.debugFileOnly(rank++ + ":: " + stateSnapshot.toString(), SUSPICIOUS_STATE_SNAPSHOT);
                }
            }
        }

        // Generate fix action for each suspicious snapshot list
        addFileLogger(CANDIDATE_ID_FOR_SBFL, Level.DEBUG);
        Map<Long, FixAction> allFixes = new LinkedHashMap<>();
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> fixActionMaps = new HashMap<>();
        for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : stateSnapshots.keySet()) {
            //Fix generation
            List<FixAction> fixes = generateFixActions(stateSnapshots.get(sbflAlgorithm)).values().stream()
                    .flatMap(Set::stream).collect(Collectors.toCollection(LinkedList::new));
            List<Long> fixIdList = new ArrayList<>();
            removeAllIllformedFixActions(fixes);//each fix get its id after this
            fixes.sort((o1, o2) -> Double.compare(o2.getStateSnapshot().getSuspiciousness(sbflAlgorithm),
                    o1.getStateSnapshot().getSuspiciousness(sbflAlgorithm)));
            for (FixAction fix : fixes) {
                if (allFixes.keySet().contains(fix.getFixId()))
                    allFixes.get(fix.getFixId()).updateSeedForDuplicated(fix);
                else
                    allFixes.put(fix.getFixId(), fix);
                fixIdList.add(fix.getFixId());
            }
            fixActionMaps.put(sbflAlgorithm, fixIdList);
            LoggingService.infoAll("Generated fixactions - " + sbflAlgorithm.toString() + " ::" + fixIdList.size());
            fixIdList.forEach(x -> LoggingService.debugFileOnly("fixId :: " + x, CANDIDATE_ID_FOR_SBFL));
        }
        removeExtraLogger(CANDIDATE_ID_FOR_SBFL);


        // Validations
        List<FixAction> allFixActions = new LinkedList<>(allFixes.values());
        if (shouldLogDebug()) logFixActionsForDebug(allFixActions);
        List<FixAction> validFixes = validation(allFixActions, fixActionMaps);
        secondValidation(validFixes, fixActionMaps);

        // Rankings
        AbsRankingCal.RankingAlgorithm rankingAlgorithm = config.getExperimentControl().getRankingAlgorithm();
        if (rankingAlgorithm != null && !rankingAlgorithm.equals(NoRank)) {
            Config.BATCH_SIZE = 20;
            AbsRankingCal rankingCal = AbsRankingCal.construct(javaProject, validFixes, testResults, rankingAlgorithm);
            rankingCal.rank();
        } else if (rankingAlgorithm == null) {
            Config.BATCH_SIZE = 20;
            for (AbsRankingCal.RankingAlgorithm rankingAlg : AbsRankingCal.RankingAlgorithm.values()) {
                if (!rankingAlg.equals(NoRank)) {
                    AbsRankingCal rankingCal = AbsRankingCal.construct(javaProject, validFixes, testResults, rankingAlg);
                    rankingCal.rank();
                }
            }
        }
        logRankedFix(validFixes);
    }


    private Map<LineLocation, Set<FixAction>> generateFixActions(List<StateSnapshot> snapshots) {
        // fixme: originally, enableBasicStrategies are not used in comprehensive mode. Is that really what we want?
        SnippetBuilder snippetBuilder = new SnippetBuilder();
        snippetBuilder.enableBasicStrategies();
        snippetBuilder.enableComprehensiveStrategies(Session.getSession().getConfig()
                .getSnippetConstructionStrategy() != Config.SnippetConstructionStrategy.BASIC);

        for (StateSnapshot snapshot : snapshots) {
            snippetBuilder.buildSnippets(snapshot);
        }

        Map<StateSnapshot, Set<Snippet>> snippets = snippetBuilder.getSnippets();

        LoggingService.infoAll("Finish building snippets");
        if (shouldLogDebug()) logSnippetsForDebug(snippets);

        FixActionBuilder fixActionBuilder = new FixActionBuilder(javaProject);
        for (Map.Entry<StateSnapshot, Set<Snippet>> snippetEntry : snippets.entrySet()) {
            StateSnapshot snapshot = snippetEntry.getKey();
            for (Snippet snippet : snippetEntry.getValue()) {
                fixActionBuilder.buildFixActions(snapshot, snippet);
            }
        }
        Map<LineLocation, Set<FixAction>> fixActions = fixActionBuilder.getFixActionMap();

        LoggingService.infoAll("Finish building fixes");
        return fixActions;
    }


    private void removeAllIllformedFixActions(List<FixAction> fixActions) {
        IllformedFixActionsRemover remover = new IllformedFixActionsRemover(javaProject);
        int nbrRemoved = 0, totalRemoved = 0;

        // repeatedly remove all ill-formed fix actions
        do {
            nbrRemoved = remover.removeIllformedFixActions(fixActions);
            totalRemoved += nbrRemoved;

            if (shouldLogDebug()) {
                LoggingService.debugFileOnly("Number of ill-formed fix actions removed: "
                        + nbrRemoved + " in this round, " + totalRemoved + " int total.", COMPILATION_ERRORS);
            }
        } while (nbrRemoved != 0);
    }

    private List<FixAction> validation(List<FixAction> allFixActions, Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> sortedFixActionMaps) {
        // Sort all fix actions for validation by suspicious score. Using AutoFix score as default
        allFixActions.sort((o1, o2) -> Double.compare(o2.getStateSnapshot().getSuspiciousness(), o1.getStateSnapshot().getSuspiciousness()));

        BatchFixValidator batchFixValidator = new BatchFixValidator(javaProject, allFixActions);
        List<FixAction> validFixes = batchFixValidator.validateFixActions();

        // Mapping valid fix action to corresponding fl algorithm
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> validFixesMap = new HashMap<>();
        for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : sortedFixActionMaps.keySet()) {
            List<Long> validFixIds = validFixes.stream().map(FixAction::getFixId).collect(Collectors.toList());
            List<Long> sbflValidFixIds = new ArrayList<>();

            for (Long fix_id:sortedFixActionMaps.get(sbflAlgorithm)){
                if (validFixIds.contains(fix_id)) sbflValidFixIds.add(fix_id);
            }
            validFixesMap.put(sbflAlgorithm, sbflValidFixIds);
        }
        if (shouldLogDebug()) {
            for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : validFixesMap.keySet()) {
                LoggingService.debugFileOnly(sbflAlgorithm + " valid fixes", PLAUSIBLE_LOG);
                int rank = 0;
                for (Long aLong : validFixesMap.get(sbflAlgorithm)) {
                    LoggingService.debugFileOnly(++rank + " :: " + aLong, PLAUSIBLE_LOG);

                }
            }
        }
        return validFixes;
    }

    /**
     * This is used by ICJ only (using all test cases (white-box tests) to check the correctness of valid fixes)
     *
     * @param validFixes
     * @param fixActionMaps
     */
    private void secondValidation(List<FixAction> validFixes, Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> fixActionMaps) {
        if (config.getExperimentControl().isEnableSecondValidation()) {
            BatchFixValidator secondValidator = new BatchFixValidator.SecondBFValidator4ICJ(javaProject, validFixes);
            List<FixAction> secondValidFixes = secondValidator.validateFixActions();

            // Mapping 'correct' fix action to corresponding fl algorithm
            Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> correctFixesMap = new HashMap<>();
            for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : fixActionMaps.keySet()) {
                List<Long> correctFixIds = new ArrayList<>(secondValidFixes.stream().map(FixAction::getFixId).collect(Collectors.toList()));
                Collections.sort(correctFixIds, Comparator.comparingInt(o -> fixActionMaps.get(sbflAlgorithm).indexOf(o)));
                correctFixesMap.put(sbflAlgorithm, correctFixIds);
            }
            if (shouldLogDebug()) {
                for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : correctFixesMap.keySet()) {
                    LoggingService.debugFileOnly(sbflAlgorithm + " valid fixes", SECOND_VALIDATION_LOG);
                    int rank = 0;
                    for (Long aLong : correctFixesMap.get(sbflAlgorithm)) {
                        LoggingService.debugFileOnly(++rank + " :: " + aLong, SECOND_VALIDATION_LOG);

                    }
                }
            }
        }
        removeExtraLogger(SECOND_VALIDATION_LOG);
    }


    public double computeSimilarity(double locationSimilarity, double stateSimilarity) {
        return 2.0 / (1.0 / locationSimilarity + 1.0 / stateSimilarity);
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
