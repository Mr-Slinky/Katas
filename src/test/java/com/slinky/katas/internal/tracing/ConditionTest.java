package com.slinky.katas.internal.tracing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Condition}.
 * <p>
 * TDD progress: {@code findClosingBracket}, {@code isWrappedInBrackets}, {@code splitTopLevel}, {@code stripComment},
 * {@code Operator.eval}, {@code findOperator}, {@code parseCheck}, {@code evaluate} and {@code parseLine} are covered.
 * {@code Check.eval} is still to be tested directly.
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            EQUAL            |  6 | 6 | true
            EQUAL            |  6 | 7 | false
            NOT_EQUAL        |  6 | 7 | true
            NOT_EQUAL        |  6 | 6 | false
            LESS             |  5 | 6 | true
            LESS             |  6 | 6 | false
            LESS             | -1 | 0 | true
            LESS_OR_EQUAL    |  6 | 6 | true
            LESS_OR_EQUAL    |  7 | 6 | false
            GREATER          |  7 | 6 | true
            GREATER          |  6 | 6 | false
            GREATER_OR_EQUAL |  6 | 6 | true
            GREATER_OR_EQUAL |  5 | 6 | false
            IS_TRUE          |  1 | 0 | true
            IS_TRUE          |  0 | 0 | false
            IS_TRUE          | -1 | 0 | true
            IS_TRUE          |  1 | 5 | true
            IS_TRUE          |  0 | 5 | false
            """)
    void testEval_withValidArgs_ReturnsExpectedValue(Condition.Operator operator, long left, long right, boolean expected) {
        var actual = operator.eval(left, right);

        assertEquals(expected, actual);
    }

    /**
     * Checks the {@link Condition.Operator} that {@code findOperator} returns for the body of one check.
     *
     * <h4 id="find-operator-limitations">Known limitations</h4>
     * The rows below leave out two kinds of input, for which {@code findOperator} returns the wrong operator:
     * <ol>
     *     <li>
     *       A shift operator before the comparison. In {@code a << 2 > b}, the method matches {@code <} at the
     *       first {@code <<} and returns {@code LESS}. The check compares with {@code >}, and the method
     *       should return {@code GREATER}.
     *     </li>
     *     <li>
     *       A comparison symbol inside a literal. In {@code '<' == c}, the method matches the {@code <} inside
     *       the char literal and returns {@code LESS}. The check compares with {@code ==}, and the method
     *       should return {@code EQUAL}. A symbol inside a string literal gives the same result.
     *     </li>
     * </ol>
     *
     * @param body     the body of one check, with any leading {@code !} and wrapping brackets removed
     * @param expected the operator the method should return
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `i>1`                    | GREATER
            `i >= 1`                 | GREATER_OR_EQUAL
            `i < n`                  | LESS
            `i <= n`                 | LESS_OR_EQUAL
            `big % i == 0`           | EQUAL
            `x != y`                 | NOT_EQUAL
            `s.equals(t) != false`   | NOT_EQUAL
            `(a + b) > c`            | GREATER
            `c == '<'`               | EQUAL
            `found`                  | IS_TRUE
            `isPrime(a < b)`         | IS_TRUE
            ``                       | IS_TRUE
            """)
    void testFindOperator_withValidArgs_ReturnsExpectedValue(String body, Condition.Operator expected) {
        var actual = Condition.findOperator(body);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `i > 1`                  | GREATER   | false
            `!(i > 1)`               | GREATER   | true
            `!!(i > 1)`              | GREATER   | false
            `!(!(x < y))`            | LESS      | false
            `! (a == b)`             | EQUAL     | true
            `x != y`                 | NOT_EQUAL | false
            `(a + b) > c`            | GREATER   | false
            `((found))`              | IS_TRUE   | false
            `!found`                 | IS_TRUE   | true
            `(!found)`               | IS_TRUE   | true
            `!isPrime(n)`            | IS_TRUE   | true
            """)
    void testParseCheck_withValidArgs_ReturnsExpectedValue(String text, Condition.Operator operator, boolean isNegated) {
        var expected = new Condition.Check(text, operator, isNegated);

        var actual = Condition.parseCheck(text);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("provideSettledEvaluateCases")
    void testEvaluate_withValidArgs_ReturnsExpectedValue(String text, Map<Integer, Boolean> answers, Boolean expected) {
        var condition = new Condition(text);

        var actual = condition.evaluate(answers);

        assertEquals(expected, actual);
    }

    // Each map holds the answers of the checks that have run so far, keyed by their index from left to right
    static Stream<Arguments> provideSettledEvaluateCases() {
        return Stream.of(Arguments.of("a",             Map.of(0, true), true),
                         Arguments.of("a",             Map.of(0, false), false),
                         Arguments.of("a && b",        Map.of(0, false), false),
                         Arguments.of("a && b",        Map.of(0, true, 1, true), true),
                         Arguments.of("a && b",        Map.of(0, true, 1, false), false),
                         Arguments.of("a || b",        Map.of(0, true), true),
                         Arguments.of("a || b",        Map.of(0, false, 1, true), true),
                         Arguments.of("a || b",        Map.of(0, false, 1, false), false),
                         Arguments.of("!(a && b)",     Map.of(0, false), true),
                         Arguments.of("!(a && b)",     Map.of(0, true, 1, true), false),
                         Arguments.of("a && b || c",   Map.of(0, true, 1, true), true),
                         Arguments.of("a && b || c",   Map.of(0, false, 2, true), true),
                         Arguments.of("a && (b || c)", Map.of(0, true, 1, false, 2, true), true),
                         Arguments.of("(a || b) && c", Map.of(0, false, 1, false), false));
    }

    @ParameterizedTest
    @MethodSource("provideUnsettledEvaluateCases")
    void testEvaluate_withValidArgs_ReturnsNull(String text, Map<Integer, Boolean> answers) {
        var condition = new Condition(text);

        var actual = condition.evaluate(answers);

        assertNull(actual);
    }

    @ParameterizedTest
    @MethodSource("provideParseLineCases")
    void testParseLine_withValidArgs_ReturnsExpectedValue(String line, List<String> expected) {
        var conditions = Condition.parseLine(line);

        var actual = conditions.stream()
                               .map(Condition::text)
                               .toList();
        assertEquals(expected, actual);
    }

    // Each case lists the text of every condition the line contains, from left to right
    static Stream<Arguments> provideParseLineCases() {
        return Stream.of(Arguments.of("if (big % i == 0 && small % i == 0) return i;", List.of("big % i == 0 && small % i == 0")),
                         Arguments.of("while (big != 0) {", List.of("big != 0")),
                         Arguments.of("} while (n > 0);", List.of("n > 0")),
                         Arguments.of("} else if (x < y) {", List.of("x < y")),
                         Arguments.of("if(x>0)", List.of("x>0")),
                         Arguments.of("if (isPrime(n)) {", List.of("isPrime(n)")),
                         Arguments.of("while (s.equals(\")\")) {", List.of("s.equals(\")\")")),
                         Arguments.of("if (a) b(); if (c) d();", List.of("a", "c")),
                         Arguments.of("if (x > 0) { // if (y > 0)", List.of("x > 0")),
                         Arguments.of("for (int i = small; i > 1; i--) {", List.of("i > 1")),
                         Arguments.of("for (;;) {", List.of()),
                         Arguments.of("for (String s : list) {", List.of()),
                         Arguments.of("if (a &&", List.of()),
                         Arguments.of("notify(x);", List.of()),
                         Arguments.of("int temp = big;", List.of()),
                         Arguments.of("", List.of()));
    }

    // Each map leaves out a check whose answer the condition still depends on
    static Stream<Arguments> provideUnsettledEvaluateCases() {
        return Stream.of(Arguments.of("a",             Map.of()),
                         Arguments.of("a && b",        Map.of()),
                         Arguments.of("a && b",        Map.of(0, true)),
                         Arguments.of("a || b",        Map.of(0, false)),
                         Arguments.of("!(a && b)",     Map.of(0, true)),
                         Arguments.of("a && b || c",   Map.of(0, false)),
                         Arguments.of("a && (b || c)", Map.of(0, true, 1, false)));
    }

}
