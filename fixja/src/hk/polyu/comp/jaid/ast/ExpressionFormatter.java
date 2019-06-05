package hk.polyu.comp.jaid.ast;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ExpressionFormatter extends ASTVisitor {
    Expression expressionAST;
    AST ast;

    public static Expression formatExpression(Expression originalExpressionAST) {
        ExpressionFormatter formatter = new ExpressionFormatter(originalExpressionAST);
        return formatter.getExpressionAST();

    }

    private ExpressionFormatter(Expression expressionAST) {
        this.ast = expressionAST.getAST();
        this.expressionAST = (Expression) ASTNode.copySubtree(this.ast, expressionAST);
        this.expressionAST.accept(this);
    }

    public Expression getExpressionAST() {
        return expressionAST;
    }


    /**
     * This method is called after all of the given node's children have been visited (or immediately, if visit returned false).
     *
     * @param node
     */
    @Override
    public void endVisit(InfixExpression node) {
        super.endVisit(node);

        InfixExpression.Operator op = node.getOperator();
        InfixExpression.Operator newOp = getNewOperator(op);
        if (newOp != null) formatting(node, newOp);
    }

    private void formatting(InfixExpression node, InfixExpression.Operator newOp) {
        if (node.hasExtendedOperands()) {
            if (node.getOperator().equals(InfixExpression.Operator.PLUS)) {
                List nodeList = new ArrayList();
                nodeList.add(ASTNode.copySubtree(this.ast, node.getLeftOperand()));
                nodeList.add(ASTNode.copySubtree(this.ast, node.getRightOperand()));
                for (Object extendedNode : node.extendedOperands()) {
                    nodeList.add(ASTNode.copySubtree(this.ast, (ASTNode) extendedNode));
                }
                nodeList = rankNodeList(nodeList);

                node.setLeftOperand((Expression) nodeList.get(0));
                nodeList.remove(0);
                node.setRightOperand((Expression) nodeList.get(0));
                nodeList.remove(0);

                node.extendedOperands().clear();
                node.extendedOperands().addAll(nodeList);
            } else {
                throw new RuntimeException("Un expected InfixExpression with extendedOperands" + node.toString());
            }
        } else {
            if (isSwapNeeded(node)) {
                Expression oLeftExp = (Expression) ASTNode.copySubtree(this.ast, node.getLeftOperand());
                Expression oRightExp = (Expression) ASTNode.copySubtree(this.ast, node.getRightOperand());
                node.setLeftOperand(oRightExp);
                node.setRightOperand(oLeftExp);
                node.setOperator(newOp);
            }
        }
    }

    private boolean isSwapNeeded(InfixExpression node) {
        return node.getLeftOperand().toString().compareTo(node.getRightOperand().toString()) > 0;
    }

    // Sort all operands of the infix expression in string order
    private static List rankNodeList(List nodeList) {
        return (List) nodeList.stream().sorted((n1, n2) -> n1.toString().compareTo(n2.toString())).collect(Collectors.toList());
    }


    public static InfixExpression.Operator getNewOperator(InfixExpression.Operator op) {
        if (op.equals(InfixExpression.Operator.PLUS))
            return InfixExpression.Operator.PLUS;
        else if (op.equals(InfixExpression.Operator.LESS))
            return InfixExpression.Operator.GREATER;
        else if (op.equals(InfixExpression.Operator.GREATER))
            return InfixExpression.Operator.LESS;
        else if (op.equals(InfixExpression.Operator.LESS_EQUALS))
            return InfixExpression.Operator.GREATER_EQUALS;
        else if (op.equals(InfixExpression.Operator.GREATER_EQUALS))
            return InfixExpression.Operator.LESS_EQUALS;
        else if (op.equals(InfixExpression.Operator.EQUALS))
            return InfixExpression.Operator.EQUALS;
        else if (op.equals(InfixExpression.Operator.NOT_EQUALS))
            return InfixExpression.Operator.NOT_EQUALS;
        return null;
    }

    public static String formatExpression(InfixExpression.Operator op, String[] operands) {
        List newOperands = rankNodeList(Arrays.asList(operands));
        StringBuilder exp = new StringBuilder();
        InfixExpression.Operator newOp = getNewOperator(op);
        if (newOp != null)
            if (operands.length == 2) {
                exp.append(newOperands.get(0)).append(op).append(newOperands.get(1));
            } else if (operands.length > 2) {
                if (newOp.equals(InfixExpression.Operator.PLUS)) {
                    for (Object operand : newOperands) {
                        if (exp.length() > 0)
                            exp.append(InfixExpression.Operator.PLUS);
                        exp.append(operand);
                    }
                } else
                    throw new RuntimeException("Un expected InfixExpression operands string with extendedOperands");
            }
        return exp.toString();

    }


}
