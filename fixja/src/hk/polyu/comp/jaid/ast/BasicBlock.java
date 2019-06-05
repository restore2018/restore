package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.monitor.LineLocation;
import org.eclipse.jdt.core.dom.Statement;

import java.util.*;
import java.util.stream.Collectors;

import static hk.polyu.comp.jaid.fixer.config.FixerOutput.LogFile.BASIC_BLOCK_MAP;
import static hk.polyu.comp.jaid.util.LogUtil.logCollectionForDebug;

public class BasicBlock {
    Set<Integer> lines;
    boolean isLoopConditionExp = false;
    int startOfLoopBody = -1;
    int endOfLoopBody = -1;

    public BasicBlock() {
        this.lines = new HashSet<>();
    }

    public BasicBlock(Set<Integer> lines) {
        this.lines = lines;
    }

    public Set<Integer> getLines() {
        return lines;
    }

    public void addLine(int line) {
        lines.add(line);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public void setLoopConditionExp(int startOfLoopBody, int endOfLoopBody) {
        isLoopConditionExp = true;
        this.startOfLoopBody = startOfLoopBody;
        this.endOfLoopBody = endOfLoopBody;
    }

    public boolean isLoopConditionExp() {
        return isLoopConditionExp;
    }

    public int getStartOfLoopBody() {
        return startOfLoopBody;
    }

    public int getEndOfLoopBody() {
        return endOfLoopBody;
    }

    public boolean contains(int line) {
        return lines.contains(line);
    }

    @Override
    public String toString() {
        StringBuilder linesStr = new StringBuilder();
        lines.forEach(x -> linesStr.append(x).append(","));
        return "BasicBlock{" +
                "lines=" + linesStr +
                '}';
    }

    public static List<BasicBlock> getExecutableLineBb(Map<LineLocation, Statement> locationStatementMap, List<BasicBlock> allBasicBlock) {
        List<Integer> executableLines = locationStatementMap.keySet().stream().map(LineLocation::getLineNo).collect(Collectors.toList());
        List<BasicBlock> executableBbList = new ArrayList<>();
        for (BasicBlock basicBlock : allBasicBlock) {
            BasicBlock bb = new BasicBlock();
            for (Integer line : basicBlock.getLines()) {
                if (executableLines.contains(line))
                    bb.addLine(line);
            }
            if (!bb.isEmpty())
                executableBbList.add(bb);
        }
        logCollectionForDebug(executableBbList, BASIC_BLOCK_MAP, false);
        return executableBbList;
    }

    public static List<BasicBlock> getCorrespondingBb(List<BasicBlock> executableBbs, List<LineLocation> coveredList) {
        List<BasicBlock> correspondingBbs = new ArrayList<>();

        for (int i = 0; i < coveredList.size(); i++) {
            BasicBlock correspondingBb = null;
            for (BasicBlock bb : executableBbs) {
                if (bb.contains(coveredList.get(i).getLineNo())) {
                    correspondingBb = bb;
                    break;
                }
            }
            if (correspondingBb == null)
                throw new RuntimeException("Line " + coveredList.get(i) + " have no matching Bb.");
            // treatment for loop statements
            if (correspondingBb.isLoopConditionExp() && i > 0) {
                int prevLine = coveredList.get(i - 1).getLineNo();
                if (correspondingBb.getStartOfLoopBody() <= prevLine && prevLine <= correspondingBb.getEndOfLoopBody())
                    // when previous location is inside current loop body, use the Bb that contains only loop-condition-exp
                    for (BasicBlock bb : executableBbs)
                        if (bb.contains(coveredList.get(i).getLineNo()) && correspondingBb != bb) {
                            correspondingBb = bb;
                            break;
                        }
            }
            if ((correspondingBbs.size() == 0 || correspondingBbs.get(correspondingBbs.size() - 1) != correspondingBb))
                correspondingBbs.add(correspondingBb);

        }
        return correspondingBbs;
    }
}
