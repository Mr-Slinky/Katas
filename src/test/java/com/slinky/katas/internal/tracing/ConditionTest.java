package com.slinky.katas.internal.tracing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Condition}.
 * <p>
 * TDD progress: {@code findClosingBracket}, {@code isWrappedInBrackets}, {@code splitTopLevel} and {@code stripComment} are
 * covered.
 * The remaining methods of {@code Condition} are still to be written.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-05
 * @since 1.0.0
 */
class ConditionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
                                            `(a)`                        | 0 | 2
                                            `if (a > b) {`               | 3 | 9
                                            `if (a > (b + 1)) {`         | 3 | 15
                                            `if (a > (b + 1)) {`         | 8 | 14
                                            `while (x < 5`               | 6 | -1
                                            `if (s.equals("(")) {`       | 3 | 17
                                            `if (s.equals(")")) {`       | 3 | 17
                                            `if (s.equals("\\")")) {`    | 3 | 19
                                            `if (c == '"' && (x > 0)) {` | 3 | 23
                                            `if (s.equals("\\\\")) {`    | 3 | 18
                                            """)
    void testFindClosingBracket_withValidArgs_ReturnsExpectedValue(String text, int open, int expected) {
        var actual = Condition.findClosingBracket(text, open);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(a > b)", "((a > b))", "(a > (b + 1))", "(s.equals(\")\"))"})
    void testIsWrappedInBrackets_withValidArgs_ReturnsTrue(String text) {
        var actual = Condition.isWrappedInBrackets(text);

        assertTrue(actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a > b", "(a) && (b)", "(a > b", "(a > b) ", " (a > b)"})
    void testIsWrappedInBrackets_withValidArgs_ReturnsFalse(String text) {
        var actual = Condition.isWrappedInBrackets(text);

        assertFalse(actual);
    }

    @ParameterizedTest
    @MethodSource("provideSplitTopLevelCases")
    void testSplitTopLevel_withValidArgs_ReturnsExpectedValue(
            String text, String separator, List<String> expected
    ) {
        var actual = Condition.splitTopLevel(text, separator);

        assertEquals(expected, actual);
    }

    static Stream<Arguments> provideSplitTopLevelCases() {
        return Stream.of(Arguments.of("a > b && c < d", "&&", List.of("a > b ", " c < d")),
                         Arguments.of("(a && b) || c", "||", List.of("(a && b) ", " c")),
                         Arguments.of("(a && b) || c", "&&", List.of("(a && b) || c")),
                         Arguments.of("int i = 0; i < n; i++", ";", List.of("int i = 0", " i < n", " i++")),
                         Arguments.of("a && b && c", "&&", List.of("a ", " b ", " c")),
                         Arguments.of("a &&", "&&", List.of("a ", "")),
                         Arguments.of("", "&&", List.of("")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `int temp = big;`                  | `int temp = big;`
            `int temp = big; // keep big`      | `int temp = big; `
            `// the whole line`                | ``
            `int a = b / c;`                   | `int a = b / c;`
            `String s = "http://x";`           | `String s = "http://x";`
            `String s = "a\\"//b";`            | `String s = "a\\"//b";`
            `String s = "x"; // note`          | `String s = "x"; `
            `if (c == '"') { // a quote`       | `if (c == '"') { `
            `String s = "\\\\"; // note`       | `String s = "\\\\"; `
            ``                                 | ``
            """)
    void testStripComment_withValidArgs_ReturnsExpectedValue(String line, String expected) {
        var actual = Condition.stripComment(line);

        assertEquals(expected, actual);
    }

}
