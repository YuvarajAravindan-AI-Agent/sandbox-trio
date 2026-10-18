package com.aiagentic.trio.sandbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * NVIDIA OpenShell: a throwaway sandbox per run behind the local OpenShell gateway.
 * OpenShell's built-in default-deny policy blocks egress and no credential providers are attached.
 */
@Component
@ConditionalOnProperty(name = "trio.sandbox", havingValue = "openshell")
public class OpenShellSandbox implements CodeSandbox {

    private final String image;

    public OpenShellSandbox(@Value("${trio.openshell.image:python:3.12-slim}") String image) {
        this.image = image;
    }

    @Override public String backend() { return "openshell"; }

    @Override
    public ExecResult run(String pythonCode, byte[] dataset) throws Exception {
        long t0 = System.currentTimeMillis();
        String name = "trio-" + UUID.randomUUID().toString().substring(0, 8);
        Cli.must(List.of("openshell", "sandbox", "create", "--name", name, "--from", image, "--detach",
                "--no-auto-providers", "--no-tty", "--label", "project=sandbox-trio", "-o", "json"), null, 300);
        try {
            Cli.must(exec(name, "sh", "-c", "cat > /tmp/claims.csv"), dataset, 60);
            byte[] code = ("DATA_PATH = '/tmp/claims.csv'\n" + pythonCode).getBytes(StandardCharsets.UTF_8);
            var out = Cli.run(exec(name, "python3", "-"), code, 120);
            return new ExecResult(out.code(), out.text(), "openshell/" + name, System.currentTimeMillis() - t0);
        } finally {
            Cli.run(List.of("openshell", "sandbox", "delete", name), null, 60);
        }
    }

    private static List<String> exec(String name, String... cmd) {
        var l = new java.util.ArrayList<>(List.of("openshell", "sandbox", "exec", "-n", name, "--no-tty", "--no-login-shell", "--timeout", "110", "--"));
        l.addAll(List.of(cmd));
        return l;
    }
}
