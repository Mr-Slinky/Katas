package com.slinky.katas.internal.tracing;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The lines of the {@code .java} file that a class was compiled from.
 *
 * <p>
 * A caller obtains a {@code SourceCode} from {@link #find(Class)}, which searches for the file and reads it.
 * Line numbers count from 1, matching the line numbers that Java records in a compiled class.
 * {@link #line(int)} returns the text of one line, and {@link #conditionsOn(int)} returns the conditions
 * written on it.
 *
 * <p>
 * For a class {@code com.slinky.katas.Gcd} whose source file has {@code if (small == 0) return big;} on line
 * 12:
 *
 * <pre>{@code
 * var source = SourceCode.find(Gcd.class);
 *
 * source.isFound();                            // true
 * source.fileName();                           // "Gcd.java"
 * source.line(12).strip();                     // "if (small == 0) return big;"
 * source.conditionsOn(12).getFirst().text();   // "small == 0"
 * }</pre>
 *
 * <p>
 * When {@code find} cannot locate or read the file, it returns a {@code SourceCode} with no lines. For that
 * {@code SourceCode}, {@link #isFound()} returns {@code false}, {@link #lineCount()} returns 0, and
 * {@link #line(int)} returns an empty string for every number.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-05
 * @since 1.0.0
 */
final class SourceCode {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    // The deepest folder level the search enters, counting the folder the program runs from as level 0
    private static final int         MAX_DEPTH    = 12;
    // Build output folders, which contain compiled classes rather than source files
    private static final Set<String> SKIPPED      = Set.of("target", "out", "build", "bin", "node_modules");
    // The encoding that many Windows editors save in, used for a file that is not valid UTF-8
    private static final Charset     WINDOWS_1252 = Charset.forName("windows-1252");

    /**
     * Searches for the {@code .java} file that a class was compiled from, and reads its lines.
     *
     * <p>
     * The search starts in the folder the program runs from and covers every folder inside it, down to 12
     * levels. It passes over each folder whose name starts with a dot, along with the build folders
     * {@code target}, {@code out}, {@code build}, {@code bin} and {@code node_modules}. A nested class maps to
     * the file of its outermost class. The method reads the file as UTF-8, or as Windows-1252 where the file
     * contains a byte sequence that UTF-8 rejects.
     *
     * <pre>
     * com.slinky.katas.Gcd       ->  .../com/slinky/katas/Gcd.java
     * com.slinky.katas.Gcd$Step  ->  .../com/slinky/katas/Gcd.java
     * </pre>
     *
     * @param type the compiled class
     *
     * @return the source file, with no lines when the file cannot be found or read
     */
    static SourceCode find(Class<?> type) {
        // Gets the file name from the class name, using the outer class's name for a nested class
        var name     = type.getName();
        var dot      = name.lastIndexOf('.');
        var simple   = name.substring(dot + 1);
        var fileName = (simple.contains("$") ? simple.substring(0, simple.indexOf('$')) : simple) + ".java";
        // Builds the path the file must end with by turning the package's dots into folders
        var wanted = Path.of(dot < 0 ? "" : name.substring(0, dot).replace('.', '/'), fileName);

        // Gets the absolute path of the working directory, where the search starts
        var root = Path.of("").toAbsolutePath();
        // Stores the path of the matching file once the walk finds it
        var found = new Path[1];
        try {
            var visitor = new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Skips hidden folders and build folders, but always enters the working directory
                    var folder = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    var skip   = !dir.equals(root) && (folder.startsWith(".") || SKIPPED.contains(folder));
                    return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Stores the first file whose path ends with the wanted path, then stops the walk
                    if (file.endsWith(wanted)) {
                        found[0] = file;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException ex) {
                    // Skips a folder or file that cannot be opened and carries on walking
                    return FileVisitResult.CONTINUE;
                }
            };
            // Walks every folder below the working directory, up to MAX_DEPTH levels deep
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH, visitor);
            // Reads every line of the file when the walk found it
            if (found[0] != null) {
                return new SourceCode(fileName, readLines(found[0]));
            }
        } catch (IOException ex) {
            // Falls through to the empty SourceCode below when a folder or file cannot be read
        }
        // Returns a SourceCode with no lines when the file was not found
        return new SourceCode(fileName, List.of());
    }

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final String       fileName;
    private final List<String> lines;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private SourceCode(String fileName, List<String> lines) {
        this.fileName = fileName;
        this.lines    = lines;
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\

    /**
     * @return the name of the source file, such as {@code "Gcd.java"}, whether or not the file was found
     */
    String fileName() {
        return fileName;
    }

    /**
     * @return the number of lines in the file, or 0 when the file was not found
     */
    int lineCount() {
        return lines.size();
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\

    /**
     * Reports whether {@link #find(Class)} located and read the source file.
     *
     * <pre>
     * file found and read, one or more lines  ->  true
     * file missing or unreadable              ->  false
     * file found but empty                    ->  false
     * </pre>
     *
     * @return {@code true} when the file contains at least one line
     */
    boolean isFound() {
        return !lines.isEmpty();
    }

    /**
     * Returns the text of one line, without its line terminator.
     *
     * @param number a line number, where the first line of the file is 1
     *
     * @return the text of that line, or an empty string for a number below 1 or above {@link #lineCount()}
     */
    String line(int number) {
        return number >= 1 && number <= lines.size() ? lines.get(number - 1) : "";
    }

    /**
     * Returns the conditions written on one line, as {@link Condition#parseLine(String)} finds them.
     *
     * @param number a line number, where the first line of the file is 1
     *
     * @return the conditions on that line from left to right, or an empty list when the line contains none or
     *         the number is outside the file
     */
    List<Condition> conditionsOn(int number) {
        return Condition.parseLine(line(number));
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\

    /**
     * Returns every line of a file, without line terminators. The method reads the file as UTF-8, and reads it
     * again as Windows-1252 where it contains a byte sequence that UTF-8 rejects.
     *
     * @param file the file to read
     *
     * @return the lines of the file, from first to last
     *
     * @throws IOException if the file cannot be opened or read
     */
    private static List<String> readLines(Path file) throws IOException {
        try {
            return Files.readAllLines(file);
        } catch (CharacterCodingException ex) {
            // Decodes the bytes as Windows-1252, which replaces any undefined byte rather than throwing
            return new String(Files.readAllBytes(file), WINDOWS_1252).lines().toList();
        }
    }

}
