package hk.polyu.comp.jaid.fixer;

import ch.qos.logback.classic.Level;
import hk.polyu.comp.jaid.fixaction.FixAction;
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
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.addFileLogger;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

public class BatchFixValidator {
    protected List<TestRequest> testsForValidation;
    JavaProject project;
    List<FixAction> fixesToBeValidate;
    List<FixAction> validFixes;
    int nbrValid = 0;

    public BatchFixValidator(JavaProject project, List<FixAction> fixesToBeValidate) {
        this.project = project;
        this.fixesToBeValidate = fixesToBeValidate;
        validFixes = new LinkedList<>();
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
                validating(currentBatch, j);
            }
        }

        LoggingService.infoAll("Number of valid fix actions:: " + validFixes.size());
        return validFixes;
    }


    void validating(List<FixAction> currentBatch, int indexInBatch) {
        FixAction fixAction = currentBatch.get(indexInBatch);
        if (shouldLogDebug())
            LoggingService.debug("===== Validating fix " + indexInBatch + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");

        SingleFixValidator singleFixValidator = new SingleFixValidator(project,
                Session.getSession().getConfig().getLogLevel(), 0,
                project.getTimeoutPerTest() * 5, FailureHandling.CONTINUE,
                getTestsForValidation());
        singleFixValidator.validate(currentBatch, indexInBatch);

        if (fixAction.getSuccessfulTestExecutionResultsCount() == getTestsForValidation().size()) {
            LoggingService.infoAll("NO." + ++nbrValid + " valid fix found::" + fixAction.getFixId());
            LoggingService.infoFileOnly(fixAction.toString(), FixerOutput.LogFile.PLAUSIBLE_LOG);

            fixAction.setValid(true);
            validFixes.add(fixAction);
        } else {
            List<TestExecutionResult> failingTest = fixAction.getTestExecutionResults().stream().filter(x -> x != null && !x.wasSuccessful()).collect(Collectors.toList());
            project.moveSensitiveTestForward(failingTest);
        }
    }

    List<TestRequest> getTestsForValidation() {
        if (testsForValidation == null)
            testsForValidation = Stream.concat(project.getValidTestsToRun().stream(), project.getTimeoutTests().stream())
                    .collect(Collectors.toList());
        return testsForValidation;
    }


    public static class SecondBFValidator4ICJ extends BatchFixValidator {
        List<TestRequest> allTestsOfProgram;

        public SecondBFValidator4ICJ(JavaProject project, List<FixAction> fixesToBeValidate) {
            super(project, fixesToBeValidate);
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
