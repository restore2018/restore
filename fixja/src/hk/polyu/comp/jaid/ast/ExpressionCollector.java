package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.java.JavaProject;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class ExpressionCollector extends ASTVisitor {

    private boolean shouldCheckType = true;

    public ExpressionCollector(boolean shouldCheckType) {
        super(false);
        this.shouldCheckType = shouldCheckType;
    }

    public void collect(ASTNode rootNode) {
        this.subExpressionSet = new HashSet<>();
        this.toExclude = new HashSet<>();

        rootNode.accept(this);

        excludeUndesirableExpressions();
    }

    public Set<Expression> getSubExpressionSet() {
        return subExpressionSet;
    }

    public Set<Expression> getSubExpressionSetWithinClass() {
        return subExpressionSetWithinClass;
    }

    // =============================== Storage

    private Set<Expression> subExpressionSet;
    private Set<Expression> subExpressionSetWithinClass;

    // ============================== Implementation

    private Set<Expression> toExclude;


    private void excludeUndesirableExpressions() {
        Set<String> toExcludeText = new HashSet<>();
        for (Expression node : toExclude) {
            // exclude all parent expressions of those from 'toExclude'.
            ASTNode nodeToExclude = node;
            while (nodeToExclude != null && !(nodeToExclude instanceof MethodDeclaration) && !(nodeToExclude instanceof Statement)) {
                if (nodeToExclude instanceof Expression && subExpressionSet.contains(nodeToExclude)) {
                    toExcludeText.add(nodeToExclude.toString());
                }
                nodeToExclude = nodeToExclude.getParent();
            }
        }
        //remove duplicated exp
        Set<String> includedText = new HashSet<>();
        Set<Expression> toExcludeExp = new HashSet<>();
        for (Expression exp : subExpressionSet) {
            String text = exp.toString();
            if (!toExcludeText.contains(text) && !includedText.contains(text)) {
                includedText.add(text);
            } else {
                toExcludeExp.add(exp);
            }
        }
        subExpressionSet.removeAll(toExcludeExp);
    }

    private void categorizeExpression(Expression exp) {
        if (!shouldCheckType || isTypeOK(exp))
            subExpressionSet.add(exp);
        else
            toExclude.add(exp);
    }

    private void excludeExpression(Expression exp) {
        toExclude.add(exp);
    }

    private boolean isTypeOK(Expression expNode) {
        ITypeBinding typeBinding = expNode.resolveTypeBinding();
        if (typeBinding == null)
            return false;

        if (typeBinding.isPrimitive()) {
            if (!typeBinding.getName().equals("int")
                    && !typeBinding.getName().equals("double")
                    && !typeBinding.getName().equals("float")
                    && !typeBinding.getName().equals("long")
                    && !typeBinding.getName().equals("short")
                    && !typeBinding.getName().equals("boolean")
                    && !typeBinding.getName().equals("char"))
                return false;
        } else if (!typeBinding.isClass()
                && !typeBinding.isArray()
                && !typeBinding.isGenericType()
                && !typeBinding.isInterface())
            return false;

        if (expNode instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation) expNode;
            if (!ASTUtils4SelectInvocation.isOfGetStateMethodType(methodInvocation.resolveMethodBinding(), JavaProject.expToExclude))
                return false;
            if (ASTUtils4SelectInvocation.hasForbiddenWordsInName(methodInvocation.getName().getIdentifier(), JavaProject.expToExclude))
                return false;
        }

        return true;
    }


    // =============================== visitor methods


    public boolean visit(AnnotationTypeDeclaration node) {
        return false;
    }

    public boolean visit(AnnotationTypeMemberDeclaration node) {
        return false;
    }

    public boolean visit(AnonymousClassDeclaration node) {
        return false;
    }

    public boolean visit(ArrayAccess node) {
        categorizeExpression(node);
        return true;
    }

    public boolean visit(ArrayCreation node) {
        return true;
    }

    public boolean visit(ArrayInitializer node) {
        return true;
    }

    public boolean visit(Assignment node) {
        return true;
    }

    public boolean visit(BooleanLiteral node) {
        return true;
    }

    public boolean visit(CastExpression node) {
        node.getExpression().accept(this);
        return false;
    }

    public boolean visit(CharacterLiteral node) {
        categorizeExpression(node);
        return false;
    }

    public boolean visit(ClassInstanceCreation node) {
        return true;
    }

    public boolean visit(ConditionalExpression node) {
        return true;
    }

    public boolean visit(FieldAccess node) {
        categorizeExpression(node);
        return true;
    }

    public boolean visit(InfixExpression node) {
        categorizeExpression(node);
        return true;
    }

    public boolean visit(InstanceofExpression node) {
        return true;
    }

    // skip Lambda expression
    public boolean visit(LambdaExpression node) {
        return false;
    }

    public boolean visit(MethodDeclaration node) {
        return true;
    }

    public boolean visit(MethodInvocation node) {
        categorizeExpression(node);

        if (node.getExpression() != null)
            node.getExpression().accept(this);
        if (node.arguments() != null && !node.arguments().isEmpty()) {
            node.arguments().forEach(x -> ((ASTNode) x).accept(this));
        }

        return false;
    }

    public boolean visit(NumberLiteral node) {
        categorizeExpression(node);
        return false;
    }

    public boolean visit(ParenthesizedExpression node) {
        return true;
    }

    public boolean visit(PostfixExpression node) {
        excludeExpression(node);

        return true;
    }

    public boolean visit(PrefixExpression node) {
        if (node.getOperator().toString().length() == 2)
            excludeExpression(node);
        else
            categorizeExpression(node);

        return true;
    }

    // fixme: When is a qualified name an expression but not a field access?
    //options.dependencyOptions.needsManagement() && options.closurePass
    //the 'options' is a field. And the 'options.closurePass' is a qualified name but not a field access
    public boolean visit(QualifiedName node) {
        if (!(node.getParent() instanceof SimpleType &&
                (node.getParent().getParent() instanceof ClassInstanceCreation ||
                        node.getParent().getParent() instanceof VariableDeclarationStatement)))
            categorizeExpression(node);
        return false;
    }

    public boolean visit(SimpleName node) {
        IBinding binding = node.resolveBinding();
        if (!shouldCheckType || binding instanceof IVariableBinding
                && !((IVariableBinding)binding).isField()
                ) {
            // all fields should be qualified.
            categorizeExpression(node);
        }
        return false;
    }

    public boolean visit(StringLiteral node) {
        excludeExpression(node);
        return false;
    }

    public boolean visit(SuperFieldAccess node) {
        categorizeExpression(node);
        return false;
    }

    public boolean visit(SuperMethodInvocation node) {
        return true;
    }

    public boolean visit(ThisExpression node) {
        categorizeExpression(node);
        return false;
    }

}
