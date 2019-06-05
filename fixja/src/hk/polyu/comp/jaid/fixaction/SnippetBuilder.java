package hk.polyu.comp.jaid.fixaction;

import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.preliminary.*;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;

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
            snippetSet.addAll(s.Building(snapshot));
        }
        if (usingSubExpression) {
            snippetSet.addAll(buildSnapshots4AllSubExp(snapshot));
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
