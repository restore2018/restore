package hk.polyu.comp.jaid.ast;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.ReplaceEdit;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Created by Max PEI.
 */
public class ThisMemberAccessRewriter extends ASTVisitor {

    private Document document;
    private ASTRewrite rewrite;
    private RangeMarker rangeMarker;
    private String  methodSignature;
    private String  MTFTypeBindingName;

    public String rewrite(Document originalDocument, MethodDeclaration methodDeclaration) {
        this.methodSignature = MethodUtil.getMethodSignature(methodDeclaration);
        MTFTypeBindingName=methodDeclaration.resolveBinding().getDeclaringClass().getErasure().getQualifiedName();
        Block block = methodDeclaration.getBody();
        String blockText = "";
        this.document = new Document(originalDocument.get());

        this.rewrite = ASTRewrite.create(block.getAST());
        ITrackedNodePosition nodePosition = rewrite.track(block);
        this.rangeMarker = new RangeMarker(nodePosition.getStartPosition(), nodePosition.getLength());

        block.accept(this);

        try {
            rangeMarker.apply(this.document);
            blockText = this.document.get(rangeMarker.getOffset(), rangeMarker.getLength());
            rangeMarker.removeChildren();
        } catch (BadLocationException e) {
            throw new IllegalStateException("Failed to rewrite this member accesses.");
        }

        return blockText;
    }

    // =============================== visitor methods


    public boolean visit(MethodInvocation node) {
        if (node.getExpression() == null && node.resolveMethodBinding() != null) {
            // unqualified field access
            ITrackedNodePosition nodePosition = rewrite.track(node);
            InsertEdit insertEdit;
            if (node.getExpression() == null) {
                if (Modifier.isStatic(node.resolveMethodBinding().getModifiers())) {
                    String typeParentOfMethod = node.resolveMethodBinding().getDeclaringClass().getName();
                    insertEdit = new InsertEdit(nodePosition.getStartPosition(), typeParentOfMethod + ".");
                    rangeMarker.addChild(insertEdit);
                } else {
                    if (node.resolveMethodBinding().getDeclaringClass().getErasure().getQualifiedName().equals(MTFTypeBindingName)){
//                    if (Arrays.stream(node.resolveMethodBinding().getDeclaringClass().getDeclaredMethods()).
//                            map(x -> MethodUtil.getMethodSignature(x)).collect(Collectors.toList()).
//                            contains(methodSignature)) {//Check if the method getDeclaringClass the same as the MTF declaring type
                        insertEdit = new InsertEdit(nodePosition.getStartPosition(), "this.");
                        rangeMarker.addChild(insertEdit);
                    }

                }
            }
        }

        return true;
    }


    public boolean visit(SimpleName node) {
        IBinding binding = node.resolveBinding();
        if (binding instanceof IVariableBinding) {
            IVariableBinding variableBinding = (IVariableBinding) binding;
            if (variableBinding.isField()) {
                if ((!(node.getParent() instanceof FieldAccess)) && (!(node.getParent() instanceof QualifiedName))) {
                    // unqualified field access
                    ITrackedNodePosition nodePosition = rewrite.track(node);
                    InsertEdit insertEdit;

                    if (Modifier.isStatic(variableBinding.getModifiers())&& !variableBinding.getDeclaringClass().isEnum()) {
                        insertEdit = new InsertEdit(nodePosition.getStartPosition(), variableBinding.getDeclaringClass().getQualifiedName() + ".");
                        rangeMarker.addChild(insertEdit);
                    } else {
                        if (variableBinding.getDeclaringClass().getErasure().getQualifiedName().equals(MTFTypeBindingName)){

//                            if (Arrays.stream(variableBinding.getDeclaringClass().getDeclaredMethods()).
//                                map(x -> MethodUtil.getMethodSignature(x)).collect(Collectors.toList()).
//                                contains(methodSignature)) {//Check if the variable getDeclaringClass the same as the MTF declaring type
                            insertEdit = new InsertEdit(nodePosition.getStartPosition(), "this.");
                            rangeMarker.addChild(insertEdit);
                        }
                    }
                }
            }
        }
        return false;
    }

}
