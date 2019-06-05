package hk.polyu.comp.jaid.fixaction.strategy.preliminary;

import hk.polyu.comp.jaid.ast.ExpNodeFinder;
import hk.polyu.comp.jaid.ast.ExpressionCollector;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.util.CommonUtils;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import static hk.polyu.comp.jaid.util.CommonUtils.isAllConstant;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.EQUALS;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.NOT_EQUALS;
import static org.eclipse.jdt.core.dom.PrefixExpression.Operator.NOT;

/**
 * Created by Ls CHEN.
 * Chart9, Closure62, Closure63, Math85 need this strategy to generate fix
 */
public class Strategy4IfCondition extends Strategy {
    Set<Snippet> snippetSet;
    ITypeBinding type;
    Set<Expression> operandSet;


    @Override
    public Set<Snippet> process() {
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

            //FIXME: there are a lot of duplication in the generated fixes
            //Apply templates
            templateMutateIfCond(oldIfStmt);
            templateDisableSubIfCond(oldIfStmt);
            if (shouldAppendIfCond(oldIfStmt)) {
                templateAppendSnapshotToIfCond(oldIfStmt);
                getStateSnapshot().disableInstantiateSchemaC();
            }
        }
        return snippetSet;
    }

    private boolean shouldAppendIfCond(IfStatement oldIfStmt) {
        ExpressionCollector collector = new ExpressionCollector(false);
        collector.collect(oldIfStmt.getExpression());
        Set<Expression> ifCondSubs = collector.getSubExpressionSet();
        Set<ExpressionToMonitor> snapshotSubs = getStateSnapshot().getSnapshotExpression().getSubExpressions();
        Set<ASTNode> aggregate = new HashSet<>();
        ASTMatcher matcher = new ASTMatcher();
        ifCondSubs.stream().forEach(ifSub -> {
            snapshotSubs.stream().forEach(ssSub -> {
                if (ifSub.subtreeMatch(matcher, ssSub.getExpressionAST()))
                    aggregate.add(ifSub);
            });
        });
        if (aggregate.size() != 2 && !isAllConstant(aggregate))
            return true;
        return false;
    }


    private void templateAppendSnapshotToIfCond(IfStatement oldIfStmt) {
        // this part may creates fix candidates duplicated with the schema_C that can not be detected.
        // snapshots that apply this template should not apply schema_C in this location.

        IfStatement newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
        InfixExpression newExp_1 = ast.newInfixExpression();
        newExp_1.setLeftOperand((Expression) ASTNode.copySubtree(ast, oldIfStmt.getExpression()));
        newExp_1.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        Expression aaa = (Expression) ASTNode.copySubtree(ast, getStateSnapshot().getFailingStateExpressionNegation());
        newExp_1.setRightOperand((Expression) ASTNode.copySubtree(ast, getStateSnapshot().getFailingStateExpressionNegation()));
        newIfStmt_1.setExpression(newExp_1);
        snippetSet.add(new Snippet(newIfStmt_1, StrategyUtils.fitSchemaE, getStrategyName("if_cond && !snapshot"), getStateSnapshot().getID()));
        IfStatement newIfStmt = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
        InfixExpression newExp = ast.newInfixExpression();
        newExp.setRightOperand((Expression) ASTNode.copySubtree(ast, oldIfStmt.getExpression()));
        newExp.setOperator(InfixExpression.Operator.CONDITIONAL_AND);
        newExp.setLeftOperand((Expression) ASTNode.copySubtree(ast, getStateSnapshot().getFailingStateExpressionNegation()));
        newIfStmt.setExpression(newExp);
        snippetSet.add(new Snippet(newIfStmt, StrategyUtils.fitSchemaE, getStrategyName("!snapshot && if_cond"), getStateSnapshot().getID()));

        IfStatement newIfStmt1 = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
        InfixExpression newExp1 = ast.newInfixExpression();
        newExp1.setLeftOperand((Expression) ASTNode.copySubtree(ast, oldIfStmt.getExpression()));
        newExp1.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
        newExp1.setRightOperand((Expression) ASTNode.copySubtree(ast, getStateSnapshot().getFailingStateExpression()));
        newIfStmt1.setExpression(newExp1);
        snippetSet.add(new Snippet(newIfStmt1, StrategyUtils.fitSchemaE, getStrategyName("if_cond || snapshot"), getStateSnapshot().getID()));

    }


    /**
     * 【*】 去掉语义上重复的snapshotEXP 之后，会影响这个strategy.
     * 原条件：如果OldIfCon 中含有snapshotEXP，则将其替换为T/F（和simi==1是等价的）
     * 新条件：如果OldIfCon 的某个sub_condition 中含有snapshotEXP的subEXP，则将此sub_condition替换为T/F
     *
     * @param oldIfStmt
     */
    private void templateDisableSubIfCond(IfStatement oldIfStmt) {
        operandSet.stream().forEach(subCon -> {
            disableSubIfCon(oldIfStmt, subCon);
        });
    }

    /**
     * Disable the old-sub-condition if it contains any operand of the snapshot
     *
     * @param oldIfStmt
     * @param oldSubCon
     */
    private void disableSubIfCon(IfStatement oldIfStmt, Expression oldSubCon) {
        ExpNodeFinder findNodeByExp = new ExpNodeFinder(oldSubCon);
        for (ExpressionToMonitor ss_operand : getStateSnapshot().getSnapshotExpression().getOperands()) {
            Expression toDisable = (Expression) findNodeByExp.find(ss_operand.getExpressionAST());
            if (toDisable != null) {
                replaceSubConditionByBooleanLiteral(oldIfStmt, oldSubCon, true);
                replaceSubConditionByBooleanLiteral(oldIfStmt, oldSubCon, false);
                return;
            }
        }
    }

    private void replaceSubConditionByBooleanLiteral(IfStatement oldIfStmt, Expression toBeReplaced, boolean replaceWith) {
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
            rewrite.replace(toBeReplaced, ast.newBooleanLiteral(replaceWith), null);
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
            snippetSet.add(new Snippet(newIfStmt, StrategyUtils.fitSchemaE, getStrategyName("if_cond-remove"), getStateSnapshot().getID()));
        }

    }


    private void templateMutateIfCond(IfStatement oldIfStmt) {
        operandSet.stream().forEach(subCon -> {
            mutate(subCon, oldIfStmt);
        });

    }

    /**
     * mutate a sub ifConExp and replace the oldSubIfCon by the mutated one
     *
     * @param oldSubIfCon
     * @param oldIfStmt
     */
    private void mutate(Expression oldSubIfCon, IfStatement oldIfStmt) {
        Set<Expression> mutatedExpSet = new HashSet<>();
        mutatedExpSet.add(mutatedNegation(oldSubIfCon));
        if (oldSubIfCon instanceof InfixExpression) {
            mutatedExpSet.addAll(mutatedInfixOperator((InfixExpression) oldSubIfCon));
        } else if (oldSubIfCon instanceof PrefixExpression) {
        } else if (oldSubIfCon instanceof MethodInvocation) {
        } else if (oldSubIfCon instanceof SimpleName) {
        }
        mutatedExpSet.stream().forEach(mutatedExp -> {
            replaceSubIfCond(oldSubIfCon, mutatedExp, oldIfStmt);
        });
    }

    private void replaceSubIfCond(Expression oldSubExp, Expression newSubExp, IfStatement oldIfStmt) {

        IfStatement newIfStmt = (IfStatement) ASTNode.copySubtree(ast, oldIfStmt);
        Expression oldIfCond = newIfStmt.getExpression();
//        ASTRewrite rewrite = ASTRewrite.create(oldIfCond.getAST());//直接用这个AST会导致rewrite的edits不修改document

        ASTParser ifCondParser = ASTParser.newParser(AST.JLS8);
        ifCondParser.setSource(oldIfCond.toString().toCharArray());
        ifCondParser.setKind(ASTParser.K_EXPRESSION);
        Expression oldIfConAst = (Expression) ifCondParser.createAST(null);
        ASTRewrite rewriter = ASTRewrite.create(oldIfConAst.getAST());

        ExpNodeFinder findNodeByExp = new ExpNodeFinder(oldIfConAst);
        Expression toReplace = (Expression) findNodeByExp.find(oldSubExp);

        if (toReplace != null) {
            Document document = new Document(oldIfConAst.toString());
            rewriter.replace(toReplace, newSubExp, null);
            TextEdit edits = rewriter.rewriteAST(document, null);
            try {
                edits.apply(document);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            String newIfCondStr = document.get();
            ASTParser astParser = ASTParser.newParser(AST.JLS8);
            astParser.setSource(newIfCondStr.toCharArray());
            astParser.setKind(ASTParser.K_EXPRESSION);
            Expression newIfCond = (Expression) astParser.createAST(null);
            newIfStmt.setExpression((Expression) ASTNode.copySubtree(ast, newIfCond));
            snippetSet.add(new Snippet(newIfStmt, StrategyUtils.fitSchemaE, getStrategyName("if_cond-mutate"), getStateSnapshot().getID()));
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
        prefixExpression.setOperand(CommonUtils.checkParenthesizeNeeded(exp));
        prefixExpression.setOperator(NOT);
        return prefixExpression;

    }

    private Set<Expression> mutatedInfixOperator(InfixExpression infixExp) {
        Set<Expression> mutatedExpSet = new HashSet<>();
        InfixExpression.Operator op = infixExp.getOperator();
        if (op.equals(InfixExpression.Operator.GREATER)) {
            InfixExpression mutateExp = (InfixExpression) ASTNode.copySubtree(ast, infixExp);
            mutateExp.setOperator(InfixExpression.Operator.GREATER_EQUALS);
            mutatedExpSet.add(mutateExp);
        } else if (op.equals(InfixExpression.Operator.GREATER_EQUALS)) {
            InfixExpression mutateExp = (InfixExpression) ASTNode.copySubtree(ast, infixExp);
            mutateExp.setOperator(InfixExpression.Operator.GREATER);
            mutatedExpSet.add(mutateExp);
        } else if (op.equals(InfixExpression.Operator.LESS)) {
            InfixExpression mutateExp = (InfixExpression) ASTNode.copySubtree(ast, infixExp);
            mutateExp.setOperator(InfixExpression.Operator.LESS_EQUALS);
            mutatedExpSet.add(mutateExp);
        } else if (op.equals(InfixExpression.Operator.LESS_EQUALS)) {
            InfixExpression mutateExp = (InfixExpression) ASTNode.copySubtree(ast, infixExp);
            mutateExp.setOperator(InfixExpression.Operator.LESS);
            mutatedExpSet.add(mutateExp);
        } else if (op.equals(InfixExpression.Operator.EQUALS)) {
            if (!infixExp.getLeftOperand().resolveTypeBinding().isPrimitive()) {
                MethodInvocation mutateExp = ast.newMethodInvocation();
                mutateExp.setExpression((Expression) ASTNode.copySubtree(ast, infixExp.getLeftOperand()));
                mutateExp.setName(ast.newSimpleName("equals"));
                mutateExp.arguments().add((Expression) ASTNode.copySubtree(ast, infixExp.getRightOperand()));
                mutatedExpSet.add(mutateExp);
            }
        }
        return mutatedExpSet;
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

}
