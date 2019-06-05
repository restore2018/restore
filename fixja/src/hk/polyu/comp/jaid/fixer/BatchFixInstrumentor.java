package hk.polyu.comp.jaid.fixer;

import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.fixaction.FixedMethodNameFormatter;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.util.CommonUtils;
import hk.polyu.comp.jaid.util.FileUtil;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.ReplaceEdit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static hk.polyu.comp.jaid.fixer.config.Config.BATCH_SIZE;
import static hk.polyu.comp.jaid.util.FileUtil.FQClassNameToRelativeSourcePath;
import static hk.polyu.comp.jaid.util.FileUtil.writeFile;

/**
 * Created by Max PEI.
 */
public class BatchFixInstrumentor {

    /**
     * Candidate fixes are grouped into at most BATCH_SIZE batches, each batch with at most BATCH_SIZE fixes.
     */
    public static int getTotalBatchSize(){
        return BATCH_SIZE * BATCH_SIZE;
    }
    private JavaProject project;
    private List<FixAction> fixActions;

    public BatchFixInstrumentor(JavaProject project){
        this.project = project;
    }

    public BatchFixInstrumentor(JavaProject project, boolean shouldUpdateFixId) {
        this.project = project;
        this.shouldUpdateFixId = shouldUpdateFixId;
    }

    public MethodToMonitor getMethodToInstrument(){
        if(this.fixActions.isEmpty()){
            throw new IllegalStateException();
        }

        return fixActions.get(0).getLocation().getContextMethod();
    }

    public void instrument(List<FixAction> fixActions){
        this.fixActions = fixActions;

        String classContent = classWithInstrumentation();
        Path newSourceFilePath = project.getSourceFileWithAllFixes();
        FixerOutput.setBatchFixFilePath(newSourceFilePath);
        writeFile(newSourceFilePath, classContent);

        LoggingService.info("Batch fix file generated successfully.");
    }

    // Second level dispatcher. (See getOriginalMethod for the first level dispatcher)
    // A call to the fixed method is dispatched to the actual method with corresponding fix.
    private String getDispatcher(int dispatcherID){
        boolean methodReturnsVoid = methodToInstrument.returnsVoid();
        String newMethodDeclaration = null;

        document = new Document(classFileContent);
        rewrite = ASTRewrite.create(unit.getAST());

        ITrackedNodePosition methodPosition = rewrite.track(methodToInstrument.getMethodAST());
        ITrackedNodePosition methodNamePosition = rewrite.track(methodToInstrument.getMethodAST().getName());
        ITrackedNodePosition methodBodyPosition = rewrite.track(methodToInstrument.getMethodAST().getBody());

        RangeMarker rangeMarker = new RangeMarker(methodPosition.getStartPosition(), methodPosition.getLength());

        // Remove the "@Override" annotation if present.
        Annotation overrideAnnotation = methodToInstrument.getOverrideAnnotation();
        if(overrideAnnotation != null){
            ITrackedNodePosition annotationPosition = rewrite.track(overrideAnnotation);
            ReplaceEdit replaceEdit = new ReplaceEdit(annotationPosition.getStartPosition(), annotationPosition.getLength(), "");
            rangeMarker.addChild(replaceEdit);
        }

        // New method Name
        String methodName = methodNameFormatter.getDispatcherName(methodToInstrument.getSimpleName(), dispatcherID);
        ReplaceEdit replaceEdit = new ReplaceEdit(methodNamePosition.getStartPosition(), methodNamePosition.getLength(), methodName);
        rangeMarker.addChild(replaceEdit);

        // New method body
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        // Dispatching calls
        boolean hasCase = false;
        String methodInvocation = null;
        sb.append("\tswitch(").append(Tester.class.getName()).append(".").append(Tester.MemberName.GET_INDEX_INSIDE_BATCH_METHOD_NAME.getName()).append("()){\n");
        int startIndex = dispatcherID * BATCH_SIZE;
        int endIndex = startIndex + BATCH_SIZE;
        for(int i = startIndex; i < endIndex && i < fixActions.size(); i++){

            if(fixActions.get(i).needsValidation()){
                String fixedMethodName = methodNameFormatter.getFixedMethodName(methodToInstrument.getSimpleName(), i);
                methodInvocation = methodCallTemplate.replace(METHOD_NAME_PLACEHOLDER, fixedMethodName);
                sb.append("\t\tcase ").append(i - startIndex).append(": ");
                if(methodReturnsVoid){
                    sb.append(methodInvocation).append("; return; \n");
                }
                else {
                    sb.append("return ").append(methodInvocation).append(";\n");
                }
                hasCase = true;
            }
        }

        // in case nothing is generated in the loop.
        sb.append("\t\tcase -1: throw new IllegalStateException();\n");

        sb.append("\t\tdefault: throw new IllegalStateException();\n");
        sb.append("\t} // end of switch\n");
        sb.append("} // end of method\n");

        replaceEdit = new ReplaceEdit(methodBodyPosition.getStartPosition(), methodBodyPosition.getLength(), sb.toString());
        rangeMarker.addChild(replaceEdit);

        try {
            rangeMarker.apply(document);
            newMethodDeclaration = document.get(rangeMarker.getOffset(), rangeMarker.getLength());
        }
        catch(BadLocationException e){
            throw new IllegalStateException("Failed to generate second level dispatcher.");
        }
        return newMethodDeclaration;
    }

    // After instrumentation, the original method becomes the first level dispatcher. In this first level dispatcher,
    // a call to the fixed method are dispatched to a particular second level dispatcher (See method getDispatcher).
    private String getOriginalMethod(){
        boolean methodReturnsVoid = methodToInstrument.returnsVoid();
        String newMethodDeclaration = null;

        document = new Document(classFileContent);
        rewrite = ASTRewrite.create(unit.getAST());

        ITrackedNodePosition methodPosition = rewrite.track(methodToInstrument.getMethodAST());
        ITrackedNodePosition methodBodyPosition = rewrite.track(methodToInstrument.getMethodAST().getBody());

        RangeMarker rangeMarker = new RangeMarker(methodPosition.getStartPosition(), methodPosition.getLength());

        StringBuilder sb = new StringBuilder();
        // New method body
        sb.append("{\n");

        // Dispatching calls
        sb.append("\tswitch(").append(Tester.class.getName()).append(".").append(Tester.MemberName.GET_BATCH_INDEX_METHOD_NAME.getName()).append("()){\n");
        String methodInvocation = null;
        int nbrFixActions = fixActions.size();
        for(int i = 0; i * BATCH_SIZE < nbrFixActions; i++){
            methodInvocation = methodCallTemplate.replace(METHOD_NAME_PLACEHOLDER, methodNameFormatter.getDispatcherName(methodToInstrument.getSimpleName(), i));
            sb.append("\t\tcase ").append(i).append(": ");
            if(methodReturnsVoid){
                sb.append(methodInvocation).append("; return;\n");
            }
            else {
                sb.append("return ").append(methodInvocation).append(";\n");
            }
        }
        sb.append("\t\tdefault: throw new IllegalStateException();\n");
        sb.append("\t} // end of switch\n");
        sb.append("} // end of method\n");

        ReplaceEdit replaceEdit = new ReplaceEdit(methodBodyPosition.getStartPosition(), methodBodyPosition.getLength(), sb.toString());
        rangeMarker.addChild(replaceEdit);

        try {
            rangeMarker.apply(document);
            newMethodDeclaration = document.get(rangeMarker.getOffset(), rangeMarker.getLength());
        }
        catch(BadLocationException e){
            throw new IllegalStateException("Failed to generate first level dispatcher.");
        }
        return newMethodDeclaration;
    }

    private static final String COMMENT_FIX_ID_PLACEHOLDER = "$fix_id$";
    private static final String COMMENT_FIX_ID = "\n/* FixID = " + COMMENT_FIX_ID_PLACEHOLDER + " */\n";

    private String getFixedMethod(FixAction action, int index){
        Statement statementAtLocation = action.getLocation().getStatement();
        String fixIdComment = COMMENT_FIX_ID.replace(COMMENT_FIX_ID_PLACEHOLDER, String.valueOf(action.getFixId()));
        String newMethodDeclaration = null;

        document = new Document(classFileContent);
        rewrite = ASTRewrite.create(unit.getAST());

        MethodDeclaration methodDeclaration = methodToInstrument.getMethodAST();
        ITrackedNodePosition methodNamePosition = rewrite.track(methodDeclaration.getName());
        ITrackedNodePosition statementPosition = rewrite.track(statementAtLocation);
        ITrackedNodePosition methodPosition = rewrite.track(methodDeclaration);

        RangeMarker rangeMarker = new RangeMarker(methodPosition.getStartPosition(), methodPosition.getLength());

        // Remove the "@Override" annotation if present.
        Annotation overrideAnnotation = methodToInstrument.getOverrideAnnotation();
        if(overrideAnnotation != null){
            ITrackedNodePosition annotationPosition = rewrite.track(overrideAnnotation);
            ReplaceEdit replaceEdit = new ReplaceEdit(annotationPosition.getStartPosition(), annotationPosition.getLength(), "");
            rangeMarker.addChild(replaceEdit);
        }

        // replace the old statement with the new one.
        String replaceStr = action.getFix();
        if(isSingleChild(statementAtLocation)) {
            replaceStr = action.getFix();
        }
        ReplaceEdit replaceEdit = new ReplaceEdit(statementPosition.getStartPosition(), statementPosition.getLength(), replaceStr);
        rangeMarker.addChild(replaceEdit);

        // replace the old method name with the new one.
        String newMethodName = methodNameFormatter.getFixedMethodName(methodToInstrument.getMethodAST().getName().getIdentifier(), index);
        replaceEdit = new ReplaceEdit(methodNamePosition.getStartPosition(), methodNamePosition.getLength(), newMethodName);
        rangeMarker.addChild(replaceEdit);

        try {
            String statementTextToBeReplaced = document.get(statementPosition.getStartPosition(), statementPosition.getLength());
            action.setStatementTextToBeReplaced(statementTextToBeReplaced);

            rangeMarker.apply(document);
            newMethodDeclaration = document.get(rangeMarker.getOffset(), rangeMarker.getLength());
        }
        catch(BadLocationException e){
            throw new IllegalStateException("Failed to generate fixed method for fix #" + action.getFixId() + ".");
        }

        //update Setting FixId using formatted generated method body string
        if (shouldUpdateFixId && action.getFixId() == -1) {
            //Parse the method string to AST
            ASTParser fixedMethodPaser = ASTParser.newParser(AST.JLS8);
            fixedMethodPaser.setSource(newMethodDeclaration.toCharArray());
            fixedMethodPaser.setKind(ASTParser.K_CLASS_BODY_DECLARATIONS);
            TypeDeclaration fixedMethod = (TypeDeclaration) fixedMethodPaser.createAST(null);
            //using the formatted method body to generate fix id
            action.setFixId(CommonUtils.getId(fixedMethod.getMethods()[0].getBody().toString()));
        }
        return fixIdComment + newMethodDeclaration;
    }


    private boolean isSingleChild(Statement statement) {
        ASTNode parent = statement.getParent();
        if (parent instanceof Block)
            parent = parent.getParent();
        return parent instanceof IfStatement || parent instanceof WhileStatement
                || parent instanceof DoStatement || parent instanceof ForStatement || parent instanceof EnhancedForStatement;
    }

    private MethodToMonitor methodToInstrument;
    private CompilationUnit unit;
    private ASTRewrite rewrite;
    private String className;
    private String classFilePath;
    private String classFileContent;
    private Path classFileRelativePath;
    private Document document;
    private FixedMethodNameFormatter methodNameFormatter;
    private boolean shouldUpdateFixId = false;

    private void initialize(){
        methodToInstrument = getMethodToInstrument();
        unit = (CompilationUnit) methodToInstrument.getMethodAST().getRoot();
        rewrite = ASTRewrite.create(unit.getAST());

        className = methodToInstrument.getFullQualifiedClassName();
        classFilePath = (String) unit.getProperty(JavaProject.COMPILATION_UNIT_PROPERTY_PATH);
        classFileRelativePath = FQClassNameToRelativeSourcePath(className);
        classFileContent = FileUtil.getFileContent(Paths.get(classFilePath), StandardCharsets.UTF_8);

        methodNameFormatter = new FixedMethodNameFormatter();
        prepareMethodCallTemplate();
    }

    private String classWithInstrumentation(){
        initialize();

        StringBuilder sb = new StringBuilder("\n\n");

        // Generate method declarations for all fixes
        for(int i = 0; i < fixActions.size(); i++){
            FixAction fixAction = fixActions.get(i);
            if (fixAction == null || !fixAction.needsValidation())
                continue;

            String fixedMethod = getFixedMethod(fixAction, i);
            sb.append(fixedMethod).append("\n\n");

//            if(shouldLogDebug()) {
//                LoggingService.debug("Method with fix #" + fixAction.getFixId() + " generated:");
//                LoggingService.debug(fixAction.toString());
//            }
        }

        String firstLevelDispatcher = getOriginalMethod();
        sb.append(firstLevelDispatcher).append("\n\n");

//        if(shouldLogDebug()) {
//            LoggingService.debug("First level dispatcher generated:");
//            LoggingService.debug(firstLevelDispatcher);
//        }

        for(int i = 0; i < BATCH_SIZE && i * BATCH_SIZE <= fixActions.size(); i++){
            String secondLevelDispatcher = getDispatcher(i);
            sb.append(secondLevelDispatcher).append("\n\n");

//            if(shouldLogDebug()) {
//                LoggingService.debug("Second level dispatcher on level " + i + " generated:");
//                LoggingService.debug(secondLevelDispatcher);
//            }
        }

        document = new Document(classFileContent);
        rewrite = ASTRewrite.create(unit.getAST());

        ITrackedNodePosition methodPosition = rewrite.track(methodToInstrument.getMethodAST());

        ReplaceEdit replaceEdit = new ReplaceEdit(methodPosition.getStartPosition(), methodPosition.getLength(), sb.toString());

        try {
            replaceEdit.apply(document);
            return document.get();
        }
        catch(BadLocationException e){
            throw new IllegalStateException("Failed to generate class content with instrumentation.");
        }
    }

    private String methodCallTemplate;

    private void prepareMethodCallTemplate(){
        StringBuilder sb = new StringBuilder(METHOD_NAME_PLACEHOLDER);
        sb.append("(");

        List parameters = methodToInstrument.getMethodAST().parameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i) instanceof SingleVariableDeclaration) {
                SingleVariableDeclaration declaration = (SingleVariableDeclaration) parameters.get(i);
                sb.append(declaration.getName().getIdentifier());
                if (i != parameters.size() - 1) {
                    sb.append(", ");
                }
            }
        }
        sb.append(")");
        methodCallTemplate = sb.toString();
    }

    public static final String METHOD_NAME_PLACEHOLDER = "$method_name$";

}
