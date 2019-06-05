package hk.polyu.comp.jaid.ast;

import org.eclipse.jdt.core.dom.*;

/**
 * Created by Ls CHEN.
 */
public class ExpNodeFinder extends ASTVisitor {
    ASTNode root;
    ASTNode exp;
    ASTNode result;

    public ExpNodeFinder(ASTNode root) {
        this.root = root;
    }

    /**
     * Find desired node from the root
     * @return
     */
    public ASTNode find( ASTNode exp) {
        result=null;
        this.exp = exp;
        this.root.accept(this);
        return result;
    }

    @Override
    public void endVisit(ExpressionStatement node) {
        isDesiredNode(node);
    }

    @Override
    public void endVisit(ConditionalExpression node) {
        isDesiredNode(node);
    }

    @Override
    public void endVisit(MethodInvocation node) {
        isDesiredNode(node);
    }

    public void endVisit(SimpleName node) {
        isDesiredNode(node);
    }

    @Override
    public void endVisit(ArrayAccess node) {
        isDesiredNode(node);
    }

    @Override
    public void endVisit(InfixExpression node) {
        isDesiredNode(node);
    }

    @Override
    public void endVisit(QualifiedName node) {
        isDesiredNode(node);
    }


    private void isDesiredNode(ASTNode node) {
        ASTMatcher matcher=new ASTMatcher();
        if(node.subtreeMatch(matcher,exp)) result=node;
    }
}
