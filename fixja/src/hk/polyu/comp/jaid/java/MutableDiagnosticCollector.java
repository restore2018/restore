package hk.polyu.comp.jaid.java;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Max PEI.
 */
public class MutableDiagnosticCollector<S> implements DiagnosticListener<S> {
    private List<Diagnostic<? extends S>> diagnostics =
            Collections.synchronizedList(new ArrayList<Diagnostic<? extends S>>());

    public void report(Diagnostic<? extends S> diagnostic) {
        diagnostic.getClass(); // null check
        diagnostics.add(diagnostic);
    }

    /**
     * Gets a list view of diagnostics collected by this object.
     *
     * @return a list view of diagnostics
     */
    public List<Diagnostic<? extends S>> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public void clear(){
        diagnostics.clear();
    }

}