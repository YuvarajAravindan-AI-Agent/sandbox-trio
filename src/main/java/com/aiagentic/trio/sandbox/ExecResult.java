package com.aiagentic.trio.sandbox;

/** Outcome of one code run inside an isolated sandbox. */
public record ExecResult(int exitCode, String output, String sandboxId, long millis) {
    public boolean ok() { return exitCode == 0; }
}
