package com.slinky.katas.internal.tracing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The trace table of one traced call, stored as one {@link Step} for each cell. The constructor, still to be
 * written, will read the events that {@link Probe} recorded during the call and turn them into these steps.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-07
 * @since 1.0.0
 */
public final class Trace {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static final int MAX_ROWS = 40;

    static Trace build(List<Probe.Event> events, Object result, Throwable failure) {
        return new Trace(events, result, failure);
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    record Column(String heading, boolean isCondition) {}

    record Step(int line, int row, int column, String value) {}

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final List<Column> columns = new ArrayList<>();
    private final List<Step>   steps   = new ArrayList<>();
    private final Object       result;
    private final Throwable    failure;

    private int     rowCount;
    private int     startSteps;
    private int     lastLine   = -1;
    private int     returnLine = -1;
    private boolean isCutShort;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Trace(List<Probe.Event> events, Object result, Throwable failure) {
        this.result  = result;
        this.failure = failure;

        var working = new HashSet<String>();
        var stack   = new ArrayDeque<String>();
        for (var event : events) {
            switch (event) {
                case Probe.Enter enter -> stack.push(enter.method());
                case Probe.Exit _ -> stack.poll();
                case Probe.Set _, Probe.Result _ -> working.add(stack.peek());
                default -> { }
            }
        }

        var keys    = new HashMap<String, Integer>(); // TODO: Add comments explaining what this variable is
        var owners  = new ArrayList<String>();        // TODO: Add comments explaining what this variable is
        var calls   = new ArrayDeque<Call>();         // TODO: Add comments explaining what this variable is
        var builder = new RowBuilder();               // TODO: Add comments explaining what this variable is

        for (var event : events) {
            switch (event) {
                case Probe.Enter enter -> calls.push(new Call(enter.method()));
                case Probe.Exit _ -> {
                    var call = calls.poll();
                    if (working.contains(call.method)) {
                        returnLine = call.line;
                    }
                }
                case Probe.Line line -> {
                    lastLine = line.line();
                    var call = calls.peek();
                    if (line.line() < call.line) {
                        builder.breakBeforeNext = true;
                    }
                    call.line = line.line();
                }
                case Probe.Param param -> {
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
    List<Column> getColumns() {
        return List.copyOf(columns);
    }

    List<Step> getSteps() {
        return List.copyOf(steps);
    }

    int getStartSteps() {
        return startSteps;
    }

    int getRowCount() {
        return rowCount;
    }

    Object getResult() {
        return result;
    }

    Throwable getFailure() {
        return failure;
    }

    int getLastLine() {
        return lastLine;
    }

    int getReturnLine() {
        return failure == null && returnLine > 0 ? returnLine : lastLine;
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    boolean hasStartRow() {
        return startSteps > 0;
    }

    int countPasses() {
        return hasStartRow() ? rowCount - 1 : rowCount;
    }

    boolean hasConditions() {
        return columns.stream()
                      .anyMatch(Column::isCondition);
    }

    boolean isCutShort() {
        return isCutShort;
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    private static String formatAnswer(boolean answer) {
        return answer ? "T" : "F";
    }

    private int findColumn(
            Map<String, Integer> keys,
            List<String> owners,
            String method,
            String key,
            String heading,
            boolean isCondition
    ) {
        return keys.computeIfAbsent(method + "\n" + isCondition + "\n" + key, _ -> {
            columns.add(new Column(heading, isCondition));
            owners.add(method);
            return columns.size() - 1;
        });
    }

    private void fillCheck(
            Map<String, Integer> keys,
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

        for (var c = 0; c < checkCount; c++) {
            var checkKey     = conditionKey + " #" + c;
            var checkHeading = checks.get(c).text();
            findColumn(keys, owners, call.method, checkKey, checkHeading, true);
        }
        var wholeColumn = -1;
        if (checkCount >= 2) {
            wholeColumn = findColumn(keys, owners, call.method, conditionKey, conditionText, true);
        }

        var index         = checked.index();
        var resultKey     = conditionKey + " #" + index;
        var resultHeading = checked.getCheck().text();
        var resultColumn  = findColumn(keys, owners, call.method, resultKey, resultHeading, true);
        var answer        = result.value();
        var answerText    = formatAnswer(answer);
        builder.put(resultColumn, answerText, false, line);

        if (checkCount < 2) return;
        if (index == 0 || call.condition != condition) {
            call.answers.clear();
            call.condition = condition;
        }
        call.answers.put(index, answer);

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
     * Stores the method that is running, the line it last reported, the condition it is part-way through and
     * the answer of each check in that condition that has run.
     */
    private static final class Call {

        private final String                method;
        private final Map<Integer, Boolean> answers;

        private int       line = -1;
        private Condition condition;

        Call(String method) {
            this.answers = new HashMap<>();
            this.method  = method;
        }

    }

    private final class RowBuilder {

        private final Map<Integer, String> cells = new LinkedHashMap<>();

        private boolean isStart;
        private boolean breakBeforeNext;

        void put(int column, String value, boolean isParameter, int line) {
            if (isCutShort) return;

            var startsRow = rowCount == 0
                            || breakBeforeNext
                            || cells.containsKey(column)
                            || (isStart && !isParameter);

            if (startsRow) {
                if (rowCount >= MAX_ROWS) {
                    isCutShort = true;
                    return;
                }

                cells.clear();
                isStart = isParameter && rowCount == 0;
                rowCount++;
            }

            breakBeforeNext = false;
            cells.put(column, value);
            if (isStart) {
                startSteps++;
            }

            steps.add(new Step(line, rowCount - 1, column, value));
        }

    }

}
