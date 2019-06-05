package hk.polyu.comp.jaid.monitor;

import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.monitor.suspiciouness.AbsSuspiciousnessAlgorithm;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;

import java.util.*;


/**
 * Created by Max PEI.
 */
public class LineLocation implements Comparable<LineLocation> {

    private final MethodToMonitor contextMethod;
    private final int lineNo;
    private List<ExpressionToMonitor> expressionsToMonitor;

    private static int maximumDistanceToFailure;

    public static int getMaximumDistanceToFailure() {
        return maximumDistanceToFailure;
    }

    public static void setMaximumDistanceToFailure(int value) {
        maximumDistanceToFailure = value;
    }

    private int occurrenceInPassing = 0;
    private int occurrenceInFailing = 0;
    private Map<AbsSuspiciousnessAlgorithm.SbflAlgorithm, Double> suspiciousness = new HashMap<>();

    private static Map<String, LineLocation> allLineLocationMap = new HashMap<>();

    public static LineLocation newLineLocation(MethodToMonitor contextMethod, int lineNo) {
        String locationKey = locationToString(contextMethod, lineNo);
        if (!allLineLocationMap.keySet().contains(locationKey)) {
            LineLocation lineLocation = new LineLocation(contextMethod, lineNo);
            allLineLocationMap.put(locationKey, lineLocation);
        }
        return allLineLocationMap.get(locationKey);
    }

    private LineLocation(MethodToMonitor contextMethod, int lineNo) {
        this.contextMethod = contextMethod;
        this.lineNo = lineNo;
    }

    public int getLineNo() {
        return lineNo;
    }

    public boolean isBefore(LineLocation location) {
        return getLineNo() < location.getLineNo();
    }

    public boolean isBeforeOrEqual(LineLocation location) {
        return isBefore(location) || equals(location);
    }

    public MethodDeclaration getMethodDeclaration() {
        return contextMethod.getMethodAST();
    }

    public MethodToMonitor getContextMethod() {
        return contextMethod;
    }

    public Statement getStatement() {
        return getContextMethod().getMethodDeclarationInfoCenter().getAllLocationStatementMap().getOrDefault(this, null);
    }

    public Set<ExpressionToMonitor> getExpressionsToMonitor() {
        return getContextMethod().getMethodDeclarationInfoCenter().getLocationExpressionMap().getOrDefault(this, new TreeSet<>(ExpressionToMonitor.getByLengthComparator()));
    }

    public Set<ExpressionToMonitor> getExpressionsAppearedAtLocation() {
        return getContextMethod().getMethodDeclarationInfoCenter().getExpressionsAppearAtLocationMap().getOrDefault(this, new HashSet<>());
    }
    public Statement getStatementForMoreFix(){
        return getContextMethod().getMethodDeclarationInfoCenter().getAllLocationStatementMapForMoreFix().getOrDefault(this, null);
    }

    public Set<ExpressionToMonitor> getExpressionsAppearedAtLocationForMoreFix(){
        return getContextMethod().getMethodDeclarationInfoCenter().getExpressionsAppearAtLocationMapForMoreFix().getOrDefault(this, new HashSet<>());
    }

    public void registerExpressionsToMonitor(List<ExpressionToMonitor> expressionToRegister) {
        getExpressionsToMonitor().addAll(expressionToRegister);
    }

    public void removeExpressionsToMonitor(Collection<ExpressionToMonitor> expressionsToRemove) {
        getExpressionsToMonitor().removeAll(expressionsToRemove);
    }

    public void removeExpressionToMonitor(ExpressionToMonitor expressionToRemove) {
        getExpressionsToMonitor().remove(expressionToRemove);
    }


    public int getOccurrenceInPassing() {
        return occurrenceInPassing;
    }

    public void increasingOccurrenceInPassing() {
        this.occurrenceInPassing++;
    }

    public int getOccurrenceInFailing() {
        return occurrenceInFailing;
    }

    public void increasingOccurrenceInFailing() {
        this.occurrenceInFailing++;
    }

    public void computeSuspiciousness(MethodDeclarationInfoCenter infoCenter, AbsSuspiciousnessAlgorithm computeAlgorithm) {
        suspiciousness.put(computeAlgorithm.getSbflAlgorithm(),
                computeAlgorithm.computeSuspiciousness(occurrenceInFailing, occurrenceInPassing, 0, 0));
    }
    public double getSuspiciousness(AbsSuspiciousnessAlgorithm.SbflAlgorithm sbflAlgorithm) {
        if (suspiciousness.containsKey(sbflAlgorithm))
            return suspiciousness.get(sbflAlgorithm);
        else
            throw new IllegalStateException("No suspicious score for algorithm:" + sbflAlgorithm.toString());
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LineLocation that = (LineLocation) o;

        if (getMethodDeclaration() != that.getMethodDeclaration() || getLineNo() != that.getLineNo())
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = getMethodDeclaration() != null ? getMethodDeclaration().hashCode() : 0;
        result = 31 * result + getLineNo();
        return result;
    }


    public String richInfoToString() {
        return this.toString() +
                "{occurrenceInPassing=" + occurrenceInPassing +
                ", occurrenceInFailing=" + occurrenceInFailing + "}";
    }

    @Override
    public String toString() {
        return locationToString(this.getContextMethod(), this.getLineNo());
    }

    private static String locationToString(MethodToMonitor mtm, int lineNo) {
        String sig = mtm == null ? "null" : mtm.getSignature();
        return sig + "[" + lineNo + "]";

    }

    @Override
    public int compareTo(LineLocation o) {
        return this.getLineNo() - o.getLineNo();
    }
}
