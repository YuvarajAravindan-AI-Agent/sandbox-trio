package com.aiagentic.trio.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Graph state: the question, the latest generated code and its sandbox result, plus an append-only audit trail. */
public class ClaimsState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "audit", Channels.appender(ArrayList::new));

    public ClaimsState(Map<String, Object> init) { super(init); }

    public String question() { return this.<String>value("question").orElseThrow(); }
    public String code() { return this.<String>value("code").orElse(""); }
    public String output() { return this.<String>value("output").orElse(""); }
    public int exitCode() { return this.<Integer>value("exitCode").orElse(-1); }
    public int attempts() { return this.<Integer>value("attempts").orElse(0); }
    public String answer() { return this.<String>value("answer").orElse(""); }
    public List<Map<String, Object>> audit() { return this.<List<Map<String, Object>>>value("audit").orElse(List.of()); }
}
