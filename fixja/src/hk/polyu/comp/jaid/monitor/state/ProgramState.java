package hk.polyu.comp.jaid.monitor.state;

import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Value;
import hk.polyu.comp.jaid.ast.MethodDeclarationInfoCenter;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LightLocation;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.util.CommonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProgramState {

    LightLocation runtimeLocation;
    protected LineLocation location;
    protected Map<ExpressionToMonitor, DebuggerEvaluationResult> jdiApiEtmValMap;
    protected Map<ExpressionToMonitor, DebuggerEvaluationResult> jdbEvaluateEtmValMap;

    public ProgramState(LineLocation location) {
        this.location = location;
        this.jdiApiEtmValMap = new HashMap<>();
        this.jdbEvaluateEtmValMap = new HashMap<>();
    }

    public ProgramState(LightLocation location) {
        this.runtimeLocation = location;
        this.jdiApiEtmValMap = new HashMap<>();
        this.jdbEvaluateEtmValMap = new HashMap<>();
    }

    public void setLocation(LineLocation location) {
        this.location = location;
    }

    public LineLocation getLocation() {
        return location;
    }

    public LightLocation getRuntimeLocation() {
        return runtimeLocation;
    }

    public Map<ExpressionToMonitor, DebuggerEvaluationResult> getJdiApiEtmValMap() {
        return jdiApiEtmValMap;
    }

    public Map<ExpressionToMonitor, DebuggerEvaluationResult> getJdbEvaluateEtmValMap() {
        return jdbEvaluateEtmValMap;
    }

    public Map<ExpressionToMonitor, DebuggerEvaluationResult> getExpressionToValueMap() {
        HashMap allMap = new HashMap<>(getJdiApiEtmValMap());
        allMap.putAll(getJdbEvaluateEtmValMap());
        return allMap;
    }

    public void extend(ExpressionToMonitor expressionToMonitor, DebuggerEvaluationResult result) {
        if (jdbEvaluateEtmValMap.containsKey(expressionToMonitor)) {
            throw new IllegalStateException();
        }

        jdbEvaluateEtmValMap.put(expressionToMonitor, result);
    }

    public void extendLocalVariables(MethodDeclarationInfoCenter infoCenter, Map<LocalVariable, Value> jdiLocalVariableValueMap, boolean createNewETM) {

        for (LocalVariable jdiVar : jdiLocalVariableValueMap.keySet()) {
            ExpressionToMonitor etm = infoCenter.getExpressionByText(jdiVar.name(), jdiVar.typeName(), createNewETM);
            if (etm != null && etm.getType().getName().equals(jdiVar.typeName()))
                this.jdiApiEtmValMap.put(etm, DebuggerEvaluationResult.fromValue(jdiLocalVariableValueMap.get(jdiVar)));
        }
    }

    public void extendLocalVarFields(Map<ExpressionToMonitor, Value> referenceFieldsMap) {
        for (ExpressionToMonitor expressionToMonitor : referenceFieldsMap.keySet()) {
            this.jdiApiEtmValMap.put(
                    expressionToMonitor,
                    DebuggerEvaluationResult.fromValue(referenceFieldsMap.get(expressionToMonitor))
            );
        }
    }

    public void extendFields(MethodDeclarationInfoCenter infoCenter, Map<Field, Value> jdiFieldValueMap, String refStr, boolean createNewETM) {
        for (Field jdiVar : jdiFieldValueMap.keySet()) {
            String varStr = CommonUtils.getRefFieldAccess(refStr, jdiVar.name());
            ExpressionToMonitor etm = infoCenter.getExpressionByText(varStr, jdiVar.typeName(), createNewETM);
            if (etm != null)
                this.jdiApiEtmValMap.put(
                        etm, DebuggerEvaluationResult.fromValue(jdiFieldValueMap.get(jdiVar)));
        }
    }

    public void putAllFields(Map<ExpressionToMonitor, Value> jdiFieldValueMap) {
        for (ExpressionToMonitor expressionToMonitor : jdiFieldValueMap.keySet())
            this.jdiApiEtmValMap.put(
                    expressionToMonitor,
                    DebuggerEvaluationResult.fromValue(jdiFieldValueMap.get(expressionToMonitor)));
    }

    public DebuggerEvaluationResult getValue(ExpressionToMonitor expressionToMonitor) {
        if (expressionToMonitor.isLiteral())
            return DebuggerEvaluationResult.getIntegerDebugValue(expressionToMonitor.getLiteralIntegerValue());

        if (getExpressionToValueMap().containsKey(expressionToMonitor))
            return getExpressionToValueMap().get(expressionToMonitor);

        return null;
    }

    /**
     * using jdiApiValMap only since no jdb evaluate will be used in ranking
     *
     * @param other
     * @return
     */
    public double computeSimilarity(ProgramState other) {
        Set<ExpressionToMonitor> commonExpressions = getJdiApiEtmValMap().keySet();
        commonExpressions.retainAll(other.getJdiApiEtmValMap().keySet());

        int commonValues = 0;
        for (ExpressionToMonitor exp : commonExpressions) {
            if (getJdiApiEtmValMap().get(exp).equals(other.getJdiApiEtmValMap().get(exp)))
                commonValues++;
        }

        return ((double) commonValues) / (getJdiApiEtmValMap().size() + other.getJdiApiEtmValMap().size() - commonValues);
    }

    protected String statesToString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ExpressionToMonitor, DebuggerEvaluationResult> entry : getExpressionToValueMap().entrySet()) {
            sb.append("\n").append(entry.getKey().toString()).append(" ==>");
            if (entry.getValue() instanceof DebuggerEvaluationResult.BooleanDebuggerEvaluationResult)
                sb.append(((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) entry.getValue()).getValue()).append(";");
            else
                sb.append(entry.getValue().toString()).append(";");

        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ProgramState{location=");
        if (location != null) sb.append(location);
        else sb.append(runtimeLocation);
        sb.append(", expressionToValueMap=[").append(statesToString()).append("]\n")
                .append(getExpressionToValueMap().size()).append("}");
        return sb.toString();
    }

    public static class DiffFrameState extends ProgramState {
        protected Map<ExpressionToMonitor, DebuggerEvaluationResult> changedJdiApiEtmValMap;

        // the location follows the exitFrame location.
        public DiffFrameState(LightLocation location) {
            super(location);
            changedJdiApiEtmValMap = new HashMap<>();
        }

        public void putAllVarDiff(List<Map<ExpressionToMonitor, DebuggerEvaluationResult>> fieldDiff) {
            if (fieldDiff == null || fieldDiff.size() != 2 || fieldDiff.get(0).size() != fieldDiff.get(1).size())
                throw new IllegalStateException("Format of Diff <Exp,Val> pair is not correct.");
            this.jdiApiEtmValMap.putAll(fieldDiff.get(0));
            this.changedJdiApiEtmValMap.putAll(fieldDiff.get(1));
        }

        public Map<ExpressionToMonitor, DebuggerEvaluationResult> getChangedJdiApiEtmValMap() {
            return changedJdiApiEtmValMap;
        }

        @Override
        public String toString() {
            return "DiffFrameState{" +
                    "location=" + location +
                    '}';
        }
    }
}
