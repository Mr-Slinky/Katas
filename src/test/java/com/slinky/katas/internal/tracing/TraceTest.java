package com.slinky.katas.internal.tracing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceTest {

    @Test
    void testBuild_withEmptyArgs_ReturnsEmptyTrace() {
        var events = List.<Probe.Event>of();

        var trace = Trace.build(events, null, null);

        assertAll(() -> assertTrue(trace.getColumns().isEmpty()),
                  () -> assertTrue(trace.getSteps().isEmpty()),
                  () -> assertEquals(0, trace.getRowCount()),
                  () -> assertEquals(0, trace.getStartSteps()),
                  () -> assertEquals(-1, trace.getLastLine()),
                  () -> assertEquals(-1, trace.getReturnLine()),
                  () -> assertFalse(trace.hasStartRow()),
                  () -> assertFalse(trace.hasConditions()),
                  () -> assertFalse(trace.isCutShort()));
    }

    @Test
    void testBuild_withResultAndFailure_ReturnsBoth() {
        var events  = List.<Probe.Event>of();
        var result  = "6";
        var failure = new ArithmeticException("/ by zero");

        var trace = Trace.build(events, result, failure);

        assertAll(() -> assertSame(result, trace.getResult()),
                  () -> assertSame(failure, trace.getFailure()));
    }

    @Test
    void testGetColumns_withGcdEvents_ReturnsOneColumnPerVariable() {
        var events   = buildGcdEvents();
        var expected = List.of(new Trace.Column("a", false),
                               new Trace.Column("b", false));

        var trace = Trace.build(events, "6", null);

        assertEquals(expected, trace.getColumns());
    }

    @Test
    void testGetSteps_withGcdEvents_ReturnsOneStepPerCell() {
        var events   = buildGcdEvents();
        var expected = List.of(new Trace.Step(-1, 0, 0, "18"),
                               new Trace.Step(-1, 0, 1, "12"),
                               new Trace.Step(3, 1, 0, "12"),
                               new Trace.Step(4, 1, 1, "6"),
                               new Trace.Step(3, 2, 0, "6"),
                               new Trace.Step(4, 2, 1, "0"));

        var trace = Trace.build(events, "6", null);

        assertEquals(expected, trace.getSteps());
    }

    @Test
    void testCountPasses_withParamsFirst_ReturnsRowsAfterStartRow() {
        var events = buildGcdEvents();

        var trace = Trace.build(events, "6", null);

        assertAll(() -> assertTrue(trace.hasStartRow()),
                  () -> assertEquals(2, trace.getStartSteps()),
                  () -> assertEquals(3, trace.getRowCount()),
                  () -> assertEquals(2, trace.countPasses()));
    }

    @Test
    void testCountPasses_withNoParams_ReturnsRowCount() {
        var events = List.<Probe.Event>of(new Probe.Enter("count"),
                                          new Probe.Line(2),
                                          new Probe.Set("x", "1"),
                                          new Probe.Line(3),
                                          new Probe.Set("x", "2"),
                                          new Probe.Exit(null));

        var trace = Trace.build(events, null, null);

        assertAll(() -> assertFalse(trace.hasStartRow()),
                  () -> assertEquals(0, trace.getStartSteps()),
                  () -> assertEquals(2, trace.getRowCount()),
                  () -> assertEquals(2, trace.countPasses()));
    }

    @Test
    void testGetSteps_withLineGoingBack_StartsNewRow() {
        var events = List.<Probe.Event>of(new Probe.Enter("count"),
                                          new Probe.Line(3),
                                          new Probe.Set("a", "1"),
                                          new Probe.Line(4),
                                          new Probe.Set("b", "2"),
                                          new Probe.Line(3),
                                          new Probe.Set("c", "3"),
                                          new Probe.Exit(null));

        var trace = Trace.build(events, null, null);

        var lastStep = trace.getSteps().getLast();
        assertAll(() -> assertEquals(2, trace.getRowCount()),
                  () -> assertEquals(new Trace.Step(3, 1, 2, "3"), lastStep));
    }

    @Test
    void testGetSteps_withLoopInHelperMethod_KeepsWorkingMethodRow() {
        var events = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                          new Probe.Param("a", "18"),
                                          new Probe.Line(3),
                                          new Probe.Set("a", "12"),
                                          new Probe.Enter("helper"),
                                          new Probe.Line(40),
                                          new Probe.Line(41),
                                          new Probe.Line(40),
                                          new Probe.Exit(null),
                                          new Probe.Line(4),
                                          new Probe.Set("b", "6"),
                                          new Probe.Exit("6"));

        var trace = Trace.build(events, "6", null);

        var lastStep = trace.getSteps().getLast();
        assertAll(() -> assertEquals(2, trace.getRowCount()),
                  () -> assertEquals(new Trace.Step(4, 1, 1, "6"), lastStep));
    }

    @Test
    void testGetSteps_withRecursiveCallRepeatingParams_StartsNewRow() {
        var events   = List.<Probe.Event>of(new Probe.Enter("fact"),
                                            new Probe.Param("n", "3"),
                                            new Probe.Enter("fact"),
                                            new Probe.Param("n", "2"),
                                            new Probe.Line(3),
                                            new Probe.Set("r", "2"),
                                            new Probe.Exit("2"),
                                            new Probe.Exit("6"));
        var expected = List.of(new Trace.Step(-1, 0, 0, "3"),
                               new Trace.Step(-1, 1, 0, "2"),
                               new Trace.Step(3, 1, 1, "2"));

        var trace = Trace.build(events, "6", null);

        assertAll(() -> assertEquals(expected, trace.getSteps()),
                  () -> assertEquals(2, trace.getRowCount()),
                  () -> assertEquals(1, trace.getStartSteps()));
    }

    @Test
    void testGetColumns_withParamOfHelperMethod_ReturnsWorkingMethodColumnsOnly() {
        var events   = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                            new Probe.Param("a", "18"),
                                            new Probe.Line(3),
                                            new Probe.Set("a", "6"),
                                            new Probe.Enter("helper"),
                                            new Probe.Param("n", "5"),
                                            new Probe.Line(40),
                                            new Probe.Exit("5"),
                                            new Probe.Exit("6"));
        var expected = List.of(new Trace.Column("a", false));

        var trace = Trace.build(events, "6", null);

        assertEquals(expected, trace.getColumns());
    }

    @Test
    void testGetColumns_withNoWorkingMethod_ReturnsParamColumns() {
        var events   = List.<Probe.Event>of(new Probe.Enter("identity"),
                                            new Probe.Param("x", "1"),
                                            new Probe.Line(2),
                                            new Probe.Exit("1"));
        var expected = List.of(new Trace.Column("x", false));

        var trace = Trace.build(events, "1", null);

        assertEquals(expected, trace.getColumns());
    }

    @Test
    void testGetColumns_withSharedVariableName_ReturnsQualifiedHeadings() {
        var events   = List.<Probe.Event>of(new Probe.Enter("outer"),
                                            new Probe.Line(2),
                                            new Probe.Set("i", "0"),
                                            new Probe.Enter("inner"),
                                            new Probe.Line(10),
                                            new Probe.Set("i", "5"),
                                            new Probe.Exit(null),
                                            new Probe.Exit(null));
        var expected = List.of(new Trace.Column("outer.i", false),
                               new Trace.Column("inner.i", false));

        var trace = Trace.build(events, null, null);

        assertEquals(expected, trace.getColumns());
    }

    @Test
    void testGetColumns_withConditionSharingVariableName_KeepsHeadings() {
        var ids      = registerChecks("search", "found");
        var events   = List.<Probe.Event>of(new Probe.Enter("search"),
                                            new Probe.Line(2),
                                            new Probe.Set("found", "1"),
                                            new Probe.Line(7),
                                            new Probe.Result(ids.getFirst(), true),
                                            new Probe.Exit(null));
        var expected = List.of(new Trace.Column("found", false),
                               new Trace.Column("found", true));

        var trace = Trace.build(events, null, null);

        assertEquals(expected, trace.getColumns());
    }

    @Test
    void testBuild_withSingleCheckCondition_ReturnsCheckColumnOnly() {
        var ids             = registerChecks("gcd", "big != 0");
        var events          = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                                   new Probe.Line(7),
                                                   new Probe.Result(ids.getFirst(), true),
                                                   new Probe.Exit(null));
        var expectedColumns = List.of(new Trace.Column("big != 0", true));
        var expectedSteps   = List.of(new Trace.Step(7, 0, 0, "T"));

        var trace = Trace.build(events, null, null);

        assertAll(() -> assertTrue(trace.hasConditions()),
                  () -> assertEquals(expectedColumns, trace.getColumns()),
                  () -> assertEquals(expectedSteps, trace.getSteps()));
    }

    @Test
    void testBuild_withBothChecksRun_ReturnsWholeConditionAnswer() {
        var ids             = registerChecks("gcd", "a > 1 && b < 2");
        var events          = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                                   new Probe.Line(7),
                                                   new Probe.Result(ids.get(0), true),
                                                   new Probe.Result(ids.get(1), false),
                                                   new Probe.Exit(null));
        var expectedColumns = List.of(new Trace.Column("a > 1", true),
                                      new Trace.Column("b < 2", true),
                                      new Trace.Column("a > 1 && b < 2", true));
        var expectedSteps   = List.of(new Trace.Step(7, 0, 0, "T"),
                                      new Trace.Step(7, 0, 1, "F"),
                                      new Trace.Step(7, 0, 2, "F"));

        var trace = Trace.build(events, null, null);

        assertAll(() -> assertEquals(expectedColumns, trace.getColumns()),
                  () -> assertEquals(expectedSteps, trace.getSteps()));
    }

    @Test
    void testGetSteps_withShortCircuitedCheck_ReturnsWholeAnswerAfterFirstCheck() {
        var ids      = registerChecks("gcd", "a > 1 && b < 2");
        var events   = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                            new Probe.Line(7),
                                            new Probe.Result(ids.get(0), false),
                                            new Probe.Exit(null));
        var expected = List.of(new Trace.Step(7, 0, 0, "F"),
                               new Trace.Step(7, 0, 2, "F"));

        var trace = Trace.build(events, null, null);

        assertEquals(expected, trace.getSteps());
    }

    @Test
    void testGetSteps_withConditionCheckedOnSecondPass_ReturnsAnswersOfSecondPass() {
        var ids      = registerChecks("gcd", "a > 1 && b < 2");
        var events   = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                            new Probe.Line(7),
                                            new Probe.Result(ids.get(0), true),
                                            new Probe.Result(ids.get(1), true),
                                            new Probe.Line(8),
                                            new Probe.Set("i", "1"),
                                            new Probe.Line(7),
                                            new Probe.Result(ids.get(0), true),
                                            new Probe.Result(ids.get(1), false),
                                            new Probe.Exit(null));
        var expected = List.of(new Trace.Step(7, 0, 0, "T"),
                               new Trace.Step(7, 0, 1, "T"),
                               new Trace.Step(7, 0, 2, "T"),
                               new Trace.Step(8, 0, 3, "1"),
                               new Trace.Step(7, 1, 0, "T"),
                               new Trace.Step(7, 1, 1, "F"),
                               new Trace.Step(7, 1, 2, "F"));

        var trace = Trace.build(events, null, null);

        assertEquals(expected, trace.getSteps());
    }

    @Test
    void testGetSteps_withSecondConditionStartedBeforeFirstFinishes_ReturnsNoWholeAnswer() {
        var firstIds  = registerChecks("gcd", "a > 1 && b < 2");
        var secondIds = registerChecks("gcd", "c > 0 && d > 0");
        var events    = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                             new Probe.Line(7),
                                             new Probe.Result(firstIds.get(0), true),
                                             new Probe.Result(secondIds.get(0), true),
                                             new Probe.Result(firstIds.get(1), true),
                                             new Probe.Exit(null));
        var expected  = List.of(new Trace.Step(7, 0, 0, "T"),
                                new Trace.Step(7, 0, 3, "T"),
                                new Trace.Step(7, 0, 1, "T"));

        var trace = Trace.build(events, null, null);

        assertEquals(expected, trace.getSteps());
    }

    @ParameterizedTest
    @CsvSource({"false, 3", "true, 40"})
    void testGetReturnLine_withHelperCalledLast_ReturnsExpectedLine(boolean hasFailure, int expected) {
        var events  = List.<Probe.Event>of(new Probe.Enter("gcd"),
                                           new Probe.Line(3),
                                           new Probe.Set("a", "6"),
                                           new Probe.Enter("helper"),
                                           new Probe.Line(40),
                                           new Probe.Exit("1"),
                                           new Probe.Exit("6"));
        var failure = hasFailure ? new IllegalStateException() : null;

        var trace = Trace.build(events, "6", failure);

        assertAll(() -> assertEquals(40, trace.getLastLine()),
                  () -> assertEquals(expected, trace.getReturnLine()));
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, true"})
    void testIsCutShort_withRowsAroundLimit_ReturnsExpectedValue(int extraRows, boolean expected) {
        var events = new ArrayList<Probe.Event>();
        events.add(new Probe.Enter("count"));
        var setCount = Trace.MAX_ROWS + extraRows;
        for (var i = 0; i < setCount; i++) {
            var value = String.valueOf(i);
            events.add(new Probe.Set("i", value));
        }

        var trace = Trace.build(events, null, null);

        assertAll(() -> assertEquals(expected, trace.isCutShort()),
                  () -> assertEquals(Trace.MAX_ROWS, trace.getRowCount()),
                  () -> assertEquals(Trace.MAX_ROWS, trace.getSteps().size()));
    }

    @Test
    void testIsCutShort_withLimitReachedPartwayThroughRow_KeepsLastRowCells() {
        var events = new ArrayList<Probe.Event>();
        events.add(new Probe.Enter("count"));
        for (var i = 0; i < Trace.MAX_ROWS; i++) {
            var value = String.valueOf(i);
            events.add(new Probe.Set("i", value));
        }
        events.add(new Probe.Set("j", "x"));
        events.add(new Probe.Set("i", "cut"));
        events.add(new Probe.Set("k", "after"));
        var lastRow  = Trace.MAX_ROWS - 1;
        var expected = new Trace.Step(-1, lastRow, 1, "x");

        var trace = Trace.build(events, null, null);

        var steps = trace.getSteps();
        assertAll(() -> assertTrue(trace.isCutShort()),
                  () -> assertEquals(Trace.MAX_ROWS, trace.getRowCount()),
                  () -> assertEquals(Trace.MAX_ROWS + 1, steps.size()),
                  () -> assertEquals(expected, steps.getLast()));
    }

    @Test
    void testGetColumns_withValidArgs_ThrowsUnsupportedOperationException() {
        var trace   = Trace.build(buildGcdEvents(), "6", null);
        var columns = trace.getColumns();
        var extra   = new Trace.Column("c", false);

        assertThrows(UnsupportedOperationException.class, () -> columns.add(extra));
    }

    @Test
    void testGetSteps_withValidArgs_ThrowsUnsupportedOperationException() {
        var trace = Trace.build(buildGcdEvents(), "6", null);
        var steps = trace.getSteps();
        var extra = new Trace.Step(1, 0, 0, "x");

        assertThrows(UnsupportedOperationException.class, () -> steps.add(extra));
    }

    private static List<Probe.Event> buildGcdEvents() {
        return List.of(new Probe.Enter("gcd"),
                       new Probe.Param("a", "18"),
                       new Probe.Param("b", "12"),
                       new Probe.Line(3),
                       new Probe.Set("a", "12"),
                       new Probe.Line(4),
                       new Probe.Set("b", "6"),
                       new Probe.Line(3),
                       new Probe.Set("a", "6"),
                       new Probe.Line(4),
                       new Probe.Set("b", "0"),
                       new Probe.Exit("6"));
    }

    private static List<Integer> registerChecks(String method, String text) {
        var condition  = new Condition(text);
        var checkCount = condition.getChecks().size();
        var ids        = new ArrayList<Integer>();
        for (var index = 0; index < checkCount; index++) {
            var checked = new Probe.Checked(method, 7, condition, index);
            ids.add(Probe.register(checked));
        }
        return ids;
    }

}
