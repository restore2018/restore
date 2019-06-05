package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * Created by Max PEI.
 */
public class StatementLocationCollector extends ASTVisitor {

    private final MethodToMonitor contextMethod;
    private final CompilationUnit compilationUnit;

    private Map<LineLocation, Statement> lineNoLocationMap;

    public StatementLocationCollector(MethodToMonitor contextMethod){
        this.contextMethod = contextMethod;
        this.compilationUnit = (CompilationUnit) contextMethod.getMethodAST().getRoot();
    }

    public StatementLocationCollector(MethodToMonitor contextMethod, CompilationUnit compilationUnit){
        this.contextMethod = contextMethod;
        this.compilationUnit = compilationUnit;
    }

    public void collectStatements(Block block){
        lineNoLocationMap = new HashMap<>();
        block.accept(this);
    }

    public Map<LineLocation, Statement> getLineNoLocationMap() {
        return lineNoLocationMap;
    }

    private void collectStatement(Statement statement){
        int lineNumber = this.compilationUnit.getLineNumber(statement.getStartPosition());
        LineLocation lineLocation =  LineLocation.newLineLocation(contextMethod, lineNumber);
        lineNoLocationMap.put(lineLocation, statement);
    }

    private void collectStatements(List statements){
        for(Object o: statements){
            if(o instanceof Statement){
                ((Statement)o).accept(this);
            }
        }
    }

    // ================================= Visitor methods

    public boolean visit(AssertStatement node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(BreakStatement node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(Block node) {
        collectStatements(node.statements());
        return false;
    }

    public boolean visit(ConstructorInvocation node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(ContinueStatement node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(DoStatement node) {
        collectStatement(node);
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(EmptyStatement node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(EnhancedForStatement node) {
        collectStatement(node);
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(ExpressionStatement node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(ForStatement node) {
        collectStatement(node);
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(IfStatement node) {
        collectStatement(node);

        node.getThenStatement().accept(this);
        if(node.getElseStatement() != null)
            node.getElseStatement().accept(this);
        return false;
    }

    public boolean visit(LabeledStatement node) {
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(ReturnStatement node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(SuperConstructorInvocation node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(SwitchStatement node) {
        collectStatement(node);

        collectStatements(node.statements());
        return false;
    }

    public boolean visit(SynchronizedStatement node) {
        collectStatement(node);

        node.getBody().accept(this);
        return false;
    }

    public boolean visit(ThrowStatement node) {
        collectStatement(node);
        return false;
    }

    public boolean visit(TryStatement node) {
        collectStatement(node);

        node.getBody().accept(this);

        List catchClauses = node.catchClauses();
        for(Object o: catchClauses){
            if(o instanceof CatchClause){
                ((CatchClause)o).getBody().accept(this);
            }
        }

        if(node.getFinally() != null)
            node.getFinally().accept(this);

        return false;
    }

    public boolean visit(TypeDeclarationStatement node) {
        return false;
    }

    public boolean visit(VariableDeclarationStatement node) {
        return false;
    }

    public boolean visit(WhileStatement node) {
        collectStatement(node);

        node.getBody().accept(this);
        return false;
    }


}
