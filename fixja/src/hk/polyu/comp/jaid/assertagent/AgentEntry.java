package hk.polyu.comp.jaid.assertagent;

import java.lang.instrument.Instrumentation;

public class AgentEntry {

    public static void premain(String args, Instrumentation instrumentation)throws Exception{
        instrumentation.addTransformer(new AssertTransformer());
        instrumentation.addTransformer(new TimeoutTransformer());
    }
}
