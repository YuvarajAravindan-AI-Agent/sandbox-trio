package com.aiagentic.trio.sandbox;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Minimal process runner for the CLI-driven sandboxes (kubectl, openshell). */
final class Cli {
    record Out(int code, String text) {}

    static Out run(List<String> cmd, byte[] stdin, long timeoutSec) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (var in = p.getOutputStream()) {
            if (stdin != null) in.write(stdin);
        }
        var buf = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try { p.getInputStream().transferTo(buf); } catch (Exception ignored) { }
        });
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return new Out(124, "timeout after " + timeoutSec + "s");
        }
        reader.join();
        return new Out(p.exitValue(), buf.toString(StandardCharsets.UTF_8));
    }

    static Out must(List<String> cmd, byte[] stdin, long timeoutSec) throws Exception {
        Out o = run(cmd, stdin, timeoutSec);
        if (o.code() != 0) throw new IllegalStateException(String.join(" ", cmd) + " -> " + o.code() + ": " + o.text());
        return o;
    }
}
