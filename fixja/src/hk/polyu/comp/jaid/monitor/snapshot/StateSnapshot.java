package hk.polyu.comp.jaid.monitor.snapshot;

import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;

import java.util.HashMap;
import java.util.Map;

import static hk.polyu.comp.jaid.util.CommonUtils.checkParenthesizeNeeded;

/**
 * Created by Max PEI.
 */
public class StateSnapshot {
    private long ID;

    private final LineLocation location;
    private final StateSnapshotExpression snapshotExpression;
    private final DebuggerEvaluationResult value;

    private int occurrenceInPassingNoDup;
    private int occurrenceInFailingNoDup;
    private int occurrenceInPassing;
    private int occurrenceInFailing;
    private double distanceToFailure;

    private Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, Double> suspiciousness = new HashMap<>();
    private boolean shouldSkipSchemaBCD = false;

    public StateSnapshot(LineLocation location, StateSnapshotExpression snapshotExpression, DebuggerEvaluationResult value) {
        this.location = location;
        this.snapshotExpression = snapshotExpression;
        this.value = value;
        this.ID = CommonUtils.getId(contentToString());
    }

    public long getID() {
        return ID;
    }

    public LineLocation getLocation() {
        return location;
    }

    public StateSnapshotExpression getSnapshotExpression() {
        return snapshotExpression;
    }

    private boolean instantiateSchemaC = true;

    public boolean shouldInstantiateSchemaC() {
        return instantiateSchemaC;
    }

    public void disableInstantiateSchemaC() {
        this.instantiateSchemaC = false;
    }

    private Expression failingStateExpression;

    public Expression getFailingStateExpression() {
        if (failingStateExpression == null) {
            AST ast = getSnapshotExpression().getExpressionAST().getAST();
            InfixExpression infixExpression = ast.newInfixExpression();
            Expression snapshotExp = checkParenthesizeNeeded((Expression) ASTNode.copySubtree(ast, getSnapshotExpression().getExpressionAST()));
            infixExpression.setLeftOperand(snapshotExp);
            infixExpression.setRightOperand(ast.newBooleanLiteral(((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) value).getValue()));
            infixExpression.setOperator(InfixExpression.Operator.EQUALS);
            failingStateExpression = infixExpression;
        }
        return failingStateExpression;
    }

    public Expression getFailingStateExpressionNegation() {
        AST ast = getSnapshotExpression().getExpressionAST().getAST();
        InfixExpression infixExpression = ast.newInfixExpression();
        Expression snapshotExp = checkParenthesizeNeeded((Expression) ASTNode.copySubtree(ast, getSnapshotExpression().getExpressionAST()));
        infixExpression.setLeftOperand(snapshotExp);
        infixExpression.setRightOperand(ast.newBooleanLiteral(((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) value).getValue()));
        infixExpression.setOperator(InfixExpression.Operator.NOT_EQUALS);
        return infixExpression;
    }

    public boolean isShouldSkipSchemaBCD() {
        return shouldSkipSchemaBCD;
    }

    public void setShouldSkipSchemaBCD() {
        this.shouldSkipSchemaBCD = true;
    }

    public DebuggerEvaluationResult getValue() {
        return value;
    }

    public double getSuspiciousness(AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm) {
        if (suspiciousness.containsKey(sbflAlgorithm))
            return suspiciousness.get(sbflAlgorithm);
        else
            throw new IllegalStateException("No suspicious score for algorithm:" + sbflAlgorithm.toString());
    }

    public double getSuspiciousness() {
        if (suspiciousness.size() > 0)
            if (suspiciousness.containsKey(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix))
                return suspiciousness.get(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix);
            else
                return suspiciousness.values().iterator().next();
        throw new IllegalStateException("There is no any suspicious score");
    }

    public int getOccurrenceInFailing() {
        return occurrenceInFailing;
    }

    public void increaseOccurrenceInPassing() {
        this.occurrenceInPassing++;
    }

    public void increaseOccurrenceInFailing() {
        this.occurrenceInFailing++;
    }

    public void increaseOccurrenceInPassingNoDup() {
        this.occurrenceInPassingNoDup++;
    }

    public void increaseOccurrenceInFailingNoDup() {
        this.occurrenceInFailingNoDup++;
    }

    public void setDistanceToFailure(double distanceToFailure) {
        // Ignore distance greater than MAXIMUM_DISTANCE_TO_FAILURE.
        this.distanceToFailure = Math.min(distanceToFailure, LineLocation.getMaximumDistanceToFailure());
    }

    public void computeSuspiciousness(MethodDeclarationInfoCenter infoCenter, AbsSuspiciousnessAlgorithm computeAlgorithm) {
        double similarityContribution = ExpressionToMonitor.similarityBetween(getSnapshotExpression().getSubExpressions(), infoCenter.getExpressionsAppearAtLocationMap().get(getLocation()));

        if (computeAlgorithm.getSbflAlgorithm().equals(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AutoFix)||
                computeAlgorithm.getSbflAlgorithm().equals(AbsSuspiciousnessAlgorithm.SbflAlgorithm.AFFormula) )
            suspiciousness.put(computeAlgorithm.getSbflAlgorithm(),
                    computeAlgorithm.computeSuspiciousness(occurrenceInFailing, occurrenceInPassing, distanceToFailure, similarityContribution));
        else
            suspiciousness.put(computeAlgorithm.getSbflAlgorithm(),
                    computeAlgorithm.computeSuspiciousness(occurrenceInFailingNoDup, occurrenceInPassingNoDup, distanceToFailure, similarityContribution));
    }

    private String getSsScoreStr() {
        StringBuilder sb = new StringBuilder();
        for (AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm : suspiciousness.keySet()) {
            sb.append(sbflAlgorithm.toString()).append("=").append(suspiciousness.get(sbflAlgorithm)).append("|");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "StateSnapshot{" +
                "ID=" + ID +
                ", location=" + location +
                ", snapshotExpression=" + snapshotExpression +
                ", value=" + value +
                ", occurrenceInPassingNoDup=" + occurrenceInPassingNoDup +
                ", occurrenceInFailingNoDup=" + occurrenceInFailingNoDup +
                ", occurrenceInPassing=" + occurrenceInPassing +
                ", occurrenceInFailing=" + occurrenceInFailing +
                ", suspiciousness=" + getSsScoreStr() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StateSnapshot that = (StateSnapshot) o;

        if (!getLocation().equals(that.getLocation())) return false;
        if (!getSnapshotExpression().equals(that.getSnapshotExpression())) return false;
        return getValue().equals(that.getValue());
    }

    @Override
    public int hashCode() {
        int result = getLocation().hashCode();
        result = 31 * result + getSnapshotExpression().getText().hashCode();
        result = 31 * result + getValue().toString().hashCode();
        return result;
    }

    public String contentToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("snapshotExpression=").append(snapshotExpression);
        sb.append("value=").append(value);
        if (getLocation() != null)
            sb.append("\n, location=").append(location);
        sb.append("}\n");
        return sb.toString();
    }
}
