package hk.polyu.comp.jaid.monitor;

import com.sun.jdi.Location;

import java.util.HashMap;
import java.util.Map;

public class LightLocation {
    String declaringType;
    String method;
    int line;

    private static Map<String, LightLocation> lightLocationCacheMap = new HashMap<>();

    public static LightLocation constructLightLocation(String declaringType, String method, int line) {
        if (lightLocationCacheMap.keySet().contains(declaringType + method + line))
            return lightLocationCacheMap.get(declaringType + lightLocationCacheMap + line);
        else
            return new LightLocation(declaringType, method, line);
    }

    public static LightLocation constructLightLocation(Location location) {
        String declaringType = location.declaringType().name();
        String method = location.method().name();
        int line = location.lineNumber();
        return constructLightLocation(declaringType, method, line);
    }

    private LightLocation(String declaringType, String method, int line) {
        this.declaringType = declaringType;
        this.method = method;
        this.line = line;
    }

    private LightLocation(Location location) {
        this.declaringType = location.declaringType().name();
        this.method = location.method().name();
        this.line = location.lineNumber();
    }

    public String getDeclaringType() {
        return declaringType;
    }

    public String getMethod() {
        return method;
    }

    public int getLine() {
        return line;
    }

    public boolean equalMethod(LightLocation that) {
        if (that == null) return false;
        if (!declaringType.equals(that.declaringType)) return false;
        return method.equals(that.method);
    }

    public boolean equalInstrumentedMethod(LightLocation that) {
        if (that == null) return false;
        if (!declaringType.equals(that.declaringType)) return false;
        if ((that.getMethod().contains("__") || method.contains("__"))
                && !(that.getMethod().contains("__") && method.contains("__")))
            return that.getMethod().startsWith(method + "__") || method.startsWith(that.getMethod() + "__");
        return method.equals(that.method);
    }

    @Override
    public String toString() {
        return method + "@" + declaringType + "[" + line + ']';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LightLocation that = (LightLocation) o;

        if (line != that.line) return false;
        if (!declaringType.equals(that.declaringType)) return false;
        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        int result = declaringType.hashCode();
        result = 31 * result + method.hashCode();
        result = 31 * result + line;
        return result;
    }
}
