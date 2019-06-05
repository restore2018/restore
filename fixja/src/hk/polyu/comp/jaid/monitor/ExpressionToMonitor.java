package hk.polyu.comp.jaid.monitor;

import hk.polyu.comp.jaid.ast.ExpressionCollector;
import hk.polyu.comp.jaid.ast.ExpressionFormatter;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * Created by Max PEI.
 */
public class ExpressionToMonitor {

    public static ExpressionToMonitor construct(Expression expressionAST, ITypeBinding type) {
        ExpressionToMonitor etm;
        ITypeBinding a = expressionAST.resolveTypeBinding();
        ITypeBinding b = type;
        if (expressionAST.resolveTypeBinding() != null)
            if (!expressionAST.resolveTypeBinding().equals(type))
                etm = OriginalETM.construct(expressionAST, type);
            else
                etm = OriginalETM.construct(expressionAST, expressionAST.resolveTypeBinding());
        else
            etm = ConstructedETM.construct(expressionAST, type);
        return etm;
    }

    public static ExpressionToMonitor construct(Expression expressionAST, ITypeBinding type,boolean isForMoreFix) {
        ExpressionToMonitor etm;
        ITypeBinding a = expressionAST.resolveTypeBinding();
        ITypeBinding b = type;
        if(isForMoreFix){
            if (expressionAST.resolveTypeBinding() != null)
                if (!expressionAST.resolveTypeBinding().equals(type))
                    etm = OriginalETM.construct(expressionAST, type,true);
                else
                    etm = OriginalETM.construct(expressionAST, expressionAST.resolveTypeBinding(),true);
            else
                etm = ConstructedETM.construct(expressionAST, type,true);

            //IMethodBinding[] f = etm.getType().getDeclaredMethods();
            return etm;
        }
        if (expressionAST.resolveTypeBinding() != null)
            if (!expressionAST.resolveTypeBinding().equals(type))
                etm = OriginalETM.construct(expressionAST, type);
            else
                etm = OriginalETM.construct(expressionAST, expressionAST.resolveTypeBinding());
        else
            etm = ConstructedETM.construct(expressionAST, type);
        return etm;
    }

    /**
     * @param expressionAST not null, formatted ast
     * @param type          not null
     */
    protected ExpressionToMonitor(Expression expressionAST, ITypeBinding type) {
        if (expressionAST == null || type == null)
            throw new IllegalArgumentException();

        this.expressionAST = expressionAST;
        this.textCache = getText(getExpressionAST().toString());
        this.type = type;
//        this.binding = CommonUtils.resolveBinding4Variables(expressionAST);

        this.hasMethodInvocation = MethodInvocationDetector.methodInvocationFound(this.expressionAST);
        this.isLiteral = isIntegerType() && isSimpleInteger(getText());
        this.isValidVariable = CommonUtils.isValidVariable(getExpressionAST(), getBinding());
        this.orderTextCache = calculateOrderText(this.textCache, this.getType().getName());
    }


    // ================================== Operation
    protected static MethodDeclarationInfoCenter infoCenter = Session.getSession().getConfig().getJavaProject().getMethodToMonitor().getMethodDeclarationInfoCenter();


    private static String calculateOrderText(String text, String typeName) {
        return String.format("%5d", text.length()) + "#" + text + "#" + typeName;
    }


    public static double similarityBetween(Set<ExpressionToMonitor> set1, Set<ExpressionToMonitor> set2) {
        Set<ExpressionToMonitor> union = new HashSet<>();
        union.addAll(set1);
        union.addAll(set2);
        int nbrCommonExpressions = set1.size() + set2.size() - union.size();
        double similarity = ((double) nbrCommonExpressions) / union.size();
        return similarity;
    }

    private static boolean isSimpleInteger(String s) {
        // Treat only literals like 124 and -/+324 as simple integer.
        return (s.startsWith("-") || s.startsWith("+")) && s.substring(1, s.length()).matches("\\d+")
                || s.matches("\\d+");
    }

    private static int getSimpleInteger(String s) {
        if (!isSimpleInteger(s))
            throw new IllegalStateException();

        int value;
        if (s.startsWith("-") || s.startsWith("+"))
            value = Integer.parseInt(s.substring(1, s.length()));
        else
            value = Integer.parseInt(s);
        return s.startsWith("-") ? -value : value;
    }


    public static String getText(String originalText) {
        return originalText.toString().replace("\n", "").replace("\r", "");
    }

    public static Comparator<ExpressionToMonitor> getByLengthComparator() {
        if (byLengthComparator == null)
            byLengthComparator = new Comparator<ExpressionToMonitor>() {
                @Override
                public int compare(ExpressionToMonitor o1, ExpressionToMonitor o2) {
                    return o1.getOrderText().compareTo(o2.getOrderText());
                }
            };

        return byLengthComparator;
    }

    // ========================= Storage

    final Expression expressionAST;
    final String textCache;
    final ITypeBinding type;
    IBinding binding;
    final String orderTextCache;

    final boolean hasMethodInvocation;
    final boolean isLiteral;
    final boolean isValidVariable;

    boolean hasChangedState;
    boolean isInvokeMTF;

    Set<ExpressionToMonitor> subExpressions;
    Set<ExpressionToMonitor> guardExpressions;
    Map<IVariableBinding, ExpressionToMonitor> fieldsAccessExpressions;

    static Comparator<ExpressionToMonitor> byLengthComparator;

    public Expression getExpressionAST() {
        return expressionAST;
    }

    public String getText() {
        return textCache;
    }

    public ITypeBinding getType() {
        return type;
    }

    public IBinding getBinding() {
        return binding;
    }

    public void setBinding(IBinding binding) {
        if (this.binding == null)
            this.binding = binding;
    }

    /**
     *
     * @return true if not known
     */
    public boolean isPublic() {//fixme: problematic
        if (binding != null && !Modifier.isPublic(binding.getModifiers())) return false;
        else return true;
    }

    /**
     *
     * @return false if not known
     */
    public boolean isFinal() {
        if (binding != null && Modifier.isFinal(binding.getModifiers())) return true;
        else return false;
    }
    public boolean isBooleanType() {
        return getType() != null && getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.BOOLEAN;
    }

    public boolean isIntegerType() {
        return getType() != null && getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.INT;
    }

    public boolean isNumericType() {
        return getType() != null
                && (getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.INT
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.DOUBLE
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.CHAR
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.LONG
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.FLOAT
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.SHORT
                || getType().isPrimitive() && PrimitiveType.toCode(getType().getName()) == PrimitiveType.BYTE);
    }

    public boolean isReferenceType() {
        return getType() != null && !getType().isPrimitive();
    }

    public boolean isArrayType() {
        return getType() != null && getType().isArray();
    }

    public boolean isSideEffectFree() {
        return !hasMethodInvocation() || !hasChangedState();
    }

    public boolean hasMethodInvocation() {
        return hasMethodInvocation;
    }

    public boolean isInvokeMTF() {
        return isInvokeMTF;
    }

    public void setInvokeMTF(boolean invokeMTF) {
        isInvokeMTF = invokeMTF;
    }

    public boolean hasChangedState() {
        return hasChangedState;
    }

    public boolean isLiteral() {
        return isLiteral;
    }

    public boolean isValidVariable() {
        return isValidVariable;
    }

    public String getOrderText() {
        return orderTextCache;
    }

    public Integer getLiteralIntegerValue() {
        if (!isLiteral())
            throw new IllegalStateException();

        return getSimpleInteger(getText());
    }

    public void setChangedState(boolean hasChangedState) {
        this.hasChangedState = hasChangedState;
    }

    public Set<ExpressionToMonitor> getSubExpressions() {
        if (subExpressions == null) {
            subExpressions = new HashSet<>();

            ExpressionCollector collector = new ExpressionCollector(false);
            collector.collect(getExpressionAST());
            Set<Expression> expressions = collector.getSubExpressionSet();
            expressions.stream().filter(x -> infoCenter.hasExpressionTextRegistered(x.toString()))
                    .forEach(x -> subExpressions.add(infoCenter.getExpressionByText(x.toString())));
        }
        return subExpressions;
    }

    public boolean isSuperExpressionOf(ExpressionToMonitor expr) {
        return getSubExpressions().contains(expr);
    }

    public boolean isProperSuperExpressionOf(ExpressionToMonitor expr) {
        return !this.equals(expr) && getSubExpressions().contains(expr);
    }

    public boolean isSubExpressionOf(ExpressionToMonitor expr) {
        return expr.getSubExpressions().contains(this);
    }

    public boolean isProperSubExpressionOf(ExpressionToMonitor expr) {
        return !this.equals(expr) && expr.getSubExpressions().contains(this);
    }

//    public Set<ExpressionToMonitor> getGuardExpressions() {
//        //fixme: why not add other valid expressions as guards?
//        if (guardExpressions == null) {
//            guardExpressions = new HashSet<>();
//            if (hasMethodInvocation()) {
//                // Expressions with no method invocation are side-effect free, therefore they do NOT need guard expressions
//
//                // Use all side-effect free sub-expressions as guard
//                getSubExpressions().stream().filter(x -> !x.hasMethodInvocation() && !x.equals(this)).forEach(guardExpressions::add);
//
//                // also include field accesses based on sub-expressions of reference types
//                Set<ExpressionToMonitor> fieldAccessGuardExpressions = new HashSet<>();
//                for (ExpressionToMonitor subExpr : guardExpressions) {
//                    fieldAccessGuardExpressions.addAll(subExpr.getFieldsToMonitor().values());
//                }
//                guardExpressions.addAll(fieldAccessGuardExpressions);
//            }
//        }
//        return guardExpressions;
//    }

    /**
     * Select fields of ETM that could be monitored
     *
     * @return
     */
    public Map<IVariableBinding, ExpressionToMonitor> getFieldsToMonitor() {
        if (fieldsAccessExpressions == null)
            fieldsAccessExpressions = new HashMap<>();
        return fieldsAccessExpressions;
    }


    // ========================== Override

    @Override
    public String toString() {
        return getText() + "[" + type.getName().toString() + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExpressionToMonitor that = (ExpressionToMonitor) o;

        return textCache.equals(that.textCache);
    }

    @Override
    public int hashCode() {
        return textCache.hashCode();
    }

// ========================== Implementation


    /**
     * Object to collect bindings to local variables from an "Expression" AST.
     */
    private static class LocalVariableCollector extends ASTVisitor {
        private Set<IVariableBinding> localVariables;

        public void collect(Expression expression) {
            localVariables = new HashSet<IVariableBinding>();
            expression.accept(this);
        }

        public Set<IVariableBinding> getLocalVariables() {
            return localVariables;
        }

        public boolean visit(SimpleName node) {
            if (node.resolveBinding() instanceof IVariableBinding) {
                IVariableBinding binding = (IVariableBinding) node.resolveBinding();
                if (!binding.isField() && !binding.isEnumConstant()) {
                    localVariables.add(binding);
                }
            }
            return false;
        }
    }

    private static class MethodInvocationDetector extends ASTVisitor {
        private static MethodInvocationDetector detector = new MethodInvocationDetector();
        private boolean hasMethodInvocation;

        public static boolean methodInvocationFound(Expression exp) {
            detector.hasMethodInvocation = false;
            exp.accept(detector);
            return detector.hasMethodInvocation;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            hasMethodInvocation = true;
            return super.visit(node);
        }

    }


    public boolean isMethodInvocation() {
        return getExpressionAST() instanceof MethodInvocation;
    }

    public boolean isQualified() {
        String opString = getText();
        if (opString.length() > 0 && opString.contains(".")
                && !(getExpressionAST() instanceof NumberLiteral)
                && (getExpressionAST() instanceof QualifiedName)) {
            return true;
        }
        return false;
    }
}
