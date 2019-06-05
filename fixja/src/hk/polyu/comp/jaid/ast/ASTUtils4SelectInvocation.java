package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by liushanchen on 16/8/9.
 */
public class ASTUtils4SelectInvocation {

    public static List<IMethodBinding> selectChangeStateMethods(IMethodBinding[] methodBindings, LineLocation location) {
        List<IMethodBinding> selectedMethod = new ArrayList<IMethodBinding>();
        for (int i = 0; i < methodBindings.length; i++) {
            IMethodBinding method = methodBindings[i];
            if (isChangeStateMethod(method, location)) {
                selectedMethod.add(method);
            }
        }
        return selectedMethod;
    }

    public static List<IMethodBinding> selectNumericGetStateMethods(IMethodBinding[] methodBindings) {
        List<IMethodBinding> selectedMethod = new ArrayList<IMethodBinding>();
        for (int i = 0; i < methodBindings.length; i++) {
            IMethodBinding method = methodBindings[i];
            if (isGetStateMethod(method, null))
                if (isNumericType(method.getReturnType()))
                    selectedMethod.add(method);

        }
        return selectedMethod;
    }

    public static List<IMethodBinding> selectBooleanGetStateMethods(IMethodBinding[] methodBindings) {
        List<IMethodBinding> selectedMethod = new ArrayList<IMethodBinding>();
        for (int i = 0; i < methodBindings.length; i++) {
            IMethodBinding method = methodBindings[i];
            if (isGetStateMethod(method, null))
                if (method.getReturnType().getQualifiedName().equals(PrimitiveType.BOOLEAN.toString()))
                    selectedMethod.add(method);

        }
        return selectedMethod;
    }

    public static List<IMethodBinding> selectGetStateMethods(IMethodBinding[] methodBindings, List<String> expToExclude) {
        List<IMethodBinding> selectedMethod = new ArrayList<IMethodBinding>();
        for (int i = 0; i < methodBindings.length; i++) {
            IMethodBinding method = methodBindings[i];
            if (isGetStateMethod(method, expToExclude)) {
                selectedMethod.add(method);
            }
        }
        return selectedMethod;
    }

    public static List<IMethodBinding> selectGetStateMethods(IMethodBinding[] methodBindings, List<String> expToExclude,ITypeBinding type) {
        List<IMethodBinding> selectedMethod = new ArrayList<IMethodBinding>();
        for (int i = 0; i < methodBindings.length; i++) {
            IMethodBinding method = methodBindings[i];
            if (isGetStateMethod(method, expToExclude,type)) {
                selectedMethod.add(method);
            }
        }
        return selectedMethod;
    }

    public static List<IVariableBinding> selectBooleanAndPublicFields(IVariableBinding[] variableBindings) {
        List<IVariableBinding> selectedVaribles = new ArrayList<IVariableBinding>();
        for (int i = 0; i < variableBindings.length; i++) {
            IVariableBinding varible = variableBindings[i];
            String a = varible.getType().getName();
            if (isValidModifiers(varible)&&varible.getType().getName().equals("boolean")) {
                selectedVaribles.add(varible);
            }
        }
        return selectedVaribles;
    }

    /**
     * array.length; collection.size; str.length()
     *
     * @param methodBindings
     * @param expToExclude
     * @return
     */
    public static List<IMethodBinding> selectBasicMethods(IMethodBinding[] methodBindings, List<String> expToExclude) {
        List<IMethodBinding> selectedMethod = new ArrayList<IMethodBinding>();
        for (int i = 0; i < methodBindings.length; i++) {
            IMethodBinding method = methodBindings[i];
            if (isBasicMethod(method, expToExclude)) {
                selectedMethod.add(method);
            }
        }
        return selectedMethod;
    }


    public static List<IMethodBinding> selectReturnBooleanMethods(IMethodBinding[] methodBindings, List<String> expToExclude) {
        List<IMethodBinding> selectedMethod = new ArrayList<IMethodBinding>();
        for (int i = 0; i < methodBindings.length; i++) {
            IMethodBinding method = methodBindings[i];
            if (isReturnBooleanMethod(method, expToExclude)) {
                selectedMethod.add(method);
            }
        }
        return selectedMethod;
    }


    /**
     * 寻找有效变量中合适的方法调用，将结果赋值给当前变量。
     *
     * @return
     */
    public static List<MethodInvocation> assignableStateInvocation1(boolean isNumberType, LineLocation location) {
        if (location == null) return null;
        List<MethodInvocation> invocations = new ArrayList<>();
        Collection<ExpressionToMonitor> exps = location.getExpressionsAppearedAtLocation();

        for (ExpressionToMonitor expToMonitor : exps) {
            ITypeBinding expType = expToMonitor.getType();
            if (expType != null && !expType.isPrimitive()) {//找到当前location中的所有type 为reference的exp（包括方法调用）
                List<IMethodBinding> validMethods = null;
                if (isNumberType) {
                    validMethods = selectNumericGetStateMethods(expType.getDeclaredMethods());
                } else {
                    validMethods = selectBooleanGetStateMethods(expType.getDeclaredMethods());
                }
                for (IMethodBinding method : validMethods) {
                    MethodInvocation invocation = null;
                    if (method.getParameterTypes().length == 0) {
                        invocation = CommonUtils.appendInvoking(expToMonitor.getExpressionAST(), method.getName(), null);
                        invocations.add(invocation);
                    } else {
                        Set<List<ASTNode>> paramsList = getCombinedParametersName(method, location);
                        for (List<ASTNode> params : paramsList) {
                            invocation = CommonUtils.appendInvoking(expToMonitor.getExpressionAST(), method.getName(), params);
                            invocations.add(invocation);
                        }
                    }
                }
            }
        }
        return invocations;
    }

    public static List<MethodInvocation> assignableStateInvocation1ForMoreFix(boolean isNumberType, LineLocation location) {
        if (location == null) return null;
        List<MethodInvocation> invocations = new ArrayList<>();
        Collection<ExpressionToMonitor> exps = location.getExpressionsAppearedAtLocationForMoreFix();

        for (ExpressionToMonitor expToMonitor : exps) {
            ITypeBinding expType = expToMonitor.getType();
            if (expType != null && !expType.isPrimitive()) {//找到当前location中的所有type 为reference的exp（包括方法调用）
                List<IMethodBinding> validMethods = null;
                if (isNumberType) {
                    validMethods = selectNumericGetStateMethods(expType.getDeclaredMethods());
                } else {
                    validMethods = selectBooleanGetStateMethods(expType.getDeclaredMethods());
                }
                for (IMethodBinding method : validMethods) {
                    MethodInvocation invocation = null;
                    if (method.getParameterTypes().length == 0) {
                        invocation = CommonUtils.appendInvoking(expToMonitor.getExpressionAST(), method.getName(), null);
                        invocations.add(invocation);
                    } else {
                        Set<List<ASTNode>> paramsList = getCombinedParametersName(method, location);
                        for (List<ASTNode> params : paramsList) {
                            invocation = CommonUtils.appendInvoking(expToMonitor.getExpressionAST(), method.getName(), params);
                            invocations.add(invocation);
                        }
                    }
                }
            }
        }
        return invocations;
    }

    public static boolean isOfGetStateMethodType(IMethodBinding method, List<String> expToExclude) {
        if (method.getParameterTypes().length == 0
                && !hasForbiddenWords(method, expToExclude)
                && !method.isConstructor()
                && isValidModifiers(method)
                && isGetStateMethodName(method)
                && !isThrow(method)
                && isGetStateReturn(method)
                )
            return true;
        return false;
    }

    public static boolean isReturnBooleanMethod(IMethodBinding method, List<String> expToExclude) {
        if (isValidGetStateParams(method)
                && !hasForbiddenWords(method, expToExclude)
                && !method.isConstructor()
                && isValidModifiers(method)
                && isGetStateMethodName(method)
                && !isThrow(method)
                ) {
            if (method.getReturnType() != null) {
                String ReturnTypeName = method.getReturnType().getName();
                if (ReturnTypeName.equals(PrimitiveType.BOOLEAN.toString()))
                    return true;
            }
        }
        return false;
    }
    /*
    public static boolean isGetStateMethod(IMethodBinding method, List<String> expToExclude) {
        if (isValidGetStateParams(method)
                && !hasForbiddenWords(method, expToExclude)
                && !method.isConstructor()
                && isValidModifiers(method)
                && isGetStateMethodName(method)
                && !isThrow(method)
                && isGetStateReturn(method)
                )
            return true;
        return false;
    }
    */

    public static boolean isGetStateMethod(IMethodBinding method, List<String> expToExclude) {
        if (isValidGetStateParams(method)
                && !hasForbiddenWords(method, expToExclude)
                && !method.isConstructor()
                && isValidModifiers(method)
                && isGetStateMethodName(method)
                && !isThrow(method)
                && isGetStateReturn(method)
                )
            return true;
        return false;
    }

    public static boolean isGetStateMethod(IMethodBinding method, List<String> expToExclude,ITypeBinding type) {
        if (isValidGetStateParams(method)
                && !hasForbiddenWords(method, expToExclude)
                && !method.isConstructor()
                && (isValidModifiers(method)||type.isEnum())
                && isGetStateMethodName(method)
                && !isThrow(method)
                && isGetStateReturn(method)
                )
            return true;
        return false;
    }

    public static boolean isBasicMethod(IMethodBinding method, List<String> expToExclude) {
        if (isValidGetStateParams(method)
                && !hasForbiddenWords(method, expToExclude)
                && !method.isConstructor()
                && isValidModifiers(method)
                && isBasicMethodName(method)
                && !isThrow(method)
                && isGetStateReturn(method)
                )
            return true;
        return false;
    }

    public static boolean isChangeStateMethod(IMethodBinding method, LineLocation location) {
        if (isValidParams(method, location)
                && !method.isConstructor()
                && isValidModifiers(method)
                && !isExcludedMethod(method)
                && !isGetStateMethodName(method)
                && isChangeStateReturn(method)
                ) {
            return true;
        }
        return false;
    }

    public static boolean hasForbiddenWords(IMethodBinding method, List<String> expToExclude) {
        if (expToExclude == null) return false;

        String invokingName = method.getName();
        return hasForbiddenWordsInName(invokingName, expToExclude);
    }

    public static boolean hasForbiddenWordsInName(String invokingName, List<String> expToExclude) {
        if (expToExclude == null) return false;
        if (invokingName.equals("getNext"))
            return true;
        for (String e : expToExclude) {
            if (invokingName.equals(e))
                return true;
        }
        return false;
    }

    private static boolean isGetStateMethodName(IMethodBinding method) {
        String invokingName = method.getName();
        if (invokingName.startsWith("get") && !invokingName.equals("getBytes")) {
            return true;
        } else if (invokingName.startsWith("is")) {
            return true;
        } else if (invokingName.startsWith("has") && !invokingName.equals("hashCode")) {
            return true;
        } else if (invokingName.equals("size")) {
            return true;
        } else if (invokingName.equals("length")) {
            return true;
        } else if (invokingName.equals("count")) {
            return true;
        }
        return false;
    }

    private static boolean isBasicMethodName(IMethodBinding method) {
        String invokingName = method.getName();
        if (invokingName.equals("size")) {
            return true;
        } else if (invokingName.equals("length")) {
            return true;
        }
        return false;
    }

    private static boolean isGetStateReturn(IMethodBinding method) {
        if (method.getReturnType() != null) {
            String ReturnTypeName = method.getReturnType().getName();
            if (!ReturnTypeName.equals(PrimitiveType.VOID.toString()))
                return true;
        }
        return false;
    }

    private static boolean isValidGetStateParams(IMethodBinding method) {
        ITypeBinding[] params = method.getParameterTypes();
        if (params.length == 0) {
            return true;
        }
        return false;
    }


    /**
     * 检查当前变量中是否存在可以作为method的参数的变量，如果存在，则method是valid的。
     *
     * @param method
     * @return
     */
    public static boolean isValidParams(IMethodBinding method, LineLocation location) {
        ITypeBinding[] params = method.getParameterTypes();
        if (params.length == 0) {
            return true;
        }
        //check if there is any valid variable for method parameters
        if (location == null) return false;
        Set<ExpressionToMonitor> exps = location.getExpressionsToMonitor();
        int validParams = 0;
        for (ITypeBinding p : params) {
            boolean hasMatchParam = false;
            for (ExpressionToMonitor expToMonitor : exps) {
                ITypeBinding t1 = expToMonitor.getType();
                if (p.equals(t1)) {
                    validParams++;
                    hasMatchParam = true;
                    break;
                }
            }
            if (!hasMatchParam) return false;
        }
        if (validParams == params.length)
            return true;
        return false;
    }


    public static boolean isChangeStateReturn(IMethodBinding method) {
        String ReturnName = method.getReturnType().getName();
        if (ReturnName.equals(PrimitiveType.VOID.toString())) {
            return true;
        }
        return false;
    }


    public static boolean isValidModifiers(IMethodBinding method) {
        if (Modifier.isPublic(method.getModifiers()))
            return true;
        return false;
    }

    public static boolean isValidModifiers(IVariableBinding method) {
        if (Modifier.isPublic(method.getModifiers())||Modifier.isFinal(method.getModifiers()))
            return true;
        return false;
    }

    public static boolean isExcludedMethod(IMethodBinding method) {
        String methodName = method.getName();
        if (methodName.equals("hashCode")
                || methodName.equals("wait")
                || methodName.equals("notifyAll")
                || methodName.equals("notify")
                || methodName.startsWith("set"))
            return true;
        return false;
    }

    public static boolean isThrow(IMethodBinding method) {
        if (method.getExceptionTypes().length > 0)
            return true;
        return false;
    }

    public static Set<List<ASTNode>> getCombinedParametersName(IMethodBinding imb, LineLocation location) {
        //获取params相应type的参数。
        ITypeBinding[] params = imb.getParameterTypes();
        List<Set<ASTNode>> paramsValidVars = new ArrayList<>(params.length);
        Set combinedParams = new HashSet<String[]>();

        for (ITypeBinding p : params) {
            Set<ASTNode> validVars = getValidVars(p, location);
            paramsValidVars.add(validVars);
        }
        List<ASTNode> fixVars = new ArrayList<>();
        if (paramsValidVars.size() == params.length)
            combineParams(combinedParams, fixVars, paramsValidVars);
        return combinedParams;

    }


    /**
     * 将所有的合适有效参数排列组合
     *
     * @param combinedParams  结果集
     * @param fixVars         固定的参数
     * @param paramsValidVars 合适的可以用来组合的参数
     * @return
     */
    public static Set<List<ASTNode>> combineParams(Set<List<ASTNode>> combinedParams, List<ASTNode> fixVars, List<Set<ASTNode>> paramsValidVars) {
        if (paramsValidVars == null || paramsValidVars.size() <= 0) return null;
        Set<ASTNode> vars = paramsValidVars.get(0);
        paramsValidVars.remove(0);

        if (paramsValidVars.size() == 0) {
            for (ASTNode var : vars) {
                List<ASTNode> nFixVars = new ArrayList<>(fixVars);
                nFixVars.add(var);
//                combinedParams.add(nFixVars.toArray(new String[nFixVars.size()]));
                combinedParams.add(nFixVars);
            }
        } else {
            for (ASTNode var : vars) {
                List<ASTNode> nFixVars = new ArrayList<>(fixVars);
                nFixVars.add(var);
                Set<List<ASTNode>> cps = combineParams(combinedParams, nFixVars, paramsValidVars);
                if (cps != null) {
                    combinedParams.addAll(cps);
                }
            }
        }
        return combinedParams;
    }

    /**
     * Given Location and type, getting valid expressions in that location
     *
     * @param p
     * @return
     */
    public static Set<ASTNode> getValidVars(ITypeBinding p, LineLocation location) {
        Set<ASTNode> validVars = new HashSet<>();
        if (location != null) {
            Set<ExpressionToMonitor> exps = location.getExpressionsToMonitor();
            exps.addAll(location.getContextMethod().getMethodDeclarationInfoCenter().getThisExpressionToMonitor()
                    .getFieldsToMonitor().values().stream().filter(x -> !x.isFinal()).collect(Collectors.toSet()));
            for (ExpressionToMonitor expToMonitor : exps) {
                ITypeBinding t1 = expToMonitor.getType();
                if (p.isCastCompatible(t1)) {
                    validVars.add(expToMonitor.getExpressionAST());
                }
            }
        }
        return validVars;
    }

    public static Set<ASTNode> getValidVarsIncludeThisFinal(ITypeBinding p, LineLocation location) {
        Set<ASTNode> validVars = new HashSet<>();
        if (location != null) {
            Set<ExpressionToMonitor> exps = location.getExpressionsToMonitor();
            exps.addAll(location.getContextMethod().getMethodDeclarationInfoCenter().getThisExpressionToMonitor().getFieldsToMonitor().values());
            for (ExpressionToMonitor expToMonitor : exps) {
                ITypeBinding t1 = expToMonitor.getType();
                if (p.isCastCompatible(t1)) {
                    validVars.add(expToMonitor.getExpressionAST());
                }
            }
        }
        return validVars;
    }


    public static boolean isNumericType(ITypeBinding type) {
        String name = type.getQualifiedName();
        if (name.equals(PrimitiveType.DOUBLE.toString()) || type.equals(PrimitiveType.FLOAT.toString())
                || name.equals(PrimitiveType.SHORT.toString())
                || name.equals(PrimitiveType.INT.toString()) || type.equals(PrimitiveType.LONG.toString()))
            return true;
        return false;
    }

}
