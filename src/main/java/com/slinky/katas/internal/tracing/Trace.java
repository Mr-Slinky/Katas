package com.slinky.katas.internal.tracing;

import java.util.*;

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

    private int     rowCount;
    private int     startSteps;
    private boolean isCutShort;

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
        var checked   = Probe.getChecked(result.id());
        var condition = checked.condition();
        var where     = "line " + checked.line() + ": ";

        int checkCount = condition.getChecks().size();
        for (int c = 0; c < checkCount; c++) {
            findColumn(
                    keys,
                    owners,
                    call.method,
                    where + condition.getText() + " #" + c,
                    condition.getChecks().get(c).text(),
                    true
            );
        }
        int wholeColumn = checkCount < 2 ? -1 : findColumn(
                keys,
                owners,
                call.method,
                where + condition.getText(),
                condition.getText(),
                true
        );

        int column = findColumn(
                keys,
                owners,
                call.method, where + condition.getText() + " #" + checked.index(),
                checked.getCheck().text(),
                true
        );
        builder.put(column, formatAnswer(result.value()), false, checked.line());

        if (checkCount < 2) {
            return;
        }
        if (checked.index() == 0 || call.condition != condition) {
            call.answers.clear();
            call.condition = condition;
        }
        call.answers.put(checked.index(), result.value());

        var whole = condition.evaluate(call.answers);
        if (whole != Condition.Answer.UNDECIDED) {
            builder.put(wholeColumn, formatAnswer(whole == Condition.Answer.TRUE), false, checked.line());
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
