# CoreGuard Swarm Architecture

CoreGuard uses multi-agent swarms where they add leverage, and keeps
latency-critical RASP on the native path. This document is the decision
matrix for when a swarm is appropriate.

## Use-case matrix

| Use case | Is swarm recommended? | Best implementation |
|----------|----------------------|---------------------|
| Code auditing & security CI/CD | **Highly recommended** | Lightweight Python agents (MASVS, static vuln, RASP/ASM audit) under `scripts/agents/`, orchestrated by [`.github/workflows/security-swarm.yml`](../.github/workflows/security-swarm.yml) and gated by [`scripts/security_gate.py`](../scripts/security_gate.py). |
| Server-side threat intelligence | **Recommended** | Multi-agent routines on a backend that correlate attack patterns across installations (STIX/IOC feeds, Quilla-style evidence correlation). Keep this off the hot path of the Android process. |
| Real-time on-device RASP | **Not recommended for LLM swarms** | Keep core execution blocking and integrity probes in native C++ (`app/src/main/cpp/tamperguard.cpp`). Limit on-device agents to offline / background log analysis and signal handoff — never put an LLM or heavy model in the microsecond RASP path. |

## How CoreGuard maps today

### 1. Code auditing & security CI/CD (shipped)

Four CI agents run on every pull request and push to `main`:

1. **MASVS Compliance** — `scripts/agents/masvs_agent.py`
2. **Static Vulnerability Scan** — `scripts/agents/vuln_scan_agent.py`
3. **RASP / ASM Audit** — `scripts/agents/rasp_audit_agent.py`
4. **PR Gatekeeper** — `scripts/security_gate.py` (aggregates JSON → Markdown, fails on any `FAIL`)

The gate writes a durable `gate-summary.md` for the PR comment and appends the
same content to `$GITHUB_STEP_SUMMARY`. Do not read `$GITHUB_STEP_SUMMARY` from a
later step for comments — Actions harvests that file between steps.

### 2. Server-side threat intelligence (recommended direction)

Cross-device correlation belongs on a backend, not inside the APK:

- Aggregate anonymized signals / IOC hits across installations
- Correlate with STIX / Amnesty-style feeds already consumed on-device
- Feed refined indicators back to clients
- Optional hypothesis refine loop: `scripts/agents/quilla_hypothesis_evaluator.py`
  (local deterministic by default; LangGraph+OpenAI only with `OPENAI_API_KEY` + `--llm`)

On-device path: swarm signals → signed `TelemetryDelta` (`com.coreguard.security.telemetry`)
stored in an in-memory ring for Quilla — **no automatic upload**, **no on-device LLM**.

On-device Quilla / Nemesis components remain local evidence helpers; they are not
a substitute for fleet-wide TI swarming.

### 3. Real-time on-device RASP (native, not LLM)

| Layer | Role | Location |
|-------|------|----------|
| Native TamperGuard | Microsecond-path probes: Frida ports, hook libs, tracer PID, text integrity, ptrace protect | `app/src/main/cpp/tamperguard.cpp` via `NativeTamperGuard` |
| Kotlin swarm agents | Background polling, signal broadcast, peer handoff for isolation / escalation | `app/src/main/java/com/coldboar/coreguard/swarm/` (`SwarmModule` façade) |
| Redux-style alert Counter | UI-facing WARN+ count separated from Compose | `SwarmAlertCounterStore` + `SwarmAlertCounter` |

Rules of engagement for on-device agents:

- **No LLMs** in the RASP or swarm hot path
- Agents must be single-purpose, injectable, and unit-testable on the JVM
- Polling intervals stay in the seconds range (background analysis), not busy-loops
- Blocking / hard-fail decisions that must beat a hook race stay in native code
- Swarm handoff coordinates peers after a native signal; it does not replace native detection

## Anti-patterns

- Calling cloud LLM APIs from a RASP check or JNI bridge
- Moving Frida / maps / ptrace detection exclusively into Kotlin
- Treating CI swarm FAIL findings as optional noise on security-sensitive PRs
- Building “smart” on-device model inference for real-time blocking

## Related files

- Workflow: `.github/workflows/security-swarm.yml`
- Gate script: `scripts/security_gate.py`
- CI agents: `scripts/agents/`
- On-device swarm: `app/src/main/java/com/coldboar/coreguard/swarm/`
- Native RASP: `app/src/main/cpp/tamperguard.cpp`
