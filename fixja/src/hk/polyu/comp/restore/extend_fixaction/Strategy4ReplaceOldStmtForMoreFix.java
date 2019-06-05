package hk.polyu.comp.restore.extend_fixaction;

import hk.polyu.comp.jaid.ast.ExpNodeFinder;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.restore.Application;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import java.util.*;

/**
 * Created by Ls CHEN.
 */
public class Strategy4ReplaceOldStmtForMoreFix extends Strategy {
    Set<Snippet> snippetSet;
    ITypeBinding type;
    static Set<String> methodsReplaceBooleanParam= new HashSet<String>();
    static Set<String> methodsReplaceLocalVarParam= new HashSet<String>();
    static Set<String> methodsReplaceEnumVarParam= new HashSet<String>();
    private List<Integer> locationTailored;
    private List<StateSnapshot> snapshotSample;

    public Strategy4ReplaceOldStmtForMoreFix(List<Integer> locationTailored,List<StateSnapshot> snapshotSample){
        this.locationTailored = locationTailored;
        this.snapshotSample = snapshotSample;
    }

    @Override
    public Set<Snippet> process() {
        this.snippetSet = new HashSet<>();
        // top 1,3,5
        if(Application.isRestore&&(!locationTailored.contains(getStateSnapshot().getLocation().getLineNo())||snapshotSample.contains(getStateSnapshot()))){
            return snippetSet;
        }

        type = getStateSnapshot().getSnapshotExpression().getOperands().get(0).getType();
        Statement oldStmt = getStateSnapshot().getLocation().getStatement();
        Statement oldStmtForMoreFix = getStateSnapshot().getLocation().getStatementForMoreFix();
        ast = oldStmt.getRoot().getAST();

        CollectInvocation collector = new CollectInvocation();
        CollectInvocation collectorForMoreFix = new CollectInvocation();


        Set<MethodInvocation> invocationSet = collector.collect(oldStmt);
        Set<MethodInvocation> invocationSetForMoreFix = collectorForMoreFix.collect(oldStmtForMoreFix);

        invocationSet.stream().forEach(invo -> {
            //templateReplaceInvocation(invo);
            templateReplaceBooleanParam(invo);
            templateReplaceLocalVarParam(invo,invocationSetForMoreFix);
            templateReplaceEnumVarParam(invo);
        });

        return snippetSet;
    }


    /**
     * Replace the invoked method of a method invocation by another method that can be invoked
     *
     * This template dose not related to the program states, and it cannot fix any bug in D4J currently
     * This template is removed.
     * @param oldInvo
     */
    private void templateReplaceInvocation(MethodInvocation oldInvo) {
        Set<IMethodBinding> newInvo = new HashSet<>();
        if (oldInvo == null) return;
        List potentialInvocations = null;
        if (oldInvo.getExpression() != null) {
            if (oldInvo.getExpression().resolveTypeBinding() != null &&
                    !oldInvo.getExpression().resolveTypeBinding().getQualifiedName().equals("java.lang.StringBuilder")) {
                potentialInvocations = Arrays.asList(oldInvo.getExpression().resolveTypeBinding().getDeclaredMethods());
            }
        } else {
            potentialInvocations = Arrays.asList(
                    ((AbstractTypeDeclaration) getStateSnapshot().getLocation().getContextMethod().getMethodAST().getParent()).resolveBinding().getDeclaredMethods());
        }

        if (potentialInvocations != null)
            for (Object invocation : potentialInvocations) {
                IMethodBinding method = (IMethodBinding) invocation;
                boolean match = true;
                if (method.getParameterTypes().length != oldInvo.arguments().size())
                    match = false;
                for (ITypeBinding newArg : Arrays.asList(method.getParameterTypes())) {
                    for (Object o : oldInvo.arguments()) {
                        Expression oldArg = (Expression) o;
                        if (newArg == null || newArg.getQualifiedName() == null ||
                                oldArg == null || oldArg.resolveTypeBinding() == null ||
                                !newArg.getQualifiedName().equals(oldArg.resolveTypeBinding().getQualifiedName())) {
                            match = false;
                            break;
                        }
                    }
                }
                if (match && !oldInvo.resolveMethodBinding().getReturnType().getName().equals(method.getReturnType().getName()))
                    match = false;
                if (match && !method.getName().equals(oldInvo.getName().toString())) newInvo.add(method);
            }
        if (newInvo.size() > 0)
            newInvo.stream().forEach(invo -> {
                MethodInvocation methodInvocation = ast.newMethodInvocation();
                methodInvocation.setExpression((Expression) ASTNode.copySubtree(ast, oldInvo.getExpression()));
                oldInvo.arguments().forEach(o -> {
                    methodInvocation.arguments().add(ASTNode.copySubtree(ast, (Expression) o));
                });
                methodInvocation.setName(ast.newSimpleName(invo.getMethodDeclaration().getName()));
                replaceInvocation(oldInvo, methodInvocation);
            });
    }

    /**
     * Replace the boolean literature by its negation in a method invocation
     *
     * This template dose not related to the program states, but it can fix 2 bugs in D4J
     *
     */

    private MethodInvocation getMethodInvocationForMoreFix(MethodInvocation oldinvo,Set<MethodInvocation> invocationSetForMoreFix){
        for(MethodInvocation invo:invocationSetForMoreFix) {
            if(oldinvo.getName().toString().equals(invo.getName().toString())){
                return invo;
            }
        }
        return null;
    }

    private void templateReplaceBooleanParam(MethodInvocation oldInvo) {
        if (methodsReplaceBooleanParam.contains(oldInvo.toString())) return;//Avoid duplicated fixes.
        MethodInvocation copy = (MethodInvocation) ASTNode.copySubtree(ast, oldInvo);
        List args = oldInvo.arguments(), copyArgs = copy.arguments();
        for (int i = 0; i < args.size(); i++) {
            Expression arg = (Expression) args.get(i);
            if (arg.toString().equals("true")) {
                copyArgs.remove(i);
                copyArgs.add(i, ast.newBooleanLiteral(false));
            } else if (arg.toString().equals("false")) {
                copyArgs.remove(i);
                copyArgs.add(i, ast.newBooleanLiteral(true));

            }
        }
        if (!oldInvo.toString().equals(copy.toString())){
            methodsReplaceBooleanParam.add(oldInvo.toString());
            replaceInvocation(oldInvo, copy);
        }
    }

    private void templateReplaceLocalVarParam(MethodInvocation oldInvo,Set<MethodInvocation> invocationSetForMoreFix) {
        if (methodsReplaceLocalVarParam.contains(oldInvo.toString())) return;//Avoid duplicated fixes.

        SortedSet<ExpressionToMonitor> etms = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getBasicExpressionsWithinClass();
        List args = oldInvo.arguments();
        for(int i = 0; i<args.size();i++){
            for(ExpressionToMonitor etm:etms){
                MethodInvocation copy = (MethodInvocation) ASTNode.copySubtree(ast, oldInvo);
                ITypeBinding[] methodParameters = oldInvo.resolveMethodBinding()==null?new ITypeBinding[0]:oldInvo.resolveMethodBinding().getParameterTypes();
                List copyArgs = copy.arguments();
                Expression arg = (Expression) args.get(i);

                ITypeBinding a = etm.getType();
                ITypeBinding b = arg.resolveTypeBinding();
                HashMap<Integer,ArrayList<String>> otherArgsList = findOtherArgs(oldInvo,invocationSetForMoreFix);
                List otherArgs = new ArrayList<>();
                if(otherArgsList.containsKey(i)){
                    otherArgs = findOtherArgs(oldInvo,invocationSetForMoreFix).get(i);
                }

                if(arg.resolveTypeBinding()==null){
                    break;
                }
                if(etm.getType().getName().equals(arg.resolveTypeBinding().getName())||otherArgs.stream().anyMatch(x->x.equals(etm.getType().getName()))){
                    copyArgs.remove(i);
                    if(etm.getExpressionAST().getParent()!=null){
                        continue;
                    }
                    copyArgs.add(i, (Expression) ASTNode.copySubtree(ast, etm.getExpressionAST()));
                }

                if (!oldInvo.toString().equals(copy.toString())){
                    methodsReplaceLocalVarParam.add(oldInvo.toString());
                    replaceInvocation(oldInvo, copy);
                }

            }

        }


    }

    private void templateReplaceEnumVarParam(MethodInvocation oldInvo) {
        if (methodsReplaceEnumVarParam.contains(oldInvo.toString())) return;//Avoid duplicated fixes.
        SortedSet<ExpressionToMonitor> etms = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getBasicExpressionsWithinClass();
        List args = oldInvo.arguments();
        for(int i = 0; i<args.size();i++){
            MethodInvocation copy = (MethodInvocation) ASTNode.copySubtree(ast, oldInvo);
            List copyArgs = copy.arguments();
            Expression arg = (Expression) args.get(i);

            if(arg instanceof QualifiedName){
                IVariableBinding[] qualifiedNameList = ((QualifiedName) arg).getQualifier().resolveTypeBinding().getDeclaredFields();
                for(IVariableBinding qualifiedName: qualifiedNameList){
                    if(qualifiedName.getType().getName().equals(arg.resolveTypeBinding().getName())){
                        copyArgs.remove(i);
                        QualifiedName a = ast.newQualifiedName(ast.newName(((QualifiedName) arg).getQualifier().toString()),ast.newSimpleName(qualifiedName.getName()));
                        copyArgs.add(i, ast.newQualifiedName(ast.newName(((QualifiedName) arg).getQualifier().toString()),ast.newSimpleName(qualifiedName.getName())));
                        if (!oldInvo.toString().equals(copy.toString())){
                            methodsReplaceEnumVarParam.add(oldInvo.toString());
                            replaceInvocation(oldInvo, copy);
                        }
                    }

                }
            }

        }
    }

    private void replaceInvocation(MethodInvocation oldInvo, MethodInvocation newInvo) {
        if (oldInvo.toString().equals(newInvo.toString())) return;
        Statement oldStmt = getStateSnapshot().getLocation().getStatement();
        ASTParser ifCondParser = ASTParser.newParser(AST.JLS8);
        ifCondParser.setSource(oldStmt.toString().toCharArray());
        ifCondParser.setKind(ASTParser.K_STATEMENTS);
        Block old_stmt_ast = (Block) ifCondParser.createAST(null);
        ASTRewrite rewriter = ASTRewrite.create(old_stmt_ast.getAST());

        ExpNodeFinder findNodeByExp = new ExpNodeFinder(old_stmt_ast);
        Expression toReplace = (Expression) findNodeByExp.find(oldInvo);

        if (toReplace != null) {
            Document document = new Document(oldStmt.toString());
            rewriter.replace(toReplace, newInvo, null);
            TextEdit edits = rewriter.rewriteAST(document, null);
            try {
                edits.apply(document);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }

            ASTParser newStmtParser = ASTParser.newParser(AST.JLS8);
            newStmtParser.setSource(document.get().toCharArray());
            newStmtParser.setKind(ASTParser.K_STATEMENTS);
            Block new_stmt_ast = (Block) newStmtParser.createAST(null);
            snippetSet.add(new Snippet(new_stmt_ast, StrategyUtils.fitSchemaE, getStrategyName("invocation-replace"), getStateSnapshot().getID()));
        }
    }

    private class CollectInvocation extends ASTVisitor {
        Set<MethodInvocation> invocationSet;

        public CollectInvocation() {
            this.invocationSet = new HashSet<>();
        }

        public Set<MethodInvocation> collect(Statement root) {
            root.accept(this);
            return invocationSet;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            invocationSet.add(node);
            return true;
        }

    }

    private class CollectIfStatement extends ASTVisitor {
        Set<IfStatement> ifStatementSet;

        public CollectIfStatement() {
            this.ifStatementSet = new HashSet<>();
        }

        public Set<IfStatement> collect(Statement root) {
            root.accept(this);
            return ifStatementSet;
        }

        @Override
        public boolean visit(IfStatement node) {
            ifStatementSet.add(node);
            return true;
        }

    }

    private HashMap<Integer,ArrayList<String>> findOtherArgs(MethodInvocation oldInvo,Set<MethodInvocation> invocationSetForMoreFix) {
        MethodInvocation oldInvoForMoreFix = getMethodInvocationForMoreFix(oldInvo,invocationSetForMoreFix);
        if(oldInvoForMoreFix.getExpression()==null) return new HashMap<>();
        ITypeBinding type = oldInvoForMoreFix.getExpression().resolveTypeBinding();
        ITypeBinding[] methodParameters = oldInvoForMoreFix.resolveMethodBinding()==null?new ITypeBinding[0]:oldInvoForMoreFix.resolveMethodBinding().getParameterTypes();

        HashMap<Integer,ArrayList<String>> argsType = new HashMap<>();
        IMethodBinding[] methodBindings = type.getDeclaredMethods();
        if (methodBindings.length == 0) methodBindings = type.getTypeDeclaration().getDeclaredMethods();
        for(IMethodBinding imb:methodBindings){
            if(oldInvoForMoreFix.resolveMethodBinding()==null) continue;
            if(!oldInvoForMoreFix.resolveMethodBinding().getName().equals(imb.getName())) continue;
            ITypeBinding[] imbParameters = imb.getParameterTypes();
            if(methodParameters.length==imbParameters.length){
                for(int i=0;i<methodParameters.length;i++){
                    if(!methodParameters[i].getName().equals(imbParameters[i].getName())){
                        if(argsType.containsKey(i)){
                            argsType.get(i).add(imbParameters[i].getName());
                        }else{
                            ArrayList<String> tempList = new ArrayList<>();
                            tempList.add(imbParameters[i].getName());
                            argsType.put(i,tempList);
                        }

                    }
                }
            }
        }
        return argsType;

    }

}
