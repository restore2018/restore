package hk.polyu.comp.jaid.ast;

import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.BASIC_BLOCK_MAP;
import static hk.polyu.comp.jaid.util.LogUtil.logCollectionForDebug;

/**
 * The process of generating the line to basic block map is:
 * 1) get the MethodDeclaration node of MTF
 * 2) mark all jumping statements (if-stmt, else-stmt, elseif-stmt, switch-stmt, case-stmt, break, continue, return)
 * 3) mark all loops (for, enhanced for, while) and record the loop body range
 * 4) consider all statements before a jumping statement as a basic block
 * 5) add extra basic block that consist of only one line for each loop statement
 * Note: there can be two exactly the same block (after adding loop basic block) in some cases(loop is the first stmt).
 */
public class BasicBlockMapper extends ASTVisitor {
    private final MethodDeclaration contextMethod;
    private final CompilationUnit compilationUnit;
    Set<Integer> jumpingStmtLines;
    Map<Integer, List<Integer>> recordLoopStmtMap;// the key is the start line the loop, value is the condition, start and end line of body.

    List<BasicBlock> basicBlocks;//use list to keep the basic block in statement order (important for finding correct bb for loops)

    public BasicBlockMapper(MethodDeclaration contextMethod) {
        this.contextMethod = contextMethod;
        this.compilationUnit = (CompilationUnit) contextMethod.getRoot();
        jumpingStmtLines = new LinkedHashSet<>();
        recordLoopStmtMap = new HashMap<>();
        addJumpingLine(this.contextMethod);
        this.contextMethod.accept(this);
    }

    public List<BasicBlock> getBasicBlocks() {
        if (basicBlocks == null)
            constructBbMap();
        return basicBlocks;
    }


    private void constructBbMap() {
        basicBlocks = new ArrayList<>();
        ASTNode node = contextMethod.getBody();
        int startMTF = this.compilationUnit.getLineNumber(node.getStartPosition());
        int endMTF = this.compilationUnit.getLineNumber(node.getStartPosition() + node.getLength());
        int bbStart = startMTF;
        for (int current = startMTF; current <= endMTF; current++) {
            if (jumpingStmtLines.contains(current)) {
                basicBlocks.add(new BasicBlock(IntStream.range(bbStart, current + 1).boxed().collect(Collectors.toSet())));
                if (recordLoopStmtMap.keySet().contains(current)) {
                    List<Integer> record = recordLoopStmtMap.get(current);
                    BasicBlock bb = new BasicBlock(Stream.of(record.get(0)).collect(Collectors.toSet()));
                    bb.setLoopConditionExp(record.get(1), record.get(2));
                    basicBlocks.add(bb);
                }
                bbStart = current + 1;
            }
        }
        logCollectionForDebug(basicBlocks, BASIC_BLOCK_MAP, false);
    }

// ================================= Visitor methods

    //================Jump
    public boolean visit(BreakStatement node) {
        addJumpingLine(node);
        return false;
    }


    public boolean visit(ContinueStatement node) {
        addJumpingLine(node);
        return false;
    }

    public boolean visit(ReturnStatement node) {
        addJumpingLine(node);
        return false;
    }

    public boolean visit(ThrowStatement node) {
        addJumpingLine(node);
        return false;
    }

    //==================Loop
    public boolean visit(DoStatement node) {
        recordLoop(node, node.getExpression(), node.getBody());
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(EnhancedForStatement node) {
        recordLoop(node, node.getExpression(), node.getBody());
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(ForStatement node) {
        recordLoop(node, node.getExpression(), node.getBody());
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(WhileStatement node) {
        recordLoop(node, node.getExpression(), node.getBody());
        node.getBody().accept(this);
        return false;
    }

    //==================Branching
    public boolean visit(IfStatement node) {
        addJumpingLine(node);

        node.getThenStatement().accept(this);
        if (node.getElseStatement() != null) {
            addJumpingLine(node.getElseStatement());
            node.getElseStatement().accept(this);

        }
        return false;
    }

    public boolean visit(LabeledStatement node) {
        addJumpingLine(node);
        node.getBody().accept(this);
        return false;
    }

    public boolean visit(TryStatement node) {//next stmt?
        addJumpingLine(node);
        node.getBody().accept(this);

        List catchClauses = node.catchClauses();
        for (Object o : catchClauses) {
            if (o instanceof CatchClause) {
                addJumpingLine(((CatchClause) o));
                ((CatchClause) o).getBody().accept(this);
            }
        }

        if (node.getFinally() != null) {
            addJumpingLine(node.getFinally());
            node.getFinally().accept(this);
        }

        return false;
    }

    public boolean visit(SwitchStatement node) {
        addJumpingLine(node);
        for (Object stmt : node.statements()) {
            ((ASTNode) stmt).accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(SwitchCase node) {
        addJumpingLine(node);
        return super.visit(node);
    }

    public boolean visit(SynchronizedStatement node) {
        addJumpingLine(node);
        node.getBody().accept(this);
        return false;
    }


    private void addJumpingLine(ASTNode node) {
        int startP = this.compilationUnit.getLineNumber(node.getStartPosition());
        jumpingStmtLines.add(startP);
        int endP = this.compilationUnit.getLineNumber(node.getStartPosition() + node.getLength());
        jumpingStmtLines.add(endP);
    }

    private void recordLoop(Statement loopStmt, Expression expression, Statement body) {
        int lineNumber = this.compilationUnit.getLineNumber(loopStmt.getStartPosition()); //key
        addJumpingLine(loopStmt);

        int expressionLine = this.compilationUnit.getLineNumber(expression.getStartPosition()); // condition
        int startLine = this.compilationUnit.getLineNumber(body.getStartPosition());
        int endLine = this.compilationUnit.getLineNumber(body.getStartPosition() +
                body.getLength());
        recordLoopStmtMap.put(lineNumber, Stream.of(expressionLine, startLine, endLine).collect(Collectors.toList()));
    }

}
