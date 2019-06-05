package hk.polyu.comp.jaid.monitor.snapshot;

import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;

import java.util.*;

/**
 * Created by Max PEI.
 */
public abstract class StateSnapshotExpression extends ExpressionToMonitor {


    public StateSnapshotExpression(Expression expressionAST, ITypeBinding type) {
        super(expressionAST, type);
    }

    public abstract DebuggerEvaluationResult evaluate(ProgramState state);

    public List<ExpressionToMonitor> getOperands() {
        if (operands == null)
            operands = new LinkedList<>();

        return operands;
    }

    public boolean isValidAtLocation(LineLocation location, Map<LineLocation, SortedSet<ExpressionToMonitor>> locationExpressionMap, MethodDeclarationInfoCenter mdi
    ) {
        List<ExpressionToMonitor> operands = getOperands();
        SortedSet<ExpressionToMonitor> validOperands = locationExpressionMap.get(location);
        Set<ExpressionToMonitor> etm_operands = new HashSet<>();
        for (ExpressionToMonitor operand : operands) {
            if (operand.isMethodInvocation()) {
                MethodInvocation mi = (MethodInvocation) operand.getExpressionAST();
                if (mi.getExpression() != null) {
                    ExpressionToMonitor expression_etm = mdi.getExpressionByText(mi.getExpression().toString());
                    if (expression_etm == null) return false;
                    else etm_operands.add(expression_etm);
                }
            }
        }
        return etm_operands.stream().allMatch(x -> validOperands.contains(x) || x.isLiteral());
    }

    private List<ExpressionToMonitor> operands;

}
