package hk.polyu.comp.jaid.test;

import hk.polyu.comp.jaid.fixer.log.LoggingService;
import hk.polyu.comp.jaid.tester.Tester;
import hk.polyu.comp.jaid.tester.TraceFormat;
import hk.polyu.comp.jaid.util.FileUtil;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static hk.polyu.comp.jaid.fixer.log.LoggingService.shouldLogDebug;

public class ResultReader {
    private List<TestExecutionResult> results;
    private TestExecutionResult lastResult;
    private int lastFixIndex;

    private boolean readingFailureMsg = false;

    public List<TestExecutionResult> getNewResults(Path logFilePath) {
        results = new LinkedList<>();

        String fileContent = FileUtil.getFileContent(logFilePath, Charset.defaultCharset());
        BufferedReader bufferedReader = new BufferedReader(new StringReader(fileContent));
        bufferedReader.lines().forEach(x -> processLine(x));
        return results;
    }

    private void processLine(String line) {
        String testClassAndMethod = "";
        int prefixPosition = line.indexOf(Tester.PREFIX_MARKER);
        if (prefixPosition >= 0)
            line = line.substring(prefixPosition);
//        else
//            return;

        if (line.startsWith(Tester.TEST_START_PREFIX)) {
            lastFixIndex = -1;
            testClassAndMethod = "";
            readingFailureMsg=false;

            String[] parts = line.split(Tester.SEPERATOR_SYMBOL);
            for (String part : parts) {
                if (part.startsWith(Tester.FIX_INDEX_PREFIX)) {
                    lastFixIndex = Integer.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                } else if (part.startsWith(Tester.TEST_REQUEST_PREFIX)) {
                    testClassAndMethod = part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length());
                }
            }

            if (lastFixIndex == -1 || testClassAndMethod.isEmpty())
                throw new IllegalStateException();

            lastResult = new TestExecutionResult(testClassAndMethod.substring(0, testClassAndMethod.lastIndexOf('.')),
                    testClassAndMethod.substring(testClassAndMethod.lastIndexOf('.') + 1, testClassAndMethod.length()));
            results.add(lastResult);

        } else if (line.startsWith(Tester.TEST_END_PREFIX)) {
            readingFailureMsg=false;
            if (shouldLogDebug()) {
                LoggingService.debug(line);
            }

            testClassAndMethod = "";
            int fixIndex = -1;
            boolean wasSuccessful = false;
            long runTime = 0;

            String[] parts = line.split(Tester.SEPERATOR_SYMBOL);
            for (String part : parts) {
                try {
                    if (part.startsWith(Tester.FIX_INDEX_PREFIX)) {
                        fixIndex = Integer.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                    } else if (part.startsWith(Tester.TEST_REQUEST_PREFIX)) {
                        testClassAndMethod = part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length());
                    } else if (part.startsWith(Tester.WAS_SUCCESSFUL_PREFIX)) {
                        wasSuccessful = Boolean.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                    } else if (part.startsWith(Tester.RUN_TIME_PREFIX)) {
                        runTime = Long.valueOf(part.substring(part.indexOf(Tester.EQUAL_SYMBOL) + 1, part.length()));
                    }
                } catch (Exception e) {

                }
            }
            if (fixIndex == -1 || fixIndex != lastFixIndex || testClassAndMethod.isEmpty() || !testClassAndMethod.equals(lastResult.getTestClassAndMethod())) {
                LoggingService.errorAll("Test end message does not match the previous test start message!");
            }

            if (lastResult != null) {
                lastResult.setWasSuccessful(wasSuccessful);
                lastResult.setRunTime(runTime);
            }
        } else if (line.trim().startsWith(TraceFormat.RESULT_FIELD_FAILURE_PREFIX)) {
            readingFailureMsg = true;
            int idx = line.indexOf(TraceFormat.RESULT_FIELD_FAILURE_PREFIX);
            if (idx >= 0)
                line = line.substring(idx);
            lastResult.setFailureMsg(line.trim());
        } else if(readingFailureMsg){
            lastResult.appendFailureMsg(line.trim());
        }

    }
}
