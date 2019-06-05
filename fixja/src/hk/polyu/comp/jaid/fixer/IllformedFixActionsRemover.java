package hk.polyu.comp.jaid.fixer;

import hk.polyu.comp.jaid.ast.TypeCollector;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.FixedMethodNameFormatter;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.java.MutableDiagnosticCollector;
import hk.polyu.comp.jaid.java.ProjectCompiler;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import org.eclipse.jdt.core.dom.*;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

/**
 * Created by Max PEI.
 */
public class IllformedFixActionsRemover {

    private final JavaProject project;

    public IllformedFixActionsRemover(JavaProject javaProject){
        this.project = javaProject;
    }

    public JavaProject getProject() {
        return project;
    }

    /**
     * Remove ill-formed fix actions from 'fixActionList'.
     * Return the number of fix actions removed.
     *
     * @param fixActionList
     * @return
     */
    public int removeIllformedFixActions(List<FixAction> fixActionList){
        int originalAmount = fixActionList.size();
        int batchSize = BatchFixInstrumentor.getTotalBatchSize();
        int nbrBatches = (originalAmount + batchSize - 1) / batchSize;

        // marking
        for(int i = 0; i < nbrBatches; i++) {
            int startIndex = i * batchSize;
            int endIndex = startIndex + batchSize > originalAmount ? originalAmount : startIndex + batchSize;
            List<FixAction> currentBatch = fixActionList.subList(startIndex, endIndex);
            markIllformedFixActions(currentBatch);
        }

        // removing
        Iterator<FixAction> iterator = fixActionList.iterator();
        while(iterator.hasNext()){
            FixAction fixAction = iterator.next();
            if(!fixAction.isWellformed()){
                iterator.remove();

                if(shouldLogDebug()){
                    LoggingService.debugFileOnly("Illformed fix action #" + fixAction.getFixId(), FixerOutput.LogFile.COMPILATION_ERRORS);
                    LoggingService.debugFileOnly(fixAction.toString(), FixerOutput.LogFile.COMPILATION_ERRORS);
                }
            }
        }

        int currentAmount = fixActionList.size();
        return originalAmount - currentAmount;
    }

    private void markIllformedFixActions(List<FixAction> fixActions) {
        // instrument all fixes
        BatchFixInstrumentor instrumentor = new BatchFixInstrumentor(getProject(),true);
        instrumentor.instrument(fixActions);

        // get compilation errors
        ProjectCompiler compiler = new ProjectCompiler(getProject());
        compiler.compileFixCandidatesInBatch();
        MutableDiagnosticCollector<JavaFileObject> diagnostics = compiler.getSharedDiagnostics();
        if(shouldLogDebug()){
            for(Diagnostic diagnostic: diagnostics.getDiagnostics()) {
                LoggingService.debugFileOnly("Illformed fix action: ", FixerOutput.LogFile.COMPILATION_ERRORS);
                LoggingService.debugFileOnly(diagnostic.toString(), FixerOutput.LogFile.COMPILATION_ERRORS);
            }
        }

        Set<Integer> errorLineNumbers = getErrorLineNumbers(diagnostics);

        // get fix ranges
        Map<String, AbstractTypeDeclaration> typeDeclarationMap = loadASTFromBatchFixFile();
        Map<Integer, LineRange> fixIndexToRangeMap = getFixIndexToRangeMap(typeDeclarationMap);

        // start marking
        Set<Integer> matchedErrorLineNumbers = new HashSet<>();
        for (int fixIndex : fixIndexToRangeMap.keySet()) {
            LineRange range = fixIndexToRangeMap.get(fixIndex);
            for (int lineNo = range.beginLine; lineNo <= range.endLine; lineNo++) {
                if (errorLineNumbers.contains(lineNo)) {
                    matchedErrorLineNumbers.add(lineNo);
                    fixActions.get(fixIndex).setWellformed(false);
                }
            }
        }
        if (matchedErrorLineNumbers.size() != errorLineNumbers.size()) {
            LoggingService.error("some git errors have no matching fix!");
            for (int lineNo : matchedErrorLineNumbers)
                LoggingService.error("Error Line No: " + lineNo);
        }
    }

    private Set<Integer> getErrorLineNumbers(MutableDiagnosticCollector<JavaFileObject> diagnostics){
        Set<Integer> errorLineNumbers = new HashSet<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                int errorLineNumber = (int) diagnostic.getLineNumber();
                errorLineNumbers.add(errorLineNumber);
            }
        }
        return errorLineNumbers;
    }

    private Map<Integer, LineRange> getFixIndexToRangeMap(Map<String, AbstractTypeDeclaration> typeDeclarationMap) {

        MethodToMonitor mtm = project.getMethodToMonitor();
        // fixme: check correct class name
        String ctf = mtm.getFullQualifiedClassName();
        AbstractTypeDeclaration typeDeclaration = typeDeclarationMap.get(ctf);

        final String originalMethodName = mtm.getSimpleName();
        final FixedMethodNameFormatter methodNameFormatter = new FixedMethodNameFormatter();
        final CompilationUnit cu = (CompilationUnit) typeDeclaration.getRoot();
        final Map<Integer, LineRange> fixIndexToRangeMap = new HashMap<>();

        typeDeclaration.accept(new ASTVisitor() {
            public boolean visit(MethodDeclaration node) {
                String methodName = node.getName().getIdentifier();
                int fixIndex = methodNameFormatter.getFixActionIndex(methodName, originalMethodName);
                if (fixIndex != methodNameFormatter.INVALID_INDEX) {
                    int beginLine = cu.getLineNumber(node.getStartPosition());
                    int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength() - 1);
                    fixIndexToRangeMap.put(fixIndex, new LineRange(beginLine, endLine));
                }
                return false;
            }
        });

        return fixIndexToRangeMap;
    }

    private Map<String, AbstractTypeDeclaration> loadASTFromBatchFixFile() {
        String[] files = new String[]{FixerOutput.getBatchFixFilePath().toString()};
        String[] encodes = new String[]{StandardCharsets.UTF_8.name()};

        final TypeCollector collector = new TypeCollector();

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.createASTs(files, encodes, new String[]{}, collector.getASTRequestor(), null);
        return collector.getTypes();
    }

    private static class LineRange {
        public int beginLine;
        public int endLine;

        private LineRange(int beginLine, int endLine) {
            this.beginLine = beginLine;
            this.endLine = endLine;
        }
    }


}
