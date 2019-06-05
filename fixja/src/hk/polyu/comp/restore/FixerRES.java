package hk.polyu.comp.restore;

import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.FixActionBuilder;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixer.IllformedFixActionsRemover;
import hk.polyu.comp.jaid.fixer.ProgramMonitor;
import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.fixer.config.Config;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.ClassToFixPreprocessor;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.jdi.debugger.LocationSelector;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.restore.extend_fixaction.SnippetBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.*;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.removeExtraLogger;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;
import static hk.polyu.comp.jaid.util.LogUtil.logFixActionsForDebug;
import static hk.polyu.comp.jaid.util.LogUtil.logSnippetsForDebug;

public class FixerRES {
    Config config;
    JavaProject javaProject;

    static Date FixerBeginTime = new Date();
    private List<Integer> initTemp;
    static List<Integer> topLocationTailored = new ArrayList<Integer>();
    static List<TestExecutionResult> testResults = new ArrayList<TestExecutionResult>();
    public static List<TestExecutionResult> failingTestResults = new ArrayList<TestExecutionResult>();

    public void execute() throws Exception {
        // Initialization
        config = Session.getSession().getConfig();
        javaProject = config.getJavaProject();
        Date timeBeforeMonitor = new Date();
        ClassToFixPreprocessor preprocessor = new ClassToFixPreprocessor(javaProject, config);
        preprocessor.preprocess();
        javaProject.registerMethodToMonitor(config);
        javaProject.initMethodToMonitor();
        javaProject.compile();


        // Monitoring
        ProgramMonitorRES programMonitor = new ProgramMonitorRES(config);

        List<StateSnapshot> stateSnapshotsAll = programMonitor.execute(true).get(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix);

        List<StateSnapshot> stateSnapshots = stateSnapshotsAll.subList(0, Math.min(stateSnapshotsAll.size(), 1500));
        List<TestExecutionResult> testResults = programMonitor.getTestResults();
        FixerRES.testResults = testResults;
        FixerRES.failingTestResults = testResults.stream().filter(x->!x.wasSuccessful()).collect(Collectors.toList());
        LocationSelectorRES locationSelector = programMonitor.getLocationSelector();
        initTemp = getTailorTest(testResults, Application.isOnlyUseFailedTest);
        List<Integer> initFailingAndPartialPassingTestResult = testResults.stream().map(x -> x.wasSuccessful()?1:0).collect(Collectors.toList());
        List<Integer> initTestResult = new ArrayList<>(initFailingAndPartialPassingTestResult);
        List<Integer> initPassingTestResult = javaProject.getValidTestsToRun().subList(initFailingAndPartialPassingTestResult.size(), javaProject.getValidTestsToRun().size()).stream().map(x -> 1).collect(Collectors.toList());
        initTestResult.addAll(initPassingTestResult);



        Map<Long, FixAction> allFixes = new HashMap<>();
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> fixActionMaps = new HashMap<>();
        List<FixAction> fixes = new LinkedList<>(generateFixActions(false,stateSnapshots,new ArrayList<>(),new ArrayList<>()).values().stream()
                .flatMap(Set::stream).collect(Collectors.toList()));
        List<Long> fixIdList = new ArrayList<>();
        removeAllIllformedFixActions(fixes);//each fix get its id after this
        fixes.sort((o1, o2) -> Double.compare(o2.getStateSnapshot().getSuspiciousness(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix),
                o1.getStateSnapshot().getSuspiciousness(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix)));
        for (FixAction fix : fixes) {
            if (allFixes.keySet().contains(fix.getFixId()))
                allFixes.get(fix.getFixId()).updateSeedForDuplicated(fix);
            else
                allFixes.put(fix.getFixId(), fix);
            fixIdList.add(fix.getFixId());
        }
        fixActionMaps.put(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix, fixIdList);
        LoggingService.infoAll("Generated fixactions - " + AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix.toString() + " ::" + fixIdList.size());
        fixIdList.forEach(x -> LoggingService.debugFileOnly("fixId :: " + x, CANDIDATE_ID_FOR_SBFL));
        removeExtraLogger(CANDIDATE_ID_FOR_SBFL);
        Date timeAfterMonitor = new Date();
        Long deltaTimeOfMonitor = timeAfterMonitor.getTime()-timeBeforeMonitor.getTime();
        LoggingService.debugFileOnly("monitor & genrate fix time cost using "+ Application.faultLocatizationMethod+":"+deltaTimeOfMonitor/60.0/1000,LOCATION_SORT_LOG);

        // Validations
        List<FixAction> allFixActions = new LinkedList<>(allFixes.values());
        allFixActions.sort((o1,o2)->Integer.compare(stateSnapshots.indexOf(o1.getStateSnapshot()),stateSnapshots.indexOf(o2.getStateSnapshot())));
        setIsForFaultLocalization(stateSnapshots,allFixActions);

        if (shouldLogDebug()) logFixActionsForDebug(allFixActions);

        List<Integer> locationTailored = new ArrayList<Integer>();
        int sampleRate = 0;
        List<FixAction> validFixs = new ArrayList<>();
        while(locationTailored.size()==0&&sampleRate<10){
            sampleRate++;
            List<StateSnapshot> snapshotSample = getTailoredStateSnapshots(sampleRate,stateSnapshots,locationSelector);
            List<StateSnapshot> snapshotSampleToRemove = getTailoredStateSnapshots(sampleRate-1,stateSnapshots,locationSelector);
            List<FixAction> fixActionInFaultLocalization = allFixActions.stream().filter(x->snapshotSample.contains(x.getStateSnapshot())&&x.isForFaultLocalization()).filter(x->!snapshotSampleToRemove.contains(x.getStateSnapshot())).collect(Collectors.toList());
            LoggingService.debugFileOnly("the "+sampleRate+" process validates"+ fixActionInFaultLocalization.size(),LOCATION_SORT_LOG);
            validFixs = validation(fixActionInFaultLocalization, fixActionMaps,initTestResult,true);
            locationTailored = getTailoredLocationFinal(locationSelector,fixActionInFaultLocalization,"FL",sampleRate);
        }

        topLocationTailored = locationTailored;

        List<StateSnapshot> preciseSnapshot = getTailoredStateSnapsnots(locationSelector,locationTailored,stateSnapshots);
        Map<Long, FixAction> allFixesPrecise = new HashMap<>();
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> fixActionMapsPrecise = new HashMap<>();
        Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> fixActionMapsAll = new HashMap<>();
        List<FixAction> fixesPrecise = new LinkedList<>(generateFixActions(true,preciseSnapshot,locationTailored,getTailoredStateSnapshots(sampleRate,stateSnapshots,locationSelector)).values().stream()
                .flatMap(Set::stream).collect(Collectors.toList()));
        List<Long> fixIdListPrecise = new ArrayList<>();
        removeAllIllformedFixActions(fixesPrecise);//each fix get its id after this
        fixesPrecise.sort((o1, o2) -> Double.compare(o2.getStateSnapshot().getSuspiciousness(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix),
                o1.getStateSnapshot().getSuspiciousness(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix)));
        for (FixAction fix : fixesPrecise) {
            if (allFixesPrecise.keySet().contains(fix.getFixId()))
                allFixesPrecise.get(fix.getFixId()).updateSeedForDuplicated(fix);
            else
                allFixesPrecise.put(fix.getFixId(), fix);
            fixIdListPrecise.add(fix.getFixId());
        }
        fixActionMapsPrecise.put(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix, fixIdListPrecise);
        fixIdList.addAll(fixIdListPrecise);
        fixActionMapsAll.put(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix,fixIdList);
        LoggingService.infoAll("Generated fixactions - " + AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix.toString() + " ::" + fixIdListPrecise.size());
        fixIdListPrecise.forEach(x -> LoggingService.debugFileOnly("fixId :: " + x, CANDIDATE_ID_FOR_SBFL));
        removeExtraLogger(CANDIDATE_ID_FOR_SBFL);


        List<FixAction> allFixActionsPrecise = new LinkedList<>(allFixesPrecise.values());
        allFixActionsPrecise.sort((o1,o2)->Integer.compare(stateSnapshots.indexOf(o1.getStateSnapshot()),stateSnapshots.indexOf(o2.getStateSnapshot())));

        List<StateSnapshot> snapshotSample = getTailoredStateSnapshots(sampleRate,stateSnapshots,locationSelector);
        List<FixAction> fixActionInFaultLocalization = allFixActions.stream().filter(x->snapshotSample.contains(x.getStateSnapshot())&&x.isForFaultLocalization()).collect(Collectors.toList());
        List<FixAction> fixActionInPreciseValidation = allFixActionsPrecise.stream().filter(x->!isShouldRemove(fixActionInFaultLocalization,x)).collect(Collectors.toList());
        fixActionInPreciseValidation = reRankFixAction(fixActionInPreciseValidation,locationTailored);

        validFixs.addAll(validation(fixActionInPreciseValidation, fixActionMapsPrecise,initTestResult));

        LoggingService.debugFileOnly("more fix time cost using "+ Application.faultLocatizationMethod+":"+ BatchFixValidatorRES.timeForMoreFix/60.0/1000,LOCATION_SORT_LOG);
        LoggingService.debugFileOnly("more fix with ifcondition "+ BatchFixValidatorRES.numberForIfcondition,LOCATION_SORT_LOG);
        LoggingService.debugFileOnly("more fix with controlflow "+ BatchFixValidatorRES.numberForControlFlow,LOCATION_SORT_LOG);
        LoggingService.debugFileOnly("more fix with replaceoldexpression "+ BatchFixValidatorRES.numberForReplaceExpression,LOCATION_SORT_LOG);
        LoggingService.debugFileOnly("more fix with basicNumeric "+ BatchFixValidatorRES.numberForBasicNumeric,LOCATION_SORT_LOG);
        LoggingService.debugFileOnly("more fix with basicBoolean "+ BatchFixValidatorRES.numberForBasicBoolean,LOCATION_SORT_LOG);
        LoggingService.debugFileOnly("more fix with basicReference "+ BatchFixValidatorRES.numberForReference,LOCATION_SORT_LOG);

        secondValidation(validFixs, fixActionMapsAll);

        if (shouldLogDebug()) logFixActionsForDebug(fixActionInPreciseValidation);
    }


    private boolean isShouldRemove(List<FixAction> fixActionList, FixAction fix){
        for(FixAction fix_:fixActionList){
            if(fix_.equals(fix)){
                return true;
            }
        }
        return false;
    }

    public List<FixAction> reRankFixAction(List<FixAction> fixActionInPreciseValidation,List<Integer> locationTailored){
        List<FixAction> fixActionInPreciseValidation_susLocation = new ArrayList<FixAction>();
        for(Integer location:locationTailored){
            for(FixAction fa:fixActionInPreciseValidation){
                if(fa.getIsFakeSnapshot()==1.0&&fa.getLocation().getLineNo()==location){
                    fixActionInPreciseValidation_susLocation.add(fa);
                }
            }
        }
        for(Integer location:locationTailored){
            for(FixAction fa:fixActionInPreciseValidation){
                if(fa.getIsFakeSnapshot()==0.0&&fa.getLocation().getLineNo()==location){
                    fixActionInPreciseValidation_susLocation.add(fa);
                }
            }
        }
        return fixActionInPreciseValidation_susLocation;
    }

    private List<StateSnapshot> getTailoredStateSnapsnots(LocationSelectorRES locationSelector, List<Integer> locationTailored,List<StateSnapshot> stateSnapshots){
        List<StateSnapshot> stateSnapshots1 = new ArrayList<StateSnapshot>();
        if(Application.snapshotProcessStep==10 || !Application.isSpectrumBased) {
            return stateSnapshots;
        }

        for(int i=0;i<stateSnapshots.size();i++){
            if(locationTailored.contains(stateSnapshots.get(i).getLocation().getLineNo())){
                stateSnapshots1.add(stateSnapshots.get(i));
            }
        }


        return stateSnapshots1;
    }

    private List<Integer> getTailoredLocationFinal(LocationSelectorRES locationSelector, List<FixAction> allFixActions, String mutationMethod, int processStep){
        List<Integer> returnLocation = new ArrayList<Integer>();
        HashMap<Integer,Double> returnLocationWithSuspicuous = new HashMap<Integer,Double>();
        HashMap<Integer,Double> returnLocationWithSuspicuousMax = new HashMap<Integer,Double>();
        HashMap<Integer,Double> returnLocationWithSuspicuousAverage = new HashMap<Integer,Double>();
        HashMap<Integer,Double> returnLocationWithSuspicuousSum = new HashMap<Integer,Double>();
        if(mutationMethod.equals("FL")){
            LoggingService.debugFileOnly("use fl, find location, process:" + processStep + "0%;",LOCATION_SORT_LOG);
            LoggingService.debugFileOnly("original location size:" + locationSelector.getRelevantLocations().size(),LOCATION_SORT_LOG);
            for (LineLocation str : locationSelector.getRelevantLocations()) {
                int line_ = str.getLineNo();
                double temp = 0;
                int sum = 0;
                List<Double> topSuspiciousFixCandidateList = new ArrayList<>();
                for (int i = 0; i < allFixActions.size(); i++) {
                    if (allFixActions.get(i).getLocation().getLineNo() == line_){
                        sum++;
                        topSuspiciousFixCandidateList.add((double)allFixActions.get(i).getOchiai());
                    }
                }

                Collections.sort(topSuspiciousFixCandidateList,Collections.reverseOrder());

                double max = 0, average = 0, sumAll = 0;

                if(topSuspiciousFixCandidateList.size()!=0){
                    max = topSuspiciousFixCandidateList.get(0);
                }

                int sum2 = 0;
                for(double susTemp:topSuspiciousFixCandidateList){
                    if(susTemp>0){
                        sum2 ++;
                        sumAll = sumAll + susTemp;
                    }
                }
                average = sumAll/sum2;

                if(max>0&&average>0){
                    returnLocationWithSuspicuousMax.put(line_,max);
                    returnLocationWithSuspicuousAverage.put(line_,average);
                    returnLocationWithSuspicuousSum.put(line_,sumAll);

                    LoggingService.debugFileOnly("topNumber:"+topSuspiciousFixCandidateList.size(),LOCATION_SORT_LOG);
                    LoggingService.debugFileOnly("line:" + line_ + ";max:" + max,LOCATION_SORT_LOG);
                    LoggingService.debugFileOnly("line:" + line_ + ";average:" + average,LOCATION_SORT_LOG);
                    LoggingService.debugFileOnly("line:" + line_ + ";sum:" + sumAll,LOCATION_SORT_LOG);
                }

            }

            List<Integer> locationList = new ArrayList<>();
            List<Map.Entry<Integer, Double>> infoIds =
                    new ArrayList<Map.Entry<Integer, Double>>(returnLocationWithSuspicuousMax.entrySet());

            Collections.sort(infoIds, new Comparator<Map.Entry<Integer, Double>>() {
                public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
                    if(o2.getValue().compareTo(o1.getValue())==1){
                        return 1;
                    }else if(o2.getValue().compareTo(o1.getValue())==0){
                        if(returnLocationWithSuspicuousAverage.get(o2.getKey()).compareTo(returnLocationWithSuspicuousAverage.get(o1.getKey()))==1){
                            return 1;
                        }else if(returnLocationWithSuspicuousAverage.get(o2.getKey()).compareTo(returnLocationWithSuspicuousAverage.get(o1.getKey()))==0){
                            if(returnLocationWithSuspicuousSum.get(o2.getKey()).compareTo(returnLocationWithSuspicuousSum.get(o1.getKey()))==1){
                                return 1;
                            }else if(returnLocationWithSuspicuousSum.get(o2.getKey()).compareTo(returnLocationWithSuspicuousSum.get(o1.getKey()))==0){
                                return 0;
                            }else{
                                return -1;
                            }
                        }else{
                            return -1;
                        }
                    }else{
                        return -1;
                    }
                }
            });
            for(Map.Entry<Integer, Double> i:infoIds){
                locationList.add(i.getKey());
            }
            for(int iLocation=0;iLocation<Math.min(5,locationList.size());iLocation++){
                LoggingService.debugFileOnly("Top:" + (iLocation+1) + ";line:" + locationList.get(iLocation),LOCATION_SORT_LOG);
            }

            List<Double> locationValueList = new ArrayList<>();
            for(Map.Entry<Integer, Double> i:infoIds){
                locationValueList.add(i.getValue());
            }
            locationValueList = locationValueList.subList(0,Math.min(5,locationValueList.size()));
            locationList = locationList.subList(0,Math.min(5,locationList.size()));
            if(processStep==1&&locationValueList.size()==5&&locationValueList.get(0)!=1.0&&locationValueList.get(0).equals(locationValueList.get(1))&&locationValueList.get(1).equals(locationValueList.get(2))&&locationValueList.get(2).equals(locationValueList.get(3))&&locationValueList.get(3).equals(locationValueList.get(4)))
                return new ArrayList<>();

            return locationList;
        }
        return returnLocation;
    }

    private void setIsForFaultLocalization(List<StateSnapshot> stateSnapshots,List<FixAction> fixActions){
        Map<StateSnapshot,StateSnapshot> dualStatesnapshot = new HashMap<StateSnapshot,StateSnapshot>();
        for(StateSnapshot sss:stateSnapshots){
            for(StateSnapshot sssDual:stateSnapshots){
                if(sss.getLocation()==sssDual.getLocation()&&sss.getSnapshotExpression()==sssDual.getSnapshotExpression()&&sss.getValue()!=sssDual.getValue()){
                    dualStatesnapshot.put(sss,sssDual);
                    break;
                }
            }
        }

        for(FixAction fa1 : fixActions){
            StateSnapshot s_fa1 = fa1.getStateSnapshot();
            String schema_fa1 = fa1.getSchema();
            Map schemaMap = new HashMap<String,String>();
            schemaMap.put("B","A");
            schemaMap.put("A","B");
            schemaMap.put("D","E");
            schemaMap.put("E","D");
            if(schema_fa1.equals("C")){fa1.setIsForFaultLocalization(true);continue;}
            int noSchema1 = 0;
            int noSchema2 = 0;
            boolean flagEqual = false;
            for(FixAction fa2: fixActions){
                if(fa2.getStateSnapshot()==fa1.getStateSnapshot()&&fa2.getSchema().equals(schema_fa1)){
                    noSchema1++;
                }
                String dsdsd = (String)schemaMap.get(schema_fa1);
                if(fa2.getStateSnapshot()==fa1.getStateSnapshot()&&fa2.getSchema().equals((String)schemaMap.get(schema_fa1))){
                    noSchema2++;
                    if(fa2.isForFaultLocalization()){
                        flagEqual = true;
                    }
                }
            }
            if(noSchema1==noSchema2&&flagEqual){
                continue;
            }
            if(noSchema1>=noSchema2||(dualStatesnapshot.containsKey(fa1.getStateSnapshot())&&dualStatesnapshot.get(fa1.getStateSnapshot()).getOccurrenceInFailing()!=0)){
                fa1.setIsForFaultLocalization(true);
            }
            fa1.setIsForFaultLocalization(true);
        }
    }

    private List<StateSnapshot> getTailoredStateSnapshots(int processStep,List<StateSnapshot> stateSnapshots,LocationSelectorRES locationSelector){

        List<StateSnapshot> stateSnapshots1 = new ArrayList<StateSnapshot>() ;

        //if(stateSnapshots.size()<100 || processStep==10){
        if(processStep==10){
            return stateSnapshots;
        }

        if(processStep==0){
            return stateSnapshots1;
        }

        int thresholdNum = processStep*stateSnapshots.size()/10/locationSelector.getRelevantLocations().size();

        for (LineLocation lineNo : locationSelector.getRelevantLocations()) {
            int line = lineNo.getLineNo();
            int cal_1 = 0;
            for(int i=0;i<stateSnapshots.size();i++){
                if(line==stateSnapshots.get(i).getLocation().getLineNo()){
                    cal_1 ++;
                    stateSnapshots1.add(stateSnapshots.get(i));
                }
                if(cal_1>thresholdNum){
                    break;
                }
            }
        }
        return stateSnapshots1;
    }

    private List<Integer> getTailorTest(List<TestExecutionResult> testResults, boolean isOnlyUseFailedTest){
        List<Integer> initTemp = new ArrayList<Integer>();
        initTemp = testResults.stream().map(x->x.wasSuccessful()?1:0).collect(Collectors.toList());
        ArrayList<Integer> temp1 = new ArrayList<Integer>();
        int sum1 =0;
        if(initTemp.size()>=50){
            for(int i=0;i<initTemp.size();i++){
                if(initTemp.get(i)==0){
                    temp1.add(0);
                    sum1++;
                }
            }
            for(int i=0;i<initTemp.size();i++){
                if(isOnlyUseFailedTest){break;}
                if(initTemp.get(i)==1 && sum1<50){
                    sum1++;
                    temp1.add(1);
                }
            }
            initTemp = temp1;
        }else if(isOnlyUseFailedTest){
            for(int i=0;i<initTemp.size();i++){
                if(initTemp.get(i)==0){
                    temp1.add(0);
                    sum1++;
                }
            }
            initTemp = temp1;
        }
        return initTemp;
    }


    private Map<LineLocation, Set<FixAction>> generateFixActions(boolean isForMoreFix,List<StateSnapshot> snapshots,List<Integer> locationTailored,List<StateSnapshot> snapshotSample) throws Exception {
        // fixme: originally, enableBasicStrategies are not used in comprehensive mode. Is that really what we want?
        SnippetBuilder snippetBuilder = new SnippetBuilder();
        if(isForMoreFix) {
            snippetBuilder.enableBasicStrategiesForMoreFix(locationTailored, snapshotSample);
        }else{
            snippetBuilder.enableBasicStrategies();
        }
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

    private List<FixAction> validation(List<FixAction> allFixActions, Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> fixActionMaps,List<Integer> initTestResult) {
        // Sort all fix actions for validation by suspicious score. Using AutoFix score as default
        //allFixActions.sort((o1, o2) -> Double.compare(o2.getStateSnapshot().getSuspiciousness(), o1.getStateSnapshot().getSuspiciousness()));
        if(Application.isRestore){
            BatchFixValidatorRES.FixValidatorStartTime = new Date();
        }
        BatchFixValidatorRES batchFixValidatorRES = new BatchFixValidatorRES(javaProject, allFixActions,false,initTestResult,initTemp);
        List<FixAction> validFixes = batchFixValidatorRES.validateFixActions();

        LoggingService.infoFileOnly("---- data for top 1,3,5 ----",FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
        LoggingService.infoFileOnly("=====total fix number: " + BatchFixValidatorRES.numFirstValid + "/"+") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
        LoggingService.infoFileOnly("=====total time: "+ BatchFixValidatorRES.timeTotal/60.0/1000+"; ==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);

        LoggingService.infoFileOnly("=====top 1 fix number: " + BatchFixValidatorRES.numFirstValid_top1 + "/"+") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
        LoggingService.infoFileOnly("=====top 1 time: "+ BatchFixValidatorRES.timeTop1/60.0/1000+"; ==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);

        LoggingService.infoFileOnly("=====top 3 fix number: " + BatchFixValidatorRES.numFirstValid_top3 + "/"+") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
        LoggingService.infoFileOnly("=====top 3 time: "+ BatchFixValidatorRES.timeTop3/60.0/1000+"; ==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);

        LoggingService.infoFileOnly("=====top 5 fix number: " + BatchFixValidatorRES.numFirstValid_top5 + "/"+") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
        LoggingService.infoFileOnly("=====top 5 time: "+ BatchFixValidatorRES.timeTop5/60.0/1000+"; ==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);


        return validFixes;
    }

    private List<FixAction> validation(List<FixAction> allFixActions, Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, List<Long>> fixActionMaps,List<Integer> initTestResult,boolean isRespective) {
        // Sort all fix actions for validation by suspicious score. Using AutoFix score as default
        //allFixActions.sort((o1, o2) -> Double.compare(o2.getStateSnapshot().getSuspiciousness(), o1.getStateSnapshot().getSuspiciousness()));

        BatchFixValidatorRES batchFixValidatorRES = new BatchFixValidatorRES(javaProject, allFixActions,isRespective,initTestResult,initTemp);
        List<FixAction> validFixes = batchFixValidatorRES.validateFixActions();


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
            BatchFixValidatorRES secondValidator = new BatchFixValidatorRES.SecondBFValidatorRES4ICJ(javaProject, validFixes);
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
