package hk.polyu.comp.jaid.test;

import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.monitor.state.ExitState;
import hk.polyu.comp.jaid.monitor.state.FramesStack;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.tester.TestRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Max PEI.
 */
public class TestExecutionResult {
    private String testClass;
    private String testMethod;
    private long runTime;
    private boolean wasSuccessful;
    private boolean wasTestKilled;
    private String failureMsg;

    private List<ProgramState> observedStates;

    private List<ExitState> exitStates;
    private Set<Integer> observedLocations;

    private List<FramesStack> framesStackList;// Store ENTRY and EXIT states only
    private List<FramesStack.DiffFrameStack> methodImpact;
    private List<FramesStack.DiffFrameStack> methodImpactFirst;
    private List<FramesStack.DiffFrameStack> fixImpact;
    private List<FramesStack.DiffFrameStack> fixImpactFirst;

    public TestExecutionResult(String testClass, String testMethod) {
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.runTime = 0;
        this.wasSuccessful = false;
        this.wasTestKilled = false;
    }

    public boolean isEqualRequest(TestRequest request) {
        return getTestClass().equals(request.getTestClass()) && getTestMethod().equals(request.getTestMethod());
    }

    public String getTestClass() {
        return testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }

    public String getTestClassAndMethod() {
        return testClass + "." + testMethod;
    }


    public double computeLocationScore(TestExecutionResult other) {
        if (!getTestClassAndMethod().equals(other.getTestClassAndMethod()))
            throw new IllegalStateException();

        // Compute location similarity
        Set<Integer> thisObservedLocations = getObservedLocations(this);
        Set<Integer> otherObservedLocations = getObservedLocations(other);
//        double locationSimilarity = ((double) thisObservedLocations.size() + 1) / (thisObservedLocations.size() + otherObservedLocations.size() + 1);
        double locationSimilarity = ((double) 1) / (Math.abs(thisObservedLocations.size() - otherObservedLocations.size()) + 1);
        return locationSimilarity;
    }

    public double computeStateScore(TestExecutionResult other) {
        if (!getTestClassAndMethod().equals(other.getTestClassAndMethod()))
            throw new IllegalStateException();
        // Compute state similarity
        int minSize = Math.min(getExitStates().size(), other.getExitStates().size());
        double stateSimilarity;
        if (minSize == 0) {
            // this condition should not be true after changing the location to  monitor exit state
            stateSimilarity = MINIMAL_SIMILARITY;
            LoggingService.warnAll("Missing exit state!");
        } else {
            //compare first minSize's exit states
//            List<Double> stateSimilarities = new LinkedList<>();
//            for (int i = 0; i < minSize; i++) {
//                stateSimilarities.add(getExitStates().get(i).computeSimilarity(other.getExitStates().get(i)));
//            }
//            stateSimilarity = stateSimilarities.stream().mapToDouble(x -> x).summaryStatistics().getAverage();

            //compare last exit state
//            stateSimilarity =getExitStates().get(getExitStates().size()-1).computeSimilarity(other.getExitStates().get(other.getExitStates().size()-1));
            //compare exit state before each assertionDivider
            stateSimilarity = compareExitStateBeforeAssertion(other);
        }
        return stateSimilarity;
    }

    /**
     * compare exit state before each assertionDivider
     *
     * @return
     */
    private double compareExitStateBeforeAssertion(TestExecutionResult other) {
        //Collect exit states before assertions
        double stateSimilarity = 0;
        List<ExitState> thisStateToCompare = new ArrayList<>();
        List<ExitState> otherStateToCompare = new ArrayList<>();
        //find last exitState before a DIVIDER
        for (int i = 0; i < getExitStates().size(); i++) {
            if (getExitStates().get(i).equals(ExitState.divider)
                    && i - 1 >= 0
                    && !(getExitStates().get(i - 1).equals(ExitState.divider))) {
                thisStateToCompare.add(getExitStates().get(i - 1));
            }
        }
        //get last exitState
        if (!(getExitStates().get(getExitStates().size() - 1).equals(ExitState.divider))
                && (thisStateToCompare.size() == 0 || thisStateToCompare.get(thisStateToCompare.size() - 1) != getExitStates().get(getExitStates().size() - 1))
                ) {
            thisStateToCompare.add(getExitStates().get(getExitStates().size() - 1));
        }
        for (int i = 0; i < other.getExitStates().size(); i++) {
            if (other.getExitStates().get(i).equals(ExitState.divider)
                    && i - 1 >= 0
                    && !(other.getExitStates().get(i - 1).equals(ExitState.divider))) {
                otherStateToCompare.add(other.getExitStates().get(i - 1));
            }
        }
        if (!(other.getExitStates().get(other.getExitStates().size() - 1).equals(ExitState.divider))
                && (otherStateToCompare.size() == 0 || otherStateToCompare.get(otherStateToCompare.size() - 1) != other.getExitStates().get(other.getExitStates().size() - 1))
                ) {
            otherStateToCompare.add(other.getExitStates().get(other.getExitStates().size() - 1));
        }
        //Compute the similarity
        if (thisStateToCompare.size() != otherStateToCompare.size())
            throw new IllegalStateException();
        for (int i = 0; i < thisStateToCompare.size(); i++) {
            stateSimilarity += thisStateToCompare.get(i).computeSimilarity(otherStateToCompare.get(i));

        }
        return stateSimilarity / (double) thisStateToCompare.size();
    }


    public static final double MINIMAL_SIMILARITY = 0.1;

    public long getRunTime() {
        return runTime;
    }

    private static Set<Integer> getObservedLocations(TestExecutionResult testExecutionResult) {
        Set<Integer> observedLocations;
        if (testExecutionResult.getObservedLocations().size() > 0)
            observedLocations = new HashSet<>(testExecutionResult.getObservedLocations());
        else
            observedLocations = testExecutionResult.getObservedStates().stream().mapToInt(x -> x.getLocation().getLineNo()).boxed().collect(Collectors.toSet());
        return observedLocations;
    }

    public List<ProgramState> getObservedStates() {
        if (observedStates == null)
            observedStates = new LinkedList<>();

        return observedStates;
    }

    public void removeObservedStates() {
        LoggingService.debug("removeObservedStates");
        this.observedStates = null;
    }

    public boolean wasSuccessful() {
        return !wasTestKilled && wasSuccessful;
    }

    public void setRunTime(long runTime) {
        this.runTime = runTime;
    }

    public void setWasSuccessful(boolean wasSuccessful) {
        this.wasSuccessful = wasSuccessful;
    }

    public boolean wasTestKilled() {
        return wasTestKilled;
    }

    public void setTestKilled(boolean wasTestKilled) {
        this.wasTestKilled = wasTestKilled;
    }

    /**
     * Calculate snapshot suspiciousness and QloseRank
     *
     * @return
     */
    public Set<Integer> getObservedLocations() {
        if (observedLocations == null)
            observedLocations = new HashSet<>();
        return observedLocations;
    }

    public List<ExitState> getExitStates() {
        if (exitStates == null)
            exitStates = new LinkedList<>();

        return exitStates;
    }

    public String getFailureMsg() {
        return failureMsg;
    }

    public void setFailureMsg(String failureMsg) {
        this.failureMsg = failureMsg;
    }
    public void appendFailureMsg(String failureMsg) {
        this.failureMsg += failureMsg;
    }

    public void addExitState(ExitState exitState) {
        this.getExitStates().add(exitState);
    }

    public void addDividerExitState() {
        if (getExitStates().size() != 0 && !getExitStates().get(getExitStates().size() - 1).equals(ExitState.divider))
            getExitStates().add(ExitState.divider);
    }

    /**
     * Calculate JaidRank
     *
     * @return
     */
    public List<FramesStack> getFramesStackList() {
        if (framesStackList == null)
            framesStackList = new LinkedList<>();
        return framesStackList;
    }

    public void clearFramesStackList() {
        if (framesStackList != null)
            framesStackList.clear();
    }

    public List<FramesStack.DiffFrameStack> getMethodImpact() {
        return methodImpact;
    }

    public void setMethodImpact(List<FramesStack.DiffFrameStack> methodImpact) {
        this.methodImpact = methodImpact;
        clearFramesStackList();
    }

    public List<FramesStack.DiffFrameStack> getMethodImpactFirst() {
        return methodImpactFirst;
    }

    public void setMethodImpactFirst(List<FramesStack.DiffFrameStack> methodImpactFirst) {
        this.methodImpactFirst = methodImpactFirst;
    }

    public List<FramesStack.DiffFrameStack> getFixImpact() {
        return fixImpact;
    }

    public void setFixImpact(List<FramesStack.DiffFrameStack> fixImpact) {
        this.fixImpact = fixImpact;
    }

    public List<FramesStack.DiffFrameStack> getFixImpactFirst() {
        return fixImpactFirst;
    }

    public void setFixImpactFirst(List<FramesStack.DiffFrameStack> fixImpactFirst) {
        this.fixImpactFirst = fixImpactFirst;
    }

    public void addDividerFramesStack() {
        if (getFramesStackList().size() != 0 && !getFramesStackList().get(getFramesStackList().size() - 1).equals(FramesStack.divider))
            getFramesStackList().add(FramesStack.divider);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestExecutionResult that = (TestExecutionResult) o;

        if (getRunTime() != that.getRunTime()) return false;
        if (wasSuccessful != that.wasSuccessful) return false;
        if (!getTestClass().equals(that.getTestClass())) return false;
        return getTestMethod().equals(that.getTestMethod());
    }

    @Override
    public int hashCode() {
        int result = getTestClass().hashCode();
        result = 31 * result + getTestMethod().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TestExecutionResult{" +
                "testClass='" + testClass + '\'' +
                ", testMethod='" + testMethod + '\'' +
                ", runTime=" + runTime +
                ", wasSuccessful=" + wasSuccessful +
                '}';
    }
}
