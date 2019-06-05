package hk.polyu.comp.jaid.fixaction.strategy.preliminary;

import hk.polyu.comp.jaid.ast.ASTUtils4SelectInvocation;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import org.eclipse.jdt.core.dom.*;

import static hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils.getReplaceVarName;

/**
 * Created by Ls CHEN
 */
public class BasicStrategy4Numeric extends AbsBasicStrategy {


    @Override
    protected boolean isDesiredType() {
        return getStateSnapshot().getSnapshotExpression().getOperands().get(0).isNumericType();
    }

    /**
     * Build snippetMap for a binary snapshot expression.
     *
     * @return
     * @throws Exception
     */
    protected void building(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand) {
        templateAssignR2L(leftOperand, rightOperand);
        templateAssignR2L(rightOperand, leftOperand);
    }

    protected void building(ExpressionToMonitor operand) {
        templateAssignInvoke(operand);
    }


    private void templateSelfIncrement(ExpressionToMonitor operand) {
        String strategyName = getStrategyName("o++");
        if (operand.isValidVariable()) {
            PostfixExpression postfixExpression = ast.newPostfixExpression();
            postfixExpression.setOperator(PostfixExpression.Operator.INCREMENT);
            postfixExpression.setOperand((Expression) ASTNode.copySubtree(ast, operand.getExpressionAST()));
            createSnippet(postfixExpression, strategyName);
        } else {
            SimpleName tmp_exp = ast.newSimpleName(getReplaceVarName(operand));
            PostfixExpression postfixExpression = ast.newPostfixExpression();
            postfixExpression.setOperator(PostfixExpression.Operator.INCREMENT);
            postfixExpression.setOperand(tmp_exp);
            createSnippet(operand, tmp_exp, postfixExpression, strategyName);
        }
    }

    private void templateSelfDecrement(ExpressionToMonitor operand) {
        String strategyName = getStrategyName("o--");
        if (operand.isValidVariable()) {
            PostfixExpression postfixExpression = ast.newPostfixExpression();
            postfixExpression.setOperator(PostfixExpression.Operator.DECREMENT);
            postfixExpression.setOperand((Expression) ASTNode.copySubtree(ast, operand.getExpressionAST()));
            createSnippet(postfixExpression, strategyName);
        } else {
            SimpleName tmp_exp = ast.newSimpleName(getReplaceVarName(operand));
            PostfixExpression postfixExpression = ast.newPostfixExpression();
            postfixExpression.setOperator(PostfixExpression.Operator.DECREMENT);
            postfixExpression.setOperand(tmp_exp);
            createSnippet(operand, tmp_exp, postfixExpression, strategyName);
        }
    }

    private void templateAssignNumberLiteral(ExpressionToMonitor operand) {
        NumberLiteral one = ast.newNumberLiteral("1");
        NumberLiteral zero = ast.newNumberLiteral("0");
        PrefixExpression minusOne = ast.newPrefixExpression();
        minusOne.setOperator(PrefixExpression.Operator.MINUS);
        minusOne.setOperand(ast.newNumberLiteral("1"));
        assignNumberLiteral(operand, one);
        assignNumberLiteral(operand, zero);
        assignNumberLiteral(operand, minusOne);

    }

    private void assignNumberLiteral(ExpressionToMonitor operand, Expression numberLiteral) {
        String strategyName = getStrategyName("o=" + numberLiteral.toString());
        checkIfLeftVariableAndConstructSnippet(strategyName, operand, numberLiteral);
    }

    private void templateAssignInvoke(ExpressionToMonitor operand) {
        String strategyName = getStrategyName("o=valid_invoke");
        for (MethodInvocation invocation : ASTUtils4SelectInvocation.assignableStateInvocation1(true, getStateSnapshot().getLocation())) {
            constructAndCreate(operand, invocation, strategyName);
        }
    }

    private void templateAssignR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        if (left.getType().getQualifiedName().equals(right.getType().getQualifiedName())) {
            String strategyName = getStrategyName("l=r");
            checkIfLeftVariableAndConstructSnippet(strategyName, left, right.getExpressionAST());
        }
    }

    private void templateAssignLpR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        String strategyName = getStrategyName("l=l+r");

        InfixExpression newRight = ast.newInfixExpression();
        newRight.setOperator(InfixExpression.Operator.PLUS);
        newRight.setLeftOperand((Expression) ASTNode.copySubtree(ast, left.getExpressionAST()));
        newRight.setRightOperand((Expression) ASTNode.copySubtree(ast, right.getExpressionAST()));

        checkIfLeftVariableAndConstructSnippet(strategyName, left, newRight);
    }

    private void templateAssignLmR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        String strategyName = getStrategyName("l=l-r");

        InfixExpression newRight = ast.newInfixExpression();
        newRight.setOperator(InfixExpression.Operator.MINUS);
        newRight.setLeftOperand((Expression) ASTNode.copySubtree(ast, left.getExpressionAST()));
        newRight.setRightOperand((Expression) ASTNode.copySubtree(ast, right.getExpressionAST()));

        checkIfLeftVariableAndConstructSnippet(strategyName, left, newRight);
    }

}
