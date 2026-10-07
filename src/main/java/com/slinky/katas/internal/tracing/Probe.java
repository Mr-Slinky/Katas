package com.slinky.katas.internal.tracing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Receives a call from the reporting copy of a class after each step, and records each call as an event
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 * @since 1.0.0
 */
public final class Probe {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static final int MAX_LOOPS  = 500; // Keep things civilised
    static final int MAX_EVENTS = 50_000;

    private static final List<Event>   EVENTS = new ArrayList<>();
    private static final List<Checked> CHECKS = new ArrayList<>();

    private static int loops;

    static void reset() {
        EVENTS.clear();
        loops = 0;
    }

    static List<Event> getEvents() {
        return List.copyOf(EVENTS);
    }

    static int register(Checked checked) {
        CHECKS.add(Objects.requireNonNull(checked));
        return CHECKS.size() - 1;
    }

    static Checked getChecked(int id) {
        return CHECKS.get(id);
    }

    public static void enter(String method) {
        add(new Enter(method));
    }

    public static void param(String name, String value) {
        add(new Param(name, value));
    }

    public static void set(String name, String value) {
        add(new Set(name, value));
    }

    public static void line(int line) {
        add(new Line(line));
    }

    public static void loop() {
        loops++;
        if (loops > MAX_LOOPS) {
            throw new Runaway(String.format("Still looping after %d passes", MAX_LOOPS));
        }
    }

    public static void exit(String value) {
        add(new Exit(value));
    }

    public static void check(int left, int right, int id) {
        add(new Result(id, getChecked(id).getCheck().eval(left, right)));
    }

    public static void check(int value, int id) {
        check(value, 0, id);
    }

    public static void checkSame(Object left, Object right, int id) {
        check(left == right ? 0 : 1, 0, id);
    }

    public static void checkNull(Object value, int id) {
        check(value == null ? 0 : 1, 0, id);
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    sealed interface Event permits Enter, Param, Set, Result, Line, Exit {}

    // The name of a method
    record Enter(String method)             implements Event {}
    // A key-value pair per parameter
    record Param(String name, String value) implements Event {}
    // A variable having its value changed
    record Set(String name, String value)   implements Event {}

    record Result(int id, boolean value)    implements Event {}
    record Line(int line)                   implements Event {}
    record Exit(String value)               implements Event {}

    record Checked(String method, int line, Condition condition, int index) {

        Checked {
            Objects.checkIndex(index, condition.getChecks().size()); // Fail fast!
        }

        Condition.Check getCheck() {
            return condition.getChecks().get(index);
        }
    }

    /**
     * Stops code that loops too many times or reports too many steps.
     */
    static final class Runaway extends Error {

        Runaway(String message) {
            super(message);
        }
    }

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Probe() {
    }

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
    private static void add(Event event) {
        if (EVENTS.size() >= MAX_EVENTS) {
            throw new Runaway(String.format("Still running after %d steps", MAX_EVENTS));
        }
        EVENTS.add(event);
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\

}
