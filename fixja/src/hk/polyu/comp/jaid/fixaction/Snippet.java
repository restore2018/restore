package hk.polyu.comp.jaid.fixaction;

import hk.polyu.comp.jaid.ast.ExpressionCollector;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.Statement;

import java.util.HashSet;
import java.util.Set;

import static hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils.fitSchemaC;
import static hk.polyu.comp.jaid.util.CommonUtils.checkStmt;

/**
 * Created by Ls CHEN.
 */
public class Snippet {
    private String seed;
    private Statement initASTNode;
    private String snippetString;
    private Set<Schemas.Schema> fixSchemas;
    private double isFakeSnippet = 1;

    public Snippet(ASTNode initASTNode, Set<Schemas.Schema> fixSchemas, String snippetStrategy, long snapshotID) {
        this.initASTNode = checkStmt(initASTNode);
        initSnippetString();
        this.fixSchemas = fixSchemas;
        this.seed = getSeed(snippetStrategy, snapshotID);
    }

    public Snippet(ASTNode initASTNode, Set<Schemas.Schema> fixSchemas, String snippetStrategy, long snapshotID,double isFakeSnippet) {
        this.initASTNode = checkStmt(initASTNode);
        initSnippetString();
        this.fixSchemas = fixSchemas;
        this.seed = getSeed(snippetStrategy, snapshotID);
        this.isFakeSnippet = isFakeSnippet;
    }

    public Snippet(String snippetStrategy, long snapshotID) {
        this.snippetString = "${snippet}";
        this.fixSchemas = fitSchemaC;
        this.seed = getSeed(snippetStrategy, snapshotID);
    }

    private String getSeed(String snippetStrategy, long snapshotID) {
        return "strategy::" + snippetStrategy + ";; ssID::" + snapshotID;
    }

    private void initSnippetString() {
        if (this.initASTNode instanceof Block) {
            Block initBlock = (Block) initASTNode;
            StringBuilder sb = new StringBuilder();
            for (Object o : initBlock.statements())
                sb.append(o.toString());
            this.snippetString = sb.toString();
        } else
            this.snippetString = initASTNode.toString();
    }

    public Statement getInitASTNode() {
        return initASTNode;
    }

    public double getIsFakeSnippet() {
        return isFakeSnippet;
    }

    private Set<String> subExpressionStrings;

    public Set<String> getAllSubExpStrings() {
        if (subExpressionStrings == null) {
            subExpressionStrings = new HashSet<>();
            if (getInitASTNode() != null) {
                ExpressionCollector collector = new ExpressionCollector(false);
                collector.collect(getInitASTNode());
                collector.getSubExpressionSet().stream()
                        .filter(x -> !(x instanceof NumberLiteral))
                        .forEach(x -> subExpressionStrings.add(x.toString()));
            }
        }
        return subExpressionStrings;
    }

    public String getSnippetString() {
        return snippetString;
    }

    public Set<Schemas.Schema> getFixSchemas() {
        return fixSchemas;
    }

    public String getSeed() {
        return seed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Snippet snippet = (Snippet) o;

        if (!snippetString.equals(snippet.snippetString)) return false;
        return fixSchemas.equals(snippet.fixSchemas);
    }

    @Override
    public int hashCode() {
        int result = snippetString.hashCode();
        result = 31 * result + fixSchemas.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Snippet{" +
                "'\n" + snippetString + "\n   '" +
                ", Schemas=" + fixSchemas +
                ", seed=" + seed +
                '}';
    }
}
