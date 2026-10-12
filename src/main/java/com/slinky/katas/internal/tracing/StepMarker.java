package com.slinky.katas.internal.tracing;

import java.lang.classfile.Attributes;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_char;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;

final class StepMarker implements CodeTransform {

    // ========================================================================================== \\
    //                                           Static                                           \\
    // ========================================================================================== \\
    private static final ClassDesc PROBE = ClassDesc.of(Probe.class.getName());

    private static final MethodTypeDesc TAKES_NOTHING     = MethodTypeDesc.of(CD_void);
    private static final MethodTypeDesc TAKES_INT         = MethodTypeDesc.of(CD_void, CD_int);
    private static final MethodTypeDesc TAKES_STRING      = MethodTypeDesc.of(CD_void, CD_String);
    private static final MethodTypeDesc TAKES_TWO_STRINGS = MethodTypeDesc.of(CD_void, CD_String, CD_String);
    private static final MethodTypeDesc TAKES_TWO_INTS    = MethodTypeDesc.of(CD_void, CD_int, CD_int);
    private static final MethodTypeDesc TAKES_THREE_INTS  = MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int);

    private static final MethodTypeDesc TAKES_OBJECT_AND_INT      = MethodTypeDesc.of(CD_void, CD_Object, CD_int);
    private static final MethodTypeDesc TAKES_TWO_OBJECTS_AND_INT = MethodTypeDesc.of(CD_void, CD_Object, CD_Object, CD_int);

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final String                  methodName;
    private final MethodTypeDesc          methodType;
    private final boolean                 isStatic;
    private final List<LocalVariableInfo> variables;
    private final Set<Label>              bound = new HashSet<>();
    private final List<Integer>           checkIds;

    private int pc;
    private int pendingLine = -1;
    private int nextCheck;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    StepMarker(MethodModel method, SourceCode source) {
        methodName = method.methodName().stringValue();
        methodType = method.methodTypeSymbol();
        isStatic   = method.flags().has(AccessFlag.STATIC);
        variables  = method.code()
                           .flatMap(code -> code.findAttribute(Attributes.localVariableTable()))
                           .map(LocalVariableTableAttribute::localVariables)
                           .orElse(List.of());
        checkIds   = matchChecks(method, source);
    }

    // ========================================================================================== \\
    //                                        API Methods                                         \\
    // ========================================================================================== \\
    @Override
    public void atStart(CodeBuilder builder) {
        builder.loadConstant(methodName);
        builder.invokestatic(PROBE, "enter", TAKES_STRING);

        var slot = isStatic ? 0 : 1;
        for (var parameter : methodType.parameterList()) {
            var kind = TypeKind.from(parameter);
            if (kind != TypeKind.REFERENCE) {
                var name = findName(slot, 0, 0);
                builder.loadConstant(name);
                builder.loadLocal(kind, slot);
                convertToText(builder, parameter.descriptorString());
                builder.invokestatic(PROBE, "param", TAKES_TWO_STRINGS);
            }
            slot += kind.slotSize();
        }
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
        var start = pc;
        if (element instanceof Instruction instruction) {
            pc += instruction.sizeInBytes();
            reportPendingLine(builder);
        }

        switch (element) {
            case LabelTarget target -> {
                bound.add(target.label());
                builder.with(element);
            }
            case LineNumber line -> {
                pendingLine = line.line();
                builder.with(element);
            }
            case StoreInstruction store -> {
                builder.with(element);
                reportValue(builder, store.slot(), store.typeKind(), start);
            }
            case IncrementInstruction increment -> {
                builder.with(element);
                reportValue(builder, increment.slot(), TypeKind.INT, start);
            }
            case BranchInstruction branch -> {
                if (isConditional(branch)) {
                    var id = checkIds.get(nextCheck++);
                    if (id >= 0) {
                        reportCheck(builder, branch.opcode(), id);
                    }
                }
                if (bound.contains(branch.target())) {
                    builder.invokestatic(PROBE, "loop", TAKES_NOTHING);
                }
                builder.with(element);
            }
            case ReturnInstruction result -> {
                reportReturn(builder, result.typeKind());
                builder.with(element);
            }
            default -> builder.with(element);
        }
    }

    // ========================================================================================== \\
    //                                       Helper Methods                                       \\
    // ========================================================================================== \\
    // Returns true for a jump that depends on a comparison, and false for GOTO and GOTO_W, which always jump
    private static boolean isConditional(BranchInstruction branch) {
        var opcode = branch.opcode();
        return opcode != Opcode.GOTO && opcode != Opcode.GOTO_W;
    }

    // Adds a call to the String.valueOf that suits the descriptor, which turns the value on top of the stack into text
    private static void convertToText(CodeBuilder builder, String descriptor) {
        var argument = switch (descriptor.charAt(0)) {
            case 'Z' -> CD_boolean;
            case 'C' -> CD_char;
            case 'B', 'S', 'I' -> CD_int;
            case 'J' -> CD_long;
            case 'F' -> CD_float;
            case 'D' -> CD_double;
            default  -> CD_Object;
        };
        var valueOf = MethodTypeDesc.of(CD_String, argument);
        builder.invokestatic(CD_String, "valueOf", valueOf);
    }

    // Returns the variable that uses the slot between the positions start and end, or null when the table has none
    private LocalVariableInfo findVariable(int slot, int start, int end) {
        for (var variable : variables) {
            var scopeStart = variable.startPc();
            var scopeEnd   = scopeStart + variable.length();
            if (variable.slot() == slot && scopeStart <= end && start < scopeEnd) {
                return variable;
            }
        }
        return null;
    }

    // Returns the name of the variable in the slot, or "var" followed by the slot number when the table has none
    private String findName(int slot, int start, int end) {
        var variable = findVariable(slot, start, end);
        return variable == null ? "var" + slot : variable.name().stringValue();
    }

    // Returns the type descriptor of the variable in the slot, or the one that suits kind when the table has none
    private String findDescriptor(int slot, TypeKind kind, int start, int end) {
        var variable = findVariable(slot, start, end);
        if (variable != null) {
            return variable.type().stringValue();
        }

        return switch (kind) {
            case LONG   -> "J";
            case FLOAT  -> "F";
            case DOUBLE -> "D";
            default     -> "I";
        };
    }

    // Adds a call to Probe.line for the line that has started, when one is waiting to be reported
    private void reportPendingLine(CodeBuilder builder) {
        if (pendingLine < 0) return;

        builder.loadConstant(pendingLine);
        builder.invokestatic(PROBE, "line", TAKES_INT);
        pendingLine = -1;
    }

    // Adds a call to Probe.set with the name and new value of the variable in the slot, for primitive variables only
    private void reportValue(CodeBuilder builder, int slot, TypeKind kind, int start) {
        if (kind == TypeKind.REFERENCE) return;

        var name       = findName(slot, start, pc);
        var descriptor = findDescriptor(slot, kind, start, pc);
        builder.loadConstant(name);
        builder.loadLocal(kind, slot);
        convertToText(builder, descriptor);
        builder.invokestatic(PROBE, "set", TAKES_TWO_STRINGS);
    }

    // Adds a call to Probe.exit with the text of the value being returned, or null for a void method
    private void reportReturn(CodeBuilder builder, TypeKind kind) {
        var descriptor = methodType.returnType().descriptorString();
        switch (kind) {
            case VOID -> builder.aconst_null();
            case LONG, DOUBLE -> {
                builder.dup2();
                convertToText(builder, descriptor);
            }
            default -> {
                builder.dup();
                convertToText(builder, descriptor);
            }
        }
        builder.invokestatic(PROBE, "exit", TAKES_STRING);
    }

    // Adds a call to Probe with copies of the values that the jump compares and the id of its check
    private void reportCheck(CodeBuilder builder, Opcode opcode, int id) {
        switch (opcode) {
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                builder.dup2();
                builder.loadConstant(id);
                builder.invokestatic(PROBE, "check", TAKES_THREE_INTS);
            }
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                builder.dup();
                builder.loadConstant(id);
                builder.invokestatic(PROBE, "check", TAKES_TWO_INTS);
            }
            case IF_ACMPEQ, IF_ACMPNE -> {
                builder.dup2();
                builder.loadConstant(id);
                builder.invokestatic(PROBE, "checkSame", TAKES_TWO_OBJECTS_AND_INT);
            }
            case IFNULL, IFNONNULL -> {
                builder.dup();
                builder.loadConstant(id);
                builder.invokestatic(PROBE, "checkNull", TAKES_OBJECT_AND_INT);
            }
            default -> { }
        }
    }

    private List<Integer> matchChecks(MethodModel method, SourceCode source) {
        // Records the line number of each conditional jump, in the order the jumps appear
        var lines = new ArrayList<Integer>();
        var line  = -1;
        for (var element : method.code().orElseThrow()) {
            if (element instanceof LineNumber number) {
                line = number.line();
            } else if (element instanceof BranchInstruction branch && isConditional(branch)) {
                lines.add(line);
            }
        }

        // Gives every jump the id -1, which means it has no matching check
        var ids = new ArrayList<Integer>();
        for (var i = 0; i < lines.size(); i++) {
            ids.add(-1);
        }

        // Pairs the jumps on each line with the checks written on that line, in order
        for (var number : new TreeSet<>(lines)) {
            // Lists every check on the line, and skips the line when the checks and jumps differ in number
            var written = new ArrayList<Probe.Checked>();
            for (var condition : source.findConditionsOn(number)) {
                var checkCount = condition.getChecks().size();
                for (var c = 0; c < checkCount; c++) {
                    written.add(new Probe.Checked(methodName, number, condition, c));
                }
            }

            var writtenCount = written.size();
            var jumpCount    = Collections.frequency(lines, number);
            if (writtenCount != jumpCount) continue;

            // Registers each check with Probe and stores its id at the position of the matching jump
            var next = 0;
            for (var i = 0; i < lines.size(); i++) {
                if (lines.get(i).equals(number)) {
                    var checked = written.get(next++);
                    var id      = Probe.register(checked);
                    ids.set(i, id);
                }
            }
        }
        return ids;
    }

}
