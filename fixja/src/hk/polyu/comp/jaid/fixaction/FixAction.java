package hk.polyu.comp.jaid.fixaction;

import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.restore.FixerRES;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Ls CHEN.
 */
public class FixAction {

    private long fixId = -1;//set according to the generated formatted method body string while validation (compilables)
    private String fix;
    private Schemas.Schema schema;

    private List<TestExecutionResult> testExecutionResults;
    private List<TestExecutionResult> secondValidationResults;
    private List<TestExecutionResult> successfulTestExecutionResults;
    private List<Integer> partialSuccessAndFailTestExecutionResults;
    private int exitLineNo;
    private int entryLineNo;
    private double locationScore;
    private double QloseScore;
    private double passingImpact;
    private double failingImpact;
    private double passingImpactF;
    private double failingImpactF;

    private Boolean isForFaultLocalization = false;
    private int execTime = 0;
    private float ochiai = 0;

    public float getOchiai() {
        return ochiai;
    }

    public void setOchiai(float ochiai) {
        this.ochiai = ochiai;
    }

    public int getExecTime() {
        return execTime;
    }

    public void setExecTime(int execTime) {
        this.execTime = execTime;
    }

    public double getPassingImpactF() {
        return passingImpactF;
    }

    public void setPassingImpactF(double passingImpactF) {
        this.passingImpactF = passingImpactF;
    }

    public double getFailingImpactF() {
        return failingImpactF;
    }

    public void setFailingImpactF(double failingImpactF) {
        this.failingImpactF = failingImpactF;
    }

    public double getFailingImpactFLDiff() {
        return failingImpact - failingImpactF;
    }

    public double getPassingImpactFLDiff() {
        return passingImpact - passingImpactF;
    }


    //    private double evaluated = Integer.MIN_VALUE;
    private StateSnapshot stateSnapshot;
    private ArrayList<String> seed;
    private boolean isWellformed;
    private boolean isValid;
    private boolean wasValidated;
    private double snippet_simi;
    private String statementTextToBeReplaced;
    private double isFakeSnapshot = 1;


    public FixAction(String fix, StateSnapshot stateSnapshot, Schemas.Schema fixSchema, String seed, double snippet_simi) {
        this.fix = fix;
        this.stateSnapshot = stateSnapshot;
        this.schema = fixSchema;
        this.seed = new ArrayList<>();
        this.seed.add(seed + ";; Schema::" + fixSchema);
        this.isWellformed = true;
        this.isValid = false;
        this.snippet_simi = snippet_simi;
//        this.fixId = CommonUtils.getId(contentToString());//using fix string and location to generate fix id
    }

    public FixAction(String fix, StateSnapshot stateSnapshot, Schemas.Schema fixSchema, String seed, double snippet_simi,double isFakeSnapshot) {
        this.fix = fix;
        this.stateSnapshot = stateSnapshot;
        this.schema = fixSchema;
        this.seed = new ArrayList<>();
        this.seed.add(seed + ";; Schema::" + fixSchema);
        this.isWellformed = true;
        this.isValid = false;
        this.snippet_simi = snippet_simi;
        this.isFakeSnapshot = isFakeSnapshot;
//        this.fixId = CommonUtils.getId(contentToString());//using fix string and location to generate fix id
    }

    public String getSchema(){
        return schema.getSchemaOrder();
    }

    public double getIsFakeSnapshot(){
        return isFakeSnapshot;
    }

    public String getStatementTextToBeReplaced() {
        return statementTextToBeReplaced;
    }

    public int getExitLineNo() {
        return exitLineNo;
    }

    public double getLocationScore() {
        return locationScore;
    }

    public void setLocationScore(double locationScore) {
        this.locationScore = locationScore;
    }

    public double getQloseScore() {
        return QloseScore;
    }

    public void setQloseScore(double qloseScore) {
        this.QloseScore = qloseScore;
    }

    public double getPassingImpact() {
        return passingImpact;
    }

    public void setPassingImpact(double passingImpact) {
        this.passingImpact = passingImpact;
    }

    public double getFailingImpact() {
        return failingImpact;
    }

    public void setFailingImpact(double failingImpact) {
        this.failingImpact = failingImpact;
    }

    public void setExitLineNo(int exitLineNo) {
        this.exitLineNo = exitLineNo;
    }

    public int getEntryLineNo() {
        return entryLineNo;
    }

    public void setEntryLineNo(int entryLineNo) {
        this.entryLineNo = entryLineNo;
    }

    public void setStatementTextToBeReplaced(String statementTextToBeReplaced) {
        this.statementTextToBeReplaced = statementTextToBeReplaced;
    }

    public boolean isWellformed() {
        return isWellformed;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public void setWellformed(boolean wellformed) {
        isWellformed = wellformed;
    }

    public void setIsForFaultLocalization(boolean ForFaultLocalization) { isForFaultLocalization = ForFaultLocalization;}

    public boolean isForFaultLocalization(){ return isForFaultLocalization;}

    public boolean wasValidated() {
        return wasValidated;
    }

    public void setValidated(boolean wasValidated) {
        this.wasValidated = wasValidated;
    }

    public String getFix() {
        return fix;
    }

    public void setFix(String fix) {
        this.fix = fix;
    }

    public boolean needsValidation() {
        // fixme: when will getFix() be null or be empty?
        return getFix() != null && getFix().length() > 0 && isWellformed();
    }

    public StateSnapshot getStateSnapshot() {
        return stateSnapshot;
    }

    public LineLocation getLocation() {
        return getStateSnapshot().getLocation();
    }

    public List<TestExecutionResult> getTestExecutionResults() {
        return testExecutionResults;
    }

    public int getSuccessfulTestExecutionResultsCount() {
        return (int) getTestExecutionResults().stream().filter(x -> x != null && x.wasSuccessful()).count();
    }

    public List<Integer> getSuccessAndFailTestExecutionResults(){
        List<Integer> temp = new ArrayList<Integer>();
        for(TestExecutionResult failingTer: FixerRES.failingTestResults){
            for(TestExecutionResult ter:getTestExecutionResults()){
                if(failingTer.getTestClass().equals(ter.getTestClass())&&failingTer.getTestMethod().equals(ter.getTestMethod())){
                    temp.add(ter.wasSuccessful()?1:0);
                    break;
                }
            }

        }
        //temp = getTestExecutionResults().stream().map(x-> x.wasSuccessful()? 1: 0).collect(Collectors.toList());
        return temp;
    }

    public List<Integer> serPartialSuccessAndFailTestExecutionResults(){
        partialSuccessAndFailTestExecutionResults = getTestExecutionResults().stream().map(x-> x.wasSuccessful()? 1: 0).collect(Collectors.toList());
        return partialSuccessAndFailTestExecutionResults;
    }

    public List<Integer> getPartialSuccessAndFailTestExecutionResults(){
        return partialSuccessAndFailTestExecutionResults;
    }

    public List<TestExecutionResult> getSuccessfulTestExecutionResults() {
        if (successfulTestExecutionResults == null) {
            successfulTestExecutionResults = getTestExecutionResults().stream().filter(x -> x != null && x.wasSuccessful()).collect(Collectors.toList());
        }
        return successfulTestExecutionResults;
    }


    public List<TestExecutionResult> getSecondValidationResults() {
        return secondValidationResults;
    }

    public void setSecondValidationResults(List<TestExecutionResult> secondValidationResults) {
        this.secondValidationResults = secondValidationResults;
    }

    public void setTestExecutionResults(List<TestExecutionResult> testExecutionResults) {
        //if (this.testExecutionResults != null)
            //throw new IllegalStateException("testExecutionResults is immutable.");

        this.testExecutionResults = testExecutionResults;
    }

    public void clearDebugTestResults() {
        testExecutionResults = null;
    }

    public long getFixId() {
        return fixId;
    }

    public void setFixId(long fixId) {
        this.fixId = fixId;
    }

    public ArrayList<String> getSeed() {
        return seed;
    }

    public void updateSeedForDuplicated(FixAction duplicated) {
        this.seed.addAll(duplicated.getSeed());
    }

    private String getSeedString() {
        StringBuilder sb = new StringBuilder();
        for (String s : this.seed) {
            sb.append(s).append("|||");
        }
        sb.deleteCharAt(sb.lastIndexOf("|||"));
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FixAction{")
                .append("fixId=").append(fixId);
        sb.append(", seed=").append(getSeedString());
        sb.append(", simi=").append(snippet_simi);
        sb.append(", locationScore=").append(locationScore);
        sb.append(", fix=[\n").append(fix).append("]");
        if (getLocation() != null)
            sb.append("\n, location=").append(getLocation().getLineNo()).append("::").append(getLocation().getStatement());
        sb.append("}\n");
        return sb.toString();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FixAction fixAction = (FixAction) o;
        if (fixId != -1 && fixAction.fixId != -1) return fixId == fixAction.fixId;
        return fix.trim().equals(fixAction.fix.trim());
    }

    @Override
    public int hashCode() {
        int result;
        result = fix.trim().hashCode();
        result = 31 * result + getLocation().hashCode();
        return result;
    }

    /**
     * Use formatted method body to generate fixId instead
     *
     * @return
     */
    @Deprecated
    public String contentToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("fix=[\n").append(fix).append("]");
        if (getLocation() != null)
            sb.append("\n, location=").append(getLocation().getLineNo()).append("::").append(getLocation().getStatement());
        sb.append("}\n");
        return sb.toString();
    }
}
