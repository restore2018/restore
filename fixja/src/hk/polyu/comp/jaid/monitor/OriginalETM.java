package hk.polyu.comp.jaid.monitor;

import hk.polyu.comp.jaid.ast.ExpressionFormatter;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;

/**
 * Created by Ls CHEN.
 */
public class OriginalETM extends ExpressionToMonitor {

    public static ExpressionToMonitor construct(Expression expressionAST, ITypeBinding type) {
        //use existing ETM if there is any
        Expression formattedExp = ExpressionFormatter.formatExpression(expressionAST);
        ExpressionToMonitor etm = infoCenter.getExpressionByText(getText(formattedExp.toString()));
        if (etm != null) return etm;
        //create originalETM
        OriginalETM originalETM = new OriginalETM(formattedExp, type);
        originalETM.setOriginalExpressionAST(expressionAST);
        originalETM.setBinding(CommonUtils.resolveBinding4Variables(expressionAST));
        //register new ETM
        infoCenter.registerExpressionToMonitor(originalETM);
        return originalETM;
    }

    public static ExpressionToMonitor construct(Expression expressionAST, ITypeBinding type,boolean isForMoreFix) {
        //use existing ETM if there is any
        Expression formattedExp = ExpressionFormatter.formatExpression(expressionAST);
        ExpressionToMonitor etm = infoCenter.getExpressionByText(getText(formattedExp.toString()));
       // if (etm != null) return etm;

        //String a = getText(formattedExp.toString());
        //String b = formattedExp.toString();

        //IMethodBinding[] methodBindings = expressionAST.resolveTypeBinding().getDeclaredMethods();
        //if (methodBindings.length == 0) methodBindings = expressionAST.resolveTypeBinding().getTypeDeclaration().getDeclaredMethods();

        OriginalETM originalETM = new OriginalETM(formattedExp, type);
        originalETM.setOriginalExpressionAST(expressionAST);
        originalETM.setBinding(CommonUtils.resolveBinding4Variables(expressionAST));
        infoCenter.registerExpressionToMonitor(originalETM);
        //Expression d = originalETM.getExpressionAST();
        //ITypeBinding e = originalETM.getType();
        //if(e==null){
        //    return originalETM;
        //}
        //IMethodBinding[] f = e.getDeclaredMethods();

        //if (methodBindings.length == 0) methodBindings2 = originalETM.getExpressionAST().resolveTypeBinding().getTypeDeclaration().getDeclaredMethods();


        //register new ETM

        return originalETM;
    }

    private Expression originalExpressionAST;

    public OriginalETM(Expression expressionAST, ITypeBinding type) {
        super(expressionAST, type);
    }

    public void setOriginalExpressionAST(Expression originalExpressionAST) {
        this.originalExpressionAST = originalExpressionAST;
    }

    @Override
    public String toString() {
        return new StringBuilder("OriginalETM:").append(getText()).append(" [").append(type.getName().toString()).append("]").toString();
    }
}
