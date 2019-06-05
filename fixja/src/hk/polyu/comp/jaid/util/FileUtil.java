package hk.polyu.comp.jaid.util;

import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Max PEI.
 */
public class FileUtil {

    public static List<Path> filesFrom(Path path, Function<String, Boolean> filter, boolean isRecursive, boolean shouldReturnRelative) {
        List<Path> result = new ArrayList<>();

        try {
            Stream<Path> pathStream = isRecursive ? Files.walk(path) : Files.list(path);
            result = pathStream.filter(x -> filter.apply(x.toString())).map(x -> shouldReturnRelative ? path.relativize(x) : x)
                    .collect(Collectors.toList());
            pathStream.close();
        } catch (IOException e) {
        }

        return result;
    }

    public static List<Path> javaFiles(Path path, boolean isRecursive, boolean shouldReturnRelative) {
        return filesFrom(path, (String s) -> s.endsWith(".java"), isRecursive, shouldReturnRelative);
    }

    public static List<Path> jarFiles(Path path, boolean isRecursive, boolean shouldReturnRelative) {
        return filesFrom(path, (String s) -> s.endsWith(".jar"), isRecursive, shouldReturnRelative);
    }

    public static void ensureDir(Path path) {
        File f = path.toFile();
        if (!f.exists()) {
            f.mkdirs();
        }

        if (!Files.exists(path) || !Files.isDirectory(path))
            throw new IllegalStateException("Error: " + path + " does not exist or is not a directory.");
    }

    public static void ensureEmptyDir(Path path) {
        File f = path.toFile();
        if(f.exists()){
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            catch(IOException e){
                throw new IllegalStateException("Fail to remove directory " + path);
            }
        }
        f.mkdirs();

        if (!Files.exists(path) || !Files.isDirectory(path))
            throw new IllegalStateException("Error! " + path + " does not exist or is not a directory.");
    }

    public static Path getPathFromNewRoot(CompilationUnit unit, String fileName, Path newRootPath){
        String packageName = unit.getPackage().getName().getFullyQualifiedName();
        Path relativePath = Paths.get(packageName.replace(".", File.separator) + File.separator + fileName);
        Path newSourceFilePath = newRootPath.resolve(relativePath);
        return newSourceFilePath;
    }

    public static void ensureEmptyFile(Path path) {
        try {
            File file = path.toFile();
            if (file.exists()) {
                if (file.isDirectory())
                    throw new IllegalStateException("Error! Cannot create file: " + path.toString());

                // Clear existing contents.
                FileOutputStream fos = new FileOutputStream(file);
                FileChannel outChan = fos.getChannel();
                outChan.truncate(0);
                outChan.close();
                fos.close();
            } else {
                Files.createFile(path);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error! Cannot ensure empty file " + path.toString());
        }
    }

    public static Path FQClassNameToRelativeSourcePath(String fqClassName) {
        return Paths.get(String.join(File.separator, fqClassName.split("\\.")) + ".java");
    }

    public static String relativeClassPathToFQClassName(Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Error! Argument should be relative path. \n\t" + path.toString());
        }

        String pathStr = path.toString();
        if(pathStr.endsWith(".class"))
            pathStr = pathStr.substring(0, pathStr.lastIndexOf(".class"));

        return String.join(".", pathStr.split(Matcher.quoteReplacement(File.separator)));
    }

    public static Path getClasspath(Class cls) {
        // fixme: is "cls.getProtectionDomain().getCodeSource().getLocation().getPath()" already enough?
        String thisClassPath = new File(cls.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath();

        return Paths.get(thisClassPath);
    }

    public static String getFileContent(Path path, Charset charset) {
        try {
            return new String(Files.readAllBytes(path), charset);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Error! Failed to read from file " + path + ".");
        }
    }

    public static void writeFile(Path path, String content) {
        path.toFile().getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(path.toString(), StandardCharsets.UTF_8.toString())) {
            writer.write(content);
        } catch (IOException e) {
            throw new RuntimeException("Error! Failed to write to file " + path + ".");
        }
    }

}
