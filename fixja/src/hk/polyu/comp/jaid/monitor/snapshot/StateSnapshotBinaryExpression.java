package hk.polyu.comp.jaid.monitor.snapshot;

import hk.polyu.comp.jaid.ast.ExpressionFormatter;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import org.eclipse.jdt.core.dom.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Max PEI.
 */
public class StateSnapshotBinaryExpression extends StateSnapshotExpression{

    public static StateSnapshotBinaryExpression getBinaryExpression(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand, BinaryOperator operator){


        InfixExpression.Operator infixOperator;
        Expression left, right;
        StateSnapshotBinaryExpression result;

        switch (operator){
            case EQUAL:
                infixOperator = InfixExpression.Operator.EQUALS;
                break;
            case NOT_EQUAL:
                infixOperator = InfixExpression.Operator.NOT_EQUALS;
                break;
            case GREATER_THAN:
                infixOperator = InfixExpression.Operator.GREATER;
                break;
            case GREATER_THAN_OR_EQUAL:
                infixOperator = InfixExpression.Operator.GREATER_EQUALS;
                break;
            case LESS_THAN:
                infixOperator = InfixExpression.Operator.LESS;
                break;
            case LESS_THAN_OR_EQUAL:
                infixOperator = InfixExpression.Operator.LESS_EQUALS;
                break;
            case CONDITIONAL_AND:
                infixOperator = InfixExpression.Operator.CONDITIONAL_AND;
                break;
            case CONDITIONAL_OR:
                infixOperator = InfixExpression.Operator.CONDITIONAL_OR;
                break;
            default:
                throw new IllegalStateException();
        }


        AST ast = leftOperand.getExpressionAST().getAST();
        //Check if ParenthesizedExpression is needed
        if(OperatorPrecedence.hasOperatorGreaterEqualPrecedence(infixOperator, leftOperand.getExpressionAST())){
            ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
            parenthesizedExpression.setExpression((Expression) ASTNode.copySubtree(ast, leftOperand.getExpressionAST()));
            left = parenthesizedExpression;
        }
        else{
            left = (Expression) ASTNode.copySubtree(ast, leftOperand.getExpressionAST());
        }

        if(OperatorPrecedence.hasOperatorGreaterEqualPrecedence(infixOperator, rightOperand.getExpressionAST())){
            ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
            parenthesizedExpression.setExpression((Expression) ASTNode.copySubtree(ast, rightOperand.getExpressionAST()));
            right = parenthesizedExpression;
        }
        else{
            right = (Expression) ASTNode.copySubtree(ast, rightOperand.getExpressionAST());
        }

        //Check if there is same existing expression
        String expText = ExpressionFormatter.formatExpression(infixOperator,new String[]{left.toString(),right.toString()});
        if(expText==null) expText=left.toString()+infixOperator.toString()+right.toString();
        result = getExpressionByText(expText);
        if (result != null) return result;

        //Construct exp
        InfixExpression infixExpression = ast.newInfixExpression();
        infixExpression.setOperator(infixOperator);
        infixExpression.setLeftOperand(left);
        infixExpression.setRightOperand(right);
        //Avoid building same Expressions
        String etmText = ExpressionFormatter.formatExpression(infixExpression).toString();
        result = getExpressionByText(etmText);
        if (result == null){
            result = new StateSnapshotBinaryExpression(leftOperand, rightOperand, operator, infixExpression,
                    leftOperand.getExpressionAST().getAST().resolveWellKnownType("boolean"));
            registerExpressionToMonitor(result);
        }
        return result;

    }

    public ExpressionToMonitor getLeftOperand() {
        return leftOperand;
    }

    public ExpressionToMonitor getRightOperand() {
        return rightOperand;
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    @Override
    public DebuggerEvaluationResult evaluate(ProgramState state) {
        DebuggerEvaluationResult leftResult = state.getValue(getLeftOperand());
        DebuggerEvaluationResult rightResult = state.getValue(getRightOperand());

        if(leftResult == null || leftResult.hasSyntaxError() || rightResult == null || rightResult.hasSyntaxError())
            return DebuggerEvaluationResult.getDebuggerEvaluationResultSyntaxError();

        if(leftResult.hasSemanticError() || rightResult.hasSemanticError())
            return DebuggerEvaluationResult.getDebuggerEvaluationResultSemanticError();

        DebuggerEvaluationResult.IntegerDebuggerEvaluationResult leftIntegerResult, rightIntegerResult;
        DebuggerEvaluationResult.LongDebuggerEvaluationResult leftLongResult, rightLongResult;
        DebuggerEvaluationResult.DoubleDebuggerEvaluationResult  leftDoubleResult, rightDoubleResult;
        DebuggerEvaluationResult.FloatDebuggerEvaluationResult   leftFloatResult, rightFloatResult;
        DebuggerEvaluationResult.CharDebuggerEvaluationResult   leftCharResult, rightCharResult;
        DebuggerEvaluationResult.BooleanDebuggerEvaluationResult leftBooleanResult, rightBooleanResult;
        switch (getOperator()){
            case EQUAL:
            case NOT_EQUAL:
                return DebuggerEvaluationResult.getBooleanDebugValue(getOperator() == BinaryOperator.EQUAL ?
                        leftResult.equals(rightResult) : !leftResult.equals(rightResult));

            case GREATER_THAN:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isGreater(rightIntegerResult));
                } else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isGreater(rightLongResult));
                } else if(leftResult instanceof DebuggerEvaluationResult.FloatDebuggerEvaluationResult){
                    leftFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) leftResult;
                    rightFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftFloatResult.isGreater(rightFloatResult));
                } else if(leftResult instanceof DebuggerEvaluationResult.CharDebuggerEvaluationResult){
                    leftCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) leftResult;
                    rightCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftCharResult.isGreater(rightCharResult));
                } else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isGreater(rightDoubleResult));
                }

            case GREATER_THAN_OR_EQUAL:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isGreaterEqual(rightIntegerResult));
                }
                else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isGreaterEqual(rightLongResult));

                }else if(leftResult instanceof DebuggerEvaluationResult.FloatDebuggerEvaluationResult){
                    leftFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) leftResult;
                    rightFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftFloatResult.isGreaterEqual(rightFloatResult));

                }else if(leftResult instanceof DebuggerEvaluationResult.CharDebuggerEvaluationResult){
                    leftCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) leftResult;
                    rightCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftCharResult.isGreaterEqual(rightCharResult));
                }
                else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isGreaterEqual(rightDoubleResult));
                }

            case LESS_THAN:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isLess(rightIntegerResult));
                }
                else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isLess(rightLongResult));

                }else if(leftResult instanceof DebuggerEvaluationResult.FloatDebuggerEvaluationResult){
                    leftFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) leftResult;
                    rightFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftFloatResult.isLess(rightFloatResult));

                }else if(leftResult instanceof DebuggerEvaluationResult.CharDebuggerEvaluationResult){
                    leftCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) leftResult;
                    rightCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftCharResult.isLess(rightCharResult));
                }
                else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isLess(rightDoubleResult));
                }

            case LESS_THAN_OR_EQUAL:
                if(leftResult instanceof DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) {
                    leftIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) leftResult;
                    rightIntegerResult = (DebuggerEvaluationResult.IntegerDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftIntegerResult.isLessEqual(rightIntegerResult));
                }
                else if(leftResult instanceof DebuggerEvaluationResult.LongDebuggerEvaluationResult){
                    leftLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) leftResult;
                    rightLongResult = (DebuggerEvaluationResult.LongDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftLongResult.isLessEqual(rightLongResult));

                }else if(leftResult instanceof DebuggerEvaluationResult.FloatDebuggerEvaluationResult){
                    leftFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) leftResult;
                    rightFloatResult = (DebuggerEvaluationResult.FloatDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftFloatResult.isLessEqual(rightFloatResult));

                }else if(leftResult instanceof DebuggerEvaluationResult.CharDebuggerEvaluationResult){
                    leftCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) leftResult;
                    rightCharResult = (DebuggerEvaluationResult.CharDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftCharResult.isLessEqual(rightCharResult));
                }
                else{
                    leftDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) leftResult;
                    rightDoubleResult = (DebuggerEvaluationResult.DoubleDebuggerEvaluationResult) rightResult;
                    return DebuggerEvaluationResult.getBooleanDebugValue(leftDoubleResult.isLessEqual(rightDoubleResult));
                }

            case CONDITIONAL_AND:
                leftBooleanResult  = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) leftResult;
                rightBooleanResult = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) rightResult;
                return DebuggerEvaluationResult.getBooleanDebugValue(leftBooleanResult.getValue() && rightBooleanResult.getValue());

            case CONDITIONAL_OR:
                leftBooleanResult  = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) leftResult;
                rightBooleanResult = (DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) rightResult;
                return DebuggerEvaluationResult.getBooleanDebugValue(leftBooleanResult.getValue() || rightBooleanResult.getValue());

            default:
                throw new IllegalStateException();
        }
    }

    private static Map<String, StateSnapshotBinaryExpression> allStateSnapshotExpressionMap;

    private static Map<String, StateSnapshotBinaryExpression> getAllStateSnapshotExpressionMap() {
        if(allStateSnapshotExpressionMap ==null)
            allStateSnapshotExpressionMap=new HashMap<>();
        return allStateSnapshotExpressionMap;
    }
    private static StateSnapshotBinaryExpression getExpressionByText(String etmText){
        return getAllStateSnapshotExpressionMap().get(etmText.trim());
    }

    private static void registerExpressionToMonitor(StateSnapshotBinaryExpression expressionToMonitor){
        if(!getAllStateSnapshotExpressionMap().containsKey(expressionToMonitor.getText().trim()))
            getAllStateSnapshotExpressionMap().put(expressionToMonitor.getText().trim(),expressionToMonitor);
    }


    protected StateSnapshotBinaryExpression(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand,
                                          BinaryOperator operator, Expression wholeExpression, ITypeBinding typeBinding){
        super(wholeExpression, typeBinding);

        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
        this.operator = operator;

        this.getOperands().add(leftOperand);
        this.getOperands().add(rightOperand);
    }

    public enum BinaryOperator {
        EQUAL("=="), NOT_EQUAL("!="),
        GREATER_THAN(">"), GREATER_THAN_OR_EQUAL(">="),
        LESS_THAN("<"), LESS_THAN_OR_EQUAL("<="),
        CONDITIONAL_OR("||"), CONDITIONAL_AND("&&");

        private String symbol;

        BinaryOperator(String symbol) {
            this.symbol = symbol;
        }

        public String toString() {
            return symbol;
        }
    }

    private final ExpressionToMonitor leftOperand;
    private final ExpressionToMonitor rightOperand;
    private final BinaryOperator operator;


}
