package com.slinky.katas.internal.tracing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Records each step of a traced call as a {@link Event}.
 *
 * <p>
 * The reporting copy of a class calls the public static methods of {@code Probe} after each step: a method
 * starts, a variable takes a new value, a check runs, a new line begins, a loop goes round again, or a method
 * returns. Each call adds one event to a list, and {@link #getEvents()} returns that list in the order the
 * steps ran.
 *
 * <p>
 * A check needs an id before the reporting copy can report it. A caller registers each check with
 * {@link #register(Checked)}, clears the previous call's events with {@link #reset()}, runs the call, then
 * reads the events:
 *
 * <pre>{@code
 * var condition = Condition.parseLine("while (big != 0)").getFirst();
 * var id        = Probe.register(new Probe.Checked("gcd", 7, condition, 0));   // 0 for the first check
 *
 * Probe.reset();
 * Probe.enter("gcd");
 * Probe.param("big", "18");
 * Probe.check(18, id);
 *
 * Probe.getEvents();   // [Enter[method=gcd], Param[name=big, value=18], Result[id=0, value=true]]
 * }</pre>
 *
 * <p>
 * Every field is static, meaning the whole program shares one list of events and one list of checks. The lists
 * are unsynchronised, and {@code Probe} therefore records one traced call at a time, on one thread.
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-07
 * @since 1.0.0
 */
public final class Probe {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    /**
     * The number of passes that the loops of one traced call may make in total before {@link #loop()} throws
     * {@link Runaway}.
     */
    static final int MAX_LOOPS  = 500; // Keep things civilised
    /**
     * The number of events that one traced call may record before the next step throws {@link Runaway}.
     */
    static final int MAX_EVENTS = 50_000;

    private static final List<Event>   EVENTS = new ArrayList<>();
    // Every check registered, where the index of each one is the id that register returned for it
    private static final List<Checked> CHECKS = new ArrayList<>();

    // The passes made by every loop since the last reset
    private static int loops;

    /**
     * Clears the recorded events and the count of loop passes, ready for a new traced call. The registered
     * checks stay, since the reporting copy passes their ids on every call.
     */
    static void reset() {
        EVENTS.clear();
        loops = 0;
    }

    /**
     * Returns every event recorded since the last {@link #reset()}, in the order the steps ran.
     *
     * @return the events in a list that throws {@link UnsupportedOperationException} on any change
     */
    static List<Event> getEvents() {
        return List.copyOf(EVENTS);
    }

    /**
     * Gives a check an id. The reporting copy passes that id to {@link #check(int, int, int)} and the other
     * check methods each time the check runs.
     *
     * @param checked the check, and the place in the source file where it is written
     *
     * @return the id of the check, counting from 0 in the order the checks were registered
     *
     * @throws NullPointerException if {@code checked} is null
     */
    static int register(Checked checked) {
        CHECKS.add(Objects.requireNonNull(checked));
        return CHECKS.size() - 1;
    }

    /**
     * Returns the check registered under an id.
     *
     * @param id an id that {@link #register(Checked)} returned
     *
     * @return the check registered under that id
     *
     * @throws IndexOutOfBoundsException if no check has that id
     */
    static Checked getChecked(int id) {
        return CHECKS.get(id);
    }

    /**
     * Records that a method has started.
     *
     * @param method the name of the method
     *
     * @throws Runaway if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void enter(String method) {
        add(new Enter(method));
    }

    /**
     * Records the value a parameter has when its method starts.
     *
     * @param name  the name of the parameter
     * @param value the value, as text
     *
     * @throws Runaway if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void param(String name, String value) {
        add(new Param(name, value));
    }

    /**
     * Records that a variable has taken a new value.
     *
     * @param name  the name of the variable
     * @param value the new value, as text
     *
     * @throws Runaway if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void set(String name, String value) {
        add(new Set(name, value));
    }

    /**
     * Records that the code on a new line of the source file is about to run.
     *
     * @param line the line number, where the first line of the file is 1
     *
     * @throws Runaway if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void line(int line) {
        add(new Line(line));
    }

    /**
     * Counts one more pass of a loop. The count covers every loop in the call together, and records no event.
     *
     * @throws Runaway if the loops have made more than {@link #MAX_LOOPS} passes since the last {@link #reset()}
     */
    public static void loop() {
        loops++;
        if (loops > MAX_LOOPS) {
            throw new Runaway(String.format("Still looping after %d passes", MAX_LOOPS));
        }
    }

    /**
     * Records that a method is about to return.
     *
     * @param value the value the method returns, as text, or {@code null} for a {@code void} method
     *
     * @throws Runaway if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void exit(String value) {
        add(new Exit(value));
    }

    /**
     * Records the answer to a check that compares two whole numbers, such as {@code i > 1}. The answer is the
     * result of {@link Condition.Check#eval(long, long)} for the two values.
     *
     * <pre>
     * check "i > 1" registered under id 3, then check(5, 1, 3)     ->  Result(3, true)
     * check "!(i > 1)" registered under id 4, then check(5, 1, 4)  ->  Result(4, false)
     * </pre>
     *
     * @param left  the value on the left of the comparison
     * @param right the value on the right of the comparison
     * @param id    the id that {@link #register(Checked)} returned for the check
     *
     * @throws IndexOutOfBoundsException if no check has that id
     * @throws Runaway                   if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void check(int left, int right, int id) {
        add(new Result(id, getChecked(id).getCheck().eval(left, right)));
    }

    /**
     * Records the answer to a check that compares one value with 0, such as {@code big % i == 0}, or a check
     * with no comparison, such as a {@code boolean} variable. The method compares {@code value} with 0.
     *
     * @param value the value compared with 0
     * @param id    the id that {@link #register(Checked)} returned for the check
     *
     * @throws IndexOutOfBoundsException if no check has that id
     * @throws Runaway                   if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void check(int value, int id) {
        check(value, 0, id);
    }

    /**
     * Records the answer to a check that compares two objects with {@code ==} or {@code !=}. The method stands
     * 0 in for the same object and 1 for two different objects, then compares that number with 0.
     *
     * @param left  the object on the left of the comparison
     * @param right the object on the right of the comparison
     * @param id    the id that {@link #register(Checked)} returned for the check
     *
     * @throws IndexOutOfBoundsException if no check has that id
     * @throws Runaway                   if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void checkSame(Object left, Object right, int id) {
        check(left == right ? 0 : 1, 0, id);
    }

    /**
     * Records the answer to a check that compares an object with {@code null}, such as {@code node != null}. The
     * method stands 0 in for {@code null} and 1 for any object, then compares that number with 0.
     *
     * @param value the object compared with {@code null}
     * @param id    the id that {@link #register(Checked)} returned for the check
     *
     * @throws IndexOutOfBoundsException if no check has that id
     * @throws Runaway                   if the call has already recorded {@link #MAX_EVENTS} events
     */
    public static void checkNull(Object value, int id) {
        check(value == null ? 0 : 1, 0, id);
    }

    // ========================================================================================== \\
    //                                           Nested                                           \\
    // ========================================================================================== \\
    /**
     * One step of a traced call. {@link #getEvents()} returns the events in the order the steps ran.
     */
    sealed interface Event permits Enter, Param, Set, Result, Line, Exit {}

    /**
     * A method started.
     *
     * @param method the name of the method
     */
    record Enter(String method)             implements Event {}

    /**
     * A parameter had a value when its method started.
     *
     * @param name  the name of the parameter
     * @param value the value, as text
     */
    record Param(String name, String value) implements Event {}

    /**
     * A variable took a new value.
     *
     * @param name  the name of the variable
     * @param value the new value, as text
     */
    record Set(String name, String value)   implements Event {}

    /**
     * A check ran.
     *
     * @param id    the id that {@link #register(Checked)} returned for the check
     * @param value the answer of the check
     */
    record Result(int id, boolean value)    implements Event {}

    /**
     * The code on a new line of the source file began to run.
     *
     * @param line the line number, where the first line of the file is 1
     */
    record Line(int line)                   implements Event {}

    /**
     * A method returned.
     *
     * @param value the value the method returned, as text, or {@code null} for a {@code void} method
     */
    record Exit(String value)               implements Event {}

    /**
     * One check of a condition, and the place in the source file where it is written.
     *
     * @param method    the name of the method that contains the check
     * @param line      the line number of the condition, where the first line of the file is 1
     * @param condition the condition the check belongs to
     * @param index     the position of the check in {@link Condition#getChecks()}, counting from 0
     */
    record Checked(String method, int line, Condition condition, int index) {

        /**
         * Builds a {@code Checked} once its index identifies one of the checks of its condition.
         *
         * @throws NullPointerException      if {@code condition} is null
         * @throws IndexOutOfBoundsException if {@code index} is below 0, or not below the number of checks in
         *                                   {@code condition}
         */
        Checked {
            Objects.checkIndex(index, condition.getChecks().size()); // Fail fast!
        }

        /**
         * Returns the check at {@link #index()} in the checks of {@link #condition()}.
         *
         * @return the check this {@code Checked} identifies
         */
        Condition.Check getCheck() {
            return condition.getChecks().get(index);
        }
    }

    /**
     * Stops a traced call whose loops make more than {@link #MAX_LOOPS} passes, or which records more than
     * {@link #MAX_EVENTS} events. {@code Runaway} extends {@link Error}, meaning a {@code catch (Exception e)} in
     * the traced code lets it pass.
     */
    static final class Runaway extends Error {

        Runaway(String message) {
            super(message);
        }
    }

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private Probe() {
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\

    /**
     * Adds one event to the events of the current call.
     *
     * @param event the event to add
     *
     * @throws Runaway if the call has already recorded {@link #MAX_EVENTS} events
     */
    private static void add(Event event) {
        if (EVENTS.size() >= MAX_EVENTS) {
            throw new Runaway(String.format("Still running after %d steps", MAX_EVENTS));
        }
        EVENTS.add(event);
    }

}
