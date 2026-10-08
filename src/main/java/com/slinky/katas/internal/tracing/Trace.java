package com.slinky.katas.internal.tracing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The trace table of one traced call, stored as one {@link Step} for each cell that the call filled in.
 *
 * <p>
 * A caller passes {@link #build(List, Object, Throwable)} the events that {@link Probe#getEvents()} returned for
 * the call, along with the value the call returned and the exception it threw. {@link #getColumns()} then returns
 * one {@link Column} for each variable and each check, and {@link #getSteps()} returns the cells in the order the
 * call filled them in:
 *
 * <pre>{@code
 * var events = List.<Probe.Event>of(new Probe.Enter("gcd"),
 *                                   new Probe.Param("a", "18"),
 *                                   new Probe.Param("b", "12"),
 *                                   new Probe.Line(3),
 *                                   new Probe.Set("a", "12"),
 *                                   new Probe.Line(4),
 *                                   new Probe.Set("b", "6"),
 *                                   new Probe.Exit("6"));
 *
 * var trace = Trace.build(events, "6", null);
 *
 * trace.getColumns();    // [Column[heading=a, isCondition=false], Column[heading=b, isCondition=false]]
 * trace.getSteps();      // [Step[line=-1, row=0, column=0, value=18], Step[line=-1, row=0, column=1, value=12],
 *                        //  Step[line=3, row=1, column=0, value=12], Step[line=4, row=1, column=1, value=6]]
 * trace.getRowCount();   // 2
 * trace.countPasses();   // 1, since row 0 is the start row
 * }</pre>
 *
 * <p>
 * The first row is the start row when the call opens with its parameters, and it contains the value of each
 * parameter as the method started. Each row after it shows one pass of the call. A value starts a new row when its
 * column already has a value in the current row, or when the method has gone back to an earlier line, as a loop
 * does at the end of each pass.
 *
 * <p>
 * A working method is one that sets a variable or runs a check during the call. The table shows the parameters of
 * the working methods alone, which keeps the parameters of a helper method out of the table. When no method sets a
 * variable or runs a check, the table shows every parameter instead. Where two methods have a variable of the same
 * name, each heading takes the method name in front, as in {@code outer.i}.
 *
 * <p>
 * Each check gets a column of its own, showing {@code T} or {@code F} each time the check runs. A condition with two
 * or more checks also gets a column for the whole condition. The table fills that column in once the checks that
 * have run decide the answer.
 *
 * <p>
 * The table stores at most {@link #MAX_ROWS} rows. A value that would start one more row makes
 * {@link #isCutShort()} return {@code true}, and the table drops that value and every value after it.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-08
 * @since 1.0.0
 */
public final class Trace {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    /**
     * The most rows that one trace table stores. A value that would start a further row ends the recording, and
     * {@link #isCutShort()} then returns {@code true}.
     */
    static final int MAX_ROWS = 40;

    /**
     * Builds the trace table of one traced call from the events that {@link Probe} recorded during it.
     *
     * @param events  the events of the call, in the order the steps ran
     * @param result  the value the call returned
     * @param failure the exception or error the call threw, or {@code null} when the call returned
     *
     * @return the trace table of the call
     *
     * @throws NullPointerException if {@code events} is null
     */
    static Trace build(List<Probe.Event> events, Object result, Throwable failure) {
        return new Trace(events, result, failure);
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * One column of the trace table.
     *
     * @param heading     the text at the top of the column, which is the name of a variable or the text of a check
     *                    or a condition. A variable that two methods share takes the method name in front, as in
     *                    {@code outer.i}.
     * @param isCondition {@code true} when the column shows the {@code T} and {@code F} answers of a check or a
     *                    condition, and {@code false} when it shows the values of a variable
     */
    record Column(String heading, boolean isCondition) {}

    /**
     * One cell of the trace table, filled in by one step of the call.
     *
     * @param line   the line number of the source code that produced the value, or -1 when there is no line, as
     *               for the value of a parameter
     * @param row    the row of the cell, counting from 0
     * @param column the position of the column in {@link Trace#getColumns()}, counting from 0
     * @param value  the text in the cell, which is the new value of a variable, or {@code T} or {@code F} for a
     *               check or a condition
     */
    record Step(int line, int row, int column, String value) {}

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final List<Column> columns = new ArrayList<>();
    private final List<Step>   steps   = new ArrayList<>();
    private final Object       result;
    private final Throwable    failure;

    private int     rowCount;
    // The number of steps in the start row
    private int     startSteps;
    // The line number the call reported last, from any method
    private int     lastLine   = -1;
    // The line a working method last reported before it returned
    private int     returnLine = -1;
    private boolean isCutShort;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    /**
     * Reads each event of one traced call in order and adds the columns and steps that it produces.
     *
     * @param events  the events of the call, in the order the steps ran
     * @param result  the value the call returned
     * @param failure the exception or error the call threw, or {@code null} when the call returned
     */
    private Trace(List<Probe.Event> events, Object result, Throwable failure) {
        this.result  = result;
        this.failure = failure;

        // Collects the working methods, which are the methods that set a variable or run a check
        var working = new HashSet<String>();
        var stack   = new ArrayDeque<String>();
        for (var event : events) {
            switch (event) {
                case Probe.Enter enter -> stack.push(enter.method());
                case Probe.Exit _ -> stack.poll();
                case Probe.Set _, Probe.Result _ -> working.add(stack.peek());
                case Probe.Param _, Probe.Line _ -> { }
            }
        }

        var keys    = new HashMap<ColumnKey, Integer>(); // Maps the key of each column to its position in columns
        var owners  = new ArrayList<String>();        // Stores the method that owns each column, by position
        var calls   = new ArrayDeque<Call>();         // Stacks the running calls, with the innermost on top
        var builder = new RowBuilder();               // Places each value in a row and adds its step

        // Turns each event into columns and steps for the method that is running
        for (var event : events) {
            switch (event) {
                case Probe.Enter enter -> calls.push(new Call(enter.method()));
                case Probe.Exit _ -> {
                    // Records the last line of a working method as the line the call returned from
                    var call = calls.poll();
                    if (call != null && working.contains(call.method)) {
                        returnLine = call.line;
                    }
                }
                case Probe.Line line -> {
                    // Starts a new row when a working method goes back to an earlier line, as a loop does
                    lastLine = line.line();
                    var call = calls.peek();
                    if (call != null) {
                        if (line.line() < call.line && working.contains(call.method)) {
                            builder.breakBeforeNext = true;
                        }
                        call.line = line.line();
                    }
                }
                case Probe.Param param -> {
                    // Adds the parameters of working methods, or of every method when none is working
                    var call = calls.peek();
                    if (working.isEmpty() || working.contains(call.method)) {
                        var column = findColumn(keys, owners, call.method, param.name(), param.name(), false);
                        builder.put(column, param.value(), true, -1);
                    }
                }
                case Probe.Set set -> {
                    var call   = calls.peek();
                    var column = findColumn(keys, owners, call.method, set.name(), set.name(), false);
                    builder.put(column, set.value(), false, call.line);
                }
                case Probe.Result checkResult -> fillCheck(keys, owners, calls.peek(), builder, checkResult);
            }
        }

        // Puts the method name in front of each variable heading that two or more methods share
        var headings = columns.stream()
                              .filter(column -> !column.isCondition())
                              .map(Column::heading)
                              .toList();

        for (var i = 0; i < columns.size(); i++) {
            var column = columns.get(i);
            if (!column.isCondition() && Collections.frequency(headings, column.heading()) > 1) {
                var owner    = owners.get(i);
                var ownerStr = owner + "." + column.heading();
                columns.set(i, new Column(ownerStr, false));
            }
        }
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    /**
     * Returns the columns of the table, from left to right.
     *
     * @return the columns, in a list that throws {@link UnsupportedOperationException} on any change
     */
    List<Column> getColumns() {
        return List.copyOf(columns);
    }

    /**
     * Returns one step for each cell, in the order the call filled the cells in.
     *
     * @return the steps, in a list that throws {@link UnsupportedOperationException} on any change
     */
    List<Step> getSteps() {
        return List.copyOf(steps);
    }

    /**
     * Returns the number of steps in the start row.
     *
     * @return the number of parameter values in the start row, or 0 when the table has no start row
     */
    int getStartSteps() {
        return startSteps;
    }

    /**
     * Returns the number of rows in the table, the start row included.
     *
     * @return the number of rows, at most {@link #MAX_ROWS}
     */
    int getRowCount() {
        return rowCount;
    }

    /**
     * Returns the value that the traced call returned.
     *
     * @return the value passed to {@link #build(List, Object, Throwable)} as {@code result}
     */
    Object getResult() {
        return result;
    }

    /**
     * Returns the exception or error that the traced call threw.
     *
     * @return the exception or error, or {@code null} when the call returned
     */
    Throwable getFailure() {
        return failure;
    }

    /**
     * Returns the line number that the call reported last, from any method.
     *
     * @return the line number, or -1 when the call reported no line
     */
    int getLastLine() {
        return lastLine;
    }

    /**
     * Returns the line number of the source code from which the traced call returned.
     *
     * <pre>
     * the call returned, and a working method reported a line   ->  the last line a working method reported
     * the call threw, or no working method reported a line      ->  getLastLine()
     * </pre>
     *
     * @return the line number, or -1 when the call reported no line
     */
    int getReturnLine() {
        return failure == null && returnLine > 0 ? returnLine : lastLine;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    /**
     * Returns whether the table opens with a start row, which contains the parameter values the method started
     * with.
     *
     * @return {@code true} when the first row contains parameter values, and {@code false} when the call filled no
     *         cell before its first pass
     */
    boolean hasStartRow() {
        return startSteps > 0;
    }

    /**
     * Returns the number of passes the table shows, one for each row after the start row.
     *
     * @return the row count less one when the table has a start row, or the row count when it has none
     */
    int countPasses() {
        return hasStartRow() ? rowCount - 1 : rowCount;
    }

    /**
     * Returns whether any column shows the answers of a check or a condition.
     *
     * @return {@code true} when at least one column has {@link Column#isCondition()} set to {@code true}
     */
    boolean hasConditions() {
        return columns.stream()
                      .anyMatch(Column::isCondition);
    }

    /**
     * Returns whether the call needed more rows than {@link #MAX_ROWS}.
     *
     * @return {@code true} when the table dropped the values after its last row, and {@code false} when it stores
     *         every value of the call
     */
    boolean isCutShort() {
        return isCutShort;
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    /**
     * Returns the text that a cell shows for the answer of a check or a condition.
     *
     * @param answer the answer
     *
     * @return {@code "T"} for {@code true} and {@code "F"} for {@code false}
     */
    private static String formatAnswer(boolean answer) {
        return answer ? "T" : "F";
    }

    /**
     * Returns the position of the column for a variable or a check in one method, adding the column when it is the
     * first value for that variable or check.
     *
     * @param keys        the position of each column, keyed by its method, its kind and its key
     * @param owners      the method that owns each column, at the same position as the column
     * @param method      the name of the method that owns the column
     * @param key         the name of the variable, or the text that identifies the check or condition
     * @param heading     the heading for the column when this call adds it
     * @param isCondition {@code true} for a column of check or condition answers
     *
     * @return the position of the column in {@code columns}, counting from 0
     */
    private int findColumn(
            Map<ColumnKey, Integer> keys,
            List<String> owners,
            String method,
            String key,
            String heading,
            boolean isCondition
    ) {
        var columnKey = new ColumnKey(method, isCondition, key);
        return keys.computeIfAbsent(columnKey, _ -> {
            columns.add(new Column(heading, isCondition));
            owners.add(method);
            return columns.size() - 1;
        });
    }

    /**
     * Puts the answer of one check in its column. When the check belongs to a condition with two or more checks,
     * the method also puts the answer of the whole condition in its column once the checks that have run decide
     * it.
     *
     * @param keys    the position of each column, keyed by its method, its kind and its key
     * @param owners  the method that owns each column, at the same position as the column
     * @param call    the call that ran the check
     * @param builder the builder that places each value in a row
     * @param result  the event that reports the answer of the check
     *
     * @throws IndexOutOfBoundsException if no check has the id in {@code result}
     */
    private void fillCheck(
            Map<ColumnKey, Integer> keys,
            List<String> owners,
            Call call,
            RowBuilder builder,
            Probe.Result result
    ) {
        var checked       = Probe.getChecked(result.id());
        var condition     = checked.condition();
        var checks        = condition.getChecks();
        var conditionText = condition.getText();
        var line          = checked.line();
        var conditionKey  = "line " + line + ": " + conditionText;
        var checkCount    = checks.size();

        // Adds a column for each check of the condition, in the order of getChecks
        for (var c = 0; c < checkCount; c++) {
            var checkKey     = conditionKey + " #" + c;
            var checkHeading = checks.get(c).text();
            findColumn(keys, owners, call.method, checkKey, checkHeading, true);
        }
        // Adds a column for the whole condition when it has two or more checks
        var wholeColumn = -1;
        if (checkCount >= 2) {
            wholeColumn = findColumn(keys, owners, call.method, conditionKey, conditionText, true);
        }

        // Puts the answer of this check in its column
        var index         = checked.index();
        var resultKey     = conditionKey + " #" + index;
        var resultHeading = checked.getCheck().text();
        var resultColumn  = findColumn(keys, owners, call.method, resultKey, resultHeading, true);
        var answer        = result.value();
        var answerText    = formatAnswer(answer);
        builder.put(resultColumn, answerText, false, line);

        // Stores the answer, starting afresh on the first check or when the call has moved to another condition
        if (checkCount < 2) {
            return;
        }
        if (index == 0 || call.condition != condition) {
            call.answers.clear();
            call.condition = condition;
        }
        call.answers.put(index, answer);

        // Puts the answer of the whole condition in its column once the checks that have run decide it
        var whole = condition.evaluate(call.answers);
        if (whole != Condition.Answer.UNDECIDED) {
            var isWholeTrue = whole == Condition.Answer.TRUE;
            var wholeText   = formatAnswer(isWholeTrue);
            builder.put(wholeColumn, wholeText, false, line);
            call.answers.clear();
        }
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\
    /**
     * Identifies one column of the table. Two values go in the same column when all three parts match.
     *
     * @param method      the name of the method that owns the column
     * @param isCondition {@code true} for a column of check or condition answers
     * @param key         the name of the variable, or the text that identifies the check or condition
     */
    private record ColumnKey(String method, boolean isCondition, String key) {}

    /**
     * Stores the method that is running, the line it last reported, the condition it is part-way through and
     * the answer of each check in that condition that has run.
     */
    private static final class Call {

        private final String                method;
        private final Map<Integer, Boolean> answers;

        private int       line = -1;
        private Condition condition;

        /**
         * Starts the record of one call to a method, with no line reported and no check run.
         *
         * @param method the name of the method
         */
        Call(String method) {
            this.answers = new HashMap<>();
            this.method  = method;
        }

    }

    /**
     * Decides the row of each value, then adds a {@link Step} for the value to the steps of the trace.
     */
    private final class RowBuilder {

        // The columns filled in the current row
        private final Set<Integer> filled = new HashSet<>();

        // True while the current row is the start row
        private boolean isStart;
        // True when the next value starts a new row
        private boolean breakBeforeNext;

        /**
         * Adds a step for one value to the current row, or to a new row when the value belongs to the next pass.
         *
         * <p>
         * The value starts a new row when any of these is true:
         * <ul>
         *     <li>the table has no rows yet</li>
         *     <li>a working method has gone back to an earlier line since the last value</li>
         *     <li>the current row already has a value in this column</li>
         *     <li>the current row is the start row, and the value is something other than a parameter</li>
         * </ul>
         *
         * <p>
         * A value that would start a row past {@link Trace#MAX_ROWS} makes {@link Trace#isCutShort()} return
         * {@code true}. The method then ignores that value and every value after it.
         *
         * @param column      the position of the column in {@code columns}, counting from 0
         * @param value       the text for the cell
         * @param isParameter {@code true} when the value is the value of a parameter as its method started
         * @param line        the line number to store in the step, or -1 for a parameter
         */
        void put(int column, String value, boolean isParameter, int line) {
            if (isCutShort) {
                return;
            }

            var startsRow = rowCount == 0
                            || breakBeforeNext
                            || filled.contains(column)
                            || (isStart && !isParameter);

            if (startsRow) {
                if (rowCount >= MAX_ROWS) {
                    isCutShort = true;
                    return;
                }

                filled.clear();
                isStart = isParameter && rowCount == 0;
                rowCount++;
            }

            breakBeforeNext = false;
            filled.add(column);
            if (isStart) {
                startSteps++;
            }

            steps.add(new Step(line, rowCount - 1, column, value));
        }

    }

}
