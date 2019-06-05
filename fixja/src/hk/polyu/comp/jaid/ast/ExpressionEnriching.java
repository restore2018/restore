package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ConstructedETM;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.stream.Collectors;

class ExpressionEnriching {

    static Set<ExpressionToMonitor> enrichExpressionsInAllKinds(Set<ExpressionToMonitor> existingExpressions) {
        Set<ExpressionToMonitor> newExpressions = new HashSet<>();
        Set<ExpressionToMonitor> referenceExp = existingExpressions.stream().filter(etm -> !etm.getType().isPrimitive() && !etm.hasMethodInvocation()).collect(Collectors.toSet());
        for (ExpressionToMonitor etm : referenceExp) {
            //Methods from binary types that reference unresolved types may not be included.
            IMethodBinding[] methodBindings = etm.getType().getDeclaredMethods();
            if (methodBindings.length == 0) methodBindings = etm.getType().getTypeDeclaration().getDeclaredMethods();
            ITypeBinding type=etm.getType().getTypeDeclaration();
            List<IMethodBinding> selectedInvocations =
                    ASTUtils4SelectInvocation.selectGetStateMethods(methodBindings, JavaProject.expToExclude,type);
            for (IMethodBinding binding : selectedInvocations) {
                MethodInvocation methodInvocation = CommonUtils.appendInvoking(etm.getExpressionAST(), binding.getName(), null);

                ExpressionToMonitor newExp = ExpressionToMonitor.construct(methodInvocation, binding.getReturnType());
                newExpressions.add(newExp);
            }
        }
        return newExpressions;
    }

    /**
     * Enrich the ETM within the method body
     *
     * @param existingExpressions
     * @return
     */
    static Set<ExpressionToMonitor> enrichExpressionsReturnBoolean(Set<ExpressionToMonitor> existingExpressions) {
        Set<ExpressionToMonitor> newExpressions = new HashSet<>();
        Set<ExpressionToMonitor> referenceExp = existingExpressions.stream().filter(etm -> !etm.getType().isPrimitive() && !etm.hasMethodInvocation()).collect(Collectors.toSet());
        for (ExpressionToMonitor etm : referenceExp) {
            //Methods from binary types that reference unresolved types may not be included.
            IMethodBinding[] methodBindings = etm.getType().getDeclaredMethods();
            if (methodBindings.length == 0) methodBindings = etm.getType().getTypeDeclaration().getDeclaredMethods();
            List<IMethodBinding> selectedInvocations =
                    ASTUtils4SelectInvocation.selectReturnBooleanMethods(methodBindings, JavaProject.expToExclude);
            for (IMethodBinding binding : selectedInvocations) {
                MethodInvocation methodInvocation = CommonUtils.appendInvoking(etm.getExpressionAST(), binding.getName(), null);

                ExpressionToMonitor newExp = ExpressionToMonitor.construct(methodInvocation, binding.getReturnType());
                newExpressions.add(newExp);
            }
        }
        return newExpressions;
    }

    /**
     * enrich very basic method invocations like "array.length; collection.size; str.length()"
     *
     * @param existingExpressions
     * @return
     */
    static Set<ExpressionToMonitor> basicEnrich(Set<ExpressionToMonitor> existingExpressions) {
        Set<ExpressionToMonitor> newExpressions = new HashSet<>();
        Set<ExpressionToMonitor> referenceExp = existingExpressions.stream().filter(etm -> !etm.getType().isPrimitive() && !etm.hasMethodInvocation()).collect(Collectors.toSet());
        for (ExpressionToMonitor etm : referenceExp) {
            //Methods from binary types that reference unresolved types may not be included.
            IMethodBinding[] methodBindings = etm.getType().getDeclaredMethods();
            if (methodBindings.length == 0) methodBindings = etm.getType().getTypeDeclaration().getDeclaredMethods();
            List<IMethodBinding> selectedInvocations =
                    ASTUtils4SelectInvocation.selectBasicMethods(methodBindings, JavaProject.expToExclude);
            for (IMethodBinding binding : selectedInvocations) {
                MethodInvocation methodInvocation = CommonUtils.appendInvoking(etm.getExpressionAST(), binding.getName(), null);

                ExpressionToMonitor newExp = ExpressionToMonitor.construct(methodInvocation, binding.getReturnType());
                newExpressions.add(newExp);
            }

            if (etm.getType().isArray() && etm.getExpressionAST() instanceof Name) {
                AST ast = etm.getExpressionAST().getAST();
                FieldAccess fieldAccess = ast.newFieldAccess();
                fieldAccess.setExpression((Expression) ASTNode.copySubtree(ast, etm.getExpressionAST()));
                fieldAccess.setName(ast.newSimpleName("length"));

                ExpressionToMonitor newExp = ExpressionToMonitor.construct(fieldAccess, ast.resolveWellKnownType("int"));
                newExpressions.add(newExp);
            }
        }
        return newExpressions;
    }

    static Set<ExpressionToMonitor> enrichConstantIntegers(AST ast) {
        Set<ExpressionToMonitor> enrichedConstantIntegers = new HashSet<>();
        ITypeBinding intTypeBinding = ast.resolveWellKnownType("int");
        for (Integer i : new Integer[]{0, 1}) {
            Expression expr = ast.newNumberLiteral(i.toString());
            enrichedConstantIntegers.add(ExpressionToMonitor.construct(expr, intTypeBinding));
        }
        return enrichedConstantIntegers;
    }

    /**
     * Construct fields of each RefEtm
     *
     * @param existingExpressions
     * @return
     */
    static Set<ExpressionToMonitor> extendReferenceFields(Set<ExpressionToMonitor> existingExpressions) {
        Set<ExpressionToMonitor> extendingFields = new HashSet<>();
        Set<ExpressionToMonitor> referenceExp = existingExpressions.stream().filter(etm -> !etm.getType().isPrimitive() && !etm.hasMethodInvocation()).collect(Collectors.toSet());
        for (ExpressionToMonitor etm : referenceExp) {
            //Methods from binary types that reference unresolved types may not be included.
            Set<IVariableBinding> fields = collectFieldsFromType(etm.getType());
            for (IVariableBinding variableBinding : fields) {
                if (!Modifier.isFinal(variableBinding.getModifiers()) || etm.getText().equals("this")) {
                    ExpressionToMonitor newFieldAccess = ConstructedETM.construct(
                            CommonUtils.appendField(etm.getExpressionAST(), variableBinding),
                            variableBinding.getType());
                    newFieldAccess.setBinding(variableBinding);
                    extendingFields.add(newFieldAccess);
                    etm.getFieldsToMonitor().put(variableBinding, newFieldAccess);
                }
            }
        }
        return extendingFields;
    }

    static Set<IVariableBinding> collectFieldsFromType(ITypeBinding baseType) {
        Set<IVariableBinding> allFields = new HashSet<>();

        Queue<ITypeBinding> contextTypes = new LinkedList<>();
        contextTypes.add(baseType);
        while (!contextTypes.isEmpty()) {
            ITypeBinding typeBinding = contextTypes.poll();
            // fixme: add static/non-static/final fields only
            allFields.addAll(Arrays.asList(typeBinding.getDeclaredFields()));

            if (typeBinding.getSuperclass() != null && !typeBinding.getSuperclass().getName().equals(Object.class.getName())) {
                contextTypes.offer(typeBinding.getSuperclass());
            }
        }

        return allFields;
    }
}
