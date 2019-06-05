package hk.polyu.comp.jaid.java;

import hk.polyu.comp.jaid.ast.MethodUtil;
import hk.polyu.comp.jaid.ast.ReturnStatementRewriter;
import hk.polyu.comp.jaid.ast.ThisMemberAccessRewriter;
import hk.polyu.comp.jaid.fixer.config.Config;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.util.FileUtil;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.ReplaceEdit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.util.FileUtil.writeFile;

/**
 * Created by Max Pei on 5/24/2017.
 */
public class ClassToFixPreprocessor {

    private final JavaProject project;
    private final Config config;

    public ClassToFixPreprocessor(JavaProject project, Config config) {
        this.project = project;
        this.config = config;
    }

    public void preprocess() {
        rewriteClassToFix();
    }

    private void rewriteClassToFix() {
        MethodDeclaration methodDeclaration = getMethodDeclarationToFix();
        CompilationUnit compilationUnit = (CompilationUnit) methodDeclaration.getRoot();
        project.registerIntermediateSourceFilePaths(compilationUnit);

        // Rewrite method to fix
        Document document = new Document(FileUtil.getFileContent(project.getSourceFileToFix(), StandardCharsets.UTF_8));
        int offset = methodDeclaration.getBody().getStartPosition(), length = methodDeclaration.getBody().getLength();
        ReplaceEdit replaceEdit = new ReplaceEdit(offset, length, getFormattedMethodText(document, methodDeclaration));
        try {
            replaceEdit.apply(document);
            String newContent = document.get();
            writeFile(project.getFormattedSourceFileToFix(), newContent);
        } catch (BadLocationException e) {
            throw new IllegalStateException("Error! Failed to rewrite source file.");
        }
    }

    private MethodDeclaration getMethodDeclarationToFix() {
        String[] files = new String[project.getSourceFiles().size()];
        project.getSourceFiles().stream().map(x -> x.toString()).collect(Collectors.toList()).toArray(files);
        Map<String, AbstractTypeDeclaration> typeDeclarationMap = project.loadASTFromFiles(files);

        String fqClassName = config.getFaultyClassName();
        String methodSignature = config.getFaultyMethodSignature();

        if (!typeDeclarationMap.containsKey(fqClassName)) {
            throw new IllegalStateException("Error! No class with name " + fqClassName + " found in the project.");
        }

        AbstractTypeDeclaration typeDeclaration = typeDeclarationMap.get(fqClassName);
        return MethodUtil.getMethodDeclarationBySignature(typeDeclaration, methodSignature);
    }

    /**
     * Rewrite method-to-fix
     *
     * @param originalDocument
     * @param methodDeclaration
     * @return
     */
    private String getFormattedMethodText(Document originalDocument, MethodDeclaration methodDeclaration) {
        String bodyText = bodyWithFormatThisMemberAccess(originalDocument, methodDeclaration);
        String statements = bodyText.substring(bodyText.indexOf('{') + 1, bodyText.lastIndexOf('}'));

        bodyText = bodyWithTryAndReturnVariable(methodDeclaration, statements);
        bodyText = bodyWithSeparateDelcarationAndInitialization(methodDeclaration, bodyText);
        bodyText = bodyWithOneStatementOnEachLine(methodDeclaration, bodyText);
        return bodyText;
    }

    /**
     * Index of the try statement in the formatted body (See getFormattedMethodText).
     */
    private static int tryStatementIndex;

    public static int getTryStatementIndex() {
        return tryStatementIndex;
    }

    public static int getEntryStatementIndex() {
        return 2;
    }

    // Method body enclosed in a try-finally structure:
    //     { body } ==> { [ReturnType var;] Tester.startMethodToMonitor(); try{body} finally{ Tester.endMethodToMonitor();} }
    // Index of the try statement in the formatted body is stored in tryStatementIndex.
    private String bodyWithTryAndReturnVariable(MethodDeclaration methodDeclaration, String statements) {
        ITypeBinding typeBinding = methodDeclaration.resolveBinding().getReturnType();
        String resultVariableName = "", resultDeclaration;
        if (MethodUtil.returnsVoid(methodDeclaration)) {
            resultDeclaration = "";
            tryStatementIndex = 7;
        } else {
            tryStatementIndex = 8;

            resultVariableName = MethodUtil.getTempReturnVariableName(methodDeclaration);
            resultDeclaration = typeBinding.getQualifiedName() + " " + resultVariableName + "; ";
        }

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(statements.toCharArray());
        ASTNode node = parser.createAST(null);
        Block newBlock = (Block) node;

        ReturnStatementRewriter returnStatementRewriter = new ReturnStatementRewriter();
        String newStatements = returnStatementRewriter.rewrite(new Document(statements), newBlock, resultVariableName);

        ITypeBinding[] exceptionTypes = methodDeclaration.resolveBinding().getExceptionTypes();

        if (exceptionTypes.length > 0) {//The method to fix throws any exception
            // ordering the exceptionTypes
            for (int i = 0; i < exceptionTypes.length; i++) {
                for (int j = i + 1; j < exceptionTypes.length; j++) {
                    if (exceptionTypes[i].isCastCompatible(exceptionTypes[j])) {
                        if (exceptionTypes[j].isSubTypeCompatible(exceptionTypes[i])) {
                            ITypeBinding tmp = exceptionTypes[i];
                            exceptionTypes[i] = exceptionTypes[j];
                            exceptionTypes[j] = tmp;
                        }
                    }
                }
            }
        }
        return constructNewMethodBody(resultDeclaration, newStatements, exceptionTypes);
    }


    private String constructNewMethodBody(String resultDeclaration, String statements, ITypeBinding[] exceptionTypeBindings) {
        return resultDeclaration + Tester.class.getName() + "."
                + Tester.MemberName.START_METHOD_TO_MONITOR_METHOD_NAME.getName() + "(); "
                + constructCheckMtfIsMonitored() + declareJaidMonitorVariables()
                + " try{ try{" + statements + "}"
                + getCatchClauses(exceptionTypeBindings)
                + "} finally{ "
                + constructResetMtfIsMonitored() + Tester.class.getName() + "." + Tester.MemberName.END_METHOD_TO_MONITOR_METHOD_NAME.getName() + "(); }";
    }


    private String getCatchClauses(ITypeBinding[] exceptionTypeBindings) {
        List<String> catchClauseStrList = new ArrayList<>();
        catchClauseStrList.add(getACatchClause("RuntimeException"));
        catchClauseStrList.add(getACatchClause("Error"));
        if (isParentClass(exceptionTypeBindings))
            catchClauseStrList.add(getACatchClause(exceptionTypeBindings[0].getName()));
        else
            for (int i = exceptionTypeBindings.length - 1; i >= 0; i--) {
                String catchClauseStr = getACatchClause(exceptionTypeBindings[0].getName());
                if (!catchClauseStrList.contains(catchClauseStr))
                    catchClauseStrList.add(0, catchClauseStr);
            }
        StringBuilder sb = new StringBuilder();
        for (String s : catchClauseStrList) {
            sb.append(s);
        }
        return sb.toString();
    }

    private boolean isParentClass(ITypeBinding[] exceptionTypeBindings) {
        if (exceptionTypeBindings.length == 1) {
            String excName = exceptionTypeBindings[0].getName();
            if (excName.equals("Exception") || excName.equals("Throwable"))
                return true;
        }
        return false;
    }

    private String getACatchClause(String exceptionName) {
        return " catch (" + exceptionName + " " + Tester.THROWABLE + ") {" + Tester.HAS_EXCEPTION + " =true;"
                + Tester.EXCEPTION_CLASS_TYPE + "=" + Tester.THROWABLE + ".toString();throw " + Tester.THROWABLE + ";}";
    }

    /**
     * Declare some variable for JAID to monitor the MTF
     * Note that initialing for each variable is needed. Otherwise, the variable is no visible at the "finally" block.
     *
     * @return
     */
    private String declareJaidMonitorVariables() {
        StringBuilder vars = new StringBuilder("boolean ").append(Tester.HAS_EXCEPTION).append("=false;")
                .append("String ").append(Tester.EXCEPTION_CLASS_TYPE).append("=null;");
        return vars.toString();
    }

    /**
     * Construct following statements
     * if(Tester.isMonitorMode && Tester.isMonitoringMTF){
     * throw new Tester.MTFIsMonitoredException();
     * }
     * Tester.isMonitoringMTF=true;
     *
     * @return
     */
    private String constructCheckMtfIsMonitored() {
        StringBuilder checker = new StringBuilder("if(")
                .append(Tester.class.getName()).append(".").append(Tester.MemberName.IS_MONITOR_MODE.getName()).append(" && ")
                .append(Tester.class.getName()).append(".").append(Tester.MemberName.IS_Executing_MTF.getName()).append("){")
                .append("throw new ").append(Tester.class.getName()).append(".").append(Tester.MTFIsMonitoredException.class.getSimpleName()).append("();}")
                .append(Tester.class.getName()).append(".").append(Tester.MemberName.IS_Executing_MTF.getName()).append(" = true;");
        return checker.toString();
    }

    private String constructResetMtfIsMonitored() {
        StringBuilder checker = new StringBuilder();
        checker.append(Tester.class.getName()).append(".").append(Tester.MemberName.IS_Executing_MTF.getName()).append(" = false;\n");
        return checker.toString();
    }

    // Variable initialization separated from variable declaration;
    private String bodyWithSeparateDelcarationAndInitialization(MethodDeclaration methodDeclaration, String statements) {
        Document document = new Document(statements);

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(statements.toCharArray());
        ASTNode node = parser.createAST(null);

        ASTRewrite rewrite = ASTRewrite.create(node.getAST());
        ITrackedNodePosition nodePosition = rewrite.track(node);
        RangeMarker rangeMarker = new RangeMarker(nodePosition.getStartPosition(), nodePosition.getLength());

        if (node instanceof Block) {
            Block bodyStatements = (Block) node;
            bodyStatements.accept(new ASTVisitor() {
                @Override
                public boolean visit(VariableDeclarationStatement node) {
                    StringBuilder typeText = new StringBuilder();
                    for (Object modifier : node.modifiers()) {
                        typeText.append(modifier.toString()).append(" ");
                    }
                    typeText.append(node.getType().toString());

                    StringBuilder statementText = new StringBuilder();
                    List<VariableDeclarationFragment> fragments = node.fragments();
                    boolean hasInitializer = false;
                    for (VariableDeclarationFragment fragment : fragments) {
                        if (fragment.getExtraDimensions() > 0) {
                            statementText.append(typeText.toString()).append(" ")
                                    .append(fragment.toString()).append("; ");
                        } else {
                            statementText.append(typeText.toString()).append(" ")
                                    .append(fragment.getName().toString()).append("; ")
                                    .append(fragment.toString()).append("; ");
                        }
                        if (fragment.getInitializer() != null)
                            hasInitializer = true;
                    }

                    if (hasInitializer) {
                        // replace statement
                        ITrackedNodePosition statementPosition = rewrite.track(node);
                        ReplaceEdit replaceEdit = new ReplaceEdit(statementPosition.getStartPosition(), statementPosition.getLength(), statementText.toString());
                        rangeMarker.addChild(replaceEdit);
                    }

                    return super.visit(node);
                }
            });

            try {
                rangeMarker.apply(document);
                String newBody = document.get(rangeMarker.getOffset(), rangeMarker.getLength());
                return newBody;
            } catch (BadLocationException e) {
                throw new IllegalStateException("Error! Failed to separate variable declaration and initialization.");
            }
        } else {
            throw new IllegalStateException();
        }
    }

    private String bodyWithFormatThisMemberAccess(Document originalDocument, MethodDeclaration methodDeclaration) {
        ThisMemberAccessRewriter thisMemberAccessRewriter = new ThisMemberAccessRewriter();
        return thisMemberAccessRewriter.rewrite(originalDocument, methodDeclaration);
    }

    /**
     * Formatting Each statement in a separate line, and re-ordering operands of infix-expressions
     *
     * @param methodDeclaration
     * @param statements
     * @return
     */
    private String bodyWithOneStatementOnEachLine(MethodDeclaration methodDeclaration, String statements) {
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(statements.toCharArray());
        ASTNode newBodyAST = parser.createAST(null);
        return newBodyAST.toString();
//        ExpressionFormatter expressionFormatter=new ExpressionFormatter(newBodyAST);//format infix expression in the beginning
//        return expressionFormatter.getFormattedASTNode().toString();
    }


}
