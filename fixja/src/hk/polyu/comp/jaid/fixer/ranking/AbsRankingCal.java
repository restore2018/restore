package hk.polyu.comp.jaid.fixer.ranking;

import hk.polyu.comp.jaid.ast.MethodUtil;
import hk.polyu.comp.jaid.ast.TypeCollector;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.FixedMethodNameFormatter;
import hk.polyu.comp.jaid.fixer.BatchFixInstrumentor;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.ClassToFixPreprocessor;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.java.MutableDiagnosticCollector;
import hk.polyu.comp.jaid.java.ProjectCompiler;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import org.eclipse.jdt.core.dom.*;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

public abstract class AbsRankingCal {
    public enum RankingAlgorithm {
        NoRank, AutoFix, Qlose;

        public static RankingAlgorithm get(String algorithmStr) {
            if (algorithmStr.equals(AutoFix.name())) return AutoFix;
            else if (algorithmStr.equals(Qlose.name())) return Qlose;
            else return NoRank;
        }
    }

    JavaProject project;
    List<FixAction> validFixes;
    List<TestExecutionResult> originalTestResults;

    public AbsRankingCal(JavaProject project, List<FixAction> validFixes, List<TestExecutionResult> testResults) {
        this.project = project;
        this.validFixes = validFixes;
        this.originalTestResults = testResults;
    }

    public static AbsRankingCal construct(JavaProject project, List<FixAction> validFixes, List<TestExecutionResult> testResults, RankingAlgorithm rankingAlgorithm) {
        LoggingService.infoAll("rankingAlgorithm ::" + rankingAlgorithm.toString());
        switch (rankingAlgorithm) {
            case Qlose:
                return new QloseRankingCal(project, validFixes, testResults);
            case AutoFix:
                return new JaidRankingCal(project, validFixes, testResults);
        }
        throw new IllegalArgumentException("There is no Ranking algorithm name: " + rankingAlgorithm);
    }

    abstract public void rank();


    public void dynamicRankFixActions() {
        int batchSize = BatchFixInstrumentor.getTotalBatchSize();
        int nbrBatches = (validFixes.size() + batchSize - 1) / batchSize;

        LoggingService.infoAll("Number of fix actions to rank:: " + validFixes.size());

        FixedMethodNameFormatter methodNameFormatter = new FixedMethodNameFormatter();

        for (int i = 0; i < nbrBatches; i++) {
            int startIndex = i * batchSize;
            int endIndex = startIndex + batchSize > validFixes.size() ? validFixes.size() : startIndex + batchSize;
            List<FixAction> currentBatch = validFixes.subList(startIndex, endIndex);

            // instrument all fixes
            BatchFixInstrumentor instrumentor = new BatchFixInstrumentor(project);
            instrumentor.instrument(currentBatch);

            // Recompile
            ProjectCompiler compiler = new ProjectCompiler(project);
            compiler.compileFixCandidatesInBatch();
            MutableDiagnosticCollector<JavaFileObject> diagnostics = compiler.getSharedDiagnostics();
            if (!diagnostics.getDiagnostics().isEmpty() && diagnostics.getDiagnostics().stream().anyMatch(x -> x.getKind().equals(Diagnostic.Kind.ERROR)))
                throw new IllegalStateException();

            String[] files = new String[]{project.getSourceFileWithAllFixes().toString()};
            String[] encodes = new String[]{StandardCharsets.UTF_8.name()};
            TypeCollector collector = new TypeCollector();
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setResolveBindings(false);
            parser.setBindingsRecovery(false);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.createASTs(files, encodes, new String[]{}, collector.getASTRequestor(), null);
            Map<String, AbstractTypeDeclaration> topLevelTypesByFQNames = collector.getTypes();
            AbstractTypeDeclaration typeDeclaration = topLevelTypesByFQNames.get(project.getMethodToMonitor().getFullQualifiedClassName());


            // Rank each fixaction
            for (int j = 0; j < currentBatch.size(); j++) {
                FixAction fixAction = currentBatch.get(j);
                fixAction.clearDebugTestResults();

                if (shouldLogDebug()) {
                    LoggingService.debug("===== Ranking fix " + j + "/" + currentBatch.size() + " (ID:: " + fixAction.getFixId() + ") =====");
                }

                String newMethodName = methodNameFormatter.getFixedMethodName(project.getMethodToMonitor().getMethodAST().getName().getIdentifier(), j);
                MethodDeclaration methodDeclaration = MethodUtil.getMethodDeclarationByName(typeDeclaration, newMethodName);

                Statement exitStatement = (Statement) ((TryStatement) methodDeclaration.getBody().statements().get(ClassToFixPreprocessor.getTryStatementIndex())).getFinally().statements().get(1);
                int exitLineNo = ((CompilationUnit) exitStatement.getRoot()).getLineNumber(exitStatement.getStartPosition());
                fixAction.setExitLineNo(exitLineNo);

                Statement entryStatement = (Statement) methodDeclaration.getBody().statements().get(ClassToFixPreprocessor.getEntryStatementIndex());
                int entryLineNo = ((CompilationUnit) entryStatement.getRoot()).getLineNumber(entryStatement.getStartPosition());
                fixAction.setEntryLineNo(entryLineNo);

                launchingAFix(currentBatch, fixAction, j);
            }
            afterOneBatch(currentBatch);
        }

    }

    abstract public void launchingAFix(List<FixAction> currentBatch, FixAction fixAction, int currentIdx);

    abstract public void afterOneBatch(List<FixAction> currentBatch);

    abstract public void scoreCalculation();

}
