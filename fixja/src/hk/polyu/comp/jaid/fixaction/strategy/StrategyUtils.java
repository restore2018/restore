package hk.polyu.comp.jaid.fixaction.strategy;

import hk.polyu.comp.jaid.ast.ExpNodeFinder;
import hk.polyu.comp.jaid.fixaction.Schemas;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import hk.polyu.comp.jaid.monitor.snapshot.StateSnapshot;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import java.util.*;

import static hk.polyu.comp.jaid.util.CommonUtils.checkStmt;

/**
 * Created by Ls CHEN.
 */
public class StrategyUtils {
    public final static Set<Schemas.Schema> fitSchemaABCD =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_A,
                    Schemas.Schema.SCHEMA_B, Schemas.Schema.SCHEMA_C, Schemas.Schema.SCHEMA_D));
    public final static Set<Schemas.Schema> fitSchemaA =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_A));
    public final static Set<Schemas.Schema> fitSchemaAB =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_A,
                    Schemas.Schema.SCHEMA_B));
    public final static Set<Schemas.Schema> fitSchemaD =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_D));
    public final static Set<Schemas.Schema> fitSchemaC =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_C));
    public final static Set<Schemas.Schema> fitSchemaDE =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_D, Schemas.Schema.SCHEMA_E));
    public final static Set<Schemas.Schema> fitSchemaB =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_B));
    public final static Set<Schemas.Schema> fitSchemaE =
            new HashSet<>(Arrays.asList(Schemas.Schema.SCHEMA_E));

    final static String join = "-AND-";
    final static String tem_var = "jaid_tmp_";

    public static String getReplaceVarName(ExpressionToMonitor operand) {
        return tem_var + Math.abs(operand.hashCode());
    }

    public static String generateKey(String leftVarS, String rightVarS, int lineNo) {
        return new StringBuilder(leftVarS).append(join).append(rightVarS).append(join).append(lineNo).toString();
    }

    public static String generateKey(String var, int lineNo) {
        return var + join + lineNo;
    }

    /**
     * replacing the old expression by tmpVar in oldStmt to build snippet
     * (Remove the special treatment for the VariableDeclare oldStmt, since
     * all variable declarations are separated from the initializer after rewrite MTF in the beginning.)
     */
    public static Block replacement(StateSnapshot snapshot, SimpleName tmp_exp, Expression snippetToProcess, ExpressionToMonitor exp) {
        Statement old_stmt = snapshot.getLocation().getStatement();
        if (snapshot != null && old_stmt != null && snippetToProcess != null) {
            AST ast = exp.getExpressionAST().getAST();
            if (old_stmt.toString().contains(exp.getText())) {
                VariableDeclarationFragment varFragment = ast.newVariableDeclarationFragment();
                varFragment.setName(ast.newSimpleName(tmp_exp.toString()));
                varFragment.setInitializer((Expression) ASTNode.copySubtree(ast, exp.getExpressionAST()));//see if quatation is needed
                VariableDeclarationExpression expr = ast.newVariableDeclarationExpression(varFragment);
                if (exp.getType().isPrimitive()){
                    expr.setType(ast.newPrimitiveType(PrimitiveType.toCode(exp.getType().getName())));
                }else{
                    expr.setType(ast.newSimpleType(ast.newSimpleName(exp.getType().getTypeDeclaration().getName())));
                }
                Block b = ast.newBlock();
                b.statements().add(checkStmt(expr));
                b.statements().add(checkStmt(snippetToProcess));

                Statement replacedStmt = replaceExpressionWithAst(exp.getExpressionAST(), old_stmt, varFragment.getName());
                if (replacedStmt == null)
                    return null;
                if (replacedStmt instanceof Block) {
                    Block newReplacedStmtBlock = (Block) replacedStmt;
                    for (Object o : newReplacedStmtBlock.statements()) {
                        Statement s = (Statement) o;
                        b.statements().add(ASTNode.copySubtree(ast, s));
                    }
                } else {
                    b.statements().add(ASTNode.copySubtree(ast, checkStmt(replacedStmt)));
                }
                return b;
            }
        }
        return null;
    }

    /**
     * 将 old_stmt 和newExp 都转换为AST后，将exp替换为newExp
     *
     * @param exp      在old_stmt中出现过的exp
     * @param old_stmt
     * @param newExp   要替代exp的newExp字符串
     * @return
     */
    private static Statement replaceExpressionWithAst(ASTNode exp, Statement old_stmt, Expression newExp) {

        ASTParser stmtParser = ASTParser.newParser(AST.JLS8);
        stmtParser.setSource(old_stmt.toString().toCharArray());
        stmtParser.setKind(ASTParser.K_STATEMENTS);
        Block old_stmt_ast = (Block) stmtParser.createAST(null);
        ASTRewrite rewriter = ASTRewrite.create(old_stmt_ast.getAST());

        Expression expression = (Expression) ASTNode.copySubtree(old_stmt_ast.getAST(), newExp);

        ExpNodeFinder findNodeByExp = new ExpNodeFinder(old_stmt_ast);
        exp = findNodeByExp.find(exp);

        if (expression != null && exp != null) {
            Document document = new Document(old_stmt.toString());
            rewriter.replace(exp, expression, null);
            TextEdit edits = rewriter.rewriteAST(document, null);
            try {
                edits.apply(document);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }

            ASTParser newStmtParser = ASTParser.newParser(AST.JLS8);
            newStmtParser.setSource(document.get().toCharArray());
            newStmtParser.setKind(ASTParser.K_STATEMENTS);
            Block new_stmt_ast = (Block) newStmtParser.createAST(null);
            return new_stmt_ast;

        } else {
            return null;
        }
    }

    public static Assignment constructAssignment(Expression left, Expression right) {
        AST ast = left.getRoot().getAST();
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide((Expression) ASTNode.copySubtree(ast, left));
        assignment.setOperator(Assignment.Operator.ASSIGN);
        assignment.setRightHandSide((Expression) ASTNode.copySubtree(ast, right));
        return assignment;
    }

    private Statement constructAST(String statementString) {
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(statementString.toCharArray());
        parser.setKind(ASTParser.K_STATEMENTS);
        Block stmt_block = (Block) parser.createAST(null);
        return stmt_block;
    }
}
