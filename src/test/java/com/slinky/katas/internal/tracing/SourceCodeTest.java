package com.slinky.katas.internal.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD progress: {@code find} is covered for a source file saved in UTF-8 and for one saved in Windows-1252.
 *
 * <p>
 * Each test writes a source file for {@link Objects} into a temporary folder inside the working directory, and
 * deletes the folder afterwards. {@code Objects} is a JDK class, meaning the file the test writes is the only one
 * the search can find for it.
 *
 * @author Claude Code
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-07
 * @since 1.0.0
 */
class SourceCodeTest {

    // The comment line contains an "é", which Windows-1252 saves as a byte outside UTF-8
    private static final List<String> LINES = List.of("package java.util;",
                                                      "// café",
                                                      "public final class Objects {}");

    private Path folder;

    @BeforeEach
    void createFolder() throws IOException {
        folder = Files.createTempDirectory(Path.of("").toAbsolutePath(), "source-code-test-");
    }

    @AfterEach
    void deleteFolder() throws IOException {
        try (var paths = Files.walk(folder)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /**
     * Checks that {@code find} reads a source file whatever encoding the editor saved it in. The rows cover
     * UTF-8, and Windows-1252, the default for many editors on Windows.
     *
     * @param charsetName the encoding the source file is saved in
     */
    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "windows-1252"})
    void testFind_withValidArgs_ReturnsExpectedValue(String charsetName) throws IOException {
        var file = folder.resolve("java/util/Objects.java");
        Files.createDirectories(file.getParent());
        Files.write(file, LINES, Charset.forName(charsetName));

        var source = SourceCode.find(Objects.class);

        assertAll(() -> assertTrue(source.hasLines()),
                  () -> assertEquals(3, source.getLineCount()),
                  () -> assertEquals("// café", source.readLine(2)));
    }

}
