package com.slinky.katas.internal.tracing;

import org.junit.jupiter.api.Test;
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
 * TDD progress: {@code findClosingBracket}, {@code isWrappedInBrackets}, {@code splitTopLevel},
 * {@code stripComment}, {@code maskLiterals}, {@code Operator.eval}, {@code findOperator}, {@code parseCheck},
 * {@code checks}, {@code evaluate} and {@code parseLine} are covered. {@code Check.eval} is still to be tested
 * directly.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-05
 * @since 1.0.0
 */
class ConditionTest {

    /**
     * Checks the index that {@code findClosingBracket} returns for the bracket at {@code open}. The rows
     * include nested brackets, brackets and escaped quotes inside a literal, and a bracket that the text never
     * closes.
     *
     * @param text     one line of source code
     * @param open     the index of an opening bracket in {@code text}
     * @param expected the index of the matching closing bracket, or -1 where the text ends first
     */
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

    /**
     * Checks that {@code isWrappedInBrackets} returns {@code true} when the first bracket closes at the last
     * character.
     *
     * @param text text whose opening bracket closes at its final character
     */
    @ParameterizedTest
    @ValueSource(strings = {"(a > b)", "((a > b))", "(a > (b + 1))", "(s.equals(\")\"))"})
    void testIsWrappedInBrackets_withValidArgs_ReturnsTrue(String text) {
        var actual = Condition.isWrappedInBrackets(text);

        assertTrue(actual);
    }

    /**
     * Checks that {@code isWrappedInBrackets} returns {@code false} for text that starts with anything but a
     * bracket, for a first bracket that closes early, and for a first bracket that never closes.
     *
     * @param text text whose first character is something other than a bracket, or whose first bracket closes
     *             before its final character
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "a > b", "(a) && (b)", "(a > b", "(a > b) ", " (a > b)"})
    void testIsWrappedInBrackets_withValidArgs_ReturnsFalse(String text) {
        var actual = Condition.isWrappedInBrackets(text);

        assertFalse(actual);
    }

    /**
     * Checks the parts that {@code splitTopLevel} returns. A separator inside brackets or inside a string
     * literal stays in its part, and each part keeps the spaces around it. The rows also cover a bracket inside
     * a char literal, which leaves the bracket depth unchanged.
     *
     * @param text      the text to split
     * @param separator the separator to split at, such as {@code &&} or {@code ;}
     * @param expected  the parts from left to right
     */
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
                         Arguments.of("", "&&", List.of("")),
                         Arguments.of("\"a||b\".equals(s) || t", "||", List.of("\"a||b\".equals(s) ", " t")),
                         Arguments.of("s == \"&&\" && t", "&&", List.of("s == \"&&\" ", " t")),
                         Arguments.of("c == '(' && d", "&&", List.of("c == '(' ", " d")),
                         Arguments.of("c == ')' && d", "&&", List.of("c == ')' ", " d")));
    }

    /**
     * Checks the text that {@code stripComment} returns. A {@code //} inside a string literal stays in the line,
     * as does one after a quote inside a char literal. The first {@code //} outside every literal ends the line.
     *
     * @param line     one line of source code
     * @param expected the line up to its comment, or the whole line where it has none
     */
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

    /**
     * Checks the text that {@code maskLiterals} returns. Every character inside a string or char literal becomes
     * a space, an escaped quote and an escaped backslash included. The quotes stay, and a literal that the text
     * never closes runs to the end of the text.
     *
     * @param text     one line of source code
     * @param expected the text with the inside of each literal replaced by spaces
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `a && b`                 | `a && b`
            `s.equals("a||b")`       | `s.equals("    ")`
            `c == '('`               | `c == ' '`
            `s == "\\""`             | `s == "  "`
            `c == '\\''`             | `c == '  '`
            `s == "\\\\"`            | `s == "  "`
            `"a" + "b"`              | `" " + " "`
            `"abc`                   | `"   `
            ``                       | ``
            """)
    void testMaskLiterals_withValidArgs_ReturnsExpectedValue(String text, String expected) {
        var actual = Condition.maskLiterals(text);

        assertEquals(expected, actual);
    }

    /**
     * Checks the result of {@link Condition.Operator#eval(long, long)} for each operator, with a {@code true}
     * row and a {@code false} row for every constant. The {@code IS_TRUE} rows also vary the right value, which
     * that operator ignores.
     *
     * @param operator the operator to evaluate
     * @param left     the value on the left of the operator
     * @param right    the value on the right of the operator
     * @param expected the result of the comparison
     */
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
    void testEval_withValidArgs_ReturnsExpectedValue(
            Condition.Operator operator, long left, long right, boolean expected
    ) {
        var actual = operator.eval(left, right);

        assertEquals(expected, actual);
    }

    /**
     * Checks the {@link Condition.Operator} that {@code findOperator} returns for the body of one check. The
     * rows include a comparison symbol inside a string or char literal, which the method passes over.
     *
     * <h4 id="find-operator-limitations">Known limitation</h4>
     * {@code findOperator} returns the wrong operator for a shift operator before the comparison, which the rows
     * below leave out. In {@code a << 2 > b}, the method matches {@code <} at the first {@code <<} and returns
     * {@code LESS}. The check compares with {@code >}, and the method should return {@code GREATER}.
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
            `'<' == c`               | EQUAL
            `'>' != c`               | NOT_EQUAL
            `"<".equals(s)`          | IS_TRUE
            `found`                  | IS_TRUE
            `isPrime(a < b)`         | IS_TRUE
            ``                       | IS_TRUE
            """)
    void testFindOperator_withValidArgs_ReturnsExpectedValue(String body, Condition.Operator expected) {
        var actual = Condition.findOperator(body);

        assertEquals(expected, actual);
    }

    /**
     * Checks the {@link Condition.Check} that {@code parseCheck} builds from the text of one check. Each leading
     * {@code !} flips {@code isNegated}. The method removes the wrapping brackets before it looks for the
     * operator.
     *
     * @param text      the text of one check
     * @param operator  the operator the check should contain
     * @param isNegated whether the check should be negated
     */
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
    void testParseCheck_withValidArgs_ReturnsExpectedValue(
            String text, Condition.Operator operator, boolean isNegated
    ) {
        var expected = new Condition.Check(text, operator, isNegated);

        var actual = Condition.parseCheck(text);

        assertEquals(expected, actual);
    }

    /**
     * Checks the number of checks that {@code checks} returns. A bracketed group keeps one check for each side
     * of its {@code &&} or {@code ||}, however many {@code !} stand in front of it. A {@code !} in front of a
     * single check stays part of that check, and an {@code &&} inside a string literal joins nothing.
     *
     * @param text     the source text of the condition
     * @param expected the number of checks in the condition
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `a`                        | 1
            `a && b || c`              | 3
            `!(i > 1)`                 | 1
            `!(a && b)`                | 2
            `!!(a && b)`               | 2
            `! !(a && b)`              | 2
            `!!(a || b)`               | 2
            `!!!(a && b)`              | 2
            `"a && b".isEmpty() && c`  | 2
            """)
    void testChecks_withValidArgs_ReturnsExpectedValue(String text, int expected) {
        var condition = new Condition(text);

        var actual = condition.checks().size();

        assertEquals(expected, actual);
    }

    /**
     * Checks that the list {@code checks} returns rejects a change, which keeps the index of each check in step
     * with the keys that {@code evaluate} accepts.
     */
    @Test
    void testChecks_withValidArgs_ThrowsUnsupportedOperationException() {
        var checks = new Condition("a && b").checks();
        var extra  = Condition.parseCheck("c");

        assertThrows(UnsupportedOperationException.class, () -> checks.add(extra));
    }

    /**
     * Checks that {@code evaluate} returns {@code true} or {@code false} once the answers given decide the
     * condition. Several rows leave out the checks that {@code &&} or {@code ||} stops before.
     *
     * @param text     the source text of the condition
     * @param answers  the answers of the checks that have run, keyed by index
     * @param expected the answer to the whole condition
     */
    @ParameterizedTest
    @MethodSource("provideSettledEvaluateCases")
    void testEvaluate_withValidArgs_ReturnsExpectedValue(
            String text, Map<Integer, Boolean> answers, Boolean expected
    ) {
        var condition = new Condition(text);

        var actual = condition.evaluate(answers);

        assertEquals(expected, actual);
    }

    // Each map contains the answers of the checks that have run so far, keyed by their index from left to right
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
                         Arguments.of("!!(a && b)",    Map.of(0, true, 1, true), true),
                         Arguments.of("!!!(a && b)",   Map.of(0, false), true),
                         Arguments.of("a && b || c",   Map.of(0, true, 1, true), true),
                         Arguments.of("a && b || c",   Map.of(0, false, 2, true), true),
                         Arguments.of("a && (b || c)", Map.of(0, true, 1, false, 2, true), true),
                         Arguments.of("(a || b) && c", Map.of(0, false, 1, false), false));
    }

    /**
     * Checks that {@code evaluate} returns {@code null} while the condition still depends on a check that has
     * yet to run.
     *
     * @param text    the source text of the condition
     * @param answers the answers of the checks that have run, keyed by index
     */
    @ParameterizedTest
    @MethodSource("provideUnsettledEvaluateCases")
    void testEvaluate_withValidArgs_ReturnsNull(String text, Map<Integer, Boolean> answers) {
        var condition = new Condition(text);

        var actual = condition.evaluate(answers);

        assertNull(actual);
    }

    // Each map leaves out a check whose answer the condition still depends on
    static Stream<Arguments> provideUnsettledEvaluateCases() {
        return Stream.of(Arguments.of("a",             Map.of()),
                         Arguments.of("a && b",        Map.of()),
                         Arguments.of("a && b",        Map.of(0, true)),
                         Arguments.of("a || b",        Map.of(0, false)),
                         Arguments.of("!(a && b)",     Map.of(0, true)),
                         Arguments.of("!!(a && b)",    Map.of(0, true)),
                         Arguments.of("!!(a || b)",    Map.of(0, false)),
                         Arguments.of("a && b || c",   Map.of(0, false)),
                         Arguments.of("a && (b || c)", Map.of(0, true, 1, false)));
    }

    /**
     * Checks the text of each condition that {@code parseLine} finds on one line. The rows cover every keyword,
     * two statements on one line, a keyword inside a comment or a string literal, both kinds of {@code for}, and
     * a condition that continues on the next line.
     *
     * @param line     one line of source code
     * @param expected the text of each condition on the line, from left to right
     */
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
                         Arguments.of("System.out.println(\"checking if (x > 0)\");", List.of()),
                         Arguments.of("if (a) log(\"while (b)\");", List.of("a")),
                         Arguments.of("String s = \"for (int i = 0; i < n; i++)\";", List.of()),
                         Arguments.of("for (int i = small; i > 1; i--) {", List.of("i > 1")),
                         Arguments.of("for (;;) {", List.of()),
                         Arguments.of("for (String s : list) {", List.of()),
                         Arguments.of("if (a &&", List.of()),
                         Arguments.of("notify(x);", List.of()),
                         Arguments.of("int temp = big;", List.of()),
                         Arguments.of("", List.of()));
    }

}
