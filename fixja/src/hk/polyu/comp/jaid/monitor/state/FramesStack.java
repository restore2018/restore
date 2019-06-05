package hk.polyu.comp.jaid.monitor.state;

import java.util.LinkedList;
import java.util.List;

public class FramesStack {
    public static final FramesStack divider = new FramesStack(0, StackTag.DIVIDER, 0);

    protected List<ProgramState> frameStateList;
    protected long threadId;
    protected int frameCount;
    protected StackTag tag;


    public FramesStack(long threadId, StackTag tag, int frameCount) {
        this.threadId = threadId;
        this.tag = tag;
        this.frameCount = frameCount;
        this.frameStateList = new LinkedList<>();
    }

    public void extendFrame(ProgramState frameState) {
        this.frameStateList.add(frameState);
    }

    public StackTag getTag() {
        return tag;
    }

    public List<ProgramState> getFrameStateList() {
        return frameStateList;
    }

    public int getFrameCount() {
        return frameCount;
    }

    @Override
    public String toString() {
        return "FramesStack{" +
                "threadId=" + threadId +
                "[" + tag + "]}";
    }

    public enum StackTag {
        ENTRY, EXIT, DIVIDER, DIFF, STEP;
    }

    public static class DiffFrameStack extends FramesStack {

        public DiffFrameStack(int frameCount) {
            super(0, StackTag.DIFF, frameCount);
        }

        @Override
        public String toString() {
            return "DiffFrameStack{" +
                    "frameCount=" + frameCount +
                    '}';
        }
    }

}
