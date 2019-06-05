package hk.polyu.comp.jaid.ast;


import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import org.eclipse.jdt.core.dom.*;

import java.util.*;


/**
 * Created by Max PEI.
 */
public class LocalVariableDefAssignCollector extends ASTVisitor {

    public LocalVariableDefAssignCollector() {
        super(false);
    }

    public void collect(MethodToMonitor contextMethod){
        this.contextMethod = contextMethod;
        this.variableDefinitionLocationMap = new HashMap<>();
        this.variableAssignmentLocationMap = new HashMap<>();

        getMethodDeclaration().accept(this);
    }

    public Map<IVariableBinding, LineScope> getVariableDefinitionLocationMap() {
        return variableDefinitionLocationMap;
    }

    public Map<IVariableBinding, LineLocation> getVariableAssignmentLocationMap() {
        return variableAssignmentLocationMap;
    }

    private MethodDeclaration getMethodDeclaration() {
        return contextMethod.getMethodAST();
    }

    public MethodToMonitor getContextMethod() {
        return contextMethod;
    }

    private LineLocation getLocation(ASTNode node, boolean atBeginning){
        CompilationUnit compilationUnit = (CompilationUnit) node.getRoot();
        int position = compilationUnit.getLineNumber(atBeginning ? node.getStartPosition() : node.getStartPosition() + node.getLength());
        LineLocation result =  LineLocation.newLineLocation(getContextMethod(), position);
        return result;
    }

    private ASTNode getEnclosingScopeNode(ASTNode node){
        ASTNode currentNode = node;
        while(currentNode != null && !isScopeNode(currentNode)){
            currentNode = currentNode.getParent();
        }

        return currentNode;
    }

    private boolean isScopeNode(ASTNode node){
        return node instanceof Block || node instanceof EnhancedForStatement || node instanceof ForStatement
                || node instanceof MethodDeclaration;
    }

    private MethodToMonitor contextMethod;
    private Map<IVariableBinding, LineScope> variableDefinitionLocationMap;
    private Map<IVariableBinding, LineLocation> variableAssignmentLocationMap;

    private void recordDeclaration(Expression expr) {
        if (isReferenceToLocalVariable(expr)) {
            Map<IVariableBinding, LineScope> map = getVariableDefinitionLocationMap();
            SimpleName name = (SimpleName) expr;
            IVariableBinding binding = (IVariableBinding) name.resolveBinding();
            LineLocation beginLocation = getLocation(expr, true);
            ASTNode enclosingNode = getEnclosingScopeNode(expr);
            LineLocation endLocation = getLocation(enclosingNode, false);
            LineScope varScope = new LineScope(beginLocation, endLocation);

            assert !map.containsKey(binding);

            map.put(binding, varScope);
        }
    }

    private void recordAssignment(Expression expr) {
        if (isReferenceToLocalVariable(expr)) {
            Map<IVariableBinding, LineLocation> map = getVariableAssignmentLocationMap();
            SimpleName name = (SimpleName) expr;
            IVariableBinding binding = (IVariableBinding) name.resolveBinding();

            assert getVariableDefinitionLocationMap().containsKey(binding);

            LineLocation newLocation = getLocation(expr, true);
            if (map.containsKey(binding)) {
                LineLocation oldLocation = map.get(binding);
                if (newLocation.isBefore(oldLocation)) {
                    map.replace(binding, newLocation);
                }
            } else {
                map.put(binding, newLocation);
            }
        }
    }

    private boolean isReferenceToLocalVariable(Expression expr) {
        if (expr instanceof SimpleName) {
            SimpleName name = (SimpleName) expr;
            if (name.resolveBinding() instanceof IVariableBinding) {
                IVariableBinding binding = (IVariableBinding) name.resolveBinding();
                return !(binding.isField() || binding.isEnumConstant());
            }
        }
        return false;
    }

    // =================================== visitor methods

    // Skip anonymous class declaration
    public boolean visit(AnonymousClassDeclaration node){
        return false;
    }

    public boolean visit(Assignment node) {
        recordAssignment(node.getLeftHandSide());

        return true;
    }

    // Skip Lambda expression
    public boolean visit(LambdaExpression node) {
        return false;
    }

    // Skip declarations other than that of the starting method
    public boolean visit(MethodDeclaration node) {
        return node == getMethodDeclaration();
    }

    public boolean visit(SingleVariableDeclaration node) {
        recordDeclaration(node.getName());
        recordAssignment(node.getName());

        return true;
    }

    // Skip inner type declarations
    public boolean visit(TypeDeclaration node) {
        return false;
    }

    public boolean visit(VariableDeclarationFragment node) {
        recordDeclaration(node.getName());
        if(node.getInitializer() != null)
            recordAssignment(node.getName());

        return true;
    }



}
