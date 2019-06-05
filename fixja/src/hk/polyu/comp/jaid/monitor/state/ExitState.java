package hk.polyu.comp.jaid.monitor.state;

import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotExpression;
import hk.polyu.comp.jaid.tester.Tester;

import java.util.Set;

import static hk.polyu.comp.jaid.monitor.LineLocation.newLineLocation;

public class ExitState extends ProgramState {
    private boolean hasException;
    private String thrownExceptionType;
    public static final ExitState divider = new ExitState(newLineLocation(null, 0));

    public ExitState(LineLocation location) {
        super(location);
    }

    private ExitState(ExitState exitState) {
        super(exitState.location);
        this.hasException = exitState.isHasException();
        this.thrownExceptionType = (exitState.getThrownExceptionType());
    }

    public boolean isHasException() {
        return hasException;
    }

    public void setHasException(DebuggerEvaluationResult hasException) {
        this.hasException = ((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) hasException).getValue();
    }

    public String getThrownExceptionType() {
        return thrownExceptionType;
    }

    public void setThrownExceptionType(DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult thrownExceptionType) {
        this.thrownExceptionType = thrownExceptionType.getObjectToString();
    }

    public ExitState getExtendedStateWithoutErrorResult(Set<StateSnapshotExpression> extendedExpressions) {
        ExitState newState = new ExitState(this);
        for (StateSnapshotExpression expression : extendedExpressions) {
            DebuggerEvaluationResult evaluationResult = expression.evaluate(this);
            //newState dose not contains expressions that has error DebuggerEvaluationResult
            if (!(evaluationResult.hasSyntaxError() || evaluationResult.hasSemanticError()))
                newState.extend(expression, evaluationResult);
        }
        return newState;
    }

    @Override
    public void extend(ExpressionToMonitor expressionToMonitor, DebuggerEvaluationResult result) {
        super.extend(expressionToMonitor, result);
        if (expressionToMonitor.getText().equals(Tester.HAS_EXCEPTION))
            setHasException(result);
        else if (expressionToMonitor.getText().equals(Tester.EXCEPTION_CLASS_TYPE))
            setThrownExceptionType((DebuggerEvaluationResult.ReferenceDebuggerEvaluationResult) result);
    }

    @Override
    public String toString() {
        String eStr = getThrownExceptionType() == null ? "null" : thrownExceptionType;
        return "ExitState{" +
                "location=" + location.getLineNo() +
                ", hasException=" + isHasException() +
                ", thrownExceptionType=" + eStr +
                ", expressionToValueMap=[" +
                statesToString() +
                "]\n" + getExpressionToValueMap().size() + "}";
    }
}
