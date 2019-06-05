package hk.polyu.comp.jaid.monitor.snapshot;

import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import org.eclipse.jdt.core.dom.ThisExpression;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Max PEI.
 */
public class StateSnapshotExpressionBuilder {

    private List<ExpressionToMonitor> elementExpressions;
    private List<ExpressionToMonitor> elementIntegers;
    private Set<StateSnapshotExpression> stateSnapshotExpressions;

    public void build(List<ExpressionToMonitor> elementExpressions, List<ExpressionToMonitor> elementIntegers) {
        this.elementExpressions = elementExpressions;
        this.elementIntegers = elementIntegers;

        stateSnapshotExpressions = new HashSet<>();
        stateSnapshotExpressions.addAll(buildWithNumericExpressions());
        stateSnapshotExpressions.addAll(buildWithBooleanExpressions());
        stateSnapshotExpressions.addAll(buildWithReferenceExpressions());
    }

    public void addBooleanETM(Collection<ExpressionToMonitor> enrichingBooleanETM) {
        enrichingBooleanETM.stream().filter(x -> x.isBooleanType()).forEach(x->stateSnapshotExpressions.add(
                StateSnapshotUnaryExpression.getUnaryExpression(x, StateSnapshotUnaryExpression.UnaryOperator.No_OP)
        ));
    }
    public void BuildEnrichedReferenceETM(Collection<ExpressionToMonitor> enrichingRefETM) {
        enrichingRefETM.stream().filter(x -> x.isReferenceType()).forEach(x->stateSnapshotExpressions.add(
                StateSnapshotUnaryExpression.getUnaryExpression(x, StateSnapshotUnaryExpression.UnaryOperator.IS_NULL)
        ));
    }

    /**
     * Building snapshots for method exit
     * @param elementExpressions
     * @param elementIntegers
     */
    public void buildForExit(List<ExpressionToMonitor> elementExpressions, List<ExpressionToMonitor> elementIntegers) {
        this.elementExpressions = elementExpressions;
        this.elementIntegers = elementIntegers;

        stateSnapshotExpressions = new HashSet<>();
        stateSnapshotExpressions.addAll(buildWithNumericExpressions());
        stateSnapshotExpressions.addAll(buildWithReferenceExpressions());
        addBooleanETM(elementExpressions);

    }

    public Set<StateSnapshotExpression> getStateSnapshotExpressions() {
        return stateSnapshotExpressions;
    }

    private List<StateSnapshotExpression> buildWithNumericExpressions() {
        List<StateSnapshotExpression> result = new LinkedList<>();
        List<ExpressionToMonitor> numericExpressions = elementExpressions.stream().filter(x -> x.isNumericType()).collect(Collectors.toList());
        int size = numericExpressions.size();

        // two expressions
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                buildWithTwoNumericExpressions(numericExpressions.get(i), numericExpressions.get(j), result);
            }
        }

        // expression and constant
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < this.elementIntegers.size(); j++) {
                buildWithTwoNumericExpressions(numericExpressions.get(i), this.elementIntegers.get(j), result);
            }
        }
        return result;
    }

    private void buildWithTwoNumericExpressions(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand, List<StateSnapshotExpression> result) {
        if (leftOperand.getType().equals(rightOperand.getType())) {
            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.EQUAL));
//            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.NOT_EQUAL));
            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.GREATER_THAN));
            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.GREATER_THAN_OR_EQUAL));
//            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.LESS_THAN));
//            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.LESS_THAN_OR_EQUAL));
        }
    }

    private List<StateSnapshotExpression> buildWithBooleanExpressions() {
        List<StateSnapshotExpression> result = new LinkedList<>();
        List<ExpressionToMonitor> booleanExpressions = elementExpressions.stream().filter(x -> x.isBooleanType()).collect(Collectors.toList());
        int size = booleanExpressions.size();
        for (int i = 0; i < size; i++) {
            buildWithOneBooleanExpression(booleanExpressions.get(i), result);
        }

        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                buildWithTwoBooleanExpressions(booleanExpressions.get(i), booleanExpressions.get(j), result);
            }
        }
        return result;
    }

    private void buildWithTwoBooleanExpressions(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand, List<StateSnapshotExpression> result) {
        result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.EQUAL));
//        result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.NOT_EQUAL));
        result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.CONDITIONAL_AND));
        result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.CONDITIONAL_OR));
    }

    private void buildWithOneBooleanExpression(ExpressionToMonitor operand, List<StateSnapshotExpression> result) {
        result.add(StateSnapshotUnaryExpression.getUnaryExpression(operand, StateSnapshotUnaryExpression.UnaryOperator.No_OP));
//        result.add(StateSnapshotUnaryExpression.getUnaryExpression(operand, StateSnapshotUnaryExpression.UnaryOperator.NEGATION));
    }

    private List<StateSnapshotExpression> buildWithReferenceExpressions() {
        List<StateSnapshotExpression> result = new LinkedList<>();

        List<ExpressionToMonitor> referenceExpressions = elementExpressions.stream()
                .filter(x -> (x.isReferenceType()) && !(x.getExpressionAST() instanceof ThisExpression))
                .collect(Collectors.toList());
        int size = referenceExpressions.size();
        for (int i = 0; i < size; i++) {
            buildWithOneReferenceExpression(referenceExpressions.get(i), result);
        }

        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                buildWithTwoReferenceExpressions(referenceExpressions.get(i), referenceExpressions.get(j), result);
            }
        }
        return result;
    }

    private void buildWithTwoReferenceExpressions(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand, List<StateSnapshotExpression> result) {
        if (shouldBuildSnapshotExp(leftOperand, rightOperand)) {
            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.EQUAL));
//            result.add(StateSnapshotBinaryExpression.getBinaryExpression(leftOperand, rightOperand, StateSnapshotBinaryExpression.BinaryOperator.NOT_EQUAL));
        }
    }

    private void buildWithOneReferenceExpression(ExpressionToMonitor operand, List<StateSnapshotExpression> result) {
        result.add(StateSnapshotUnaryExpression.getUnaryExpression(operand, StateSnapshotUnaryExpression.UnaryOperator.IS_NULL));
//        result.add(StateSnapshotUnaryExpression.getUnaryExpression(operand, StateSnapshotUnaryExpression.UnaryOperator.IS_NOT_NULL));
    }

    private boolean shouldBuildSnapshotExp(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand) {
        boolean result = false;
        boolean enableCompatible = false;//TODO: add this attribute to the JAID config.
        if (leftOperand.getType() != null && rightOperand.getType() != null) {
            if (leftOperand.getType().getQualifiedName().equals(rightOperand.getType().getQualifiedName()))
                result = true;
            if (enableCompatible) {
                try {
                    if (leftOperand.getType().isCastCompatible(rightOperand.getType())
                            && rightOperand.getType().isCastCompatible(leftOperand.getType()))
                        result = true;
                } catch (Exception e) {
                }// we don't know, so be conservative.
            }
        }
        return result;
    }


}
