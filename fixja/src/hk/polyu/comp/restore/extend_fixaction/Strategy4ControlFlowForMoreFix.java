package hk.polyu.comp.restore.extend_fixaction;

import hk.polyu.comp.jaid.fixaction.Schemas;
import hk.polyu.comp.jaid.fixaction.Snippet;
import hk.polyu.comp.jaid.fixaction.strategy.Strategy;
import hk.polyu.comp.jaid.fixaction.strategy.StrategyUtils;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import hk.polyu.comp.jaid.util.CommonUtils;
import hk.polyu.comp.restore.Application;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

import static hk.polyu.comp.jaid.ast.ASTUtils4SelectInvocation.getValidVarsIncludeThisFinal;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.EQUALS;
import static org.eclipse.jdt.core.dom.InfixExpression.Operator.NOT_EQUALS;
import static org.eclipse.jdt.core.dom.PrefixExpression.Operator.NOT;

/**
 * Created by Ls CHEN.
 */
public class Strategy4ControlFlowForMoreFix extends Strategy {
    private Set<Snippet> snippetSet;
    private static final String VOID = "void";
    static Set<String> addIfContinueParam= new HashSet<String>();
    static Set<String> addReturnParam= new HashSet<String>();

    private List<Integer> locationTailored;
    private List<StateSnapshot> snapshotSample;
    public Strategy4ControlFlowForMoreFix(List<Integer> locationTailored, List<StateSnapshot> snapshotSample){
        this.locationTailored = locationTailored;
        this.snapshotSample = snapshotSample;
    }

    @Override
    public Set<Snippet> process() {
        if(Application.isRestore&&(!locationTailored.contains(getStateSnapshot().getLocation().getLineNo())||snapshotSample.contains(getStateSnapshot()))){
            return snippetSet;
        }

        this.snippetSet = new HashSet<>();
        ast = getStateSnapshot().getLocation().getStatement().getAST();
        templateBuildReturn();
        templateBuildContinue();
        return snippetSet;
    }

    private void templateBuildContinue() {
        ASTNode oldStmtParent = getStateSnapshot().getLocation().getStatement().getParent().getParent();
        Statement oldstatement = getStateSnapshot().getLocation().getStatement();
        if (addIfContinueParam.contains(oldstatement.toString())) return;
        if (oldStmtParent instanceof ForStatement ||
                oldStmtParent instanceof WhileStatement) {
            Set<ExpressionToMonitor> etms = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getSemiStateSnapshotExpressionsWithinClass();
            for(ExpressionToMonitor etm:etms) {
                if (!etm.isBooleanType()) {
                    continue;
                }
                addIfContinueParam.add(oldstatement.toString());
                IfStatement newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, ast.newIfStatement());
                newIfStmt_1.setExpression((Expression) ASTNode.copySubtree(ast, etm.getExpressionAST()));
                newIfStmt_1.setThenStatement(ast.newContinueStatement());
                newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, newIfStmt_1);
                snippetSet.add(new Snippet(newIfStmt_1, StrategyUtils.fitSchemaA, getStrategyName("continue;"), getStateSnapshot().getID(),0));
                int a =1;
            }

        }
    }

    private void templateBuildReturn() {
        MethodToMonitor methodToMonitor = getStateSnapshot().getLocation().getContextMethod();
        MethodDeclaration method = methodToMonitor.getMethodAST();
        Statement oldstatement = getStateSnapshot().getLocation().getStatement();
        if (addReturnParam.contains(oldstatement.toString())) return;
        if (method != null) {
            ITypeBinding type = method.getReturnType2().resolveBinding();
            if(type.getName().equals("boolean")){
                Set<ExpressionToMonitor> etms = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getSemiStateSnapshotExpressionsWithinClass();
                for(ExpressionToMonitor etm:etms) {
                    if (!etm.isBooleanType()) {
                        continue;
                    }
                    addReturnParam.add(oldstatement.toString());
                    List<Boolean> isNegativeList = Arrays.asList(true,false);
                    List<Boolean> returnBooleanValue = Arrays.asList(true,false);

                    for(boolean isNegative:isNegativeList){
                        for(boolean returnBoolean:returnBooleanValue){
                            IfStatement newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, ast.newIfStatement());
                            newIfStmt_1.setExpression((Expression) ASTNode.copySubtree(ast, isNegative?mutatedNegation(etm.getExpressionAST()):etm.getExpressionAST()));

                            ReturnStatement returnStatement = ast.newReturnStatement();
                            returnStatement.setExpression(ast.newBooleanLiteral(returnBoolean));

                            newIfStmt_1.setThenStatement(returnStatement);
                            newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, newIfStmt_1);
                            snippetSet.add(new Snippet(newIfStmt_1, StrategyUtils.fitSchemaA, getStrategyName("return booleanliteral;"), getStateSnapshot().getID(),0));
                            int a =1;
                        }
                    }

                }



            }
            /*if(type.getName().equals("double")||type.getName().equals("int")||type.getName().equals("long")) {
                Set<ExpressionToMonitor> etms = getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getSemiStateSnapshotExpressionsWithinClass();
                for(ExpressionToMonitor etm:etms) {
                    if (!etm.isBooleanType()) {
                        continue;
                    }
                    addReturnParam.add(oldstatement.toString());
                    for(ExpressionToMonitor returnValue:getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getConstantIntegersWithinClass()){

                        if(etm.getExpressionAST() instanceof InfixExpression && ! ((InfixExpression)etm.getExpressionAST()).getRightOperand().toString().equals(returnValue.getExpressionAST().toString())){
                            continue;
                        }

                        IfStatement newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, ast.newIfStatement());
                        newIfStmt_1.setExpression((Expression) ASTNode.copySubtree(ast, etm.getExpressionAST()));

                        ReturnStatement returnStatement = ast.newReturnStatement();
                        returnStatement.setExpression((Expression) ASTNode.copySubtree(ast, returnValue.getExpressionAST()));

                        newIfStmt_1.setThenStatement(returnStatement);
                        newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, newIfStmt_1);
                        snippetSet.add(new Snippet(newIfStmt_1, StrategyUtils.fitSchemaA, getStrategyName("return numberliteral;"), getStateSnapshot().getID()));
                        int a =1;
                    }

                    for(ExpressionToMonitor returnValue:getStateSnapshot().getLocation().getContextMethod().getMethodDeclarationInfoCenter().getConstantIntegersWithinClass()){
                        if(etm.getExpressionAST() instanceof InfixExpression && ! ((InfixExpression)etm.getExpressionAST()).getRightOperand().toString().equals(returnValue.getExpressionAST().toString())){
                            continue;
                        }
                        IfStatement newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, ast.newIfStatement());
                        newIfStmt_1.setExpression((Expression) ASTNode.copySubtree(ast, mutatedNegation(etm.getExpressionAST())));

                        ReturnStatement returnStatement = ast.newReturnStatement();
                        returnStatement.setExpression((Expression) ASTNode.copySubtree(ast, returnValue.getExpressionAST()));

                        newIfStmt_1.setThenStatement(returnStatement);
                        newIfStmt_1 = (IfStatement) ASTNode.copySubtree(ast, newIfStmt_1);
                        snippetSet.add(new Snippet(newIfStmt_1, StrategyUtils.fitSchemaA, getStrategyName("return numberliteral;"), getStateSnapshot().getID()));
                        int a =1;
                    }

                }

            }*/

        }
    }

    private void buildBooleanLiteralReturn(MethodToMonitor methodToMonitor) {
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide((Expression) ASTNode.copySubtree(ast, methodToMonitor.getMethodDeclarationInfoCenter().getResultExpressionToMonitor().getExpressionAST()));
        assignment.setRightHandSide(ast.newBooleanLiteral(true));

        ReturnStatement returnStatement = ast.newReturnStatement();
        returnStatement.setExpression(assignment);

        snippetSet.add(new Snippet(returnStatement, getBooleanReturnSchema(), getStrategyName("return T;"), getStateSnapshot().getID()));
        ReturnStatement returnStatement1 = ast.newReturnStatement();
        returnStatement1.setExpression(ast.newBooleanLiteral(false));
        snippetSet.add(new Snippet(returnStatement1, getBooleanReturnSchema(), getStrategyName("return F;"), getStateSnapshot().getID()));
    }

    private Set<Schemas.Schema> getBooleanReturnSchema() {
        if (getStateSnapshot().getLocation().getStatement() instanceof ReturnStatement) {
            return StrategyUtils.fitSchemaDE;
        } else {
            return StrategyUtils.fitSchemaB;
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

//    /**
//     * 计算 snippet & oldStmt 的相似度
//     */
//    private double calSnippetAndOldStmtSimi(String snippet) {
//        ASTParser ifCondParser = ASTParser.newParser(AST.JLS8);
//        ifCondParser.setSource(snippet.toCharArray());
//        ifCondParser.setKind(ASTParser.K_STATEMENTS);
//        Block snippetAst = (Block) ifCondParser.createAST(null);
//
//        SubExpCollector subExpCollector = new SubExpCollector(snippetAst);
//        Set<ASTNode> snippetSubSet = subExpCollector.find();
//        final int[] counter = {0};
//        getStateSnapshot().getLocation().getOldStmtSubExp().stream().forEach(ifSub -> {
//            snippetSubSet.stream().forEach(ssSub -> {
//                if (ifSub.toString().equals(ssSub.toString()))
//                    counter[0]++;
//            });
//        });
//        return (double) counter[0] / snippetSubSet.size();
//    }


}
