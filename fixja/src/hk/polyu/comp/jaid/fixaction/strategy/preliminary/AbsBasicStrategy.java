package hk.polyu.comp.jaid.fixaction.strategy.preliminary;

import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotBinaryExpression;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotExpression;
import org.eclipse.jdt.core.dom.*;

import java.util.HashSet;
import java.util.Set;

import static hk.polyu.comp.jaid.fixaction.SnippetBuilder.enableTmpVariable;
import static hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils.constructAssignment;
import static hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils.getReplaceVarName;
import static hk.polyu.comp.jaid.util.CommonUtils.checkStmt;

/**
 * Created by Ls CHEN.
 */
public abstract class AbsBasicStrategy extends Strategy {

    private ITypeBinding type;

    public Set<Snippet> getSnippetSet() {
        return snippetSet;
    }

    protected String getStrategyName(String action) {
        return this.getClass().getSimpleName() + "[" + action + "]";
    }

    public ITypeBinding getType() {
        return type;
    }

    public Set<Snippet> Building(StateSnapshot snapshot) {
        setStateSnapshot(snapshot);
        return process();
    }

    public Set<Snippet> Building(LineLocation location, ExpressionToMonitor etm) {
        return new HashSet<>();
    }

    public Set<Snippet> process() {
        snippetSet = new HashSet<>();
        ExpressionToMonitor operand = getStateSnapshot().getSnapshotExpression().getOperands().get(0);
        type = operand.getType();
        ast = operand.getExpressionAST().getAST();

        if (isDesiredType()) {
            StateSnapshotExpression exp = getStateSnapshot().getSnapshotExpression();

            building(exp);
            exp.getOperands().forEach(x -> building(x));

            if (exp instanceof StateSnapshotBinaryExpression) {
                StateSnapshotBinaryExpression binaryExpression = (StateSnapshotBinaryExpression) exp;
                ExpressionToMonitor leftOperand = binaryExpression.getLeftOperand();
                ExpressionToMonitor rightOperand = binaryExpression.getRightOperand();
                building(leftOperand, rightOperand);
            }
        }

        return snippetSet;
    }

    protected abstract boolean isDesiredType();

    protected abstract void building(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand);

    protected abstract void building(ExpressionToMonitor operand);


    protected void constructAndCreate(ExpressionToMonitor operand, Expression right, String strategyName) {
        Expression constructedExp = null;
        if (operand.isValidVariable()) {
            constructedExp = constructAssignment(operand.getExpressionAST(), right);
            createSnippet(constructedExp, strategyName);
        } else {
            SimpleName tmp_exp = operand.getExpressionAST().getAST().newSimpleName(getReplaceVarName(operand));
            constructedExp = constructAssignment(tmp_exp, right);
            createSnippet(operand, tmp_exp, constructedExp, strategyName);
        }
    }

    protected void checkIfLeftVariableAndConstructSnippet(String strategyName,ExpressionToMonitor left,Expression newRight){
        if (left.isValidVariable()) {
            Expression constructedExp = constructAssignment(left.getExpressionAST(), newRight);
            createSnippet(constructedExp, strategyName);
        } else {
            SimpleName tmp_exp = ast.newSimpleName(getReplaceVarName(left));
            Expression constructedExpT = constructAssignment(tmp_exp, newRight);
            createSnippet(left, tmp_exp, constructedExpT, strategyName);
        }
    }
    protected void createSnippet(ExpressionToMonitor expToReplace, SimpleName tmp_exp, Expression snippetContent, String strategyName) {
        if (snippetContent != null && enableTmpVariable) {
            Block snippetBlock = StrategyUtils.replacement(getStateSnapshot(), tmp_exp, snippetContent, expToReplace);
            if (snippetBlock != null)
                getSnippetSet().add(new Snippet(snippetBlock, StrategyUtils.fitSchemaD, strategyName, getStateSnapshot().getID()));
        }
    }

    protected void createSnippet(Expression snippetContent, String strategyName) {
        createSnippet(checkStmt(snippetContent), strategyName);
    }

    protected void createSnippet(Statement snippetContent, String strategyName) {
        if (snippetContent != null) {
            if (getStateSnapshot().getLocation().getStatement() instanceof VariableDeclarationStatement)
                getSnippetSet().add(new Snippet(snippetContent, StrategyUtils.fitSchemaAB, strategyName, getStateSnapshot().getID()));
            else
                //fixme: Whether the schema_C should be placed outside the strategy, since it has no relationship with the snippet.
                getSnippetSet().add(new Snippet(snippetContent, StrategyUtils.fitSchemaABCD, strategyName, getStateSnapshot().getID()));
        }
    }
}
