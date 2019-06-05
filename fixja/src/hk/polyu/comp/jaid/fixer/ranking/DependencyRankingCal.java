package hk.polyu.comp.jaid.fixer.ranking;

import cn.edu.pku.sei.plde.ACS.sort.VariableSort;
import hk.polyu.comp.jaid.fixaction.FixAction;
import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.test.TestExecutionResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyRankingCal extends AbsRankingCal {
    public DependencyRankingCal(JavaProject project, List<FixAction> validFixes, List<TestExecutionResult> testResults) {
        super(project, validFixes, testResults);
    }

    List<List<String>> orderedVars;

    @Override
    public void rank() {
        Set<String> existingETMStr = project.getMethodToMonitor().getMethodDeclarationInfoCenter().getBasicExpressions()
                .stream().map(ExpressionToMonitor::getText).collect(Collectors.toSet());
        VariableSort variableSort = new VariableSort(existingETMStr, project.getMethodToMonitor().getMethodAST().getBody().toString());
        orderedVars = variableSort.getSortVariable();

        scoreCalculation();
    }

    @Override
    public void launchingAFix(List<FixAction> currentBatch, FixAction fixAction, int currentIdx) {

    }

    @Override
    public void afterOneBatch(List<FixAction> currentBatch) {

    }

    @Override
    public void scoreCalculation() {
        for (FixAction validFix : validFixes) {
            validFix.setQloseScore(getFixScore(validFix.getFix()));
        }
    }

    private int getFixScore(String fixStr) {
        int fixScore=0;
        for (int i = 0; i < orderedVars.size(); i++) {
            //TODO:sum the accurence of the var within snippet.ast.tostring
        }
        return fixScore;
    }
}
