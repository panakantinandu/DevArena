package com.devarena.coding;

import java.util.List;

/**
 * Seam for running candidate code in an isolated judge (e.g. a sandboxed container
 * service). DevArena never executes user code inside the API process.
 *
 * <p>The only implementation today is {@link Unavailable}, which reports that execution
 * isn't configured. The UI shows that honestly instead of pretending tests passed.
 */
public interface CodeExecutionService {

    record TestCase(String input, String expectedOutput) {}

    record ExecutionResult(boolean passed, String stdout, String stderr, long runtimeMillis) {}

    boolean isAvailable();

    String unavailableReason();

    List<ExecutionResult> run(String language, String code, List<TestCase> tests);

    @org.springframework.stereotype.Service
    class Unavailable implements CodeExecutionService {
        @Override
        public boolean isAvailable() { return false; }

        @Override
        public String unavailableReason() {
            return "Automated code execution isn't set up. Code is saved and reviewed manually.";
        }

        @Override
        public List<ExecutionResult> run(String language, String code, List<TestCase> tests) {
            throw new UnsupportedOperationException(unavailableReason());
        }
    }
}
