package hk.polyu.comp.jaid.test;

import hk.polyu.comp.jaid.java.JavaProject;
import hk.polyu.comp.jaid.tester.TestRequest;
import hk.polyu.comp.jaid.util.FileUtil;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Max PEI.
 */
public class TestCollector {

    public List<TestRequest> getAllTestsToRun(JavaProject project) {
        List<TestRequest> result = getAllTests(project);
        List<String> testsToInclude = project.getTestsToInclude();
        List<String> testsToExclude = project.getTestsToExclude();

        if (!testsToInclude.isEmpty()) {
            result = result.stream().filter(x -> (testsToInclude.contains(x.getTestClass()) || testsToInclude.contains(x.toString()))).collect(Collectors.toList());
        }
        if (!testsToExclude.isEmpty()) {
            result = result.stream().filter(x -> !(testsToExclude.contains(x.getTestClass()) || testsToExclude.contains(x.toString()))).collect(Collectors.toList());
        }

        Collections.sort(result, new Comparator<TestRequest>() {
            public int compare(TestRequest request1, TestRequest request2) {
                return -request1.toString().compareTo(request2.toString());
            }
        });
        if (result.isEmpty()) {
            throw new IllegalStateException("Test size is 0.");
        }

        return result;
    }

    public List<TestRequest> getAllTests(JavaProject project) {
        List<TestRequest> result = new LinkedList<>();

        ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Classpaths used in the project.
            URL[] urls = getURLsForClasspaths(project);

            // Create class loader to load classes in the target project from its classpaths
            // Use currentLoader as parent to maintain current visibility
            ClassLoader newLoader = URLClassLoader.newInstance(urls, currentLoader);
            Thread.currentThread().setContextClassLoader(newLoader);

            Class testAnnotation = newLoader.loadClass("org.junit.Test");
            List<String> fullyQualifiedTestClassNames = getFullyQualifiedTestClassNames(project);
            for (String name : fullyQualifiedTestClassNames) {
                result.addAll(getTestsFromClass(newLoader, name, testAnnotation));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Thread.currentThread().setContextClassLoader(currentLoader);
        }

        return result;
    }

    // Classpaths of the project into URLs.
    private URL[] getURLsForClasspaths(JavaProject project) {
        List<Path> classpaths = project.getClasspath();
        List<URL> urls = new LinkedList<>();

        for (Path p : classpaths) {
            try {
                urls.add(p.toUri().toURL());
            }
            catch (Exception e) {}
        }
        URL[] result = new URL[urls.size()];
        urls.toArray(result);
        return result;
    }

    private List<String> getFullyQualifiedTestClassNames(JavaProject project) {
        // Collect only test classes that are not internal.
        List<Path> testClassFiles = FileUtil.filesFrom(project.getTestOutputDir(),
                (String s) -> s.endsWith(".class") && !s.contains("$"), true,true);

        List<String> result = testClassFiles.stream()
                .map(t -> FileUtil.relativeClassPathToFQClassName(t))
                .collect(Collectors.toList());
        return result;
    }

    private List<TestRequest> getTestsFromClass(ClassLoader loader, String className, Class testAnnotationClass) {
        List<TestRequest> result = new LinkedList<>();
        try {
            Class cls = loader.loadClass(className);
            Method[] methods = cls.getDeclaredMethods();
            for (Method m : methods) {
                if (m.isAnnotationPresent(testAnnotationClass)) {
                    result.add(new TestRequest(className, m.getName()));
                } else if (m.getName().startsWith("test") && m.getParameterCount() == 0) {
                    result.add(new TestRequest(className, m.getName()));
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return result;
    }

}
