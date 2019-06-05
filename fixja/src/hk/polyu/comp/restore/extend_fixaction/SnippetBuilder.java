package hk.polyu.comp.restore.extend_fixaction;

import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.preliminary.*;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.restore.Application;

import java.util.*;

/**
 * Created by Ls CHEN.
 */
public class SnippetBuilder {

    private List<Strategy> strategies;
    private Map<StateSnapshot, Set<Snippet>> snippets;

    private boolean usingSubExpression = false;
    private boolean buildFixWithSchemaCDirectly = false;
    public static boolean enableTmpVariable = false;

    public SnippetBuilder() {
        this.snippets = new HashMap<>();
        strategies = new ArrayList<>();
    }

    public Map<StateSnapshot, Set<Snippet>> getSnippets() {
        return snippets;
    }

    public void enableBasicStrategies() {
        //for fast test fix build.
        if(!Application.isRestore&&Application.isForMoreFix&&false){
            BasicStrategy4ReferenceForMoreFix basicStrategy4ReferenceForMoreFix = new BasicStrategy4ReferenceForMoreFix(new ArrayList<>(),new ArrayList<>());
            addStrategy(basicStrategy4ReferenceForMoreFix);

            BasicStrategy4BooleanForMoreFix basicStrategy4BooleanForMoreFix = new BasicStrategy4BooleanForMoreFix(new ArrayList<>(),new ArrayList<>());
            addStrategy(basicStrategy4BooleanForMoreFix);

            BasicStrategy4NumericForMoreFix basicStrategy4NumericForMoreFix = new BasicStrategy4NumericForMoreFix(new ArrayList<>(),new ArrayList<>());
            addStrategy(basicStrategy4NumericForMoreFix);

            Strategy4ReplaceOldStmtForMoreFix strategy4ReplaceOldStmtForMoreFix = new Strategy4ReplaceOldStmtForMoreFix(new ArrayList<>(),new ArrayList<>());
            addStrategy(strategy4ReplaceOldStmtForMoreFix);

            Strategy4IfConditionForMoreFix strategy4ReplaceOldStmtForMoreFixForIfCondition = new Strategy4IfConditionForMoreFix(new ArrayList<>(),new ArrayList<>());
            addStrategy(strategy4ReplaceOldStmtForMoreFixForIfCondition);

            Strategy4ControlFlowForMoreFix strategy4ControlFlowForMoreFix = new Strategy4ControlFlowForMoreFix(new ArrayList<>(),new ArrayList<>());
            addStrategy(strategy4ControlFlowForMoreFix);
            return;
        }

        BasicStrategy4Boolean strategy4Boolean = new BasicStrategy4Boolean();
        addStrategy(strategy4Boolean);
        BasicStrategy4Numeric strategy4Numeric = new BasicStrategy4Numeric();
        addStrategy(strategy4Numeric);
        BasicStrategy4Reference strategy4Reference = new BasicStrategy4Reference();
        addStrategy(strategy4Reference);
        Strategy4ControlFlow strategy4ControlFlow = new Strategy4ControlFlow();
        addStrategy(strategy4ControlFlow);
        Strategy4IfCondition strategy4IfCondition = new Strategy4IfCondition();
        addStrategy(strategy4IfCondition);
        Strategy4ReplaceOldStmt strategy4ReplaceOldStmt = new Strategy4ReplaceOldStmt();
        addStrategy(strategy4ReplaceOldStmt);
        //Strategy4SchemaC must be the last strategy for each snapshot

        if (buildFixWithSchemaCDirectly){
            Strategy4SchemaC strategy4SchemaC=new Strategy4SchemaC();
            addStrategy(strategy4SchemaC);
        }
    }

    public void enableBasicStrategiesForMoreFix(List<Integer> locationTailored,List<StateSnapshot> snapshotSample) {
        if(!Application.isRestore&&Application.isForMoreFix){
            BasicStrategy4BooleanForMoreFix basicStrategy4BooleanForMoreFix = new BasicStrategy4BooleanForMoreFix(locationTailored, snapshotSample);
            addStrategy(basicStrategy4BooleanForMoreFix);

            BasicStrategy4NumericForMoreFix basicStrategy4NumericForMoreFix = new BasicStrategy4NumericForMoreFix(locationTailored, snapshotSample);
            addStrategy(basicStrategy4NumericForMoreFix);

            BasicStrategy4ReferenceForMoreFix basicStrategy4ReferenceForMoreFix = new BasicStrategy4ReferenceForMoreFix(locationTailored,snapshotSample);
            addStrategy(basicStrategy4ReferenceForMoreFix);

            Strategy4ReplaceOldStmtForMoreFix strategy4ReplaceOldStmtForMoreFix = new Strategy4ReplaceOldStmtForMoreFix(locationTailored,snapshotSample);
            addStrategy(strategy4ReplaceOldStmtForMoreFix);

            Strategy4IfConditionForMoreFix strategy4ReplaceOldStmtForMoreFixForIfCondition = new Strategy4IfConditionForMoreFix(locationTailored,snapshotSample);
            addStrategy(strategy4ReplaceOldStmtForMoreFixForIfCondition);

            Strategy4ControlFlowForMoreFix strategy4ControlFlowForMoreFix = new Strategy4ControlFlowForMoreFix(new ArrayList<>(),new ArrayList<>());
            addStrategy(strategy4ControlFlowForMoreFix);

            enableBasicStrategies();

            return;
        }
        BasicStrategy4Boolean strategy4Boolean = new BasicStrategy4Boolean();
        addStrategy(strategy4Boolean);
        BasicStrategy4Numeric strategy4Numeric = new BasicStrategy4Numeric();
        addStrategy(strategy4Numeric);
        BasicStrategy4Reference strategy4Reference = new BasicStrategy4Reference();
        addStrategy(strategy4Reference);
        Strategy4ControlFlow strategy4ControlFlow = new Strategy4ControlFlow();
        addStrategy(strategy4ControlFlow);
        Strategy4IfCondition strategy4IfCondition = new Strategy4IfCondition();
        addStrategy(strategy4IfCondition);
        Strategy4ReplaceOldStmt strategy4ReplaceOldStmt = new Strategy4ReplaceOldStmt();
        addStrategy(strategy4ReplaceOldStmt);

        if(Application.isRestore&&Application.isForMoreFix) {
            Strategy4ReplaceOldStmtForMoreFix strategy4ReplaceOldStmtForMoreFix = new Strategy4ReplaceOldStmtForMoreFix(locationTailored, snapshotSample);
            addStrategy(strategy4ReplaceOldStmtForMoreFix);

            Strategy4IfConditionForMoreFix strategy4ReplaceOldStmtForMoreFixForIfCondition = new Strategy4IfConditionForMoreFix(locationTailored, snapshotSample);
            addStrategy(strategy4ReplaceOldStmtForMoreFixForIfCondition);

            Strategy4ControlFlowForMoreFix strategy4ControlFlowForMoreFix = new Strategy4ControlFlowForMoreFix(locationTailored, snapshotSample);
            addStrategy(strategy4ControlFlowForMoreFix);

            BasicStrategy4BooleanForMoreFix basicStrategy4BooleanForMoreFix = new BasicStrategy4BooleanForMoreFix(locationTailored, snapshotSample);
            addStrategy(basicStrategy4BooleanForMoreFix);

            BasicStrategy4NumericForMoreFix basicStrategy4NumericForMoreFix = new BasicStrategy4NumericForMoreFix(locationTailored, snapshotSample);
            addStrategy(basicStrategy4NumericForMoreFix);

            BasicStrategy4ReferenceForMoreFix basicStrategy4ReferenceForMoreFix = new BasicStrategy4ReferenceForMoreFix(locationTailored,snapshotSample);
            addStrategy(basicStrategy4ReferenceForMoreFix);
        }
        //Strategy4SchemaC must be the last strategy for each snapshot
        if (buildFixWithSchemaCDirectly){
            Strategy4SchemaC strategy4SchemaC=new Strategy4SchemaC();
            addStrategy(strategy4SchemaC);
        }
    }

    public void enableComprehensiveStrategies(boolean shouldEnable) {
        usingSubExpression = shouldEnable;
    }

    public void addStrategy(Strategy strategy) {
        if (strategy != null)
            this.strategies.add(strategy);
    }

    public void buildSnippets(StateSnapshot snapshot) {
        Set<Snippet> snippetSet = new HashSet<>();
        for (Strategy s : strategies) {
            Set<Snippet> snippetSetTemp = s.Building((snapshot));
            if(snippetSetTemp!=null) {
                snippetSet.addAll(snippetSetTemp);
            }
        }
        if (usingSubExpression) {
            Set<Snippet> snippetSetTemp = buildSnapshots4AllSubExp(snapshot);
            if(snippetSetTemp!=null) {
                snippetSet.addAll(snippetSetTemp);
            }
        }
        snippets.put(snapshot, snippetSet);
    }

    public Set<Snippet> buildSnapshots4AllSubExp(StateSnapshot snapshot) {
        Set<Snippet> snippetSet = new HashSet<>();
        for (ExpressionToMonitor expressionToMonitor : snapshot.getSnapshotExpression().getSubExpressions()) {
            for (Strategy s : strategies) {
                snippetSet.addAll(s.Building(snapshot.getLocation(), expressionToMonitor));
            }
        }
        return snippetSet;
    }

}
