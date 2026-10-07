package com.slinky.katas.internal.tracing;

import java.util.Objects;

/**
 * Receives a call from the reporting copy of a class after each step, and records each call as an event
 *
 * @author Kheagen Haskins
 * @version 1.0.0
 * @since 1.0.0
 */
public class Probe {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\

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

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\

}
