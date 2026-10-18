package com.aiagentic.trio.sandbox;

import io.daytona.sdk.Daytona;
import io.daytona.sdk.DaytonaConfig;
import io.daytona.sdk.Sandbox;
import io.daytona.sdk.model.CreateSandboxFromSnapshotParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Managed sandbox: Daytona cloud, one throwaway sandbox per run, all egress blocked. */
@Component
@ConditionalOnProperty(name = "trio.sandbox", havingValue = "daytona")
public class DaytonaSandbox implements CodeSandbox {

    private final Daytona daytona;

    public DaytonaSandbox(@Value("${trio.daytona.api-key}") String apiKey) {
        this.daytona = new Daytona(new DaytonaConfig.Builder().apiKey(apiKey).build());
    }

    @Override public String backend() { return "daytona"; }

    @Override
    public ExecResult run(String pythonCode, byte[] dataset) {
        long t0 = System.currentTimeMillis();
        var params = new CreateSandboxFromSnapshotParams();
        params.setLanguage("python");
        params.setNetworkBlockAll(true);
        params.setAutoDeleteInterval(0);
        params.setLabels(Map.of("project", "sandbox-trio", "data-class", "synthetic-phi"));
        Sandbox sb = daytona.create(params);
        try {
            String path = sb.getWorkDir() + "/claims.csv";
            sb.fs.uploadFile(dataset, path);
            var res = sb.process.codeRun("DATA_PATH = '" + path + "'\n" + pythonCode);
            int code = res.getExitCode() == null ? -1 : res.getExitCode().intValue();
            return new ExecResult(code, res.getResult(), sb.getId(), System.currentTimeMillis() - t0);
        } finally {
            sb.delete();
        }
    }
}
