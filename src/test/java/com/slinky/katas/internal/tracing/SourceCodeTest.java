package com.slinky.katas.internal.tracing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD progress: {@code find} is covered for a source file saved in UTF-8 and for one saved in Windows-1252, for a
 * nested class, for a class with no source file, for a file inside a skipped folder, and for several matching
 * files. {@code hasLines}, {@code readLine} and {@code findConditionsOn} are covered, a line number outside the file
 * included.
 *
 * <p>
 * Each test writes source files for JDK classes, such as {@link Objects}, into a temporary folder that JUnit creates
 * for it and deletes afterwards. The test then starts the search in that folder, meaning the files the test writes
 * are the only ones the search can find.
 *
 * @author Claude Code
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-08
 * @since 1.0.0
 */
class SourceCodeTest {

    // The comment line contains an "é", which Windows-1252 saves as a byte outside UTF-8
    private static final List<String> LINES = List.of("package java.util;",
                                                      "// café",
                                                      "public final class Objects {}");

    // Line 3 contains one condition, and line 4 contains another
    private static final List<String> CODE_LINES = List.of("class Objects {",
                                                           "    void f(int a, int b) {",
                                                           "        if (a > 0 && b > 0) return;",
                                                           "        while (a < b) a++;",
                                                           "    }",
                                                           "}");

    // The folder where each test writes its source files and starts the search, new for every test
    @TempDir
    Path folder;

    /**
     * Checks that {@code find} reads a source file whatever encoding the editor saved it in. The rows cover
     * UTF-8, and Windows-1252, the default for many editors on Windows.
     *
     * @param charsetName the encoding the source file is saved in
     */
    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "windows-1252"})
    void testFind_withValidArgs_ReturnsExpectedValue(String charsetName) throws IOException {
        writeSource("java/util/Objects.java", LINES, Charset.forName(charsetName));

        var source = SourceCode.find(Objects.class, folder);

        assertAll(() -> assertTrue(source.hasLines()),
                  () -> assertEquals(3, source.getLineCount()),
                  () -> assertEquals("// café", source.readLine(2)));
    }

    /**
     * Checks that {@code find} reads the file of the outermost class for a nested class. {@code Map.Entry}
     * compiles to {@code java.util.Map$Entry}, whose source is {@code java/util/Map.java}.
     */
    @Test
    void testFind_withNestedClass_ReturnsOuterClassFile() throws IOException {
        writeSource("java/util/Map.java", List.of("// the Map file"), UTF_8);

        var source = SourceCode.find(Map.Entry.class, folder);

        assertAll(() -> assertEquals("Map.java", source.getFileName()),
                  () -> assertEquals("// the Map file", source.readLine(1)));
    }

    /**
     * Checks the {@code SourceCode} that {@code find} returns for a class whose source file is nowhere in the
     * temporary folder. It has no lines, and it keeps the name of the file the search looked for.
     */
    @Test
    void testFind_withNoSourceFile_ReturnsNoLines() {
        var source = SourceCode.find(String.class, folder);

        assertAll(() -> assertFalse(source.hasLines()),
                  () -> assertEquals(0, source.getLineCount()),
                  () -> assertEquals("String.java", source.getFileName()),
                  () -> assertEquals("", source.readLine(1)));
    }

    /**
     * Checks that {@code find} passes over a folder whose name starts with a dot, and over each build folder.
     * The only matching file in each row lies inside such a folder.
     *
     * @param skipped the name of the folder the search passes over
     */
    @ParameterizedTest
    @ValueSource(strings = {".hidden", "target", "out", "build", "bin", "node_modules"})
    void testFind_withFileInSkippedFolder_ReturnsNoLines(String skipped) throws IOException {
        writeSource(skipped + "/java/util/Objects.java", LINES, UTF_8);

        var source = SourceCode.find(Objects.class, folder);

        assertFalse(source.hasLines());
    }

    /**
     * Checks that {@code find} reads the matching file with the fewest folders between it and the working
     * directory.
     */
    @Test
    void testFind_withMatchesAtTwoDepths_ReturnsNearestFile() throws IOException {
        writeSource("deeper/java/util/Objects.java", List.of("// deeper"), UTF_8);
        writeSource("java/util/Objects.java", List.of("// nearer"), UTF_8);

        var source = SourceCode.find(Objects.class, folder);

        assertEquals("// nearer", source.readLine(1));
    }

    /**
     * Checks that {@code find} reads the file whose path comes first when sorted as text, among matching files
     * at the same depth.
     */
    @Test
    void testFind_withMatchesAtSameDepth_ReturnsFirstPathAsText() throws IOException {
        writeSource("b/java/util/Objects.java", List.of("// b"), UTF_8);
        writeSource("a/java/util/Objects.java", List.of("// a"), UTF_8);

        var source = SourceCode.find(Objects.class, folder);

        assertEquals("// a", source.readLine(1));
    }

    /**
     * Checks that {@code hasLines} returns {@code false} for a source file that {@code find} located but that
     * contains no lines.
     */
    @Test
    void testHasLines_withEmptyFile_ReturnsFalse() throws IOException {
        writeSource("java/util/Objects.java", List.of(), UTF_8);
        var source = SourceCode.find(Objects.class, folder);

        var actual = source.hasLines();

        assertFalse(actual);
    }

    /**
     * Checks that {@code readLine} returns an empty string for a line number below 1 or above the last line of
     * the three-line file.
     *
     * @param number a line number outside the file
     */
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4})
    void testReadLine_withEdgeCaseArgs_ReturnsEmptyString(int number) throws IOException {
        writeSource("java/util/Objects.java", LINES, UTF_8);
        var source = SourceCode.find(Objects.class, folder);

        var actual = source.readLine(number);

        assertEquals("", actual);
    }

    /**
     * Checks the text of each condition that {@code findConditionsOn} returns for one line of the file. The rows
     * include a line with no condition, and line numbers outside the file.
     *
     * @param number   a line number
     * @param expected the text of each condition on that line, from left to right
     */
    @ParameterizedTest
    @MethodSource("provideFindConditionsOnCases")
    void testFindConditionsOn_withValidArgs_ReturnsExpectedValue(int number, List<String> expected)
            throws IOException {
        writeSource("java/util/Objects.java", CODE_LINES, UTF_8);
        var source = SourceCode.find(Objects.class, folder);

        var actual = source.findConditionsOn(number)
                           .stream()
                           .map(Condition::getText)
                           .toList();

        assertEquals(expected, actual);
    }

    // Pairs each line number of CODE_LINES with the text of the conditions on that line
    static Stream<Arguments> provideFindConditionsOnCases() {
        return Stream.of(Arguments.of(3, List.of("a > 0 && b > 0")),
                         Arguments.of(4, List.of("a < b")),
                         Arguments.of(1, List.of()),
                         Arguments.of(0, List.of()),
                         Arguments.of(7, List.of()));
    }

    /**
     * Writes a source file into the temporary folder, creating the folders on its path.
     *
     * @param path    the path of the file inside the temporary folder, such as {@code java/util/Objects.java}
     * @param lines   the lines of the file
     * @param charset the encoding to save the file in
     *
     * @throws IOException if the file or its folders cannot be written
     */
    private void writeSource(String path, List<String> lines, Charset charset) throws IOException {
        var file = folder.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, lines, charset);
    }

}
