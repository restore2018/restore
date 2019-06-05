package hk.polyu.comp.jaid.fixaction;

import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import org.eclipse.jdt.core.dom.Statement;

import java.util.*;

import static hk.polyu.comp.jaid.fixaction.Schemas.*;
import static hk.polyu.comp.jaid.util.CommonUtils.checkStmt;

/**
 * Created by Ls CHEN.
 */
public class FixActionBuilder {

//    private JavaProject javaProject;
    private Map<LineLocation, Set<FixAction>> fixActionMap;

    public FixActionBuilder(JavaProject javaProject) {
        this.fixActionMap = new HashMap<>();
//        this.javaProject = javaProject;
    }

    public void buildFixActions(StateSnapshot snapshot, Snippet snippet) {
        Set<FixAction> fixActions = new HashSet<>();
        //build fixaction according to the snapshot and its snippet and schemas
        for (Schema schema : snippet.getFixSchemas()) {
            //skip schemaB,C,D if snapshot is false in all failing test

            if (snapshot.isShouldSkipSchemaBCD()
                    && (schema.equals(Schema.SCHEMA_B) || schema.equals(Schema.SCHEMA_C) || schema.equals(Schema.SCHEMA_D)))
                continue;
            String fix = buildFixString(snapshot, schema, snippet.getSnippetString());
            if (fix == null)
                continue;
            if(snippet.getIsFakeSnippet()==1){
                fixActions.add(new FixAction(fix, snapshot, schema, snippet.getSeed(),
                        similarityBetween(getExpressionStringsAtLocation(snapshot.getLocation()), snippet.getAllSubExpStrings())));
            }else{
                fixActions.add(new FixAction(fix, snapshot, schema, snippet.getSeed(),
                        similarityBetween(getExpressionStringsAtLocation(snapshot.getLocation()), snippet.getAllSubExpStrings()),0));
            }

        }

        //store constructed fixactions
        if (fixActionMap.containsKey(snapshot.getLocation())) {
            fixActionMap.get(snapshot.getLocation()).addAll(fixActions);
        } else {
            fixActionMap.put(snapshot.getLocation(), fixActions);
        }
    }


    private static Map<LineLocation, Set<String>> locationToExpressionStringsMap = new HashMap<>();

    private static Set<String> getExpressionStringsAtLocation(LineLocation location) {
        if (!locationToExpressionStringsMap.containsKey(location)) {
            Set<String> expressionStrings = new HashSet<>();
            locationToExpressionStringsMap.put(location, expressionStrings);

            Set<ExpressionToMonitor> expressions = location.getExpressionsAppearedAtLocation();
            expressions.forEach(x -> expressionStrings.add(x.getText()));
        }
        return locationToExpressionStringsMap.get(location);
    }

    public double similarityBetween(Set<String> set1, Set<String> set2) {
        Set<String> union = new HashSet<>();
        union.addAll(set1);
        union.addAll(set2);
        int nbrCommonExpressions = set1.size() + set2.size() - union.size();
        double similarity = ((double) nbrCommonExpressions) / union.size();
        return similarity;
    }

    private String buildFixString(StateSnapshot snapshot, Schema schema, String snippetString) {
        Statement old_stmt = snapshot.getLocation().getStatement();
        String fail = snapshot.getFailingStateExpression().toString();
        if ((snippetString != null && snippetString.length() > 0
                && old_stmt != null && old_stmt.toString().length() > 0
                && fail.length() > 0))
            return schema.getSchema().replace(SNIPPET, checkStmt(snippetString)).replace(OLD_STMT, old_stmt.toString()).replace(FAIL, fail);
        return null;
    }

    public Map<LineLocation, Set<FixAction>> getFixActionMap() {
        Map<LineLocation, Set<FixAction>> result = new LinkedHashMap<>();
        fixActionMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(p -> result.put(p.getKey(), p.getValue()));
        return result;
    }

}

