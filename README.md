# sandbox-trio

One governed claims-analytics agent (Spring Boot 4.1.1 + Spring AI 2.0.1 + LangGraph4j 1.9.3, DeepSeek chat)
run against three code sandboxes, one EC2 VM each (account 632393508307, us-east-1, tag project=sandbox-trio):

## Current setup — GCP trial (project-287c53a0-e18a-40e8-824), from 2026-10-07

AWS EC2 version was torn down 2026-10-07; recipes below are cloud-agnostic.

| Where | Backend | Isolation (verified) | Warm run |
|---|---|---|---|
| VM `trio-kata` n2-standard-4, us-east1-b, nested virt | k3s + Agent Sandbox v1.0.5 + Kata 4.2.0 `kata-qemu` | own guest kernel, deny-all NetworkPolicy | ~3.9 s |
| GKE `trio-gke` us-central1-a, pool `gvisor` (e2-standard-4) | GKE Sandbox gVisor + Agent Sandbox v1.0.5 | gVisor userspace kernel, deny-all NetworkPolicy, scoped SA `trio-agent` | ~4–8 s |
| VM `trio-openshell` n2-standard-2, us-east1-b, nested virt | OpenShell 0.1.2, **VM driver** (`OPENSHELL_COMPUTE_DRIVER=vm` in ~/.config/openshell/gateway.env) | libkrun microVM, guest kernel 6.12 vs host 7.0, default-deny | ~7.4 s |
| (pending keys) | Daytona cloud / E2B cloud | managed | — |

The GKE run uses the same Kubernetes adapter from `trio-kata` with `~/.trio-gke.env`
(`TRIO_KATA_RUNTIMECLASS=gvisor`, `KUBECONFIG=~/.kube/gke.yaml` = 24 h token for `trio-agent`, see `deploy/agent-rbac.yaml`).
Regenerate the token daily: `kubectl -n agent-sandboxes create token trio-agent --duration=24h`.

N2 capacity was exhausted in all of us-central1 on 2026-10-07; us-east1-b worked. Trial caps: 12 vCPU global, 8 E2.
