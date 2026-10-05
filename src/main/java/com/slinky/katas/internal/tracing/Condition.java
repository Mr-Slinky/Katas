package com.slinky.katas.internal.tracing;

import java.util.ArrayList;
import java.util.List;

/**
 *
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-05
 * @since 1.0.0
 */
public class Condition {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    // The two-character symbols come first, so that "<=" is matched before "<"
    private static final String[]   SYMBOLS   = {"==", "!=", "<=", ">=", "<", ">"};
    private static final Operator[] OPERATORS = {Operator.EQUAL, Operator.NOT_EQUAL, Operator.LESS_OR_EQUAL,
                                                 Operator.GREATER_OR_EQUAL, Operator.LESS, Operator.GREATER};

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\

    enum Operator {

        EQUAL,
        NOT_EQUAL,
        LESS,
        LESS_OR_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        /**
         * A test with no comparison in it, which is {@code true} when the value on its left is anything but
         *  0.
         */
        IS_TRUE;

        /**
         *
         * @param left  the value on the left of the operator
         * @param right the value on the right of the operator (which {@link #IS_TRUE} ignores)
         * @return
         */
        boolean eval(long left, long right) {
            return switch (this) {
                case EQUAL            -> left == right;
                case NOT_EQUAL        -> left != right;
                case LESS             -> left < right;
                case LESS_OR_EQUAL    -> left <= right;
                case GREATER          -> left > right;
                case GREATER_OR_EQUAL -> left >= right;
                case IS_TRUE          -> left != 0;
            };
        }
    }

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\

    // ========================================================================================== \\
    //                                          Setters                                           \\
    // ========================================================================================== \\

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    // Returns the index of the bracket that closes the one at 'open', or -1 if the text ends first.
    // Brackets inside a string or char literal are skipped.
    // Package-private, exposed for unit-test verification.
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

    // Returns true when the bracket at the start of the text closes at its last character, as in "(a > b)".
    // Package-private, exposed for unit-test verification.
    static boolean isWrappedInBrackets(String text) {
        return text.startsWith("(") && findClosingBracket(text, 0) == text.length() - 1;
    }

    // Splits the text at each `separator` that appears outside every pair of brackets.
    // Package-private, exposed for unit-test verification.
    static List<String> splitTopLevel(String text, String separator) {
        var parts = new ArrayList<String>();
        var depth = 0;   // the number of brackets open at index i
        var start = 0;   // the index at which the current part begins
        for (var i = 0; i < text.length(); i++) {
            var     c               = text.charAt(i);
            boolean outsideBrackets = depth == 0;
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (outsideBrackets && text.startsWith(separator, i)) {
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

    // Returns the line, up to the first "//" outside a string or char literal, or the whole line when it has
    // no comment
    // Package-private, exposed for unit-test verification.
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

    static Operator findOperator(String body) {
        var depth = 0;   // the number of brackets open at index i
        for (var i = 0; i < body.length(); i++) {
            var c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0) {
                for (var op = 0; op < SYMBOLS.length; op++) {
                    if (body.startsWith(SYMBOLS[op], i)) return OPERATORS[op];
                }
            }
        }

        return Operator.IS_TRUE;
    }

}
