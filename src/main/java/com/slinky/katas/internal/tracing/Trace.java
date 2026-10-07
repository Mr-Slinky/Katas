package com.slinky.katas.internal.tracing;

import java.util.*;

/**
 * Takes the list of events that Probe recorded during one call and turns it into a trace table.
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
    private final List<Step> steps = new ArrayList<>();

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

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\

    /**
     * Stores the method that is running, the line it last reported and the condition it is part-way through
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
