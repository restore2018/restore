package hk.polyu.comp.jaid.ast;

import hk.polyu.comp.jaid.monitor.LineLocation;

/**
 * Scope within a source file, denoted using a 'beginLocation' (inclusive) and a 'endLocation' (inclusive).
 */
public class LineScope {
    private final LineLocation beginLocation;
    private final LineLocation endLocation;

    public LineScope(LineLocation beginLocation, LineLocation endLocation) {
        assert beginLocation != null && endLocation != null;
        assert beginLocation.getMethodDeclaration() == endLocation.getMethodDeclaration();

        this.beginLocation = beginLocation;
        this.endLocation = endLocation;
    }

    public LineLocation getBeginLocation() {
        return beginLocation;
    }

    public LineLocation getEndLocation() {
        return endLocation;
    }

    /**
     * Does this scope cover 'location'?
     *
     * @param location
     * @return
     */
    public boolean coversLocation(LineLocation location){
        return beginLocation.getLineNo() <= location.getLineNo() && location.getLineNo() <= endLocation.getLineNo();
    }

    public boolean coversLine(int lineNo){
        return beginLocation.getLineNo() <= lineNo && lineNo <= endLocation.getLineNo();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LineScope lineScope = (LineScope) o;

        if (!getBeginLocation().equals(lineScope.getBeginLocation())) return false;
        return getEndLocation().equals(lineScope.getEndLocation());
    }

    @Override
    public int hashCode() {
        int result = getBeginLocation().hashCode();
        result = 31 * result + getEndLocation().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "[" + beginLocation.toString() + ", " + endLocation.toString() + "]";
    }
}
