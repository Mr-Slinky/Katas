package com.slinky.katas.internal.tracing;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.util.function.Supplier;

final class Tracer {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    static Class<?> instrument(Class<?> type) throws IOException {
        var name     = type.getName();
        var dot      = name.lastIndexOf('.');
        var fileName = name.substring(dot + 1) + ".class";

        byte[] original;
        try (var in = type.getResourceAsStream(fileName)) {
            if (in == null) {
                throw new IOException(String.format("Could not find the compiled class '%s'", fileName));
            }
            original = in.readAllBytes();
        }

        var source    = SourceCode.find(type);
        var classFile = ClassFile.of();
        var model     = classFile.parse(original);
        var traced    = classFile.transformClass(model, (builder, element) -> {
            if (element instanceof MethodModel method && isTraceable(method)) {
                var marker    = new StepMarker(method, source);
                var transform = MethodTransform.transformingCode(marker);
                builder.transformMethod(method, transform);
            } else {
                builder.with(element);
            }
        });

        var loader = new TracedLoader(type.getClassLoader());
        return loader.define(name, traced);
    }

    static Trace record(Supplier<?> call) {
        Probe.reset();
        Object    result  = null;
        Throwable failure = null;
        try {
            result = call.get();
        } catch (RuntimeException | Probe.Runaway | StackOverflowError ex) {
            failure = ex;
        }

        var events = Probe.getEvents();
        return Trace.build(events, result, failure);
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\

    private static boolean isTraceable(MethodModel method) {
        var name          = method.methodName().stringValue();
        var hasCode       = method.code().isPresent();
        var isConstructor = name.startsWith("<");
        var isMain        = name.equals("main");
        return hasCode && !isConstructor && !isMain;
    }

    // ========================================================================================== \\
    //                                       Helper Classes                                       \\
    // ========================================================================================== \\

    /**
     * Turns the bytes of a rebuilt class into a {@code Class<?>}. The rebuilt class has the same name and code
     * as the original, <b>with a call to {@link Probe} added after each step.</b>
     */
    private static final class TracedLoader extends ClassLoader {

        TracedLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }

    }

}
