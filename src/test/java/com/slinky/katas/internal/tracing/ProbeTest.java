package com.slinky.katas.internal.tracing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeTest {

    @BeforeEach
    void resetProbe() {
        Probe.reset();
    }

    @Test
    void testRegister_withValidArgs_ReturnsConsecutiveIds() {
        var first  = buildChecked("a");
        var second = buildChecked("b");

        var firstId  = Probe.register(first);
        var secondId = Probe.register(second);

        assertAll(() -> assertEquals(firstId + 1, secondId),
                  () -> assertSame(first, Probe.getChecked(firstId)),
                  () -> assertSame(second, Probe.getChecked(secondId)));
    }

    @Test
    void testRegister_withNullArgs_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> Probe.register(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MAX_VALUE})
    void testGetChecked_withInvalidArgs_ThrowsIndexOutOfBoundsException(int id) {
        assertThrows(IndexOutOfBoundsException.class, () -> Probe.getChecked(id));
    }

    @Test
    void testReset_withRegisteredCheck_KeepsCheck() {
        var checked = buildChecked("a");
        var id      = Probe.register(checked);

        Probe.reset();

        assertSame(checked, Probe.getChecked(id));
    }

    @Test
    void testReset_withRecordedEvents_ClearsEvents() {
        Probe.enter("gcd");
        Probe.line(3);

        Probe.reset();

        assertTrue(Probe.getEvents().isEmpty());
    }

    @Test
    void testReset_withLoopLimitReached_ClearsLoopCount() {
        for (var i = 0; i < Probe.MAX_LOOPS; i++) {
            Probe.loop();
        }

        Probe.reset();

        assertDoesNotThrow(Probe::loop);
    }

    @Test
    void testGetEvents_withRecordedSteps_ReturnsEventsInOrder() {
        var id = Probe.register(buildChecked("big != 0"));
        var expected = List.of(new Probe.Enter("gcd"),
                               new Probe.Param("big", "18"),
                               new Probe.Line(7),
                               new Probe.Result(id, true),
                               new Probe.Set("big", "6"),
                               new Probe.Exit("6"));

        Probe.enter("gcd");
        Probe.param("big", "18");
        Probe.line(7);
        Probe.check(18, id);
        Probe.set("big", "6");
        Probe.exit("6");

        assertEquals(expected, Probe.getEvents());
    }

    @Test
    void testGetEvents_withLoopPasses_ReturnsNoEvents() {
        Probe.loop();
        Probe.loop();

        var actual = Probe.getEvents();

        assertTrue(actual.isEmpty());
    }

    @Test
    void testGetEvents_withLaterStep_ReturnsUnchangedCopy() {
        Probe.enter("gcd");
        var events = Probe.getEvents();

        Probe.line(3);

        assertEquals(List.of(new Probe.Enter("gcd")), events);
    }

    @Test
    void testGetEvents_withValidArgs_ThrowsUnsupportedOperationException() {
        var events = Probe.getEvents();
        var extra  = new Probe.Line(1);

        assertThrows(UnsupportedOperationException.class, () -> events.add(extra));
    }

    @Test
    void testLoop_withPassesAtLimit_NoThrow() {
        for (var i = 0; i < Probe.MAX_LOOPS - 1; i++) {
            Probe.loop();
        }

        assertDoesNotThrow(Probe::loop);
    }

    @Test
    void testLoop_withPassesPastLimit_ThrowsRunaway() {
        for (var i = 0; i < Probe.MAX_LOOPS; i++) {
            Probe.loop();
        }

        assertThrows(Probe.Runaway.class, Probe::loop);
    }

    @ParameterizedTest
    @MethodSource("provideRecordingSteps")
    void testRecordingStep_withEventsBelowLimit_NoThrow(Executable step) {
        for (var i = 0; i < Probe.MAX_EVENTS - 1; i++) {
            Probe.line(i);
        }

        assertDoesNotThrow(step);
    }

    @ParameterizedTest
    @MethodSource("provideRecordingSteps")
    void testRecordingStep_withEventLimitReached_ThrowsRunaway(Executable step) {
        for (var i = 0; i < Probe.MAX_EVENTS; i++) {
            Probe.line(i);
        }

        assertThrows(Probe.Runaway.class, step);
    }

    static Stream<Arguments> provideRecordingSteps() {
        return Stream.of(Arguments.of(Named.of("enter", (Executable) () -> Probe.enter("gcd"))),
                         Arguments.of(Named.of("param", (Executable) () -> Probe.param("big", "18"))),
                         Arguments.of(Named.of("set", (Executable) () -> Probe.set("big", "6"))),
                         Arguments.of(Named.of("line", (Executable) () -> Probe.line(7))),
                         Arguments.of(Named.of("exit", (Executable) () -> Probe.exit("6"))));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `i > 1`     | 5 | 1 | true
            `i > 1`     | 1 | 5 | false
            `!(i > 1)`  | 5 | 1 | false
            `!(i > 1)`  | 1 | 5 | true
            `x == y`    | 3 | 3 | true
            `x <= y`    | 4 | 3 | false
            """)
    void testCheck_withTwoValues_RecordsExpectedResult(String text, int left, int right, boolean expected) {
        var id = Probe.register(buildChecked(text));

        Probe.check(left, right, id);

        assertEquals(List.of(new Probe.Result(id, expected)), Probe.getEvents());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `big % i == 0` | 0 | true
            `big % i == 0` | 3 | false
            `found`        | 1 | true
            `found`        | 0 | false
            `!found`       | 1 | false
            `!found`       | 0 | true
            """)
    void testCheck_withOneValue_RecordsExpectedResult(String text, int value, boolean expected) {
        var id = Probe.register(buildChecked(text));

        Probe.check(value, id);

        assertEquals(List.of(new Probe.Result(id, expected)), Probe.getEvents());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `a == b` | true  | true
            `a == b` | false | false
            `a != b` | true  | false
            `a != b` | false | true
            """)
    void testCheckSame_withValidArgs_RecordsExpectedResult(String text, boolean isSame, boolean expected) {
        var id    = Probe.register(buildChecked(text));
        var left  = new Object();
        var right = isSame ? left : new Object();

        Probe.checkSame(left, right, id);

        assertEquals(List.of(new Probe.Result(id, expected)), Probe.getEvents());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
            `node == null` | true  | true
            `node == null` | false | false
            `node != null` | true  | false
            `node != null` | false | true
            """)
    void testCheckNull_withValidArgs_RecordsExpectedResult(String text, boolean isNull, boolean expected) {
        var id    = Probe.register(buildChecked(text));
        var value = isNull ? null : new Object();

        Probe.checkNull(value, id);

        assertEquals(List.of(new Probe.Result(id, expected)), Probe.getEvents());
    }

    @Test
    void testCheck_withUnregisteredId_ThrowsIndexOutOfBoundsException() {
        assertThrows(IndexOutOfBoundsException.class, () -> Probe.check(1, 2, -1));
    }

    @Test
    void testGetCheck_withValidArgs_ReturnsCheckAtIndex() {
        var condition = new Condition("a && b");
        var checked   = new Probe.Checked("gcd", 7, condition, 1);

        var actual = checked.getCheck();

        assertEquals(condition.getChecks().get(1), actual);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void testChecked_withInvalidArgs_ThrowsIndexOutOfBoundsException(int index) {
        var condition = new Condition("a && b");

        assertThrows(IndexOutOfBoundsException.class, () -> new Probe.Checked("gcd", 7, condition, index));
    }

    @Test
    void testChecked_withNullArgs_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Probe.Checked("gcd", 7, null, 0));
    }

    private static Probe.Checked buildChecked(String text) {
        return new Probe.Checked("gcd", 7, new Condition(text), 0);
    }

}
