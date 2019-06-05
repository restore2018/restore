package hk.polyu.comp.jaid.fixaction.strategy.preliminary;

import hk.polyu.comp.jaid.ast.ExpNodeFinder;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Ls CHEN.
 */
public class Strategy4ReplaceOldStmt extends Strategy {
    Set<Snippet> snippetSet;
    ITypeBinding type;
    Set<String> methodsReplaceBooleanParam= new HashSet<String>();

    @Override
    public Set<Snippet> process() {
        this.snippetSet = new HashSet<>();
        type = getStateSnapshot().getSnapshotExpression().getOperands().get(0).getType();
        Statement oldStmt = getStateSnapshot().getLocation().getStatement();
        ast = oldStmt.getRoot().getAST();

        CollectInvocation collector = new CollectInvocation();
        Set<MethodInvocation> invocationSet = collector.collect(oldStmt);
        invocationSet.stream().forEach(invo -> {
            //templateReplaceInvocation(invo);
            templateReplaceBooleanParam(invo);
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
     * @param oldInvo
     */
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

}
