package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.java.JavaProject;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * Created by Max.
 */
public class TypeCollector extends ASTVisitor {

    private CompilationUnit compilationUnit;
    private CompilationUnit compilationUnitForFqName;

    private final Map<String, AbstractTypeDeclaration> types;

    /* List of nested type names. */
    private Deque<String> nestedTypeNames;

    /** Constructor.
     *
     * @param typeDeclarationMap
     */
    public TypeCollector(Map<String, AbstractTypeDeclaration> typeDeclarationMap){
        this.types = typeDeclarationMap;
    }

    public TypeCollector(){
        this.types = new HashMap<>();
    }

    public Map<String, AbstractTypeDeclaration> getTypes() {
        return types;
    }

    /** Requestor with callbacks to be used by a parser.
     *
     * @return
     */
    public FileASTRequestor getASTRequestor() {
        final TypeCollector thisCollector = this;
        FileASTRequestor requestor = new FileASTRequestor() {
            @Override
            public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                super.acceptAST(sourceFilePath, ast);
                ast.setProperty(JavaProject.COMPILATION_UNIT_PROPERTY_PATH, sourceFilePath);
                thisCollector.collectFrom(ast);

                String fqName = Session.getSession().getConfig().getFaultyClassName();
                String simpleFqName = fqName.split("\\.")[fqName.split("\\.").length-1];
                String sourceFilePathDot = sourceFilePath.replace("\\",".");
                String simpleSourceFilePathDot = sourceFilePathDot.split("\\.")[sourceFilePathDot.split("\\.").length-2];
                if(sourceFilePathDot.contains(fqName)&&simpleFqName.equals(simpleSourceFilePathDot)){
                    compilationUnitForFqName = ast;
                }
            }
        };
        return requestor;
    }

    /**
     * Collect type declarations from a compilation unit.
     *
     * @param compilationUnit CompilationUnit from which types will be collected.
     */
    public void collectFrom(CompilationUnit compilationUnit){
        this.compilationUnit = compilationUnit;

        nestedTypeNames = new LinkedList<>();
        nestedTypeNames.push(compilationUnit.getPackage().getName().toString());

        this.compilationUnit.accept(this);

    }

    public CompilationUnit getCompilationUnit(){
        return compilationUnitForFqName;
    }

    private String getTypeName(){
        Iterator<String> iterator = nestedTypeNames.iterator();
        StringBuilder sb = new StringBuilder();
        while(iterator.hasNext()){
            sb.insert(0, iterator.next());
        }
        return sb.toString();
    }

    private void collectTypeInternal(String fqName, TypeDeclaration typeDeclaration){
        if(types.containsKey(fqName))
            throw new IllegalStateException("Error! Duplicated type declarations found: " + fqName + ".");

        types.put(fqName, typeDeclaration);
    }

    //========================== Visitor methods

    public boolean visit(TypeDeclaration node) {
        String name = node.getName().getIdentifier();

        if(node.isPackageMemberTypeDeclaration())
            nestedTypeNames.push("." + name);
        else
            nestedTypeNames.push("$" + name);

        collectTypeInternal(getTypeName(), node);

        for(TypeDeclaration typeDeclaration: node.getTypes()) {
            typeDeclaration.accept(this);
        }

        nestedTypeNames.pop();
        return false;
    }

    @Override
    public boolean visit(AnonymousClassDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        return false;
    }


}