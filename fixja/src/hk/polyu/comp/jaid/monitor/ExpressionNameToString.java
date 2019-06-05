package hk.polyu.comp.jaid.monitor;

import org.eclipse.jdt.core.dom.*;

/**
 * Created by Ls CHEN.
 */
public class ExpressionNameToString extends ASTVisitor {
    StringBuilder text = null;

    public String getText(Expression expression) {
        this.text = new StringBuilder();
        expression.accept(this);
        if (this.text.length() == 0) {
            this.text.append(expression.toString().trim().replaceAll("\\n", ""));
        }
        return this.text.toString();
    }

    private static String getText(SimpleName simpleName) {
        String textCache = null;
        IBinding binding = simpleName.resolveBinding();
        if (binding instanceof IMethodBinding) {
            IMethodBinding methodBinding = (IMethodBinding) binding;
            if (Modifier.isStatic(binding.getModifiers())) {
                StringBuilder sb = new StringBuilder(methodBinding.getDeclaringClass().getQualifiedName());
                sb.append(".").append(simpleName.toString().trim().replaceAll("\\n", ""));
                textCache = sb.toString();
            }
        } else if (binding instanceof IVariableBinding) {
            IVariableBinding variableBinding = (IVariableBinding) binding;
            if (Modifier.isStatic(binding.getModifiers())) {
                StringBuilder sb = new StringBuilder(variableBinding.getDeclaringClass().getQualifiedName());
                sb.append(".").append(simpleName.toString().trim().replaceAll("\\n", ""));
                textCache = sb.toString();
            }
        }
        if (textCache == null || textCache.length() == 0) {
            textCache = simpleName.toString().trim().replaceAll("\\n", "");
        }
        return textCache;
    }

    @Override
    public boolean visit(SimpleName node) {
        this.text.append(getText(node));
        return false;
    }

    @Override
    public boolean visit(ThisExpression node) {
        this.text.append(node.toString());
        return false;
    }

    @Override
    public boolean visit(NumberLiteral node) {
        this.text.append(node.toString());
        return false;
    }

    @Override
    public boolean visit(BooleanLiteral node) {
        this.text.append(node.toString());
        return false;
    }

    @Override
    public boolean visit(NullLiteral node) {
        this.text.append(node.toString());
        return false;
    }

    @Override
    public boolean visit(CharacterLiteral node) {
        this.text.append(node.toString());
        return false;
    }

    @Override
    public boolean visit(StringLiteral node) {
        this.text.append(node.toString());
        return false;
    }

    @Override
    public boolean visit(QualifiedName node) {
        node.getQualifier().accept(this);
        this.text.append(".");
        node.getName().accept(this);
        return false;
    }

    @Override
    public boolean visit(FieldAccess node) {
        node.getExpression().accept(this);
        this.text.append(".");
        node.getName().accept(this);
        return false;
    }

    @Override
    public boolean visit(ArrayAccess node) {
        node.getArray().accept(this);
        this.text.append("[");
        node.getIndex().accept(this);
        this.text.append("]");
        return false;
    }

    @Override
    public boolean visit(InfixExpression node) {
        node.getLeftOperand().accept(this);
        this.text.append(node.getOperator().toString());
        node.getRightOperand().accept(this);
        for (Object extended : node.extendedOperands()) {
            this.text.append(node.getOperator().toString());
            ASTNode extendedNode = (ASTNode) extended;
            extendedNode.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(PrefixExpression node) {
        this.text.append(node.getOperator().toString());
        node.getOperand().accept(this);
        return false;
    }

    @Override
    public boolean visit(PostfixExpression node) {
        node.getOperand().accept(this);
        this.text.append(node.getOperator().toString());
        return false;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        Expression expression = node.getExpression();
        if (expression != null) {
            expression.accept(this);
            this.text.append(".");
        }
        node.getName().accept(this);
        this.text.append("(");
        node.arguments().forEach(arg -> {
            if (!this.text.toString().endsWith("("))
                this.text.append(",");
            ASTNode argNode = (ASTNode) arg;
            argNode.accept(this);
        });
        this.text.append(")");
        return false;
    }

    @Override
    public boolean visit(ParenthesizedExpression node) {
        this.text.append("(");
        node.getExpression().accept(this);
        this.text.append(")");
        return false;
    }

    @Override
    public boolean visit(Assignment node) {
        node.getLeftHandSide().accept(this);
        this.text.append(node.getOperator().toString());
        node.getRightHandSide().accept(this);
        return false;
    }


}
