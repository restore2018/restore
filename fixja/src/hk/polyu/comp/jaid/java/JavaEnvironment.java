package hk.polyu.comp.jaid.java;

import hk.polyu.comp.jaid.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Max PEI.
 */
public class JavaEnvironment {

    private Path jdkRootDir;

    public Path getJdkRootDir() {
        return jdkRootDir;
    }

    private void setJdkRootDir(Path jdkRootDir) {
        if (!Files.exists(jdkRootDir) || !Files.isDirectory(jdkRootDir))
            throw new IllegalStateException("Error: " + jdkRootDir + " does not exist or is not a directory.");

        String osName = System.getProperty("os.name");
        if (!osName.startsWith("Windows") && !osName.startsWith("Linux")) {
            if (!jdkRootDir.toString().endsWith("Contents/Home") || jdkRootDir.toString().endsWith("Contents/Home/"))
                jdkRootDir = jdkRootDir.resolve("Contents/Home");
        }
        this.jdkRootDir = jdkRootDir;
    }

    public Path getCompilerPath() {
        Path relativePath = Paths.get("bin/javac.exe");
        return getJdkRootDir().resolve(relativePath);
    }

    public Path getJvmPath() {
        Path relativePath = Paths.get("bin/java.exe");
        return getJdkRootDir().resolve(relativePath);
    }

    public Path getLibPath() {
        Path relativePath = Paths.get("jre/lib");
        return getJdkRootDir().resolve(relativePath);
    }

    public JavaEnvironment(Path compilerPath) {
        setJdkRootDir(compilerPath);
    }

    public JavaEnvironment(String compilerPath) {
        setJdkRootDir(Paths.get(compilerPath));
    }

    public List<Path> getLibraries() {
        List<Path> result = new ArrayList<>();
        Path[] libPaths = new Path[]{
                getJdkRootDir().resolve(Paths.get("jre/lib")),
                getJdkRootDir().resolve(Paths.get("jre/lib/ext"))
        };

        Arrays.stream(libPaths).forEach(x -> result.addAll(FileUtil.jarFiles(x, false, false)));
        return result;
    }

}
