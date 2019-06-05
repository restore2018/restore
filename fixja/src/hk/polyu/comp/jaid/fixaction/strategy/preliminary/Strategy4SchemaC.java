package hk.polyu.comp.jaid.fixaction.strategy.preliminary;

import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;

import java.util.HashSet;
import java.util.Set;

public class Strategy4SchemaC extends Strategy {
    @Override
    public Set<Snippet> process() {
        this.snippetSet = new HashSet<>();
        if (getStateSnapshot().shouldInstantiateSchemaC()){
            snippetSet.add(new Snippet(getStrategyName("Direct-schemaC"), getStateSnapshot().getID()));
        }
        return snippetSet;
    }
}
