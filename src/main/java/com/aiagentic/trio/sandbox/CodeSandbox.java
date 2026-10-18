package com.aiagentic.trio.sandbox;

/**
 * Runs untrusted, LLM-written Python against a dataset in an isolated sandbox.
 * The dataset path is exposed to the code as the variable DATA_PATH.
 */
public interface CodeSandbox {
    String backend();
    ExecResult run(String pythonCode, byte[] dataset) throws Exception;
}
