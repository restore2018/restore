package hk.polyu.comp.jaid.fixaction.strategy.preliminary;

import hk.polyu.comp.jaid.ast.ASTUtils4SelectInvocation;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;

import static hk.polyu.comp.jaid.ast.ASTUtils4SelectInvocation.isThrow;
import static hk.polyu.comp.jaid.util.CommonUtils.checkStmt;


/**
 * Created by Ls CHEN
 */
public class BasicStrategy4Reference extends AbsBasicStrategy {

    private boolean appendParamInvocation = false;//TODO: add this attribute to the JAID config.

    public void setAppendParamInvocation(boolean appendParamInvocation) {
        this.appendParamInvocation = appendParamInvocation;
    }

    @Override
    protected boolean isDesiredType() {
        return getStateSnapshot().getSnapshotExpression().getOperands().get(0).isReferenceType();
    }

    protected void building(ExpressionToMonitor leftOperand, ExpressionToMonitor rightOperand) {
        templateAssignR2L(leftOperand, rightOperand);
        templateAssignR2L(rightOperand, leftOperand);
    }

    protected void building(ExpressionToMonitor operand) {
        templateAssignNull(operand);
        templateAppendInvocation(operand);
    }

    private void templateAssignR2L(ExpressionToMonitor left, ExpressionToMonitor right) {
        if (!left.getText().equals("this")
                && left.getType().getQualifiedName().equals(right.getType().getQualifiedName())) {
            String strategyName = getStrategyName("VrefL=VrefR");
            checkIfLeftVariableAndConstructSnippet(strategyName, left, right.getExpressionAST());
        }
    }

    private void templateAssignNull(ExpressionToMonitor operand) {
        String strategyName = getStrategyName("o=null");
        if (operand.getText().equals("this")) return;
        NullLiteral nullLiteral = ast.newNullLiteral();
        constructAndCreate(operand, nullLiteral, strategyName);
    }

    /**
     * append method invocation to the reference type expression
     */
    private void templateAppendInvocation(ExpressionToMonitor operand) {
        if (!operand.isMethodInvocation() && !operand.isValidVariable()) return;
        if (operand.isMethodInvocation() && getType().getQualifiedName().equals("java.lang.String")) return;
        IMethodBinding[] methodBindings = getType().getDeclaredMethods();
        List<IMethodBinding> imbs = ASTUtils4SelectInvocation.selectChangeStateMethods(methodBindings, getStateSnapshot().getLocation());
        for (IMethodBinding imb : imbs) {
            MethodInvocation invocation;
            if (imb.getParameterTypes().length == 0) {
                invocation = CommonUtils.appendInvoking(operand.getExpressionAST(), imb.getName(), null);
                checkInvocationAndCreateSnippet(invocation, imb);
            } else if (appendParamInvocation) {
                //get parameters' name
                Set<List<ASTNode>> paramsList = ASTUtils4SelectInvocation.getCombinedParametersName(imb, getStateSnapshot().getLocation());
                for (List<ASTNode> params : paramsList) {
                    invocation = CommonUtils.appendInvoking(operand.getExpressionAST(), imb.getName(), params);
                    checkInvocationAndCreateSnippet(invocation, imb);
                }
            }
        }

        MethodDeclarationInfoCenter infoCenter = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter();
        for (ExpressionToMonitor expWithSideEffect : infoCenter.getExpressionsToMonitorWithSideEffect()) {
            if (expWithSideEffect.getExpressionAST() instanceof MethodInvocation) {
                MethodInvocation invocation = (MethodInvocation) ASTNode.copySubtree(ast,expWithSideEffect.getExpressionAST());
                checkInvocationAndCreateSnippet(invocation);
            }
        }
    }


    /**
     * If the snippet should be a method invocation with side effect
     */
    private boolean isDesiredInvocation(Expression action) {
        for (ExpressionToMonitor etm : getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getAllEnrichedEtmWithinMethod()) {
            if (etm.getText().equals(action.toString()))
                if (etm.isSideEffectFree()) {
                    return false;
                } else {
                    return true;
                }
        }
        return true;
    }

    private void checkInvocationAndCreateSnippet(MethodInvocation invocation, IMethodBinding imb) {
        Statement action;
        if (isDesiredInvocation(invocation)) {
            if (isThrow(imb))
                action = CommonUtils.appendThrowableInvoking(invocation);
            else
                action = checkStmt(invocation);
            createSnippet(action, getStrategyName("o.invoke"));
        }
    }

    private void checkInvocationAndCreateSnippet(MethodInvocation invocation) {
        Statement action;
        action = checkStmt(invocation);
        createSnippet(action, getStrategyName("o.invoke"));
    }

}
