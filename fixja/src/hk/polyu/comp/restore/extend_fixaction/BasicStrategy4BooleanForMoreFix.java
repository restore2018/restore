package hk.polyu.comp.restore.extend_fixaction;

import hk.polyu.comp.jaid.ast.ASTUtils4SelectInvocation;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.preliminary.AbsBasicStrategy;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotBinaryExpression;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotExpression;
import hk.polyu.comp.restore.Application;
import org.eclipse.jdt.core.dom.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils.constructAssignment;
import static hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils.getReplaceVarName;

/**
 * Created by Ls CHEN
 */
public class BasicStrategy4BooleanForMoreFix extends AbsBasicStrategy {

    static Set<String> methodsReplaceIfConditionParam= new HashSet<String>();

    private List<Integer> locationTailored;
    private List<StateSnapshot> snapshotSample;
    public BasicStrategy4BooleanForMoreFix(List<Integer> locationTailored, List<StateSnapshot> snapshotSample){
        this.locationTailored = locationTailored;
        this.snapshotSample = snapshotSample;
    }


    @Override
    protected boolean isDesiredType() {
        return getStateSnapshot().getSnapshotExpression().getOperands().get(0).isBooleanType();
    }




    /**
     * Build snippetMap for a binary snapshot expression.
     *
     * @return
     * @throws Exception
     */
    private ITypeBinding type;

    @Override
    public Set<Snippet> process() {
        snippetSet = new HashSet<>();

        if(Application.isRestore&&(!locationTailored.contains(getStateSnapshot().getLocation().getLineNo())||snapshotSample.contains(getStateSnapshot()))){
            return snippetSet;
        }

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

    protected void building(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand) {
    }

    protected void building(ExpressionToMonitor operand) {
        templateAssignInvoke(operand);
    }

    private void templateAssignInvoke(ExpressionToMonitor operand) {
        String strategyName = getStrategyName("o=valid_invoke");
        for (MethodInvocation invocation : ASTUtils4SelectInvocation.assignableStateInvocation1ForMoreFix(false, getStateSnapshot().getLocation())) {
            constructAndCreate(operand, invocation, strategyName);
        }
    }

    private void templateNegation(ExpressionToMonitor operand) {
        String strategyName = getStrategyName("o=!o");
        PrefixExpression right = ast.newPrefixExpression();
        right.setOperator(PrefixExpression.Operator.NOT);
        right.setOperand((Expression) ASTNode.copySubtree(ast, operand.getExpressionAST()));
        constructAndCreate(operand, right, strategyName);
    }


    private void templateBooleanLiteral(ExpressionToMonitor operand) {
        if (operand.isValidVariable()) {
            Expression constructedExpT = constructAssignment(operand.getExpressionAST(), ast.newBooleanLiteral(true));
            createSnippet(constructedExpT, getStrategyName("o=T"));
            Expression constructedExpF = constructAssignment(operand.getExpressionAST(), ast.newBooleanLiteral(false));
            createSnippet(constructedExpF, getStrategyName("o=F"));
        } else {
            SimpleName tmp_exp = ast.newSimpleName(getReplaceVarName(operand));
            Expression constructedExpT = constructAssignment(tmp_exp, ast.newBooleanLiteral(true));
            createSnippet(operand, tmp_exp, constructedExpT, getStrategyName("o=T"));
            Expression constructedExpF = constructAssignment(tmp_exp, ast.newBooleanLiteral(false));
            createSnippet(operand, tmp_exp, constructedExpF, getStrategyName("o=F"));
        }
    }

    private void templateAssignR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        String strategyName = getStrategyName("l=r");
        checkIfLeftVariableAndConstructSnippet(strategyName,left,right.getExpressionAST());

    }

    private void templateAssignNR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        String strategyName = getStrategyName("l=!r");
        PrefixExpression newRight = ast.newPrefixExpression();
        newRight.setOperator(PrefixExpression.Operator.NOT);
        newRight.setOperand((Expression) ASTNode.copySubtree(ast, right.getExpressionAST()));
        checkIfLeftVariableAndConstructSnippet(strategyName,left,newRight);

    }

    private void templateAssignLandR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        String strategyName = getStrategyName("l=l&&r");

        InfixExpression newRight = ast.newInfixExpression();
        newRight.setOperator(InfixExpression.Operator.AND);
        newRight.setLeftOperand((Expression) ASTNode.copySubtree(ast, left.getExpressionAST()));
        newRight.setRightOperand((Expression) ASTNode.copySubtree(ast, right.getExpressionAST()));
        checkIfLeftVariableAndConstructSnippet(strategyName,left,newRight);

    }

    private void templateAssignLorR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        String strategyName = getStrategyName("l=l||r");
        InfixExpression newRight = ast.newInfixExpression();
        newRight.setOperator(InfixExpression.Operator.OR);
        newRight.setLeftOperand((Expression) ASTNode.copySubtree(ast, left.getExpressionAST()));
        newRight.setRightOperand((Expression) ASTNode.copySubtree(ast, right.getExpressionAST()));
        checkIfLeftVariableAndConstructSnippet(strategyName,left,newRight);
    }

}
