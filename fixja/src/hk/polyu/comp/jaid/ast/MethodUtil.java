package hk.polyu.comp.jaid.ast;

import com.sun.jdi.Method;
import org.eclipse.jdt.core.dom.*;

import java.util.List;

/**
 * Created by Max PEI.
 */
public class MethodUtil {

    public static final char METHOD_SIGNATURE_CLASS_NAME_SEPARATOR = '@';

    public static String getMethodSignature(String methodSignatureWithClassName) {
        return methodSignatureWithClassName.substring(0, methodSignatureWithClassName.indexOf(METHOD_SIGNATURE_CLASS_NAME_SEPARATOR));
    }

    public static String getClassName(String methodSignatureWithClassName) {
        return methodSignatureWithClassName.substring(methodSignatureWithClassName.indexOf(METHOD_SIGNATURE_CLASS_NAME_SEPARATOR) + 1, methodSignatureWithClassName.length());
    }

    public static String getMethodSignatureWithClassName(String methodSignature, String className) {
        return methodSignature + METHOD_SIGNATURE_CLASS_NAME_SEPARATOR + className;
    }

    public static String getMethodSignatureWithClassName(MethodDeclaration methodDeclaration, String className) {
        return getMethodSignature(methodDeclaration) + METHOD_SIGNATURE_CLASS_NAME_SEPARATOR + className;
    }

    public static String getMethodSignature(Method m) {
        if (m == null)
            throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder();
        sb.append(m.name()).append("(");
        List<String> argumentTypes = m.argumentTypeNames();
        for (int i = 0; i < argumentTypes.size(); i++) {
            // Replace '$' in inner class names with '.', to be consistent with method getMethodSignature(MethodDeclaration).
            sb.append(argumentTypes.get(i).replace('$', '.'));

            if (i != argumentTypes.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    public static String getMethodSignatureIgnoreDollar(Method m) {
        if (m == null)
            throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder();
        sb.append(m.name()).append("(");
        List<String> argumentTypes = m.argumentTypeNames();
        for (String argumentType : argumentTypes) {
            if (argumentType.contains("$"))
                sb.append(argumentType.substring(argumentType.indexOf("$") + 1));
            else
                sb.append(argumentType);
            sb.append(",");
        }
        if (sb.toString().contains(","))
            sb.delete(sb.lastIndexOf(","), sb.lastIndexOf(",") + 1);
        sb.append(")");

        return sb.toString();
    }

    public static String getMethodSignature(MethodDeclaration methodDeclaration) {
        if (methodDeclaration == null)
            throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder();
        sb.append(methodDeclaration.getName()).append("(");
        List parameters = methodDeclaration.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            SingleVariableDeclaration declaration = (SingleVariableDeclaration) parameters.get(i);
            // Always use erased type to construct the signature.
            sb.append(declaration.getType().resolveBinding().getErasure().getQualifiedName());

            if (i != parameters.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    public static String getMethodSignature(IMethodBinding methodBinding) {
        if (methodBinding == null)
            throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder();
        sb.append(methodBinding.getName()).append("(");
        ITypeBinding[] parameterTypes = methodBinding.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            // Always use erased type to construct the signature.
            sb.append(parameterTypes[i].getErasure().getQualifiedName());
            if (i != parameterTypes.length - 1) {
                sb.append(",");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public static String getSimpleMethodSignature(MethodDeclaration methodDeclaration) {
        if (methodDeclaration == null)
            throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder();
        sb.append(methodDeclaration.getName()).append("(");
        List parameters = methodDeclaration.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            SingleVariableDeclaration declaration = (SingleVariableDeclaration) parameters.get(i);
            // Always use erased type to construct the signature.
            sb.append(declaration.getType().resolveBinding().getErasure().getName());

            if (i != parameters.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    public static String getMethodSignatureWithTypeToString(MethodDeclaration methodDeclaration) {
        if (methodDeclaration == null)
            throw new IllegalArgumentException();

        StringBuilder sb = new StringBuilder();
        sb.append(methodDeclaration.getName()).append("(");
        List parameters = methodDeclaration.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            SingleVariableDeclaration declaration = (SingleVariableDeclaration) parameters.get(i);
            // Always use erased type to construct the signature.
            sb.append(declaration.getType().toString());

            if (i != parameters.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    public static boolean returnsVoid(MethodDeclaration methodDeclaration) {
        ITypeBinding typeBinding = methodDeclaration.resolveBinding().getReturnType();
        return typeBinding.isPrimitive() && PrimitiveType.toCode(typeBinding.getName()) == PrimitiveType.VOID;
    }

    public static String getTempReturnVariableName(MethodDeclaration methodDeclaration) {
        ITypeBinding typeBinding = methodDeclaration.resolveBinding().getReturnType();
        return "result" + Math.abs(typeBinding.getName().hashCode());
    }

    /**
     * Get method with 'methodSignature' in 'typeDeclaration'.
     *
     * @param typeDeclaration AST of type declaration.
     * @param methodSignature String encoding the signature of the method to find.
     * @return
     * @see #getMethodSignatureWithClassName(MethodDeclaration, String)
     */
    public static MethodDeclaration getMethodDeclarationBySignature(AbstractTypeDeclaration typeDeclaration, String methodSignature) {
        for (Object object : typeDeclaration.bodyDeclarations()) {
            if (object instanceof MethodDeclaration) {
                MethodDeclaration methodDeclaration = (MethodDeclaration) object;
                if (methodSignature.equals(getMethodSignature(methodDeclaration)) ||
                        methodSignature.equals(getSimpleMethodSignature(methodDeclaration)) ||
                        methodSignature.equals(getMethodSignatureWithTypeToString(methodDeclaration))) {
                    return methodDeclaration;
                }
            }
        }

        throw new IllegalStateException("Failed to find a method with the specified signature.");
    }

    public static MethodDeclaration getMethodDeclarationByName(AbstractTypeDeclaration typeDeclaration, String name) {
        for (Object object : typeDeclaration.bodyDeclarations()) {
            if (object instanceof MethodDeclaration) {
                MethodDeclaration methodDeclaration = (MethodDeclaration) object;
                if (methodDeclaration.getName().getIdentifier().equals(name)) {
                    return methodDeclaration;
                }
            }
        }

        throw new IllegalStateException("Failed to find a method with the specified name: " + name);
    }

}
