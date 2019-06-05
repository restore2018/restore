package hk.polyu.comp.restore.extend_fixaction;

import hk.polyu.comp.jaid.ast.ExpNodeFinder;
import hk.polyu.comp.jaid.ast.ExpressionCollector;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.util.CommonUtils;
import hk.polyu.comp.restore.Application;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.*;
import static hk.polyu.comp.jaid.util.CommonUtils.isAllConstant;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.EQUALS;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.NOT_EQUALS;
import static org.eclipse.jdt.core.dom.PrefixExpression.Operator.NOT;




/**
 * Created by Ls CHEN.
 */
public class Strategy4IfConditionForMoreFix extends Strategy {
    Set<Snippet> snippetSet;
    ITypeBinding type;
    Set<Expression> operandSet;
    static Set<String> methodsReplaceIfConditionParam= new HashSet<String>();
    static Set<String> methodsReplaceSubIfConditionParam= new HashSet<String>();

    private List<Integer> locationTailored;
    private List<StateSnapshot> snapshotSample;
    public Strategy4IfConditionForMoreFix(List<Integer> locationTailored, List<StateSnapshot> snapshotSample){
        this.locationTailored = locationTailored;
        this.snapshotSample = snapshotSample;
    }

    @Override
    public Set<Snippet> process() {
        this.snippetSet = new HashSet<>();
        // top 1,3,5
        if(Application.isRestore&&(!locationTailored.contains(getStateSnapshot().getLocation().getLineNo())||snapshotSample.contains(getStateSnapshot()))){
            return snippetSet;
        }

        this.snippetSet = new HashSet<>();
        type = getStateSnapshot().getSnapshotExpression().getOperands().get(0).getType();

        Statement oldStmt = getStateSnapshot().getLocation().getStatement();
        if (oldStmt instanceof IfStatement) {
            IfStatement oldIfStmt = (IfStatement) oldStmt;
            ast = oldIfStmt.getRoot().getAST();
            //collect all first level sub if condition expression (ifConExp)
            Expression oldCon = oldIfStmt.getExpression();
            CollectFirstLevelChildIfConditionExpression collector = new CollectFirstLevelChildIfConditionExpression();
            operandSet = collector.collect(oldCon);

            //Apply templates
            templateAppendSnapshotToIfCond(oldIfStmt);
            if(operandSet.size()>1 && false){
                templateReplaceSubIfCond(oldIfStmt);
            }

            getStateSnapshot().disableInstantiateSchemaC();

        }
        return snippetSet;
    }

    private class CollectFirstLevelChildIfConditionExpression {
        Set<Expression> operandSet;

        public CollectFirstLevelChildIfConditionExpression() {
            this.operandSet = new HashSet<>();
        }

        public Set<Expression> collect(Expression root) {
            getOperand(root);
            return operandSet;
        }

        private void getOperand(Expression currentExp) {

            if (currentExp instanceof InfixExpression) {
                InfixExpression infixCurrent = (InfixExpression) currentExp;
                if (infixCurrent.getOperator().equals(InfixExpression.Operator.CONDITIONAL_AND) ||
                        infixCurrent.getOperator().equals(InfixExpression.Operator.CONDITIONAL_OR)) {
                    getOperand(infixCurrent.getLeftOperand());
                    getOperand(infixCurrent.getRightOperand());
                    for (Object o : infixCurrent.extendedOperands()) {
                        getOperand((Expression) o);
                    }
                } else operandSet.add(currentExp);
            } else if (currentExp instanceof PrefixExpression) {
                PrefixExpression prefixCurrent = (PrefixExpression) currentExp;
                getOperand(prefixCurrent.getOperand());
                operandSet.add(currentExp);
            } else if (currentExp instanceof ParenthesizedExpression)
                getOperand(((ParenthesizedExpression) currentExp).getExpression());
            else operandSet.add(currentExp);
        }
    }

    private void templateAppendSnapshotToIfCond(IfStatement oldIfStmt) {
        // this part may creates fix candidates duplicated with the schema_C that can not be detected.
        // snapshots that apply this template should not apply schema_C in this location.
        if (methodsReplaceIfConditionParam.contains(oldIfStmt.toString())) return;//Avoid duplicated fixes.
        SortedSet<ExpressionToMonitor> etms = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getAllEnrichingExpressionsToMonitorWithinClass();
        for(ExpressionToMonitor etm:etms){
            if(!etm.isBooleanType()){
                continue;
            }

            methodsReplaceIfConditionParam.add(oldIfStmt.toString());

            IfStatement newIfStmt2 = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
            InfixExpression newExp2 = ast.newInfixExpression();
            newExp2.setLeftOperand((Expression) ASTNode.copySubtree(ast, oldIfStmt.getExpression()));
            newExp2.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
            newExp2.setRightOperand((Expression) ASTNode.copySubtree(ast, etm.getExpressionAST()));
            newIfStmt2.setExpression(newExp2);
            snippetSet.add(new Snippet(newIfStmt2, StrategyUtils.fitSchemaE, getStrategyName("if_cond || morefix"), getStateSnapshot().getID(),0));

            IfStatement newIfStmt3 = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
            InfixExpression newExp3 = ast.newInfixExpression();
            newExp3.setLeftOperand((Expression) ASTNode.copySubtree(ast, oldIfStmt.getExpression()));
            newExp3.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
            newExp3.setRightOperand((Expression) ASTNode.copySubtree(ast, etm.getExpressionAST()));
            newIfStmt3.setExpression(newExp3);
            snippetSet.add(new Snippet(newIfStmt3, StrategyUtils.fitSchemaE, getStrategyName("if_cond && morefix"), getStateSnapshot().getID(),0));

            IfStatement newIfStmt4 = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
            InfixExpression newExp4 = ast.newInfixExpression();
            newExp4.setLeftOperand((Expression) ASTNode.copySubtree(ast, oldIfStmt.getExpression()));
            newExp4.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
            newExp4.setRightOperand((Expression) ASTNode.copySubtree(ast, mutatedNegation(etm.getExpressionAST())));
            newIfStmt4.setExpression(newExp4);
            snippetSet.add(new Snippet(newIfStmt4, StrategyUtils.fitSchemaE, getStrategyName("if_cond || !morefix"), getStateSnapshot().getID(),0));

            IfStatement newIfStmt5 = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
            InfixExpression newExp5 = ast.newInfixExpression();
            newExp5.setLeftOperand((Expression) ASTNode.copySubtree(ast, oldIfStmt.getExpression()));
            newExp5.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
            newExp5.setRightOperand((Expression) ASTNode.copySubtree(ast, mutatedNegation(etm.getExpressionAST())));
            newIfStmt5.setExpression(newExp5);
            snippetSet.add(new Snippet(newIfStmt5, StrategyUtils.fitSchemaE, getStrategyName("if_cond && !morefix"), getStateSnapshot().getID(),0));
        }
    }

    private Expression mutatedNegation(Expression exp) {
        if (exp instanceof InfixExpression) {
            InfixExpression cp_old_infix = (InfixExpression) ASTNode.copySubtree(ast, exp);
            InfixExpression.Operator op = cp_old_infix.getOperator();
            if (cp_old_infix != null && op != null && (op == EQUALS || op == NOT_EQUALS)) {
                if (op == EQUALS)
                    cp_old_infix.setOperator(NOT_EQUALS);
                else
                    cp_old_infix.setOperator(EQUALS);
                return cp_old_infix;
            }
        } else if (exp instanceof PrefixExpression) {
            PrefixExpression oldPre = (PrefixExpression) exp;
            if (oldPre.getOperator().equals(PrefixExpression.Operator.NOT)) {
                return (Expression) ASTNode.copySubtree(ast, oldPre.getOperand());
            }
        }
        PrefixExpression prefixExpression = ast.newPrefixExpression();
        prefixExpression.setOperand(CommonUtils.checkParenthesizeNeeded((Expression) ASTNode.copySubtree(ast, exp)));
        prefixExpression.setOperator(NOT);
        return prefixExpression;

    }

    private void templateReplaceSubIfCond(IfStatement oldIfStmt) {
        operandSet.stream().forEach(subCon -> {
            replaceSubIfCon(oldIfStmt, subCon);
        });
        methodsReplaceSubIfConditionParam.add(oldIfStmt.toString());
    }

    /**
     * Disable the old-sub-condition if it contains any operand of the snapshot
     * @param oldIfStmt
     * @param oldSubCon
     */
    private void replaceSubIfCon(IfStatement oldIfStmt, Expression oldSubCon) {
        replaceSubConditionByMoreFix(oldIfStmt, oldSubCon);
        return;

    }

    private void replaceSubConditionByMoreFix(IfStatement oldIfStmt, Expression toBeReplaced) {

        if (methodsReplaceSubIfConditionParam.contains(oldIfStmt.toString())) return;//Avoid duplicated fixes.
        SortedSet<ExpressionToMonitor> etms = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getAllEnrichingExpressionsToMonitorWithinClass();
        List<InfixExpression.Operator> operators = Arrays.asList(InfixExpression.Operator.CONDITIONAL_OR,InfixExpression.Operator.CONDITIONAL_AND);
        List<Boolean> isNegativeList = Arrays.asList(true,false);

        for(InfixExpression.Operator operator:operators){
            for(boolean isNegative:isNegativeList){

                for(ExpressionToMonitor etm:etms) {
                    if (!etm.isBooleanType()) {
                        continue;
                    }


                    IfStatement newIfStmt = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
                    ASTParser oldExpAstParser = ASTParser.newParser(AST.JLS8);
                    oldExpAstParser.setSource(newIfStmt.getExpression().toString().toCharArray());
                    oldExpAstParser.setKind(ASTParser.K_EXPRESSION);
                    Expression oldIfCon = (Expression) oldExpAstParser.createAST(null);
                    ExpNodeFinder findNodeByExp = new ExpNodeFinder(oldIfCon);
                    toBeReplaced = (Expression) findNodeByExp.find(toBeReplaced);


                    if (toBeReplaced != null) {
                        ASTRewrite rewrite = ASTRewrite.create(oldIfCon.getAST());

                        Document document = new Document(oldIfCon.toString());

                        InfixExpression newExp = ast.newInfixExpression();
                        newExp.setLeftOperand((Expression) ASTNode.copySubtree(ast, toBeReplaced));
                        newExp.setOperator(operator);
                        newExp.setRightOperand((Expression) ASTNode.copySubtree(ast, isNegative?mutatedNegation(etm.getExpressionAST()):etm.getExpressionAST()));

                        ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
                        parenthesizedExpression.setExpression(newExp);
                        rewrite.replace(toBeReplaced, parenthesizedExpression, null);
                        TextEdit edits = rewrite.rewriteAST(document, null);
                        try {
                            edits.apply(document);
                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                        String newIfCondStr = document.get();
                        ASTParser newExpAstParser = ASTParser.newParser(AST.JLS8);
                        newExpAstParser.setSource(newIfCondStr.toCharArray());
                        newExpAstParser.setKind(ASTParser.K_EXPRESSION);
                        Expression newIfCond = (Expression) newExpAstParser.createAST(null);
                        newIfStmt.setExpression((Expression) ASTNode.copySubtree(ast, newIfCond));
                        snippetSet.add(new Snippet(newIfStmt, StrategyUtils.fitSchemaE, getStrategyName("if_sub_cond-morefix"), getStateSnapshot().getID(),0));
                    }
                }

            }
        }




    }

}
