package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.fixer.Session;
import hk.polyu.comp.jaid.fixer.config.FixerOutput;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.java.ClassToFixPreprocessor;
import hk.polyu.comp.jaid.monitor.ConstructedETM;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.SemiStateSnapshotExpressionBuilder;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotExpression;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshotExpressionBuilder;
import hk.polyu.comp.jaid.monitor.state.DebuggerEvaluationResult;
import hk.polyu.comp.jaid.monitor.state.ProgramState;
import hk.polyu.comp.jaid.test.TestExecutionResult;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.ast.ExpressionEnriching.*;
import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.MONITORED_EXPS;
import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;
import static hk.polyu.comp.jaid.util.CommonUtils.newAstExpression;

/**
 * Created by Max PEI.
 */
public class MethodDeclarationInfoCenter {

    private final MethodToMonitor contextMethod;

    public MethodDeclarationInfoCenter(MethodToMonitor contextMethod) {
        this.contextMethod = contextMethod;
    }

    public void init() {
        originalBodyBlock = ((TryStatement) ((TryStatement) getMethodDeclaration().getBody().statements()
                .get(ClassToFixPreprocessor.getTryStatementIndex())).getBody().statements().get(0)).getBody();
        recordEntryAndExitLocation();
        constructLocationStatementMap();
        constructExpressionsAppearAtLocationMap();// maybe 'expressionsToMonitorNearLocation' is duplicated with this
        constructVarDefAssignMap();
        collectExpressionsToMonitor();
        //For restore {
        collectExpressionsToMonitorForMoreFix();
        constructExpressionsAppearAtLocationMapForMoreFix();
        //}
        registerExpressionsToLocation();

    }


    // ============================ Getters


    public MethodToMonitor getContextMethod() {
        return contextMethod;
    }

    public MethodDeclaration getMethodDeclaration() {
        return contextMethod.getMethodAST();
    }

    public LineLocation getExitLocation() {
        return exitLocation;
    }

    public LineLocation getEntryLocation() {
        return entryLocation;
    }


    public Statement getStatementAtLocation(LineLocation location) {
        return getAllLocationStatementMap().getOrDefault(location, null);
    }


    public ExpressionToMonitor getThisExpressionToMonitor() {
        if (thisExpressionToMonitor == null) {
            ThisExpression thisExp = getMethodDeclaration().getAST().newThisExpression();
            thisExpressionToMonitor = ExpressionToMonitor.construct(thisExp, getMethodDeclaration().resolveBinding().getDeclaringClass());
        }
        return thisExpressionToMonitor;
    }

    public ExpressionToMonitor getResultExpressionToMonitor() {
        if (resultExpressionToMonitor == null) {
            if (getContextMethod().returnsVoid())
                throw new IllegalStateException();

            VariableDeclarationStatement resultDeclaration = (VariableDeclarationStatement) getMethodDeclaration().getBody().statements().get(0);
            SimpleName resultExpression = ((VariableDeclarationFragment) resultDeclaration.fragments().get(0)).getName();
            if (!resultExpression.getIdentifier().equals(getContextMethod().getReturnVariableName()))
                throw new IllegalStateException();

            resultExpressionToMonitor = ExpressionToMonitor.construct(resultExpression, resultExpression.resolveTypeBinding());
        }
        return resultExpressionToMonitor;
    }

    public boolean isStaticMtf() {
        return Modifier.isStatic(getMethodDeclaration().getModifiers());
    }

    public Map<LineLocation, Statement> getRelevantLocationStatementMap() {
        return relevantLocationStatementMap;
    }

    public Map<LineLocation, Statement> getAllLocationStatementMap() {
        return allLocationStatementMap;
    }

    public List<BasicBlock> getExecutableBasicBlocks() {
        return executableBasicBlocks;
    }

    public SortedSet<ExpressionToMonitor> getExpressionsToMonitorWithSideEffect() {
        if (ExpressionsToMonitorWithSideEffect == null)
            ExpressionsToMonitorWithSideEffect = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        return ExpressionsToMonitorWithSideEffect;
    }

    public Set<ExpressionToMonitor> getSideEffectFreeExpressionsToMonitor() {
        return getAllEnrichedEtmWithinMethod().stream().filter(ExpressionToMonitor::isSideEffectFree).collect(Collectors.toSet());
    }

    public SortedSet<ExpressionToMonitor> getBasicExpressions() {
        if (basicExpressions == null) {
            basicExpressions = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        }
        return basicExpressions;
    }

    /**
     * Adjust this method to change the expressions used in the monitor stage
     *
     * @return
     */
    public SortedSet<ExpressionToMonitor> getAllEnrichedEtmWithinMethod() {
        TreeSet enrichedETM = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        enrichedETM.addAll(getBasicExpressions());
        enrichedETM.addAll(getAllEnrichingEtmWithinMethod());
        return enrichedETM;
    }


    public SortedSet<ExpressionToMonitor> getAllEnrichingEtmWithinMethod() {
        if (allEnrichingExpressionsToMonitorWithinMethod == null) {
            allEnrichingExpressionsToMonitorWithinMethod = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        }
        return allEnrichingExpressionsToMonitorWithinMethod;
    }

    public SortedSet<ExpressionToMonitor> getBooleanEnrichingEtmWithinMethod() {
        if (booleanEnrichingExpressionsToMonitorWithinMethod == null) {
            booleanEnrichingExpressionsToMonitorWithinMethod = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        }
        return booleanEnrichingExpressionsToMonitorWithinMethod;
    }

    public SortedSet<ExpressionToMonitor> getReferenceEtmFields() {
        if (referenceFields == null) referenceFields = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        return referenceFields;
    }

    public StateSnapshot getStateSnapshot(LineLocation location, StateSnapshotExpression expression, boolean value) {
        if (!getCategorizedStateSnapshotsWithinMethod().containsKey(location))
            throw new IllegalStateException();
        Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>> subMap = getCategorizedStateSnapshotsWithinMethod().get(location);
        if (!subMap.containsKey(expression))
            throw new IllegalStateException();
        Map<Boolean, StateSnapshot> subsubMap = subMap.get(expression);
        return subsubMap.get(value);
    }

    public Set<StateSnapshotExpression> getStateSnapshotExpressionsWithinMethod() {
        return stateSnapshotExpressionsWithinMethod;
    }

    public Set<StateSnapshot> getStateSnapshotsWithinMethod() {
        return stateSnapshotsWithinMethod;
    }

    public Map<LineLocation, Double> getLocationDistanceToFailureMap() {
        if (locationDistanceToFailureMap == null)
            locationDistanceToFailureMap = new HashMap<>();

        return locationDistanceToFailureMap;
    }

    public Map<LineLocation, Set<ExpressionToMonitor>> getExpressionsAppearAtLocationMap() {
        return expressionsAppearAtLocationMap;
    }


    public Map<LineLocation, Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>>> getCategorizedStateSnapshotsWithinMethod() {
        return categorizedStateSnapshotsWithinMethod;
    }

    public Set<ExpressionToMonitor> getConstantIntegers() {
        return constantIntegers;
    }

    public Map<LineLocation, SortedSet<ExpressionToMonitor>> getLocationExpressionMap() {
        return locationExpressionMap;
    }

    public Map<IVariableBinding, LineScope> getVariableDefinitionLocationMap() {
        return variableDefinitionLocationMap;
    }

    public Map<IVariableBinding, LineScope> getVariableAssignmentLocationMap() {
        return variableAssignmentLocationMap;
    }


    public Set<ExpressionToMonitor> getNearByLocationExpressionToMonitor(LineLocation lineLocation) {
        if (expressionsToMonitorNearLocation == null) expressionsToMonitorNearLocation = new HashMap<>();
        else if (expressionsToMonitorNearLocation.containsKey(lineLocation))
            return expressionsToMonitorNearLocation.get(lineLocation);

        Set<String> relatedExp = new HashSet<>();
        Set<ExpressionToMonitor> selectedExp = new HashSet<>();
        for (LineLocation l : getAllLocationStatementMap().keySet()) {
            int diff = Math.abs(lineLocation.getLineNo() - l.getLineNo());
            if (diff > 0 && diff < 3)
                for (ExpressionToMonitor appear : getExpressionsAppearAtLocationMap().get(lineLocation)) {
                    relatedExp.add(appear.getText());
                    for (ExpressionToMonitor subAppear : appear.getSubExpressions())
                        relatedExp.add(subAppear.getText());
                }
        }

        SortedSet<ExpressionToMonitor> expressions = getLocationExpressionMap().get(lineLocation);
        for (ExpressionToMonitor e : expressions) {
            if (e instanceof ConstructedETM && e.isMethodInvocation()) {
                MethodInvocation mi = (MethodInvocation) e.getExpressionAST();
                if (!relatedExp.contains(mi.getExpression().toString())) {
                    LoggingService.debug(lineLocation.getLineNo() + " :: not near constructed " + e.toString());
                    continue;
                }
            }
            selectedExp.add(e);
        }
        expressionsToMonitorNearLocation.put(lineLocation, selectedExp);
        return selectedExp;
    }

    // ============================ Implementation

    private void constructExpressionsAppearAtLocationMap() {
        expressionsAppearAtLocationMap = new HashMap<>();

        Set<LineLocation> locations = getAllLocationStatementMap().keySet();
        Map<Integer, LineLocation> lineNoToLocationMap = locations.stream().collect(Collectors.toMap(LineLocation::getLineNo, Function.identity()));

        ExpressionFromStatementCollector collector = new ExpressionFromStatementCollector();
        for (LineLocation location : getAllLocationStatementMap().keySet()) {
            Statement[] statements = new Statement[3];
            // collect also from lines directly above/below the current line
            statements[0] = getAllLocationStatementMap().get(location);
            statements[1] = getAllLocationStatementMap().getOrDefault(lineNoToLocationMap.get(location.getLineNo() - 1), null);
            statements[2] = getAllLocationStatementMap().getOrDefault(lineNoToLocationMap.get(location.getLineNo() + 1), null);

            collector.collect(statements);
            Set<ExpressionToMonitor> expressionToMonitorSet = collector.getExpressionsToMonitor();
            expressionsAppearAtLocationMap.put(location, expressionToMonitorSet);
        }
    }

    private Block getOriginalBodyBlock() {
        return originalBodyBlock;
    }

    private void constructLocationStatementMap() {
        StatementLocationCollector collector = new StatementLocationCollector(this.getContextMethod());
        Block block = getOriginalBodyBlock();
        collector.collectStatements(block);
        allLocationStatementMap = collector.getLineNoLocationMap();
        relevantLocationStatementMap = new HashMap<>(allLocationStatementMap);
        BasicBlockMapper basicBlockMapper = new BasicBlockMapper(getMethodDeclaration());
        executableBasicBlocks = BasicBlock.getExecutableLineBb(allLocationStatementMap,basicBlockMapper.getBasicBlocks());

        locationReturnStatementMap = new HashMap<>();
        for (LineLocation location : allLocationStatementMap.keySet()) {
            Statement statement = allLocationStatementMap.get(location);
            if (statement instanceof ReturnStatement) {
                locationReturnStatementMap.put(location, (ReturnStatement) statement);
            }
        }
    }

    public void pruneIrrelevantLocation(Set<LineLocation> relevantLocations) {
        Set<LineLocation> locationToRemove = new HashSet<>();
        for (LineLocation line : relevantLocationStatementMap.keySet()) {
            if (!relevantLocations.contains(line)) {
                locationToRemove.add(line);
            }
        }
        for (LineLocation location : locationToRemove) {
            relevantLocationStatementMap.remove(location);
        }
    }

    private void constructVarDefAssignMap() {
        LocalVariableDefAssignCollector collector = new LocalVariableDefAssignCollector();
        collector.collect(getContextMethod());
        variableDefinitionLocationMap = collector.getVariableDefinitionLocationMap();

        Map<IVariableBinding, LineLocation> assignmentLocations = collector.getVariableAssignmentLocationMap();
        variableAssignmentLocationMap = new HashMap<>();
        for (IVariableBinding var : assignmentLocations.keySet()) {
            LineLocation firstAssignLoc = assignmentLocations.get(var);
            LineScope scope = variableDefinitionLocationMap.get(var);
            LineScope writeScope = new LineScope(firstAssignLoc, scope.getEndLocation());
            variableAssignmentLocationMap.put(var, writeScope);
        }
    }

    public Map<String, ITypeBinding> getExpressionTextToTypeMap() {
        if (expressionTextToTypeMap == null) {
            expressionTextToTypeMap = new HashMap<>();
        }
        return expressionTextToTypeMap;
    }

    public Map<String, ITypeBinding> getAllTypeBindingMap() {
        if (allTypeBindingMap == null) allTypeBindingMap = new HashMap<>();
        return allTypeBindingMap;
    }

    public Map<String, ExpressionToMonitor> getAllExpressionToMonitorMap() {
        if (allExpressionToMonitorMap == null)
            allExpressionToMonitorMap = new HashMap<>();
        return allExpressionToMonitorMap;
    }

    public ITypeBinding getTypeByExpressionText(String text) {
        return getExpressionTextToTypeMap().getOrDefault(text.trim(), null);
    }

    public ExpressionToMonitor getExpressionByText(String etmText) {
        return getAllExpressionToMonitorMap().get(etmText.trim());
    }

    public ExpressionToMonitor getExpressionByText(String etmText, String typeText, boolean createNewETM) {
        ExpressionToMonitor etm = getExpressionByText(etmText);
        if (etm == null) {
            if (createNewETM) {
                ITypeBinding typeBinding = getTypeByExpressionText(etmText);
                if (typeBinding == null)
                    typeBinding = getAllTypeBindingMap().get(typeText);
                if (typeBinding == null)
                    typeBinding = getThisExpressionToMonitor().getExpressionAST().getAST().resolveWellKnownType(typeText);
                if (typeBinding != null)// ignore expressions with non-primitive type that not refereed by MTF exp
                    etm = ExpressionToMonitor.construct(newAstExpression(etmText), typeBinding);
                return etm;
            }
        } else {
            if (etm.getType().getErasure().getQualifiedName().equals(typeText)) return etm;
        }
        return null;
    }


    public boolean hasExpressionTextRegistered(String text) {
        return getExpressionTextToTypeMap().containsKey(text);
    }

    public void registerExpressionToMonitor(ExpressionToMonitor expressionToMonitor) {
        if (!getExpressionTextToTypeMap().containsKey(expressionToMonitor.getText().trim()))
            getExpressionTextToTypeMap().put(expressionToMonitor.getText(), expressionToMonitor.getType());
        if (!getAllExpressionToMonitorMap().containsKey(expressionToMonitor.getText().trim()))
            getAllExpressionToMonitorMap().put(expressionToMonitor.getText(), expressionToMonitor);
        if (!getAllTypeBindingMap().containsKey(expressionToMonitor.getType().getQualifiedName()))
            getAllTypeBindingMap().put(expressionToMonitor.getType().getQualifiedName().trim(), expressionToMonitor.getType());
    }

    private void collectExpressionsToMonitor() {
        Set<ExpressionToMonitor> expressionToMonitorSet = new HashSet<>();

        // Collect sub-expressions from source code
        ExpressionCollector expressionCollector = new ExpressionCollector(true);
        expressionCollector.collect(getMethodDeclaration());
        expressionCollector.getSubExpressionSet().stream()
                .filter(x -> !(x instanceof NumberLiteral))
                .forEach(x -> expressionToMonitorSet.add(ExpressionToMonitor.construct(x, x.resolveTypeBinding())));
        if (!isStaticMtf())
            expressionToMonitorSet.add(getThisExpressionToMonitor());

        expressionToMonitorSet.addAll(basicEnrich(expressionToMonitorSet.stream()
                .filter(x -> !x.getText().contains(Tester.JAID_KEY_WORD))
                .collect(Collectors.toSet())));

        //Recording and enriching ETM
        getBasicExpressions().addAll(expressionToMonitorSet.stream()
                .filter(x -> !x.getText().contains(Tester.JAID_KEY_WORD))
                .collect(Collectors.toSet())
        );
        getAllEnrichingEtmWithinMethod().addAll(enrichExpressionsInAllKinds(getBasicExpressions()));
        getBooleanEnrichingEtmWithinMethod().addAll(enrichExpressionsReturnBoolean(getBasicExpressions()));
        getReferenceEtmFields().addAll(extendReferenceFields(getBasicExpressions()));
        // Construct method invocation with collected fields (Please use all files while parsing AST to correctly enable the construction).
        // getAllEnrichingEtmWithinMethod().addAll(enrichExpressionsReturnBoolean(getReferenceEtmFields()));

        // collect integer literals
        constantIntegers = new HashSet<>();
        expressionCollector.getSubExpressionSet().stream().filter(x -> x instanceof NumberLiteral)
                .forEach(x -> constantIntegers.add(ExpressionToMonitor.construct(x, x.resolveTypeBinding())));
        constantIntegers.addAll(enrichConstantIntegers(getMethodDeclaration().getAST()));

        if (shouldLogDebug()) {
            LoggingService.debugFileOnly("============ Expressions To Monitor Within Method (total number: " + getAllEnrichedEtmWithinMethod().size() + ")", MONITORED_EXPS);
            getAllEnrichedEtmWithinMethod().forEach(f -> LoggingService.debugFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));

            LoggingService.debugFileOnly("============ Constant Integers (total number: " + getConstantIntegers().size() + ")", MONITORED_EXPS);
            getConstantIntegers().forEach(f -> LoggingService.debugFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));
        }
    }

    private void recordEntryAndExitLocation() {
        CompilationUnit compilationUnit = (CompilationUnit) getMethodDeclaration().getRoot();
        Statement entryStatement = (Statement) (getMethodDeclaration().getBody().statements().get(ClassToFixPreprocessor.getEntryStatementIndex()));
        Statement exitStatement = (Statement) (((TryStatement) getMethodDeclaration().getBody().statements().get(ClassToFixPreprocessor.getTryStatementIndex()))
                .getFinally().statements().get(1));
        entryLocation = LineLocation.newLineLocation(getContextMethod(), compilationUnit.getLineNumber(entryStatement.getStartPosition()));
        exitLocation = LineLocation.newLineLocation(getContextMethod(), compilationUnit.getLineNumber(exitStatement.getStartPosition()));
    }

    public void mappingObservedStatesToSnapshots(TestExecutionResult testExecutionResult) {
        Set<LineLocation> relatedLocation = new HashSet<>();
        Set<StateSnapshot> relatedStateSnapshots = new HashSet<>();

        for (ProgramState oneObservedState : testExecutionResult.getObservedStates()) {
            LineLocation location = oneObservedState.getLocation();
            relatedLocation.add(location);

            if (!getRelevantLocationStatementMap().containsKey(location))
                continue;
            for (StateSnapshotExpression expressionToMonitor : getStateSnapshotExpressionsWithinMethod()) {
                DebuggerEvaluationResult evaluationResult = expressionToMonitor.evaluate(oneObservedState);
                if (evaluationResult.hasSemanticError() || evaluationResult.hasSyntaxError())
                    continue;

                if (!(evaluationResult instanceof DebuggerEvaluationResult.BooleanDebuggerEvaluationResult))
                    throw new IllegalStateException();
                boolean booleanEvaluationResult = ((DebuggerEvaluationResult.BooleanDebuggerEvaluationResult) evaluationResult).getValue();

                StateSnapshot stateSnapshot = getStateSnapshot(location, expressionToMonitor, booleanEvaluationResult);
                if (stateSnapshot != null) {//if snapshot is null means it is not valid at that location
                    relatedStateSnapshots.add(stateSnapshot);
                    if (testExecutionResult.wasSuccessful())
                        stateSnapshot.increaseOccurrenceInPassing();
                    else
                        stateSnapshot.increaseOccurrenceInFailing();
                }
            }
        }
        // record location coverage information
        for (LineLocation lineLocation : relatedLocation) {
            if (testExecutionResult.wasSuccessful())
                lineLocation.increasingOccurrenceInPassing();
            else
                lineLocation.increasingOccurrenceInFailing();
        }
        // record state-snapshot coverage (no-duplication) information
        for (StateSnapshot relatedStateSnapshot : relatedStateSnapshots) {
            if (testExecutionResult.wasSuccessful())
                relatedStateSnapshot.increaseOccurrenceInPassingNoDup();
            else
                relatedStateSnapshot.increaseOccurrenceInFailingNoDup();
        }
    }


    public void buildStateSnapshotsWithinMethod() {
//        buildStateSnapshotExpressionsWithinMethod();
        buildStateSnapshotExpressionsWithinMethodWithAllEnriched();

        categorizedStateSnapshotsWithinMethod = new HashMap<>();
        stateSnapshotsWithinMethod = new HashSet<>();

        for (LineLocation location : locationExpressionMap.keySet()) {
            if (!getRelevantLocationStatementMap().containsKey(location))
                continue;

            Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>> subMap = new HashMap<>();
            categorizedStateSnapshotsWithinMethod.put(location, subMap);

            for (StateSnapshotExpression stateSnapshotExpression : stateSnapshotExpressionsWithinMethod) {
                Map<Boolean, StateSnapshot> subsubMap = new HashMap<>();
                subMap.put(stateSnapshotExpression, subsubMap);

                if (stateSnapshotExpression.isValidAtLocation(location, locationExpressionMap, this)) {
                    StateSnapshot stateSnapshotTrue = new StateSnapshot(location, stateSnapshotExpression, DebuggerEvaluationResult.BooleanDebuggerEvaluationResult.getBooleanDebugValue(true));
                    StateSnapshot stateSnapshotFalse = new StateSnapshot(location, stateSnapshotExpression, DebuggerEvaluationResult.BooleanDebuggerEvaluationResult.getBooleanDebugValue(false));
                    subsubMap.put(true, stateSnapshotTrue);
                    subsubMap.put(false, stateSnapshotFalse);

                    stateSnapshotsWithinMethod.add(stateSnapshotTrue);
                    stateSnapshotsWithinMethod.add(stateSnapshotFalse);
                }
            }
        }
    }

    private void buildStateSnapshotExpressionsWithinMethod() {
        StateSnapshotExpressionBuilder builder = new StateSnapshotExpressionBuilder();
        builder.build(getBasicExpressions().stream().filter(ExpressionToMonitor::isSideEffectFree).collect(Collectors.toList()),
                new ArrayList<>(getConstantIntegers()));
        builder.addBooleanETM(getAllEnrichingEtmWithinMethod());//Adding enriching ETM that invoke methods that return boolean.
        builder.BuildEnrichedReferenceETM(getAllEnrichingEtmWithinMethod());//Building snapshots for enriching ETM that invoke methods that return referenceType.
        stateSnapshotExpressionsWithinMethod = builder.getStateSnapshotExpressions();
        LoggingService.infoFileOnly("stateSnapshotExpressionsWithinMethod size:" + stateSnapshotExpressionsWithinMethod.size(), FixerOutput.LogFile.MONITORED_EXPS);
    }

    private void buildStateSnapshotExpressionsWithinMethodWithAllEnriched() {
        StateSnapshotExpressionBuilder builder = new StateSnapshotExpressionBuilder();
        builder.build(getAllEnrichedEtmWithinMethod().stream().filter(ExpressionToMonitor::isSideEffectFree).collect(Collectors.toList()),
                new ArrayList<>(getConstantIntegers()));
        stateSnapshotExpressionsWithinMethod = builder.getStateSnapshotExpressions();
        LoggingService.infoFileOnly("stateSnapshotExpressionsWithinMethod size:" + stateSnapshotExpressionsWithinMethod.size(), FixerOutput.LogFile.MONITORED_EXPS);
    }


    public void retainSideEffectFreeExpressionsToLocation() {
        Map newLocationExpressionMap = new HashMap<>();
        for (LineLocation line : relevantLocationStatementMap.keySet()) {
            SortedSet<ExpressionToMonitor> sideEffectFreeExpressions = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
            locationExpressionMap.get(line).stream().filter(ExpressionToMonitor::isSideEffectFree).forEach(sideEffectFreeExpressions::add);
            newLocationExpressionMap.put(line, sideEffectFreeExpressions);
        }
        locationExpressionMap = newLocationExpressionMap;
    }

    public void registerExpressionsToLocation() {
        locationExpressionMap = new HashMap<>();
        SortedSet<ExpressionToMonitor> expressionsToMonitorWithinMethod = getAllEnrichedEtmWithinMethod();
        for (LineLocation location : getAllLocationStatementMap().keySet()) {
            locationExpressionMap.put(location, new TreeSet<>(expressionsToMonitorWithinMethod));
        }
    }

    //====================================For RESTORE
    private void constructExpressionsAppearAtLocationMapForMoreFix() {
        expressionsAppearAtLocationMapForMoreFix = new HashMap<>();
        Set<LineLocation> locations = getAllLocationStatementMapForMoreFix().keySet();
        Map<Integer, LineLocation> lineNoToLocationMap = locations.stream().collect(Collectors.toMap(LineLocation::getLineNo, Function.identity()));

        ExpressionFromStatementCollector collector = new ExpressionFromStatementCollector();
        for (LineLocation location : getAllLocationStatementMapForMoreFix().keySet()) {
            Statement[] statements = new Statement[3];
            // collect also from lines directly above/below the current line
            statements[0] = getAllLocationStatementMapForMoreFix().get(location);
            statements[1] = getAllLocationStatementMapForMoreFix().getOrDefault(lineNoToLocationMap.get(location.getLineNo() - 1), null);
            statements[2] = getAllLocationStatementMapForMoreFix().getOrDefault(lineNoToLocationMap.get(location.getLineNo() + 1), null);

            collector.collect(statements);
            Set<ExpressionToMonitor> expressionToMonitorSet = collector.getExpressionsToMonitor();
            expressionsAppearAtLocationMapForMoreFix.put(location, expressionToMonitorSet);
        }
    }

    private void buildStateSnapshotExpressionsWithinClassWithAllEnriched() {
        SemiStateSnapshotExpressionBuilder builder = new SemiStateSnapshotExpressionBuilder();
        builder.build(getAllEnrichedExpressionsToMonitorWithinClass().stream().filter(ExpressionToMonitor::isSideEffectFree).collect(Collectors.toList()),
                new ArrayList<>(getConstantIntegersWithinClass()));
        semiStateSnapshotExpressionsWithinClass = builder.getStateSnapshotExpressions();
        LoggingService.infoFileOnly("semi-stateSnapshotExpressionsWithinClass size:" + semiStateSnapshotExpressionsWithinClass.size(), FixerOutput.LogFile.MONITORED_EXPS);
    }

    public SortedSet<ExpressionToMonitor> getAllEnrichedExpressionsToMonitorWithinClass() {
        TreeSet enrichedETM = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        enrichedETM.addAll(getExpressionsToMonitorWithinClass());
        enrichedETM.addAll(getAllEnrichingExpressionsToMonitorWithinClass());
        return enrichedETM;
    }

    public SortedSet<ExpressionToMonitor> getExpressionsToMonitorWithinClass() {
        TreeSet enrichedETM = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        enrichedETM.addAll(getBasicExpressionsWithinClass());
//        enrichedETM.addAll(getBooleanEnrichingExpressionsToMonitorWithinMethod());
        enrichedETM.addAll(getAllEnrichingExpressionsToMonitorWithinClass());
        return enrichedETM;
    }

    public void collectExpressionsToMonitorForMoreFix() {
        Set<ExpressionToMonitor> expressionToMonitorSet = new HashSet<>();

        ExpressionCollector expressionCollector = new ExpressionCollector(true);
        MethodDeclaration methodDeclarationForMoreBindings = Session.getSession().getConfig().getJavaProject().registerMethodToMonitorForMoreFix(Session.getSession().getConfig());
        expressionCollector.collect(methodDeclarationForMoreBindings);
        expressionCollector.getSubExpressionSet().stream()
                .filter(x -> !(x instanceof NumberLiteral))
                .forEach(x -> expressionToMonitorSet.add(ExpressionToMonitor.construct(x, x.resolveTypeBinding(), true)));

        ITypeBinding typeMethodDeclarationForMoreBindings = methodDeclarationForMoreBindings.resolveBinding().getDeclaringClass();

        if (!isStaticMtf())
            expressionToMonitorSet.add(getThisExpressionToMonitor());

        expressionToMonitorSet.addAll(basicEnrich(
                expressionToMonitorSet.stream()
                        .filter(x -> !x.getText().contains(Tester.JAID_KEY_WORD))
                        .collect(Collectors.toSet())));

        //Recording and enriching ETM
        getBasicExpressionsWithinClass().addAll(expressionToMonitorSet.stream()
                .filter(x -> !x.getText().contains(Tester.JAID_KEY_WORD))
                .collect(Collectors.toSet())
        );

        getBasicExpressionsWithinClass().addAll(extendReferenceFieldsForMoreFix(typeMethodDeclarationForMoreBindings, methodDeclarationForMoreBindings));

        getAllEnrichingExpressionsToMonitorWithinClass().addAll(enrichExpressionsInAllKinds(getBasicExpressionsWithinClass()));
        getAllEnrichingExpressionsToMonitorWithinClass().addAll(enrichExpressionsInAllKindsWithPublicField(getBasicExpressionsWithinClass()));
        getAllEnrichingExpressionsToMonitorWithinClass().addAll(getBasicExpressionsWithinClass());

        // collect integral literals

        ExpressionCollector expressionParentCollector = new ExpressionCollector(true);
        expressionParentCollector.collect(methodDeclarationForMoreBindings.getParent());
        constantIntegersWithinClass = new HashSet<>();
        expressionParentCollector.getSubExpressionSet().stream()
                .filter(x -> x instanceof NumberLiteral)
                .forEach(x -> constantIntegersWithinClass.add(ExpressionToMonitor.construct(x, x.resolveTypeBinding())));
        //enrichConstantIntegers();


        StatementLocationCollector collector = new StatementLocationCollector(this.getContextMethod(), (CompilationUnit) methodDeclarationForMoreBindings.getRoot());
        Block block = ((TryStatement) ((TryStatement) methodDeclarationForMoreBindings.getBody().statements()
                .get(ClassToFixPreprocessor.getTryStatementIndex())).getBody().statements().get(0)).getBody();
        collector.collectStatements(block);
        AllLocationStatementMapForMoreFix = collector.getLineNoLocationMap();

        getSemiStateSnapshotExpressionsWithinClass().addAll(getAllEnrichingExpressionsToMonitorWithinClass().stream().filter(x -> x.getType().getName().equals("boolean")).collect(Collectors.toList()));

        if (shouldLogDebug()) {
            LoggingService.debugFileOnly("============ Expressions To Monitor Within Method (total number: " + getAllEnrichedEtmWithinMethod().size() + ")", MONITORED_EXPS);
            getAllEnrichedEtmWithinMethod().forEach(f -> LoggingService.debugFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));

            LoggingService.debugFileOnly("============ Constant Integers (total number: " + getConstantIntegers().size() + ")", MONITORED_EXPS);
            getConstantIntegers().forEach(f -> LoggingService.debugFileOnly(f.getType().getQualifiedName() + " :: " + f.toString(), MONITORED_EXPS));
        }
    }

    private class CollectInvocation extends ASTVisitor {
        Set<MethodInvocation> invocationSet;

        public CollectInvocation() {
            this.invocationSet = new HashSet<>();
        }

        public Set<MethodInvocation> collect(Statement root) {
            root.accept(this);
            return invocationSet;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            invocationSet.add(node);
            return true;
        }

    }

    private Set<ExpressionToMonitor> extendReferenceFieldsForMoreFix(ITypeBinding bindingClass, MethodDeclaration methodDeclaration) {
        Set<ExpressionToMonitor> extendingFields = new HashSet<>();
        AST ast = methodDeclaration.getRoot().getAST();
        //Methods from binary types that reference unresolved types may not be included.
        Set<IVariableBinding> fields = collectFieldsFromType(bindingClass);
        for (IVariableBinding variableBinding : fields) {
            ExpressionToMonitor etm = ExpressionToMonitor.construct(ast.newSimpleName(variableBinding.getName()), variableBinding.getType());

            extendingFields.add(etm);


        }

        return extendingFields;
    }


    private Set<ExpressionToMonitor> enrichExpressionsInAllKindsWithPublicField(Set<ExpressionToMonitor> existingExpressions) {
        Set<ExpressionToMonitor> newExpressions = new HashSet<>();
        Set<ExpressionToMonitor> referenceExp = existingExpressions.stream().filter(etm -> !etm.getType().isPrimitive() && !etm.hasMethodInvocation()).collect(Collectors.toSet());
        for (ExpressionToMonitor etm : referenceExp) {
            //Methods from binary types that reference unresolved types may not be included.
            ITypeBinding type = etm.getType().getTypeDeclaration();

            //IVariableBinding[] classs = type.getDeclaredFields();
            IVariableBinding[] fieldBindings = etm.getType().getDeclaredFields();
            if (fieldBindings.length == 0) fieldBindings = etm.getType().getTypeDeclaration().getDeclaredFields();

            List<IVariableBinding> selectedVaribles =
                    ASTUtils4SelectInvocation.selectBooleanAndPublicFields(fieldBindings);
            //List<IMethodBinding> selectedInvocations = getFilterInvocations(selectedInvocationsAll,methodInvocationList);
            // 1. key = null  2. key contains fqName 3. key contains imports
            for (IVariableBinding varible : selectedVaribles) {
                FieldAccess fieldAccess = CommonUtils.appendField(etm.getExpressionAST(), varible);

                ExpressionToMonitor newExp = ExpressionToMonitor.construct(fieldAccess, varible.getType());
                newExpressions.add(newExp);
            }
        }
        return newExpressions;
    }

    public Map<LineLocation, Statement> getAllLocationStatementMapForMoreFix() {
        return AllLocationStatementMapForMoreFix;
    }

    public Map<LineLocation, Set<ExpressionToMonitor>> getExpressionsAppearAtLocationMapForMoreFix() {
        return expressionsAppearAtLocationMapForMoreFix;
    }

    public Set<ExpressionToMonitor> getSemiStateSnapshotExpressionsWithinClass() {
        return semiStateSnapshotExpressionsWithinClass;
    }

    public SortedSet<ExpressionToMonitor> getBasicExpressionsWithinClass() {
        if (basicExpressionsWithinClass == null) {
            basicExpressionsWithinClass = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        }
        return basicExpressionsWithinClass;
    }

    public SortedSet<ExpressionToMonitor> getAllEnrichingExpressionsToMonitorWithinClass() {
        if (allEnrichingExpressionsToMonitorWithinClass == null) {
            allEnrichingExpressionsToMonitorWithinClass = new TreeSet<>(ExpressionToMonitor.getByLengthComparator());
        }
        return allEnrichingExpressionsToMonitorWithinClass;
    }

    public Set<ExpressionToMonitor> getConstantIntegersWithinClass() {
        return constantIntegersWithinClass;
    }

    private Map<LineLocation, Statement> AllLocationStatementMapForMoreFix;
    private Map<LineLocation, Set<ExpressionToMonitor>> expressionsAppearAtLocationMapForMoreFix;
    private Set<ExpressionToMonitor> semiStateSnapshotExpressionsWithinClass = new HashSet<>();
    private SortedSet<ExpressionToMonitor> basicExpressionsWithinClass;
    private SortedSet<ExpressionToMonitor> allEnrichingExpressionsToMonitorWithinClass;
    private Set<ExpressionToMonitor> constantIntegersWithinClass;


    // ============================== Storage

    private Block originalBodyBlock;
    private LineLocation entryLocation;
    private LineLocation exitLocation;
    private ExpressionToMonitor thisExpressionToMonitor;
    private ExpressionToMonitor resultExpressionToMonitor;

    private Map<IVariableBinding, LineScope> variableDefinitionLocationMap;
    private Map<IVariableBinding, LineScope> variableAssignmentLocationMap;

    //Locations
    private Map<LineLocation, Statement> relevantLocationStatementMap;//locations exe by failing test
    private Map<LineLocation, Statement> allLocationStatementMap;//all locations
    private List<BasicBlock> executableBasicBlocks;
    private Map<LineLocation, ReturnStatement> locationReturnStatementMap;
    private Map<LineLocation, Double> locationDistanceToFailureMap;
    private Map<LineLocation, SortedSet<ExpressionToMonitor>> locationExpressionMap;//[monitor-related] valid expressions in each location
    private Map<LineLocation, Set<ExpressionToMonitor>> expressionsAppearAtLocationMap;// expression appear at or near the location
    private Map<LineLocation, Set<ExpressionToMonitor>> expressionsToMonitorNearLocation;// expression appear at or near the location

    //Enriched ETM
    private SortedSet<ExpressionToMonitor> basicExpressions;//[SS-related] EXPs appear in MTF (existingEXP) + existingEXP.size()+ existingEXP.length
    private SortedSet<ExpressionToMonitor> allEnrichingExpressionsToMonitorWithinMethod;//[SS-related]  basicEXP.getStateMethod
    private SortedSet<ExpressionToMonitor> booleanEnrichingExpressionsToMonitorWithinMethod;//[SS-related] basicEXP.getStateMethodReturnBoolean
    private SortedSet<ExpressionToMonitor> referenceFields;//[SS-related] basicEXP.fields
    private SortedSet<ExpressionToMonitor> ExpressionsToMonitorWithSideEffect;//[SS-related]

    //Registered ETM
    private Map<String, ITypeBinding> expressionTextToTypeMap;
    private Map<String, ITypeBinding> allTypeBindingMap;
    private Map<String, ExpressionToMonitor> allExpressionToMonitorMap;
    private Set<ExpressionToMonitor> constantIntegers;

    //Snapshots
    private Set<StateSnapshotExpression> stateSnapshotExpressionsWithinMethod;
    private Map<LineLocation, Map<StateSnapshotExpression, Map<Boolean, StateSnapshot>>> categorizedStateSnapshotsWithinMethod;
    private Set<StateSnapshot> stateSnapshotsWithinMethod;


}
