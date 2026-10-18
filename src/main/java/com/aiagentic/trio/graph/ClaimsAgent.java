package com.aiagentic.trio.graph;

import com.aiagentic.trio.sandbox.CodeSandbox;
import com.aiagentic.trio.sandbox.ExecResult;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * generate -> execute -> (fix -> execute)* -> summarize
 * The LLM never sees raw rows: it gets the schema only, and its code runs in the sandbox.
 */
@Component
public class ClaimsAgent {

    private static final int MAX_ATTEMPTS = 3;
    private static final String SCHEMA = "claim_id,member_id,payer,cpt_code,billed_amount,paid_amount,status(PAID|DENIED),denial_reason,service_date(YYYY-MM-DD)";
    private static final String CODE_RULES = """
            Write a self-contained Python 3 script using only the standard library (csv, statistics, collections).
            The CSV path is already in the variable DATA_PATH. Columns: %s.
            Print a concise plain-text result. Never print member_id values. Reply with code only, no markdown fences.
            """.formatted(SCHEMA);

    private final ChatClient chat;
    private final CodeSandbox sandbox;
    private final byte[] dataset;
    private final CompiledGraph<ClaimsState> graph;

    public ClaimsAgent(ChatClient.Builder chatBuilder, CodeSandbox sandbox) throws Exception {
        this.chat = chatBuilder.build();
        this.sandbox = sandbox;
        this.dataset = new ClassPathResource("data/claims.csv").getContentAsByteArray();
        this.graph = build();
    }

    private CompiledGraph<ClaimsState> build() throws GraphStateException {
        return new StateGraph<>(ClaimsState.SCHEMA, ClaimsState::new)
                .addNode("generate", node_async(s -> {
                    String code = strip(chat.prompt().system(CODE_RULES).user(s.question()).call().content());
                    return Map.of("code", code, "audit", event("generate", "code_sha256", sha(code)));
                }))
                .addNode("execute", node_async(s -> {
                    ExecResult r = sandbox.run(s.code(), dataset);
                    return Map.of("output", r.output(), "exitCode", r.exitCode(), "attempts", s.attempts() + 1,
                            "audit", event("execute", "backend", sandbox.backend(), "sandbox", r.sandboxId(),
                                    "exit", r.exitCode(), "ms", r.millis(), "output_sha256", sha(r.output())));
                }))
                .addNode("fix", node_async(s -> {
                    String code = strip(chat.prompt().system(CODE_RULES)
                            .user("Question: " + s.question() + "\n\nThis script failed:\n" + s.code()
                                    + "\n\nError output:\n" + tail(s.output()) + "\n\nReturn a corrected script.")
                            .call().content());
                    return Map.of("code", code, "audit", event("fix", "code_sha256", sha(code)));
                }))
                .addNode("summarize", node_async(s -> {
                    String answer = chat.prompt()
                            .system("You are a claims analyst at a US health payer. Answer in 3-5 sentences using only the computed result. No member identifiers.")
                            .user("Question: " + s.question() + "\n\nComputed result (exit " + s.exitCode() + "):\n" + tail(s.output()))
                            .call().content();
                    return Map.of("answer", answer, "audit", event("summarize", "answer_sha256", sha(answer)));
                }))
                .addEdge(START, "generate")
                .addEdge("generate", "execute")
                .addConditionalEdges("execute",
                        edge_async(s -> s.exitCode() == 0 || s.attempts() >= MAX_ATTEMPTS ? "done" : "retry"),
                        Map.of("done", "summarize", "retry", "fix"))
                .addEdge("fix", "execute")
                .addEdge("summarize", END)
                .compile();
    }

    public ClaimsState ask(String question) {
        return graph.invoke(Map.of("question", question)).orElseThrow();
    }

    public String backend() { return sandbox.backend(); }

    private static Map<String, Object> event(String step, Object... kv) {
        var m = new LinkedHashMap<String, Object>();
        m.put("ts", Instant.now().toString());
        m.put("step", step);
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String strip(String s) {
        return s.replaceAll("(?s)^\\s*```(?:python)?\\s*", "").replaceAll("(?s)\\s*```\\s*$", "").trim();
    }

    private static String tail(String s) {
        return s.length() <= 4000 ? s : s.substring(s.length() - 4000);
    }

    private static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
