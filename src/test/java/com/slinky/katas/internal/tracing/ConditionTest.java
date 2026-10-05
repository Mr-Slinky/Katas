package com.slinky.katas.internal.tracing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Condition}.
 * <p>
 * TDD progress: {@code findClosingBracket} and {@code isWrappedInBrackets} are covered. The remaining methods of {@code Condition} are still to be
 * written.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-05
 * @since 1.0.0
 */
class ConditionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            '(a)'                        | 0 | 2
            'if (a > b) {'               | 3 | 9
            'if (a > (b + 1)) {'         | 3 | 15
            'if (a > (b + 1)) {'         | 8 | 14
            'while (x < 5'               | 6 | -1
            'if (s.equals("(")) {'       | 3 | 17
            'if (s.equals(")")) {'       | 3 | 17
            'if (s.equals("\\")")) {'    | 3 | 19
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

}
