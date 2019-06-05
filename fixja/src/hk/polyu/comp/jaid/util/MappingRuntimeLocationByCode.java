package hk.polyu.comp.jaid.util;

import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.monitor.LightLocation;
import hk.polyu.comp.jaid.monitor.LineLocation;
import hk.polyu.comp.jaid.monitor.MethodToMonitor;
import org.eclipse.jdt.core.dom.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MappingRuntimeLocationByCode {
    private MethodToMonitor methodToMonitor;
    private Map<String, LineLocation> allLocations;
    private List<String> sourceCodeLines;

    /**
     * Mapping runtime location in the batch class to the original LineLocation by source code
     * Assuming that there is no two executable lines exactly the same as each other.
     *
     * @param methodToMonitor
     */
    public MappingRuntimeLocationByCode(MethodToMonitor methodToMonitor, Path sourceCodePath) throws IOException {
        this.methodToMonitor = methodToMonitor;
        Map<LineLocation, Statement> locationStatementMap = methodToMonitor.getMethodDeclarationInfoCenter().getAllLocationStatementMap();
        allLocations = new HashMap<>();
        for (LineLocation lineLocation : locationStatementMap.keySet()) {
            String stmt = locationStatementMap.get(lineLocation).toString().trim();
            if (stmt.indexOf("\n") > 0) stmt = stmt.substring(0, stmt.indexOf("\n"));
            allLocations.put(stmt, lineLocation);
        }
        if (allLocations.size() != locationStatementMap.size()) LoggingService.warnAll("Lines with same code!!");
        sourceCodeLines = Files.readAllLines(sourceCodePath);
    }

    public LineLocation mapping(LightLocation batchLocation) {
        if (batchLocation.getMethod().startsWith(methodToMonitor.getSimpleName())) {
            int jdiLineNo = batchLocation.getLine();
            if (jdiLineNo > 0) {
                String line = sourceCodeLines.get(batchLocation.getLine() - 1);
                return allLocations.getOrDefault(line.trim(), null);
            } else {
                LoggingService.warnAll("Cannot get Batch Line number. " + batchLocation.getMethod() + "@" + batchLocation.getDeclaringType());
            }
        }
        return null;
    }
}
