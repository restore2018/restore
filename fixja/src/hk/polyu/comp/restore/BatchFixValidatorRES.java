package hk.polyu.comp.restore;

import ch.qos.logback.classic.Level;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixer.*;
import hk.polyu.comp.jaid.fixer.config.FailureHandling;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.java.MutableDiagnosticCollector;
import hk.polyu.comp.jaid.java.ProjectCompiler;
import hk.polyu.comp.jaid.test.TestCollector;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.TestRequest;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.addFileLogger;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

public class BatchFixValidatorRES {
    protected List<TestRequest> testsForValidation;
    JavaProject project;
    List<FixAction> fixesToBeValidate;
    List<FixAction> validFixes;
    int nbrValid = 0;
    boolean isRespective;
    List<Integer> initTestResult;
    List<Integer> initTemp;
    static Date FixValidatorStartTime = new Date();
    static long timeTop1 = 0;
    static long timeTop3 = 0;
    static long timeTop5 = 0;
    static long timeTotal = 0;

    static int numFirstValid = 0;
    static int numFirstValid_top1 = 0;
    static int numFirstValid_top3 = 0;
    static int numFirstValid_top5 = 0;
    public static long timeForMoreFix=0;
    public static int numberForIfcondition=0;
    public static int numberForControlFlow=0;
    public static int numberForReplaceExpression=0;
    public static int numberForBasicNumeric=0;
    public static int numberForBasicBoolean=0;
    public static int numberForReference=0;

    public BatchFixValidatorRES(JavaProject project, List<FixAction> fixesToBeValidate, boolean isRespective, List<Integer> initTestResult, List<Integer> initTemp) {
        this.project = project;
        this.fixesToBeValidate = fixesToBeValidate;
        validFixes = new LinkedList<>();

        this.isRespective = isRespective;
        this.initTestResult = initTestResult;
        this.initTemp = initTemp;
    }

    public List<FixAction> validateFixActions() {
        int originalAmount = fixesToBeValidate.size();
        int batchSize = BatchFixInstrumentor.getTotalBatchSize();
        int nbrBatches = (fixesToBeValidate.size() + batchSize - 1) / batchSize;

        LoggingService.infoAll("Number of fix actions to validate:: " + fixesToBeValidate.size());

        for (int i = 0; i < nbrBatches; i++) {
            int startIndex = i * batchSize;
            int endIndex = startIndex + batchSize > fixesToBeValidate.size() ? fixesToBeValidate.size() : startIndex + batchSize;
            List<FixAction> currentBatch = fixesToBeValidate.subList(startIndex, endIndex);

            // instrument all fixes
            BatchFixInstrumentor instrumentor = new BatchFixInstrumentor(project);
            instrumentor.instrument(currentBatch);

            // Recompile only the class-to-fix, but not other classes or tests.
            ProjectCompiler compiler = new ProjectCompiler(project);
            compiler.compileFixCandidatesInBatch();
            MutableDiagnosticCollector<JavaFileObject> diagnostics = compiler.getSharedDiagnostics();
            if (!diagnostics.getDiagnostics().isEmpty()) {
                boolean hasError = false;
                for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                    if (shouldLogDebug()) LoggingService.debugAll(diagnostic.toString());
                    if (diagnostic.getKind().equals(Diagnostic.Kind.ERROR)) hasError = true;
                }
                if (hasError) throw new IllegalStateException();
            }

            // Validating fix one by one
            for (int j = 0; j < currentBatch.size(); j++) {
                if(isRespective){
                    numFirstValid++;
                    validating(currentBatch, j,initTestResult);
                }else {
                    numFirstValid++;
                    validating(currentBatch, j);
                }

            }
        }

        LoggingService.infoAll("Number of valid fix actions:: " + validFixes.size());
        return validFixes;
    }


    void validating(List<FixAction> currentBatch, int indexInBatch) {
        FixAction fixAction = currentBatch.get(indexInBatch);
        if (shouldLogDebug())
            LoggingService.debug("===== Validating fix " + indexInBatch + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");
            LoggingService.debug("===== Validating fix " + numFirstValid + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");
            LoggingService.debug("===== Validating tfc "+ (new Date().getTime()- FixerRES.FixerBeginTime.getTime())/60.0/1000+"; ==========");

        SingleFixValidator singleFixValidator = new SingleFixValidator(project,
                Session.getSession().getConfig().getLogLevel(), 0,
                project.getTimeoutPerTest() * 5, FailureHandling.CONTINUE,
                getTestsForValidation());
        Date beforeTime = new Date();
        singleFixValidator.validate(currentBatch, indexInBatch);
        Long deltaTime = new Date().getTime()-beforeTime.getTime();

        if(Application.isRestore){
            if(timeTotal==0){
                timeTotal = FixValidatorStartTime.getTime()- FixerRES.FixerBeginTime.getTime();
            }
            timeTotal += deltaTime;
        }

        if(fixAction.getSeed().stream().anyMatch(x->x.contains("Strategy4ReplaceOldStmtForMoreFix")||x.contains("BasicStrategy4ReferenceForMoreFix")||x.contains("Strategy4ControlFlowForMoreFix")||x.contains("BasicStrategy4NumericForMoreFix")||x.contains("BasicStrategy4BooleanForMoreFix")||x.contains("Strategy4IfConditionForMoreFix"))){
            timeForMoreFix += deltaTime;
        }
        if(fixAction.getSeed().stream().anyMatch(x->x.contains("Strategy4IfConditionForMoreFix"))){
            numberForIfcondition ++;
        }
        if(fixAction.getSeed().stream().anyMatch(x->x.contains("Strategy4ControlFlowForMoreFix"))){
            numberForControlFlow ++;
        }
        if(fixAction.getSeed().stream().anyMatch(x->x.contains("Strategy4ReplaceOldStmtForMoreFix"))){
            numberForReplaceExpression ++;
        }
        if(fixAction.getSeed().stream().anyMatch(x->x.contains("BasicStrategy4BooleanForMoreFix"))){
            numberForBasicBoolean ++;
        }
        if(fixAction.getSeed().stream().anyMatch(x->x.contains("BasicStrategy4NumericForMoreFix"))){
            numberForBasicNumeric ++;
        }
        if(fixAction.getSeed().stream().anyMatch(x->x.contains("BasicStrategy4ReferenceForMoreFix"))){
            numberForReference ++;
        }
        if(Application.isRestore && isWithinTopX(FixerRES.topLocationTailored,1,fixAction.getStateSnapshot().getLocation().getLineNo())){
            if(numFirstValid_top1==0){
                numFirstValid_top1 = numFirstValid;
                timeTop1 = FixValidatorStartTime.getTime()- FixerRES.FixerBeginTime.getTime();
            }
            numFirstValid_top1++;
            timeTop1 += deltaTime;
        }
        if(Application.isRestore && isWithinTopX(FixerRES.topLocationTailored,3,fixAction.getStateSnapshot().getLocation().getLineNo())){
            if(numFirstValid_top3==0){
                numFirstValid_top3 = numFirstValid;
                timeTop3 = FixValidatorStartTime.getTime()- FixerRES.FixerBeginTime.getTime();
            }
            numFirstValid_top3++;
            timeTop3 += deltaTime;
        }
        if(Application.isRestore && isWithinTopX(FixerRES.topLocationTailored,5,fixAction.getStateSnapshot().getLocation().getLineNo())){
            if(numFirstValid_top5==0){
                numFirstValid_top5 = numFirstValid;
                timeTop5 = FixValidatorStartTime.getTime()- FixerRES.FixerBeginTime.getTime();
            }
            numFirstValid_top5++;
            timeTop5 += deltaTime;
        }



        if (fixAction.getSuccessfulTestExecutionResultsCount() == getTestsForValidation().size()) {
            LoggingService.infoAll("NO." + ++nbrValid + " valid fix found::" + fixAction.getFixId());
            LoggingService.infoFileOnly(fixAction.toString(), FixerOutput.LogFile.PLAUSIBLE_LOG);

            fixAction.setValid(true);
            validFixes.add(fixAction);

            LoggingService.infoFileOnly("=====Validating fix " + numFirstValid + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
            LoggingService.infoFileOnly("=====Validating tfc "+ (new Date().getTime()- FixerRES.FixerBeginTime.getTime())/60.0/1000+"; ==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);

            if(Application.isRestore && isWithinTopX(FixerRES.topLocationTailored,1,fixAction.getStateSnapshot().getLocation().getLineNo())){
                LoggingService.infoFileOnly("===== Top 1 Validating fix " + numFirstValid_top1 + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
                LoggingService.infoFileOnly("===== Top 1 Validating tfc "+ timeTop1/60.0/1000+"==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
            }
            if(Application.isRestore && isWithinTopX(FixerRES.topLocationTailored,3,fixAction.getStateSnapshot().getLocation().getLineNo())){
                LoggingService.infoFileOnly("===== Top 3 Validating fix " + numFirstValid_top3 + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
                LoggingService.infoFileOnly("===== Top 3 Validating tfc "+ timeTop3/60.0/1000+"==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
            }
            if(Application.isRestore && isWithinTopX(FixerRES.topLocationTailored,5,fixAction.getStateSnapshot().getLocation().getLineNo())){
                LoggingService.infoFileOnly("===== Top 5 Validating fix " + numFirstValid_top5 + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
                LoggingService.infoFileOnly("===== Top 5 Validating tfc "+ timeTop5/60.0/1000+"==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
            }

        }
    }

    void validating(List<FixAction> currentBatch, int indexInBatch,List<Integer> initTestResult) {
        FixAction fixAction = currentBatch.get(indexInBatch);
        if (!fixAction.isForFaultLocalization()) {
            fixAction.setOchiai(0);
            numFirstValid--;
            return;
        }
        if (shouldLogDebug())
            LoggingService.debug("===== Validating fix " + indexInBatch + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");
            LoggingService.debug("===== Validating fix " + numFirstValid + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");
            LoggingService.debug("===== Validating tfc "+ (new Date().getTime()- FixerRES.FixerBeginTime.getTime())/60.0/1000+"==========");

        if (!fixAction.wasValidated()) {
            int maxFailingTests = (Application.isOnlyUseFailedTest || SingleFixValidator.isKillNum > 5) ? initTestResult.stream().filter(x -> x == 0).collect(Collectors.toList()).size() - 1 : Math.min(49, initTestResult.size() - 1);

            SingleFixValidator singleFixValidator = new SingleFixValidator(project,
                    Session.getSession().getConfig().getLogLevel(), maxFailingTests,
                    project.getTimeoutPerTest() * 5, FailureHandling.CONTINUE,
                    getTestsForValidation(initTestResult,true,false));
            singleFixValidator.validate(currentBatch, indexInBatch);
            fixAction.setValidated(true);
        }
        fixAction.setOchiai(getOchiai(initTemp.size(), initTemp, fixAction.getSuccessAndFailTestExecutionResults(),true,FixerRES.testResults,fixAction.getTestExecutionResults()));

        if(fixAction.getOchiai()==1.0){
            int aaa = 33;
        }

        if (fixAction.getSuccessfulTestExecutionResultsCount() == fixAction.getTestExecutionResults().size()) {
            fixAction.setValid(true);
            fixAction.serPartialSuccessAndFailTestExecutionResults();
        }
        if (fixAction.isValid()) {
            SingleFixValidator singleFixValidator = new SingleFixValidator(project,
                    Session.getSession().getConfig().getLogLevel(), 0,
                    project.getTimeoutPerTest() * 5, FailureHandling.CONTINUE,
                    getTestsForValidation(initTestResult,true,true));
            singleFixValidator.validate(currentBatch, indexInBatch);
            fixAction.setValidated(true);

            if (fixAction.getTestExecutionResults().stream().filter(x -> x != null && x.wasSuccessful()).collect(Collectors.toList()).size() == project.getValidTestsToRun().size() - initTestResult.stream().filter(x -> x == 0).count()) {
                LoggingService.infoAll("NO." + ++nbrValid + " valid fix found::" + fixAction.getFixId());
                LoggingService.infoAll("within fault localization stage," + fixAction.getFixId());
                LoggingService.infoFileOnly(fixAction.toString(), FixerOutput.LogFile.PLAUSIBLE_LOG);
                fixAction.setValid(true);

                LoggingService.infoFileOnly("=====Validating fix " + numFirstValid + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);
                LoggingService.infoFileOnly("=====Validating tfc "+ (new Date().getTime()- FixerRES.FixerBeginTime.getTime())/60.0/1000+"; ==========", FixerOutput.LogFile.RANDOM_LOCATION_SORT_LOG);

            } else {
                fixAction.setValid(false);
                LoggingService.infoFileOnly(fixAction.toString()+" not pass all test.", FixerOutput.LogFile.PLAUSIBLE_LOG);
            }
        }
    }

    boolean isWithinTopX(List<Integer> topList, int x, int lineNo){
        List<Integer> tempTopList = topList.subList(0,Math.min(topList.size(),x));
        if(tempTopList.contains(lineNo)){
            return true;
        }
        return false;
    }

    List<TestRequest> getTestsForValidation() {
        if (testsForValidation == null)
            testsForValidation = Stream.concat(project.getValidTestsToRun().stream(), project.getTimeoutTests().stream())
                    .collect(Collectors.toList());
        return testsForValidation;
    }

    List<TestRequest> getTestsForValidation(List<Integer> initTestResultInFormOfNumber,boolean isOnlyUseFailedTest,boolean wasValidated) {
        initTestResultInFormOfNumber = adjustInitTestResultInFormOfNumber(initTestResultInFormOfNumber);
        if (testsForValidation == null)
            testsForValidation = Stream.concat(project.getValidTestsToRun().stream(), project.getTimeoutTests().stream())
                    .collect(Collectors.toList());

        if(!isOnlyUseFailedTest){
            return testsForValidation;
        }

        if(!wasValidated){
            List<TestRequest> tempTest = new ArrayList<TestRequest>();
            for(int i=0;i<initTestResultInFormOfNumber.size();i++){
                if(initTestResultInFormOfNumber.get(i)==0){
                    tempTest.add(testsForValidation.get(i));
                }
            }
            return tempTest;
        }else {
            List<TestRequest> tempTest = new ArrayList<TestRequest>();
            for(int i=0;i<initTestResultInFormOfNumber.size();i++){
                if(initTestResultInFormOfNumber.get(i)==1){
                    tempTest.add(testsForValidation.get(i));
                }
            }
            return tempTest;
        }

    }

    private float getOchiai(int caseNumber,List<Integer> init,List<Integer> repair,boolean isPassAllTest,List<TestExecutionResult> originalTestResult,List<TestExecutionResult> CurrentTestResult){
        float temp = 0;
        int totalFail = 0, failed = 0, passed = 0;

        int failSize = init.size()-repair.size();
        if(failSize>0){
            for(int k=0;k<failSize;k++){
                repair.add(0);
            }
        }

        for (int i=0;i<caseNumber;i++){
            if(init.get(i)==0){
                totalFail++;
                if(repair.get(i)==1){
                    failed++;
                }
            }else{
                if(repair.get(i)==0){
                    passed++;
                }
            }
        }
        if(!isPassAllTest){passed++;}
        if(totalFail==0||failed==0){return 0;}
        else{
            temp = (float) (failed*1.0/Math.sqrt(totalFail*(failed+passed)));
            return temp;
        }
    }

    public List<Integer> adjustInitTestResultInFormOfNumber(List<Integer> initTestResultInFormOfNumber){
        List<Integer> resultedTestResult = new ArrayList<Integer>();
        for(TestRequest tr:getTestsForValidation()){
            for(TestExecutionResult ter:FixerRES.testResults){
                if(tr.getTestClass().equals(ter.getTestClass())&&tr.getTestMethod().equals(ter.getTestMethod())){
                    resultedTestResult.add(ter.wasSuccessful()?1:0);
                    break;
                }
            }
        }
        int passingTestNumber = initTestResultInFormOfNumber.size()-resultedTestResult.size();
        if(passingTestNumber>0){
            //LoggingService.infoFileOnly("tests number should be checked", FixerOutput.LogFile.PLAUSIBLE_LOG);
            for(int i=0;i<passingTestNumber;i++){
                resultedTestResult.add(1);
            }
        }
        return resultedTestResult;
    }

    public static class SecondBFValidatorRES4ICJ extends BatchFixValidatorRES {
        List<TestRequest> allTestsOfProgram;

        public SecondBFValidatorRES4ICJ(JavaProject project, List<FixAction> fixesToBeValidate) {
            super(project, fixesToBeValidate,false,null,null);
            this.allTestsOfProgram = getTestsForValidation();
            addFileLogger(FixerOutput.LogFile.SECOND_VALIDATION_LOG, Level.DEBUG);
        }

        @Override
        List<TestRequest> getTestsForValidation() {
            if (allTestsOfProgram == null) {
                TestCollector collector = new TestCollector();
                return collector.getAllTests(project);
            } else
                return allTestsOfProgram;

        }

        void validating(List<FixAction> currentBatch, int indexInBatch) {
            FixAction fixAction = currentBatch.get(indexInBatch);
            if (shouldLogDebug())
                LoggingService.debug("===== Validating fix " + indexInBatch + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");

            SingleFixValidator singleFixValidator = new SingleFixValidator(project,
                    Session.getSession().getConfig().getLogLevel(), 0,
                    project.getTimeoutPerTest() * 5, FailureHandling.CONTINUE,
                    getTestsForValidation());
            singleFixValidator.secondValidate(currentBatch, indexInBatch);

            if (fixAction.getSecondValidationResults().stream().filter(x -> x != null && x.wasSuccessful())
                    .count() == getTestsForValidation().size()) {
                LoggingService.infoAll("SecondValidation NO." + ++nbrValid + " valid fix found::" + fixAction.getFixId());
                LoggingService.infoFileOnly(fixAction.toString(), FixerOutput.LogFile.SECOND_VALIDATION_LOG);

                fixAction.setValid(true);
                validFixes.add(fixAction);
            } else {
                List<TestExecutionResult> failingTest = fixAction.getTestExecutionResults().stream().filter(x -> x != null && !x.wasSuccessful()).collect(Collectors.toList());
                project.moveSensitiveTestForward(failingTest);
            }
        }
    }
}
