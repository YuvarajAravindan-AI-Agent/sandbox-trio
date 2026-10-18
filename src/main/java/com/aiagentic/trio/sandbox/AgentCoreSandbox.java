package com.aiagentic.trio.sandbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.model.CodeInterpreterResult;
import software.amazon.awssdk.services.bedrockagentcore.model.ContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InputContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterResponseHandler;
import software.amazon.awssdk.services.bedrockagentcore.model.ProgrammingLanguage;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolName;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Managed sandbox on AWS: Bedrock AgentCore Code Interpreter, one throwaway session per run.
 * The default identifier aws.codeinterpreter.v1 runs in SANDBOX network mode; point
 * trio.agentcore.identifier at a custom interpreter to pin a stricter configuration.
 */
@Component
@ConditionalOnProperty(name = "trio.sandbox", havingValue = "agentcore")
public class AgentCoreSandbox implements CodeSandbox {

    private final BedrockAgentCoreAsyncClient client;
    private final String identifier;
    private final int sessionTimeoutSec;

    public AgentCoreSandbox(@Value("${trio.agentcore.identifier:aws.codeinterpreter.v1}") String identifier,
                            @Value("${trio.agentcore.region:us-east-1}") String region,
                            @Value("${trio.agentcore.session-timeout-seconds:300}") int sessionTimeoutSec) {
        this.client = BedrockAgentCoreAsyncClient.builder().region(Region.of(region)).build();
        this.identifier = identifier;
        this.sessionTimeoutSec = sessionTimeoutSec;
    }

    @Override public String backend() { return "agentcore/" + identifier; }

    @Override
    public ExecResult run(String pythonCode, byte[] dataset) throws Exception {
        long t0 = System.currentTimeMillis();
        String sessionId = client.startCodeInterpreterSession(b -> b
                        .codeInterpreterIdentifier(identifier)
                        .name("trio-" + UUID.randomUUID().toString().substring(0, 8))
                        .sessionTimeoutSeconds(sessionTimeoutSec))
                .get(60, TimeUnit.SECONDS).sessionId();
        try {
            CodeInterpreterResult w = invoke(sessionId, b -> b.name(ToolName.WRITE_FILES)
                    .arguments(a -> a.content(InputContentBlock.builder()
                            .path("claims.csv").text(new String(dataset, StandardCharsets.UTF_8)).build())));
            if (Boolean.TRUE.equals(w.isError())) throw new IllegalStateException("writeFiles failed: " + text(w));

            CodeInterpreterResult r = invoke(sessionId, b -> b.name(ToolName.EXECUTE_CODE)
                    .arguments(a -> a.language(ProgrammingLanguage.PYTHON).clearContext(true)
                            .code("DATA_PATH = 'claims.csv'\n" + pythonCode)));
            var sc = r.structuredContent();
            int code = sc != null && sc.exitCode() != null ? sc.exitCode() : Boolean.TRUE.equals(r.isError()) ? 1 : 0;
            String out = sc != null && (sc.stdout() != null || sc.stderr() != null)
                    ? nz(sc.stdout()) + nz(sc.stderr()) : text(r);
            return new ExecResult(code, out, "agentcore/" + sessionId, System.currentTimeMillis() - t0);
        } finally {
            client.stopCodeInterpreterSession(b -> b.codeInterpreterIdentifier(identifier).sessionId(sessionId));
        }
    }

    private CodeInterpreterResult invoke(String sessionId,
            Consumer<software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterRequest.Builder> req) throws Exception {
        var result = new AtomicReference<CodeInterpreterResult>();
        var handler = InvokeCodeInterpreterResponseHandler.builder()
                .subscriber(InvokeCodeInterpreterResponseHandler.Visitor.builder().onResult(result::set).build())
                .build();
        client.invokeCodeInterpreter(b -> req.accept(b.codeInterpreterIdentifier(identifier).sessionId(sessionId)), handler)
                .get(150, TimeUnit.SECONDS);
        if (result.get() == null) throw new IllegalStateException("no result event from AgentCore");
        return result.get();
    }

    private static String text(CodeInterpreterResult r) {
        var sb = new StringBuilder();
        if (r.hasContent()) for (ContentBlock c : r.content()) if (c.text() != null) sb.append(c.text());
        return sb.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
