package hk.polyu.comp.jaid.fixaction.strategy;

import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import org.eclipse.jdt.core.dom.AST;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Ls CHEN.
 */
public abstract class Strategy {

    private StateSnapshot stateSnapshot;
    protected Set<Snippet> snippetSet;
    protected AST ast;

    protected String getStrategyName(String action) {
        return this.getClass().getSimpleName() + "[" + action + "]";
    }

    protected StateSnapshot getStateSnapshot() {
        return stateSnapshot;
    }

    protected void setStateSnapshot(StateSnapshot stateSnapshot) {
        this.stateSnapshot = stateSnapshot;
    }

    public abstract Set<Snippet> process();

    public Set<Snippet> Building(StateSnapshot snapshot) {
        this.stateSnapshot = snapshot;
        return process();
    }

    public Set<Snippet> Building(LineLocation location, ExpressionToMonitor etm) {
        return new HashSet<>();
    }
}
