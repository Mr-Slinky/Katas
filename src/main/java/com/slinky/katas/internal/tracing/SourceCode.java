package com.slinky.katas.internal.tracing;

import java.util.List;

/**
 * @author Kheagen Haskins
 * @version 1.0.0
 *         <p>
 *         Last modified: 2026-10-05
 * @since 1.0.0
 */
final class SourceCode {

    // ========================================================================================== \\
    //                                           Fields                                           \\
    // ========================================================================================== \\
    private final String       fileName;
    private final List<String> lines;

    // ========================================================================================== \\
    //                                       Constructor(s)                                       \\
    // ========================================================================================== \\
    private SourceCode(String fileName, List<String> lines) {
        this.fileName = fileName;
        this.lines    = lines;
    }

    // ========================================================================================== \\
    //                                          Getters                                           \\
    // ========================================================================================== \\
    String fileName() {
        return fileName;
    }

    int lineCount() {
        return lines.size();
    }

}
