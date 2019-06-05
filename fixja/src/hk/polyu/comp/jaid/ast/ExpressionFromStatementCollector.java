package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import org.eclipse.jdt.core.dom.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by Max PEI.
 */
public class ExpressionFromStatementCollector extends ASTVisitor{

    // ============================ Storage

    private static ExpressionCollector collector = new ExpressionCollector(true);
    private Set<ExpressionToMonitor> expressionsToMonitor;

    public void collect(Statement[] statements){
        expressionsToMonitor = new HashSet<>();
        for(Statement statement: statements){
            if(statement != null)
                statement.accept(this);
        }
    }

    // ============================== Getters

    public Set<ExpressionToMonitor> getExpressionsToMonitor() {
        return expressionsToMonitor;
    }

    // ============================ Implementation

    private void collectFromASTNode(ASTNode node){
        if(node != null) {
            collector.collect(node);
            expressionsToMonitor.addAll(toExpressionsToMonitor(collector.getSubExpressionSet()));
        }
    }

    private Set<ExpressionToMonitor> toExpressionsToMonitor(Set<Expression> expressions){
        return expressions.stream().map(x -> ExpressionToMonitor.construct(x, x.resolveTypeBinding())).collect(Collectors.toSet());
    }

    // =========================== Visitor methods

    @Override
    public boolean visit(AssertStatement node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(Block node) {
        return false;
    }

    @Override
    public boolean visit(BreakStatement node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(DoStatement node) {
        collectFromASTNode(node.getExpression());
        return false;
    }

    @Override
    public boolean visit(EmptyStatement node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        return false;
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(ForStatement node) {
        node.initializers().forEach(x -> collectFromASTNode((Expression)x));
        collectFromASTNode(node.getExpression());
        node.updaters().forEach(x -> collectFromASTNode((Expression)x));

        return false;
    }

    @Override
    public boolean visit(IfStatement node) {
        collectFromASTNode(node.getExpression());
        return false;
    }

    @Override
    public boolean visit(LabeledStatement node) {
        node.getBody().accept(this);
        return false;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(SwitchCase node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(SwitchStatement node) {
        collectFromASTNode(node);
        return false;
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
        return false;
    }

    @Override
    public boolean visit(ThrowStatement node) {
        return false;
    }

    @Override
    public boolean visit(TryStatement node) {
        return false;
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationFragment node){
        if(node.getInitializer() != null)
            collectFromASTNode(node.getInitializer());
        return false;
    }

    @Override
    public boolean visit(WhileStatement node) {
        collectFromASTNode(node.getExpression());
        return false;
    }
}
