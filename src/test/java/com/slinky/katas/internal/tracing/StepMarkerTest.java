package com.slinky.katas.internal.tracing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StepMarkerTest {

    private static SourceCode source;
    private static Calls      fixture;

    @BeforeAll
    static void rebuildFixture() throws IOException, ReflectiveOperationException {
        var type = StepMarkerFixture.class;
        source = SourceCode.find(type);

        byte[] original;
        try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            original = in.readAllBytes();
        }

        var classFile = ClassFile.of();
        var model     = classFile.parse(original);
        var rebuilt   = classFile.transformClass(model, (builder, element) -> {
            if (element instanceof MethodModel method && isMarkable(method)) {
                var marker = new StepMarker(method, source);
                builder.transformMethod(method, MethodTransform.transformingCode(marker));
            } else {
                builder.with(element);
            }
        });

        var loader      = new FixtureLoader(StepMarkerTest.class.getClassLoader());
        var copy        = loader.define(type.getName(), rebuilt);
        var constructor = copy.getDeclaredConstructor();
        fixture = (Calls) constructor.newInstance();
    }

    @BeforeEach
    void resetProbe() {
        Probe.reset();
    }

    public interface Calls {

        int gcd(int a, int b);

        int sumTo(int n);

        double average(long total, int count);

        boolean isLetter(char symbol);

        String shout(String word);

        void store(int value);

        boolean isSame(Object left, Object right);

        boolean isMissing(Object value);

        boolean isBetween(int x, int low, int high);

        boolean isPositive(int x);

        int countUp(int times);

    }

    @Test
    void testAccept_withLoopingMethod_RecordsEveryStepInOrder() {
        var whileLine     = findLine("while (b != 0)");
        var remainderLine = findLine("int remainder = a % b;");
        var aLine         = findLine("a = b;");
        var bLine         = findLine("b = remainder;");
        var closingLine   = bLine + 1;
        var returnLine    = findLine("return a;");

        var result = fixture.gcd(18, 12);

        var events = Probe.getEvents();
        var id     = filterEvents(Probe.Result.class).getFirst().id();
        var check  = Probe.getChecked(id).getCheck();
        var expected = List.of(new Probe.Enter("gcd"),
                               new Probe.Param("a", "18"),
                               new Probe.Param("b", "12"),
                               new Probe.Line(whileLine),
                               new Probe.Result(id, true),
                               new Probe.Line(remainderLine),
                               new Probe.Set("remainder", "6"),
                               new Probe.Line(aLine),
                               new Probe.Set("a", "12"),
                               new Probe.Line(bLine),
                               new Probe.Set("b", "6"),
                               new Probe.Line(closingLine),
                               new Probe.Line(whileLine),
                               new Probe.Result(id, true),
                               new Probe.Line(remainderLine),
                               new Probe.Set("remainder", "0"),
                               new Probe.Line(aLine),
                               new Probe.Set("a", "6"),
                               new Probe.Line(bLine),
                               new Probe.Set("b", "0"),
                               new Probe.Line(closingLine),
                               new Probe.Line(whileLine),
                               new Probe.Result(id, false),
                               new Probe.Line(returnLine),
                               new Probe.Exit("6"));
        assertAll(() -> assertEquals(6, result),
                  () -> assertEquals("b != 0", check.text()),
                  () -> assertEquals(expected, events));
    }

    @ParameterizedTest
    @MethodSource("provideParamCases")
    void testAtStart_withPrimitiveAndReferenceParams_RecordsPrimitiveParams(Consumer<Calls> call,
                                                                            List<Probe.Param> expected) {
        call.accept(fixture);

        assertEquals(expected, filterEvents(Probe.Param.class));
    }

    static Stream<Arguments> provideParamCases() {
        return Stream.of(Arguments.of(name("long and int on an instance method", calls -> calls.average(5, 2)),
                                      List.of(new Probe.Param("total", "5"), new Probe.Param("count", "2"))),
                         Arguments.of(name("int on an instance and a static method", calls -> calls.sumTo(3)),
                                      List.of(new Probe.Param("n", "3"), new Probe.Param("n", "3"))),
                         Arguments.of(name("char", calls -> calls.isLetter('x')),
                                      List.of(new Probe.Param("symbol", "x"))),
                         Arguments.of(name("String", calls -> calls.shout("bo")),
                                      List.of()));
    }

    @Test
    void testAtStart_withNestedCall_RecordsEnterForEachMethod() {
        fixture.sumTo(3);

        var expected = List.of(new Probe.Enter("sumTo"), new Probe.Enter("sumStatic"));
        assertEquals(expected, filterEvents(Probe.Enter.class));
    }

    @ParameterizedTest
    @MethodSource("provideSetCases")
    void testAccept_withStoredValues_RecordsPrimitiveSets(Consumer<Calls> call, List<Probe.Set> expected) {
        call.accept(fixture);

        assertEquals(expected, filterEvents(Probe.Set.class));
    }

    static Stream<Arguments> provideSetCases() {
        var sumSets = List.of(new Probe.Set("total", "0"),
                              new Probe.Set("i", "1"),
                              new Probe.Set("total", "1"),
                              new Probe.Set("i", "2"),
                              new Probe.Set("total", "3"),
                              new Probe.Set("i", "3"),
                              new Probe.Set("total", "6"),
                              new Probe.Set("i", "4"));
        return Stream.of(Arguments.of(name("stores and increments in a for loop", calls -> calls.sumTo(3)),
                                      sumSets),
                         Arguments.of(name("boolean", calls -> calls.isLetter('x')),
                                      List.of(new Probe.Set("letter", "true"))),
                         Arguments.of(name("int in a void method", calls -> calls.store(7)),
                                      List.of(new Probe.Set("copy", "7"))),
                         Arguments.of(name("String", calls -> calls.shout("bo")),
                                      List.of()));
    }

    @ParameterizedTest
    @MethodSource("provideExitCases")
    void testAccept_withReturn_RecordsExitValues(Consumer<Calls> call, List<Probe.Exit> expected) {
        call.accept(fixture);

        assertEquals(expected, filterEvents(Probe.Exit.class));
    }

    static Stream<Arguments> provideExitCases() {
        return Stream.of(Arguments.of(name("int", calls -> calls.gcd(18, 12)),
                                      List.of(new Probe.Exit("6"))),
                         Arguments.of(name("int from a nested call", calls -> calls.sumTo(3)),
                                      List.of(new Probe.Exit("6"), new Probe.Exit("6"))),
                         Arguments.of(name("double", calls -> calls.average(5, 2)),
                                      List.of(new Probe.Exit("2.5"))),
                         Arguments.of(name("boolean", calls -> calls.isLetter('x')),
                                      List.of(new Probe.Exit("true"))),
                         Arguments.of(name("String", calls -> calls.shout("bo")),
                                      List.of(new Probe.Exit("BO"))),
                         Arguments.of(name("void", calls -> calls.store(7)),
                                      List.of(new Probe.Exit(null))));
    }

    @ParameterizedTest
    @MethodSource("provideCheckCases")
    void testAccept_withConditionalJumps_RecordsCheckResults(Consumer<Calls> call, List<String> expected) {
        call.accept(fixture);

        assertEquals(expected, describeResults());
    }

    static Stream<Arguments> provideCheckCases() {
        return Stream.of(Arguments.of(name("int against int", calls -> calls.sumTo(2)),
                                      List.of("i <= n -> true", "i <= n -> true", "i <= n -> false")),
                         Arguments.of(name("int against 0", calls -> calls.gcd(4, 2)),
                                      List.of("b != 0 -> true", "b != 0 -> false")),
                         Arguments.of(name("same object", calls -> {
                                          var object = new Object();
                                          calls.isSame(object, object);
                                      }),
                                      List.of("left == right -> true")),
                         Arguments.of(name("different objects", calls -> calls.isSame(new Object(), new Object())),
                                      List.of("left == right -> false")),
                         Arguments.of(name("null", calls -> calls.isMissing(null)),
                                      List.of("value == null -> true")),
                         Arguments.of(name("object against null", calls -> calls.isMissing("x")),
                                      List.of("value == null -> false")),
                         Arguments.of(name("both checks of an && condition", calls -> calls.isBetween(5, 1, 10)),
                                      List.of("low < x -> true", "x < high -> true")),
                         Arguments.of(name("first check of an && condition", calls -> calls.isBetween(0, 1, 10)),
                                      List.of("low < x -> false")),
                         Arguments.of(name("more jumps than checks on a line", calls -> calls.isPositive(3)),
                                      List.of()));
    }

    @Test
    void testAccept_withForwardJumpsInLoop_NoThrow() {
        var passes = Probe.MAX_LOOPS - 200;

        assertDoesNotThrow(() -> fixture.countUp(passes));
    }

    @Test
    void testAccept_withLoopPastLimit_ThrowsRunaway() {
        var passes = Probe.MAX_LOOPS + 1;

        assertThrows(Probe.Runaway.class, () -> fixture.countUp(passes));
    }

    private static boolean isMarkable(MethodModel method) {
        var name = method.methodName().stringValue();
        return method.code().isPresent() && !name.startsWith("<");
    }

    private static Named<Consumer<Calls>> name(String label, Consumer<Calls> call) {
        return Named.of(label, call);
    }

    private static int findLine(String text) {
        for (var number = 1; number <= source.getLineCount(); number++) {
            var line = source.readLine(number).strip();
            if (line.startsWith(text)) {
                return number;
            }
        }
        throw new IllegalArgumentException(String.format("No line of StepMarkerFixture.java starts with '%s'", text));
    }

    private static <T extends Probe.Event> List<T> filterEvents(Class<T> type) {
        return Probe.getEvents()
                    .stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
    }

    private static List<String> describeResults() {
        var descriptions = new ArrayList<String>();
        for (var result : filterEvents(Probe.Result.class)) {
            var check = Probe.getChecked(result.id()).getCheck();
            descriptions.add(check.text() + " -> " + result.value());
        }
        return descriptions;
    }

    private static final class FixtureLoader extends ClassLoader {

        FixtureLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }

    }

}
