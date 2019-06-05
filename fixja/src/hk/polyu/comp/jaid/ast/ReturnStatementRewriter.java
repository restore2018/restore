package hk.polyu.comp.jaid.ast;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.ReplaceEdit;

/**
 * Created by Max PEI.
 */
public class ReturnStatementRewriter extends ASTVisitor{

    private Document document;
    private Block block;

    private String returnVariableName;
    private ASTRewrite rewrite;
    private RangeMarker rangeMarker;

    public String rewrite(Document originalDocument, Block block, String returnVariableName){
        this.document = new Document(originalDocument.get());
        this.block = (Block) ASTNode.copySubtree(block.getAST(), block);

        this.returnVariableName = returnVariableName;
        this.rewrite = ASTRewrite.create(block.getAST());
        ITrackedNodePosition nodePosition = rewrite.track(block);
        this.rangeMarker = new RangeMarker(nodePosition.getStartPosition(), nodePosition.getLength());

        block.accept(this);

        String blockText = "";
        try{
            rangeMarker.apply(this.document);
            blockText = this.document.get(rangeMarker.getOffset(), rangeMarker.getLength());
        }
        catch(BadLocationException e){
            throw new IllegalStateException("Failed to rewrite return statements.");
        }

        return blockText;
    }

    // =============================== visitor methods


    public boolean visit(MethodDeclaration node) {
        return false;
    }

    public boolean visit(ReturnStatement node){
        if(node.getExpression() != null){
            ITrackedNodePosition nodePosition = rewrite.track(node.getExpression());
            ReplaceEdit replaceEdit;
            replaceEdit = new ReplaceEdit(nodePosition.getStartPosition(), nodePosition.getLength(), returnVariableName + " = (" + node.getExpression().toString() + ")");
            rangeMarker.addChild(replaceEdit);
        }

        return false;
    }

    public boolean visit(TypeDeclarationStatement node) {
        return false;
    }


}
