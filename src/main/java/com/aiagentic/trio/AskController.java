package com.aiagentic.trio;

import com.aiagentic.trio.graph.ClaimsAgent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AskController {

    private final ClaimsAgent agent;

    public AskController(ClaimsAgent agent) { this.agent = agent; }

    public record Ask(String question) {}

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Ask req) {
        var s = agent.ask(req.question());
        var out = new LinkedHashMap<String, Object>();
        out.put("backend", agent.backend());
        out.put("attempts", s.attempts());
        out.put("answer", s.answer());
        out.put("code", s.code());
        out.put("sandboxOutput", s.output());
        out.put("audit", s.audit());
        return out;
    }
}
