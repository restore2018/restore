package hk.polyu.comp.jaid.monitor;

import hk.polyu.comp.jaid.ast.*;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.MONITORED_EXPS;

/**
 * Created by Max PEI.
 */
public class MethodToMonitor {

    private MethodDeclarationInfoCenter methodDeclarationInfoCenter;
    private String fullQualifiedClassName;
    private String signature;
    private MethodDeclaration methodAST;
    private String returnVariableName;

    private ExpressionToMonitor thisExpressionToMonitor;
    private Set<ExpressionToMonitor> accessedFieldSet;

    public MethodToMonitor(String fullQualifiedClassName, String signature, MethodDeclaration methodAST) {
        this.fullQualifiedClassName = fullQualifiedClassName;
        this.signature = signature;
        this.methodAST = methodAST;
    }

    public void initMethodDeclarationInfoCenter(){
        methodDeclarationInfoCenter = new MethodDeclarationInfoCenter(this);
        methodDeclarationInfoCenter.init();

    }

    public MethodDeclarationInfoCenter getMethodDeclarationInfoCenter() {

        return methodDeclarationInfoCenter;
    }

    public Set<ExpressionToMonitor> getAccessedFieldSet() {
        return accessedFieldSet;
    }

    public String getFullQualifiedClassName() {
        return fullQualifiedClassName;
    }

    public String getSignature() {
        return this.signature;
    }

    public String getReturnVariableName(){
        if(returnVariableName == null)
            returnVariableName = MethodUtil.getTempReturnVariableName(getMethodAST());

        return returnVariableName;
    }

    public boolean isInnerClassMethod() {
        return fullQualifiedClassName.contains("$");
    }

    public MethodDeclaration getMethodAST() {
        return methodAST;
    }

    public Set<LineLocation> validLineLocations(){
        if(validLineLocationsCache == null){
            validLineLocationsCache = new HashSet<>();
        }
        return validLineLocationsCache;
    }

    private Set<LineLocation> validLineLocationsCache;

    public String getSimpleName() {
        return methodAST.getName().getIdentifier();
    }

    public boolean returnsVoid() {
        Type returnType = methodAST.getReturnType2();
        return returnType.isPrimitiveType() && ((PrimitiveType) returnType).getPrimitiveTypeCode() == PrimitiveType.VOID;
    }

    public void setAccessedFields(Set<String> accessedFields) {
        accessedFieldSet = new HashSet<>();
        for (String field : accessedFields) {
            for (ExpressionToMonitor declaredField : getMethodDeclarationInfoCenter().getReferenceEtmFields()) {
                if (field.equals(declaredField.getText())) {//fixme: "af_test.MyList.index" not equals "this.index"
                    accessedFieldSet.add(declaredField);
                    break;
                }
            }
        }

        //Log collected Fields
        try {
            LoggingService.infoFileOnly("============ accessedFieldSet size:" + accessedFieldSet.size(), MONITORED_EXPS);
            accessedFieldSet.stream().forEach(f ->
                    LoggingService.infoFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodToMonitor that = (MethodToMonitor) o;

        if (!getFullQualifiedClassName().equals(that.getFullQualifiedClassName())) return false;
        return getSignature().equals(that.getSignature());

    }

    @Override
    public int hashCode() {
        int result = getFullQualifiedClassName().hashCode();
        result = 31 * result + getSignature().hashCode();
        return result;
    }

    public Annotation getOverrideAnnotation() {
        if (!hasSearchedForAnnotation) {
            OverrideAnnotationFinder finder = new OverrideAnnotationFinder();
            finder.findOverrideAnnotation(this.getMethodAST());
            overrideAnnotation = finder.overrideAnnotation;
            hasSearchedForAnnotation = true;
        }
        return overrideAnnotation;
    }

    private boolean hasSearchedForAnnotation;
    private Annotation overrideAnnotation;

    private class OverrideAnnotationFinder extends ASTVisitor {
        public MarkerAnnotation overrideAnnotation;

        public void findOverrideAnnotation(MethodDeclaration methodDeclaration) {
            methodDeclaration.accept(this);
        }

        @Override
        public boolean visit(MarkerAnnotation node) {
            if ("Override".equals(node.getTypeName().toString())) {
                overrideAnnotation = node;
            }
            return false;
        }
    }
}
