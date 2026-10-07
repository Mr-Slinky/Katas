package com.slinky.katas.internal.tracing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The condition of one {@code if}, {@code while} or {@code for} statement, split into the checks that Java runs
 * to decide it. A check is one yes-or-no test, such as {@code x % i == 0}. The operators {@code &&}, {@code ||}
 * and {@code !} join the checks into the whole condition.
 *
 * <p>
 * {@link #checks()} returns the checks from left to right, which is the order in which Java runs them.
 * {@link #evaluate(Map)} identifies each check by its index in that list, counting from 0.
 *
 * <p>
 * Java stops running checks once the answer is known. An {@code &&} stops at its first {@code false} check, and
 * an {@code ||} stops at its first {@code true} check. {@link #evaluate(Map)} therefore accepts the answers of
 * the checks that have run so far, and returns {@code null} while the condition still depends on a check that
 * has yet to run.
 *
 * <p>
 * A caller reads the conditions from one line of source code with {@link #parseLine(String)}, then passes the
 * answers of the checks that have run to {@link #evaluate(Map)}:
 *
 * <pre>{@code
 * var condition = Condition.parseLine("if (big % i == 0 && small % i == 0) return i;").getFirst();
 *
 * condition.text();                               // "big % i == 0 && small % i == 0"
 * condition.checks().size();                      // 2
 * condition.checks().get(1).text();               // "small % i == 0"
 *
 * condition.evaluate(Map.of());                   // null, check 0 has yet to run
 * condition.evaluate(Map.of(0, false));           // false, the && stops at check 0
 * condition.evaluate(Map.of(0, true));            // null, check 1 has yet to run
 * condition.evaluate(Map.of(0, true, 1, true));   // true
 * }</pre>
 *
 * <p>
 * Brackets change how the checks group, and therefore which check Java runs next. The two conditions below
 * contain the same three checks in the same order. Given the same answer for check 0, they reach different
 * results:
 *
 * <pre>{@code
 * var withoutBrackets = new Condition("a && b || c");     // Java groups this as (a && b) || c
 * var withBrackets    = new Condition("a && (b || c)");
 *
 * withoutBrackets.evaluate(Map.of(0, false));   // null, the || moves on to c, which has yet to run
 * withBrackets.evaluate(Map.of(0, false));      // false, the && stops at a and skips the whole bracket
 * }</pre>
 *
 * <p>
 * A {@code !} in front of a bracket flips the answer of the whole group inside it. A {@code !} in front of a
 * single check stays part of that check:
 *
 * <pre>{@code
 * var group = new Condition("!(a && b)");
 * group.checks().size();                        // 2
 * group.evaluate(Map.of(0, false));             // true, a && b is false and the ! flips it
 * group.evaluate(Map.of(0, true, 1, true));     // false
 *
 * var single = new Condition("!(i > 1)");
 * single.checks().size();                       // 1
 * single.checks().getFirst().isNegated();       // true
 * }</pre>
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-07
 * @since 1.0.0
 */
public class Condition {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    // Matches "if", "while" or "for" as a whole word, followed by any spaces and an opening bracket
    private static final Pattern KEYWORD = Pattern.compile("\\b(if|while|for)\\s*\\(");

    /**
     * Returns the condition of each {@code if}, {@code while} and {@code for} statement on one line of source
     * code, from left to right.
     *
     * <p>
     * The method ignores everything after a {@code //} comment, and every keyword inside a string literal. A
     * {@code for} statement contributes the part between its two semicolons, meaning an enhanced {@code for}
     * and a {@code for (;;)} contribute no condition. The returned list leaves out a condition whose closing
     * bracket falls on a later line.
     *
     * <pre>
     * "if (a) b(); if (c) d();"             ->  ["a", "c"]
     * "} while (n > 0);"                    ->  ["n > 0"]
     * "for (int i = small; i > 1; i--) {"   ->  ["i > 1"]
     * "for (String s : list) {"             ->  []
     * "if (x > 0) { // if (y > 0)"          ->  ["x > 0"]
     * "if (a) log(\"while (b)\");"          ->  ["a"]
     * "if (a > b ||"                        ->  []   (the condition continues on the next line)
     * </pre>
     *
     * @param line one line of Java source code
     *
     * @return the conditions on that line in the order they appear, or an empty list where the line contains
     *         none
     */
    static List<Condition> parseLine(String line) {
        var conditions = new ArrayList<Condition>();
        var code       = stripComment(line);
        // Blanks out the inside of each literal, which hides any keyword or bracket written in a string
        var scanned    = maskLiterals(code);
        var matcher    = KEYWORD.matcher(scanned);
        while (matcher.find()) {
            var open  = matcher.end() - 1;   // the match ends just after the opening bracket
            var close = findClosingBracket(scanned, open);

            if (close < 0) continue; // the condition continues on the next line, and the method skips it

            var inside = code.substring(open + 1, close);
            if (matcher.group(1).equals("for")) {
                // A for statement places its condition between its two semicolons
                var parts = splitTopLevel(inside, ";");
                inside = parts.size() == 3 ? parts.get(1) : "";
            }
            if (!inside.isBlank()) {
                conditions.add(new Condition(inside.strip()));
            }
        }
        return conditions;
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\

    /**
     * The comparison that one check makes between the value on its left and the value on its right.
     */
    public enum Operator {

        // The two-character symbols come first, so that findOperator matches "<=" before "<"
        /** Compares with {@code ==}. */
        EQUAL("=="),
        /** Compares with {@code !=}. */
        NOT_EQUAL("!="),
        /** Compares with {@code <=}. */
        LESS_OR_EQUAL("<="),
        /** Compares with {@code >=}. */
        GREATER_OR_EQUAL(">="),
        /** Compares with {@code <}. */
        LESS("<"),
        /** Compares with {@code >}. */
        GREATER(">"),
        /**
         * The operator of a check with no comparison symbol, which is {@code true} when the value on its left
         * is anything but 0.
         */
        IS_TRUE("");

        // The symbol as written in Java source code, or an empty string for IS_TRUE
        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Returns the result of comparing {@code left} with {@code right} using this operator.
         *
         * <pre>
         * EQUAL.eval(6, 6)     ->  true
         * LESS.eval(6, 6)      ->  false
         * IS_TRUE.eval(0, 5)   ->  false   (the right value plays no part)
         * </pre>
         *
         * @param left  the value on the left of the operator
         * @param right the value on the right of the operator (which {@link #IS_TRUE} ignores)
         *
         * @return {@code true} when the comparison is true for the two values
         */
        boolean eval(long left, long right) {
            return switch (this) {
                case EQUAL -> left == right;
                case NOT_EQUAL -> left != right;
                case LESS -> left < right;
                case LESS_OR_EQUAL -> left <= right;
                case GREATER -> left > right;
                case GREATER_OR_EQUAL -> left >= right;
                case IS_TRUE -> left != 0;
            };
        }
    }

    /**
     * One yes-or-no check inside a condition, such as {@code x % i == 0}.
     *
     * <p>
     * The checks parsed from four pieces of text:
     *
     * <pre>
     * "i > 1"       ->  Check("i > 1", GREATER, false)
     * "!(i > 1)"    ->  Check("!(i > 1)", GREATER, true)
     * "!!(i > 1)"   ->  Check("!!(i > 1)", GREATER, false)
     * "!found"      ->  Check("!found", IS_TRUE, true)
     * </pre>
     *
     * @param text      the check as written in the condition, including any leading {@code !} and wrapping
     *                  brackets
     * @param operator  the comparison the check makes once its leading {@code !} and wrapping brackets are
     *                  removed
     * @param isNegated {@code true} when an odd number of {@code !} stand in front of the comparison, counted
     *                  through any wrapping brackets. Each {@code !} flips the result of the comparison.
     */
    public record Check(String text, Operator operator, boolean isNegated) {

        /**
         * Returns the result of this check for the two values. The method compares them with
         * {@link #operator()}, then flips the result when {@link #isNegated()} returns {@code true}.
         *
         * @param left  the value on the left of the operator
         * @param right the value on the right of the operator (which {@link Operator#IS_TRUE} ignores)
         *
         * @return the result of the comparison, flipped when the check is negated
         */
        boolean eval(long left, long right) {
            return operator.eval(left, right) != isNegated;
        }

    }

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final String      text;
    // The checks in the order Java runs them, copied from the list that parse fills
    private final List<Check> checks;
    // The tree that records how &&, || and ! join the checks, with a Leaf for each check
    private final Node        root;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    // Package-private, exposed for unit-test verification.
    /**
     * Builds a condition from its source text and splits that text into checks.
     *
     * @param text the condition as written between the brackets of its statement, such as
     *             {@code a > b && !found}
     */
    Condition(String text) {
        var found   = new ArrayList<Check>();
        this.text   = text;
        this.root   = parse(text, found);
        this.checks = List.copyOf(found);
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    /**
     * Returns the source text of this condition, such as {@code big % i == 0 && small % i == 0}.
     *
     * @return the text this condition was built from
     */
    String text() {
        return text;
    }

    /**
     * Returns the checks of this condition from left to right, which is the order in which Java runs them.
     *
     * @return the checks in a list that throws {@link UnsupportedOperationException} on any change, where the
     *         index of each one is its key in the map that {@link #evaluate(Map)} accepts
     */
    List<Check> checks() {
        return checks;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Returns the answer to the whole condition, given the answers of the checks that have run so far.
     *
     * <p>
     * The map uses the index of each check in {@link #checks()} as its key. A check absent from the map has yet
     * to run. For the condition {@code a && b || c}, whose checks are {@code a}, {@code b} and {@code c}:
     *
     * <pre>
     * {}                  ->  null    (check 0 has yet to run)
     * {0=false}           ->  null    (the || moves on to check 2, which has yet to run)
     * {0=false, 2=true}   ->  true
     * {0=true, 1=true}    ->  true    (the || stops before check 2)
     * </pre>
     *
     * @param answers the answer of each check that has run, keyed by its index in {@link #checks()}
     *
     * @return {@code true} or {@code false} once the checks that have run decide the condition, or {@code null}
     *         while it still depends on a check that has yet to run
     */
    Boolean evaluate(Map<Integer, Boolean> answers) {
        return evaluate(root, answers);
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    /**
     * Returns the index of the bracket that closes the opening bracket at {@code open}. The method counts
     * nested brackets on the way. It skips any bracket inside a string or char literal, including a literal
     * that contains an escaped quote.
     *
     * <pre>{@code
     * findClosingBracket("if (a > b)", 3)             ->  9
     * findClosingBracket("if (a > (b + 1))", 3)       ->  15
     * findClosingBracket("if (a > (b + 1))", 8)       ->  14
     * findClosingBracket("if (s.equals(\")\"))", 3)   ->  17 (the bracket inside the string literal is skipped)
     * findClosingBracket("while (x < 5", 6)           ->  -1 (the text ends before the bracket closes)
     * }</pre>
     *
     * @param text the text to search, such as one line of source code
     * @param open the index of an opening bracket in {@code text}
     *
     * @return the index of the matching closing bracket, or -1 where the text ends first
     */
    static int findClosingBracket(String text, int open) {
        var  depth = 0;
        char quote = 0;   // the quote that opened the literal the loop is inside, or 0 outside every literal
        for (var i = open; i < text.length(); i++) {
            var c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;   // the character after a backslash belongs to the literal, a quote included
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // Package-private, exposed for unit-test verification.
    /**
     * Returns whether one pair of brackets wraps the whole text, from its first character to its last.
     *
     * <pre>{@code
     * "(a > b)"      ->  true
     * "((a > b))"    ->  true
     * "(a) && (b)"   ->  false   (the first bracket closes after "a")
     * "(a > b) "     ->  false   (the text ends with a space)
     * " (a > b)"     ->  false   (the text starts with a space)
     * }</pre>
     *
     * @param text the text to test
     *
     * @return {@code true} when the text starts with a bracket that closes at its last character, and
     *         {@code false} otherwise
     */
    static boolean isWrappedInBrackets(String text) {
        return text.startsWith("(") && findClosingBracket(text, 0) == text.length() - 1;
    }

    /**
     * Splits the text at each {@code separator} that appears outside every pair of brackets and every literal.
     * A separator inside brackets or inside a literal stays in its part, and each part keeps the spaces around
     * it.
     *
     * <pre>{@code
     * splitTopLevel("a > b && c < d", "&&")        ->  ["a > b ", " c < d"]
     * splitTopLevel("(a && b) || c", "||")         ->  ["(a && b) ", " c"]
     * splitTopLevel("(a && b) || c", "&&")         ->  ["(a && b) || c"]
     * splitTopLevel("s == \"&&\" && t", "&&")      ->  ["s == \"&&\" ", " t"]
     * splitTopLevel("int i = 0; i < n; i++", ";")  ->  ["int i = 0", " i < n", " i++"]
     * splitTopLevel("a &&", "&&")                  ->  ["a ", ""]
     * }</pre>
     *
     * @param text      the text to split
     * @param separator the separator to split at, such as {@code &&}, {@code ||} or {@code ;}
     *
     * @return the parts from left to right, or a list containing the whole text where it has no separator
     *         outside brackets
     */
    static List<String> splitTopLevel(String text, String separator) {
        var parts   = new ArrayList<String>();
        var scanned = maskLiterals(text);   // the loop reads brackets and separators from here, outside every literal
        var depth   = 0;   // the number of brackets open at index i
        var start   = 0;   // the index at which the current part begins
        for (var i = 0; i < text.length(); i++) {
            var     c               = scanned.charAt(i);
            boolean outsideBrackets = depth == 0;
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (outsideBrackets && scanned.startsWith(separator, i)) {
                // The current part ends where the separator begins, and the next part begins after it
                parts.add(text.substring(start, i));
                start = i + separator.length();
                i     = start - 1;   // the loop's i++ then moves i on to start, past the separator
            }
        }
        // The text after the last split forms the final part, or the whole text when the loop made no split
        parts.add(text.substring(start));
        return parts;
    }

    /**
     * Returns the line up to its first {@code //} comment. A {@code //} inside a string literal belongs to the
     * string, meaning the method keeps it in the line.
     *
     * <pre>{@code
     * "int temp = big; // keep big"   ->  "int temp = big; "
     * "String s = \"http://x\";"      ->  "String s = \"http://x\";"
     * "int a = b / c;"                ->  "int a = b / c;"
     * "// the whole line"             ->  ""
     * }</pre>
     *
     * @param line one line of Java source code
     *
     * @return the text before the first {@code //} outside every literal, including any space before it, or the
     *         whole line where it has no comment
     */
    static String stripComment(String line) {
        char quote = 0;   // the quote that opened the literal the loop is inside, or 0 outside every literal
        for (var i = 0; i < line.length() - 1; i++) {   // stops one short, since "//" needs a second character
            var c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++; // the character after a backslash belongs to the literal, a quote included
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '/' && line.charAt(i + 1) == '/') return line.substring(0, i);
        }

        return line;
    }

    /**
     * Returns the text with every character inside a string or char literal replaced by a space. The quotes
     * themselves stay. The returned text has the same length as the original, meaning an index found in one
     * identifies the same position in the other.
     *
     * <pre>{@code
     * "s.equals(\"a||b\")"   ->  "s.equals(\"    \")"
     * "c == '('"             ->  "c == ' '"
     * "s == \"\\\"\""        ->  "s == \"  \""   (the escaped quote is part of the literal)
     * "a && b"               ->  "a && b"
     * }</pre>
     *
     * @param text the text to scan, such as one line of source code
     *
     * @return the text with the inside of each literal replaced by spaces
     */
    static String maskLiterals(String text) {
        var  masked = new StringBuilder(text);
        char quote  = 0;   // the quote that opened the literal the loop is inside, or 0 outside every literal
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if (quote == 0) {
                if (c == '"' || c == '\'') {
                    quote = c;
                }
            } else if (c == quote) {
                quote = 0;
            } else {
                masked.setCharAt(i, ' ');
                if (c == '\\' && i + 1 < text.length()) {
                    masked.setCharAt(++i, ' ');   // the character after a backslash belongs to the literal
                }
            }
        }
        return masked.toString();
    }

    /**
     * Returns the operator of the first comparison symbol in the body of one check. The method searches from
     * left to right and skips anything inside brackets or inside a literal. A body with no comparison symbol
     * outside them, such as {@code found} or {@code isPrime(a < b)}, gives {@link Operator#IS_TRUE}.
     *
     * <pre>{@code
     * "i >= 1"           ->  GREATER_OR_EQUAL
     * "big % i == 0"     ->  EQUAL
     * "(a + b) > c"      ->  GREATER
     * "'<' == c"         ->  EQUAL
     * "isPrime(a < b)"   ->  IS_TRUE
     * "found"            ->  IS_TRUE
     * }</pre>
     *
     * @param body the body of one check, with any leading {@code !} and wrapping brackets removed
     *
     * @return the operator of the first comparison symbol outside every pair of brackets, or
     *         {@link Operator#IS_TRUE} where the body has none
     */
    static Operator findOperator(String body) {
        var scanned = maskLiterals(body); // the loop reads brackets and symbols from here, outside every literal
        var depth   = 0;   // the number of brackets open at index i
        for (var i = 0; i < body.length(); i++) {
            var c = scanned.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                // Returns the first operator whose symbol starts at i, skipping IS_TRUE, whose empty symbol
                // matches everywhere
                for (var op : Operator.values()) {
                    if (op != Operator.IS_TRUE && scanned.startsWith(op.symbol, i)) {
                        return op;
                    }
                }
            }
        }

        return Operator.IS_TRUE;
    }

    /**
     * Builds the {@link Check} for the text of one check. The method first removes each leading {@code !} and
     * each pair of wrapping brackets, in whatever order they appear. It then finds the operator in the body
     * that remains.
     *
     * <p>
     * Each {@code !} removed flips {@link Check#isNegated()}, meaning two of them cancel out. The returned
     * check keeps the original text.
     *
     * <pre>{@code
     * "i > 1"         ->  Check("i > 1", GREATER, false)
     * "!(!(x < y))"   ->  Check("!(!(x < y))", LESS, false)
     * "(!found)"      ->  Check("(!found)", IS_TRUE, true)
     * "x != y"        ->  Check("x != y", NOT_EQUAL, false)   (the "!" of "!=" belongs to the operator)
     * }</pre>
     *
     * @param text the text of one check, such as {@code !(i > 1)}
     *
     * @return the check, with its operator and whether it is negated
     */
    static Check parseCheck(String text) {
        var body      = text;
        var isNegated = false;
        while (true) {
            if (body.startsWith("!") && !body.startsWith("!=")) {
                isNegated = !isNegated;
                body      = body.substring(1).strip();
            } else if (isWrappedInBrackets(body)) {
                body = body.substring(1, body.length() - 1).strip();
            } else {
                break;
            }
        }
        return new Check(text, findOperator(body), isNegated);
    }

    /**
     * Parses the text of a condition into a tree of nodes, adding each check it finds to {@code checks} from
     * left to right.
     *
     * <p>
     * The method splits the text at each {@code ||} outside brackets before it splits at each {@code &&}. As a
     * result, {@code a && b || c} becomes an {@code Or} whose first part is an {@code And}, matching the way
     * Java groups it. The method then removes the brackets around a group of checks, and turns each {@code !}
     * in front of such a group into a {@code Not}. Any other text becomes one {@code Leaf}, a bracketed single
     * check included.
     *
     * <p>
     * For a {@code checks} list that starts empty:
     *
     * <pre>{@code
     * "a"               ->  Leaf(0)
     * "a && b || c"     ->  Or[And[Leaf(0), Leaf(1)], Leaf(2)]
     * "a && (b || c)"   ->  And[Leaf(0), Or[Leaf(1), Leaf(2)]]
     * "!(a && b)"       ->  Not(And[Leaf(0), Leaf(1)])
     * "!!(a && b)"      ->  Not(Not(And[Leaf(0), Leaf(1)]))
     * "!(i > 1)"        ->  Leaf(0)   (the "!" stays part of the single check)
     * }</pre>
     *
     * @param expression the text of a whole condition, or of one part of it
     * @param checks     the list that receives each check, where the index of a check is the index its
     *                   {@code Leaf} stores
     *
     * @return the root of the tree built from that text
     */
    private static Node parse(String expression, List<Check> checks) {
        var trimmed = expression.strip();

        // Splits at each "||" outside brackets and returns an Or of the parsed parts
        var alternatives = splitTopLevel(trimmed, "||");
        if (alternatives.size() > 1) {
            return new Or(alternatives.stream().map(part -> parse(part, checks)).toList());
        }
        // Splits at each "&&" outside brackets and returns an And of the parsed parts
        var requirements = splitTopLevel(trimmed, "&&");
        if (requirements.size() > 1) {
            return new And(requirements.stream().map(part -> parse(part, checks)).toList());
        }

        var scanned     = maskLiterals(trimmed);
        var joinsChecks = scanned.contains("&&") || scanned.contains("||");
        if (joinsChecks && isWrappedInBrackets(trimmed)) {
            // Removes the brackets around a group of checks and parses the text inside of them
            return parse(trimmed.substring(1, trimmed.length() - 1), checks);
        }
        if (joinsChecks && trimmed.startsWith("!")) {
            // Removes one "!" in front of a group of checks, or in front of a further "!", and returns a Not of
            // the parsed text after it
            var inner = trimmed.substring(1).strip();
            if (isWrappedInBrackets(inner) || inner.startsWith("!")) {
                return new Not(parse(inner, checks));
            }
        }

        // Adds the single check that remains to 'checks' and returns a Leaf with its index
        checks.add(parseCheck(trimmed));
        return new Leaf(checks.size() - 1);
    }

    /**
     * Returns the answer of one node in the tree, given the answers of the checks that have run so far.
     *
     * <p>
     * Java runs the checks from left to right. As a result, every part after a part with no answer has yet to
     * run as well. Each kind of node answers as follows:
     *
     * <ul>
     *     <li>A {@code Leaf} returns the answer of its check from the map, or {@code null} where the map has
     *     none.</li>
     *     <li>A {@code Not} flips the answer of its inner node, and returns {@code null} while that node has
     *     none.</li>
     *     <li>An {@code And} returns the answer of its first part that is {@code false} or {@code null}, and
     *     {@code true} when every part is {@code true}.</li>
     *     <li>An {@code Or} returns the answer of its first part that is {@code true} or {@code null}, and
     *     {@code false} when every part is {@code false}.</li>
     * </ul>
     *
     * @param node    the node to evaluate
     * @param answers the answer of each check that has run, keyed by its index in {@link #checks}
     *
     * @return {@code true} or {@code false} once the checks that have run decide the node, or {@code null}
     *         while it still depends on a check that has yet to run
     */
    private static Boolean evaluate(Node node, Map<Integer, Boolean> answers) {
        return switch (node) {
            case Leaf leaf -> answers.get(leaf.index());   // null for a check that has yet to run
            case Not not -> {
                var inner = evaluate(not.inner(), answers);
                yield inner == null ? null : !inner;
            }
            case And and -> {
                for (var part : and.parts()) {
                    var answer = evaluate(part, answers);
                    if (answer == null || !answer) {
                        yield answer;
                    }
                }
                yield true;
            }
            case Or or -> {
                for (var part : or.parts()) {
                    var answer = evaluate(part, answers);
                    if (answer == null || answer) {
                        yield answer;
                    }
                }
                yield false;
            }
        };
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\

    /**
     * One node in the tree that records how the checks of a condition combine.
     */
    private sealed interface Node permits Leaf, Not, And, Or {}

    private record Leaf(int index) implements Node {}   // index is the check's position in the list of checks

    private record Not(Node inner) implements Node {}   // a "!" in front of a bracketed group of checks

    private record And(List<Node> parts) implements Node {}   // parts joined by "&&", from left to right

    private record Or(List<Node> parts) implements Node {}   // parts joined by "||", from left to right

}
