package hk.polyu.comp.jaid.util;

import hk.polyu.comp.jaid.ast.BasicBlock;
import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.monitor.ExpressionToMonitor;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by liushanchen on 16/8/8.
 */
public class CommonUtils {
    private static final String STMT_ENDING = ";";
    static final String EXP = "${exp}";
    static String tryCatchBlock = "try{" + EXP + "}catch (Exception e ){e.printStackTrace();}\n";

    /**
     * Use this method to generate fix id based on the string content
     *
     * @return fix id
     */
    public static long getId(String string) {
        long h = 98764321261L;
        int l = string.length();
        char[] chars = string.toCharArray();

        for (int i = 0; i < l; i++) {
            h = 31 * h + chars[i];
        }
        return h;
    }

    public static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValue(Hashtable<K, V> map) {
        LinkedHashMap<K, V> result = new LinkedHashMap<K, V>();
        Stream<Map.Entry<K, V>> st = map.entrySet().stream();

        st.sorted(Map.Entry.comparingByValue())
                .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public static <K extends Comparable<? super K>, V> LinkedHashMap<K, V> sortByKey(Map<K, V> map) {
        LinkedHashMap<K, V> result = new LinkedHashMap<K, V>();
        Stream<Map.Entry<K, V>> st = map.entrySet().stream();

        st.sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public static String checkStmt(String stmt) {
        stmt.replaceAll(System.lineSeparator(), "");
        if (stmt.endsWith(STMT_ENDING) || stmt.endsWith(STMT_ENDING + "\n")) {
            return stmt;
        } else if (stmt.endsWith("}") || stmt.endsWith("}\n")) {
            return stmt;
        } else {
            return stmt + STMT_ENDING;
        }
    }

    public static Statement checkStmt(ASTNode stmt) {
        if (stmt instanceof Expression)
            return stmt.getRoot().getAST().newExpressionStatement((Expression) stmt);
        else if (stmt instanceof Statement)
            return (Statement) stmt;
        else {
            LoggingService.warn("stmt:" + stmt.toString() + " neither expression nor statement.");
            return null;
        }
    }

    public static String appendInvokingByString(String expVar, String invokingName) {
        StringBuilder sb = new StringBuilder();
        sb.append("(")
                .append(expVar)
                .append(")")
                .append(".")
                .append(invokingName)
                .append("()");
        return sb.toString();
        //========  replace string operation by AST operation   =======//
//        MethodInvocation nestedMI = ast.newMethodInvocation();
//        nestedMI.setExpression(ast.newSimpleName("tmpStringBuffer"));
//        nestedMI.setName(ast.newSimpleName("append"));
//        sl = ast.newStringLiteral();
//        sl.setLiteralValue("Content: " );
//        nestedMI.arguments().add(s1);
//
//        MethodInvocation mi = ast.newMethodInvocation();
//        mi.setExpression(nestedMI);
//        mi.setName(ast.newSimpleName("append"));
//        mi.arguments().add(ast.newSimpleName("gateId"));
//
//        bufferBlock.statements().add(ast.newExpressionStatement(mi));
    }

    public static String appendInvokingWithParmsByString(String expVar, String invokingName, String[] prams) {
        StringBuilder sb = new StringBuilder();
        sb.append("(")
                .append(expVar)
                .append(")")
                .append(".")
                .append(invokingName)
                .append("(");
        for (String p : prams) {
            sb.append(p).append(",");
        }
        sb.replace(sb.lastIndexOf(","), sb.lastIndexOf(",") + 1, " ");
        sb.append(")");
        return sb.toString();
    }

    public static MethodInvocation appendInvoking(Expression exp, String invokingName, List<ASTNode> prams) {
        AST ast = exp.getRoot().getAST();
        MethodInvocation mi = ast.newMethodInvocation();
        if (exp instanceof ThisExpression) {
            Expression expression = (Expression) ASTNode.copySubtree(ast, exp);
            mi.setExpression(expression);
        } else if (!isParenthesizeNeeded(exp)) {
            Expression expression = (Expression) ASTNode.copySubtree(ast, exp);
            mi.setExpression(expression);
        } else {
            Expression expression = (Expression) ASTNode.copySubtree(ast, exp);
            ParenthesizedExpression expr = ast.newParenthesizedExpression();
            expr.setExpression(expression);
            mi.setExpression(expr);
        }
        mi.setName(ast.newSimpleName(invokingName));
        if (prams != null)
            for (ASTNode pa : prams)
                mi.arguments().add(ASTNode.copySubtree(ast, pa));
        return mi;
    }

    public static FieldAccess appendField(Expression exp, IVariableBinding variableBinding) {
        AST ast = exp.getRoot().getAST();
        FieldAccess fieldAccess = ast.newFieldAccess();
        fieldAccess.setExpression((Expression) ASTNode.copySubtree(ast, exp));
        fieldAccess.setName(ast.newSimpleName(variableBinding.getName()));
        return fieldAccess;
    }

    /**
     * 检查expression是不是合适的变量（与 方法调用、其他表达式、不可改变的变量 区别开来）
     */
    public static boolean isValidVariable(Expression opExp, IBinding binding) {
        String opString = opExp.toString();

        if (opString.length() > 0
                && !(opExp instanceof InfixExpression)
                && !(opExp instanceof PrefixExpression)
                && !(opExp instanceof MethodInvocation)
                && !(opExp instanceof NumberLiteral)
                && !(opExp instanceof QualifiedName && opExp.toString().contains("length"))
        ) {
            if (binding instanceof IVariableBinding) {
                IVariableBinding variableBinding = (IVariableBinding) binding;
                return variableBinding != null && !Modifier.isFinal(variableBinding.getVariableDeclaration().getModifiers());
            } else {
                return true;
            }
        }
        return false;
    }

    public static IBinding resolveBinding4Variables(Expression expressionAST) {
        IBinding binding = null;
        if (expressionAST instanceof Name) {
            binding = ((Name) expressionAST).resolveBinding();
        } else if (expressionAST instanceof FieldAccess) {
            binding = ((FieldAccess) expressionAST).resolveFieldBinding();
        }
        return binding;
    }

    public static boolean isParenthesizeNeeded(Expression exp) {
        if (exp instanceof SimpleName || exp instanceof QualifiedName
                || exp instanceof NumberLiteral || exp instanceof NullLiteral
                || exp instanceof CharacterLiteral || exp instanceof StringLiteral
                || exp instanceof MethodInvocation || exp instanceof ArrayAccess
                || exp instanceof ThisExpression || exp instanceof SuperFieldAccess
                || exp instanceof FieldAccess)
            return false;
        else
            return true;
    }

    public static Expression checkParenthesizeNeeded(Expression exp) {
        AST ast = exp.getRoot().getAST();
        if (isParenthesizeNeeded(exp)) {
            ParenthesizedExpression pe = ast.newParenthesizedExpression();
            pe.setExpression((Expression) ASTNode.copySubtree(ast, exp));
            return pe;
        } else
            return (Expression) ASTNode.copySubtree(ast, exp);
    }

    public static String appendThrowableInvokingByString(Expression exp, String invokingName, List<ASTNode> prams) {
        return tryCatchBlock.replace(EXP, checkStmt(appendInvoking(exp, invokingName, prams).toString()));
    }

    public static TryStatement appendThrowableInvoking(ASTNode exp) {
        AST ast = exp.getRoot().getAST();
        TryStatement tryStatement = ast.newTryStatement();
        Block b = ast.newBlock();
        b.statements().add(checkStmt(exp));
        tryStatement.setBody(b);
        CatchClause catchClause = ast.newCatchClause();
        SingleVariableDeclaration newSingleVariableDeclaration = ast.newSingleVariableDeclaration();
        newSingleVariableDeclaration.setType(ast.newSimpleType(ast.newName("Throwable")));
        newSingleVariableDeclaration.setName(ast.newSimpleName("e"));
        catchClause.setException(newSingleVariableDeclaration);
        tryStatement.catchClauses().add(catchClause);
        return tryStatement;
    }

    public static String replaceLast(String original, String tobeReplace, String replacement) {
        int idx = original.lastIndexOf(tobeReplace);
        if (idx < 0) return original;
        String head = original.substring(0, idx);
        String tail = original.substring(idx).replace(tobeReplace, replacement);
        return head + tail;
    }

    public static boolean isSubExp(ExpressionToMonitor bigger, ASTNode smaller) {
        if (equalExpString(bigger.getExpressionAST(), smaller)) return true;
        for (ASTNode astNode : bigger.getSubExpressions().stream().map(x -> x.getExpressionAST()).collect(Collectors.toList())) {
            if (equalExpString(astNode, smaller)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllConstant(Set<ASTNode> astNodeSet) {
        if (astNodeSet.size() == 0) return false;
        for (ASTNode astNode : astNodeSet) {
            if (!(astNode instanceof NullLiteral ||
                    astNode instanceof NumberLiteral ||
                    astNode instanceof BooleanLiteral ||
                    astNode instanceof CharacterLiteral ||
                    astNode instanceof StringLiteral))
                return false;
        }
        return true;
    }

    public static boolean equalExpString(ASTNode node1, ASTNode node2) {
        String node1s = node1.toString();
        node1s = node1s.replace("(", "").replace(")", "").trim();
        String node2s = node2.toString();
        node2s = node2s.replace("(", "").replace(")", "").trim();
        return node1s.equals(node2s);
    }

    public static String quotExp(String expVar) {
        StringBuilder sb = new StringBuilder("(");
        sb.append(expVar);
        sb.append(")");
        return sb.toString();
    }

    public static String file2QualifiedName(String filePath) {
        String result = filePath.replace(File.separator, ".");
        if (result.endsWith(".java")) result = result.substring(0, result.length() - 5);
        return result;
    }

    public static String getRefFieldAccess(String refStr, String fieldStr) {
        return refStr + "." + fieldStr;
    }

    public static Expression newAstExpression(String expression) {
        ASTParser newStmtParser = ASTParser.newParser(AST.JLS8);
        newStmtParser.setSource(expression.toCharArray());
        newStmtParser.setKind(ASTParser.K_EXPRESSION);
        Expression new_expression = (Expression) newStmtParser.createAST(null);
        return new_expression;
    }

    public static int ulamDistance(List<BasicBlock> listA, List<BasicBlock> listB) {
        if (listA.size() != listB.size()) throw new RuntimeException("List size dose not match!");
        int k, n = listA.size();
        //init thresh
        List<Integer> thresh = new ArrayList<>();
        thresh.add(0);
        for (int i = 1; i < n; i++) {
            thresh.add(n + 2);
        }

        for (int i = 0; i < n; i++) {
            for (int j = n - 1; j >= 0; j--) {
                if (listA.get(i) == listB.get(j)) {
                    k = findK(thresh, j);
                    thresh.set(k, j + 1);
                }
            }
        }
        for (int i = thresh.size() - 1; i >= 0; i--) {
            if (thresh.get(i) != n + 2)
                return i;
        }
        return 0;
    }

    private static int findK(List<Integer> thresh, int j) {
        for (int k = 0; k < thresh.size() - 1; k++) {
            if (thresh.get(k) < j + 1 && j + 1 <= thresh.get(k + 1))
                return k + 1;
        }
        return 0;
    }

}
