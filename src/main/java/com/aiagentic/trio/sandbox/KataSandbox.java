package com.aiagentic.trio.sandbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Self-hosted sandbox: a kubernetes-sigs Agent Sandbox (Sandbox CR) on k3s whose pod runs
 * under the Kata runtime class, i.e. inside its own lightweight VM with its own kernel.
 * A default-deny NetworkPolicy in the namespace blocks all egress.
 */
@Component
@ConditionalOnProperty(name = "trio.sandbox", havingValue = "kata")
public class KataSandbox implements CodeSandbox {

    private final String namespace;
    private final String runtimeClass;
    private final String image;
    private final String apiVersion;

    public KataSandbox(@Value("${trio.kata.namespace:agent-sandboxes}") String namespace,
                       @Value("${trio.kata.runtime-class:kata-qemu}") String runtimeClass,
                       @Value("${trio.kata.image:python:3.12-slim}") String image,
                       @Value("${trio.kata.api-version:agents.x-k8s.io/v1beta1}") String apiVersion) {
        this.namespace = namespace;
        this.runtimeClass = runtimeClass;
        this.image = image;
        this.apiVersion = apiVersion;
    }

    @Override public String backend() { return "agent-sandbox/" + runtimeClass; }

    @Override
    public ExecResult run(String pythonCode, byte[] dataset) throws Exception {
        long t0 = System.currentTimeMillis();
        String name = "trio-" + UUID.randomUUID().toString().substring(0, 8);
        String manifest = """
            apiVersion: %s
            kind: Sandbox
            metadata:
              name: %s
              namespace: %s
              labels: {project: sandbox-trio, data-class: synthetic-phi}
            spec:
              podTemplate:
                metadata:
                  labels: {app: trio-sandbox}
                spec:
                  runtimeClassName: %s
                  automountServiceAccountToken: false
                  containers:
                  - name: python
                    image: %s
                    command: ["sleep", "600"]
                    securityContext: {runAsNonRoot: true, runAsUser: 1000, allowPrivilegeEscalation: false, capabilities: {drop: [ALL]}, seccompProfile: {type: RuntimeDefault}}
                    resources: {limits: {cpu: "1", memory: 512Mi}}
            """.formatted(apiVersion, name, namespace, runtimeClass, image);
        Cli.must(List.of("kubectl", "apply", "-f", "-"), manifest.getBytes(StandardCharsets.UTF_8), 30);
        try {
            Cli.must(List.of("kubectl", "-n", namespace, "wait", "--for=condition=Ready", "sandbox/" + name, "--timeout=180s"), null, 200);
            String pod = name; // the Agent Sandbox controller names the pod after the Sandbox
            Cli.must(List.of("kubectl", "-n", namespace, "exec", "-i", pod, "--", "sh", "-c", "cat > /tmp/claims.csv"), dataset, 60);
            byte[] code = ("DATA_PATH = '/tmp/claims.csv'\n" + pythonCode).getBytes(StandardCharsets.UTF_8);
            var out = Cli.run(List.of("kubectl", "-n", namespace, "exec", "-i", pod, "--", "python3", "-"), code, 120);
            return new ExecResult(out.code(), out.text(), namespace + "/" + name + " (" + runtimeClass + ")", System.currentTimeMillis() - t0);
        } finally {
            Cli.run(List.of("kubectl", "-n", namespace, "delete", "sandbox", name, "--wait=false"), null, 30);
        }
    }
}
