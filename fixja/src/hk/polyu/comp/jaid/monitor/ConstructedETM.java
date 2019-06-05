package hk.polyu.comp.jaid.monitor;

import hk.polyu.comp.jaid.ast.ExpressionFormatter;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Created by Ls CHEN.
 */
public class ConstructedETM extends ExpressionToMonitor {

    public static ExpressionToMonitor construct(Expression expressionAST, ITypeBinding type) {
        //use existing ETM if there is any
        Expression formattedExp = ExpressionFormatter.formatExpression(expressionAST);
        ExpressionToMonitor etm = infoCenter.getExpressionByText(getText(formattedExp.toString()));
        if (etm != null) return etm;

        //Check type and Use registered type if type is null
        if (type == null)
            type = infoCenter.getTypeByExpressionText(getText(formattedExp.toString()));
        if (type == null) throw new IllegalStateException("Exp:" + formattedExp.toString() + " TypeBinding is null.");
        //create new ETM with formatted expression ast
        etm = new ConstructedETM(formattedExp, type);
        //register new ETM
        infoCenter.registerExpressionToMonitor(etm);
        return etm;
    }

    private ConstructedETM(Expression expressionAST, ITypeBinding type) {
        super(expressionAST, type);
    }

    // ========================== Override

    @Override
    public String toString() {
        return new StringBuilder("ConstructedETM:").append(getText()).append(" [").append(type.getName().toString()).append("]").toString();
    }

}
