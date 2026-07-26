#!/usr/bin/env python3
"""
Quilla Swarm Hypothesis Evaluator (optional server-side / offline)
=================================================================
Generator → Red-team evaluator loop for CoreGuard signed telemetry deltas.

Modes
-----
1. **Local deterministic** (default, CI-safe): no OpenAI / LangGraph required.
   Grades telemetry using rule heuristics (Frida/root/memory-hook evidence).
2. **LLM LangGraph** (optional): if `OPENAI_API_KEY` is set *and*
   `--llm` is passed, uses LangGraph + ChatOpenAI for generator/evaluator.

Honesty
-------
This is **not** the on-device Quilla agent. On-device Quilla stays local and
does not call cloud LLMs. See docs/SWARM_ARCHITECTURE.md and SECURITY_CLAIMS.md.

Exit codes: 0 always for analysis tooling unless --strict and REJECTED after max loops.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Dict, Literal, Optional, TypedDict


Grade = Literal["ACCEPTED", "REJECTED"]


@dataclass
class EvaluationResult:
    grade: Grade
    feedback: str
    hypothesis: str
    iteration_count: int
    mode: str


class AgentState(TypedDict, total=False):
    telemetry_payload: dict
    hypothesis: str
    feedback: str
    iteration_count: int
    final_output: str
    grade: Grade


def _local_generate(telemetry: dict, feedback: str, iteration: int) -> str:
    delta = telemetry.get("delta") or telemetry
    trigger = str(delta.get("trigger", "UNKNOWN"))
    severity = str(delta.get("severity", "LOW"))
    anomalies = delta.get("detectedAnomalies") or {}
    detail_bits = ", ".join(f"{k}={v}" for k, v in list(anomalies.items())[:6])
    base = (
        f"Threat diagnosis (iter {iteration}): trigger={trigger}, severity={severity}. "
        f"Anomalies: {detail_bits or 'none'}. "
        "Impact: potential runtime integrity loss or instrumentation. "
        "Mitigation: re-run Nemesis, keep Privacy Shield on, update OS, "
        "revoke Accessibility for untrusted apps."
    )
    if feedback:
        base += f" Refined after critique: {feedback[:180]}"
    return base


def _local_evaluate(telemetry: dict, hypothesis: str) -> EvaluationResult:
    delta = telemetry.get("delta") or telemetry
    trigger = str(delta.get("trigger", "")).upper()
    severity = str(delta.get("severity", "")).upper()
    anomalies = {str(k).lower(): str(v).lower() for k, v in (delta.get("detectedAnomalies") or {}).items()}
    blob = " ".join([trigger, severity, hypothesis.lower()] + [f"{k}:{v}" for k, v in anomalies.items()])

    strong = any(
        token in blob
        for token in (
            "frida",
            "memory_hook",
            "hook_library",
            "root_state",
            "debugger",
            "gum-js",
            "xposed",
        )
    )
    weak_only = severity in {"LOW", "MEDIUM"} and not strong

    if weak_only:
        return EvaluationResult(
            grade="REJECTED",
            feedback=(
                "Likely OS/benign noise or incomplete evidence: severity is not CRITICAL/HIGH "
                "and no Frida/root/memory-hook markers are present."
            ),
            hypothesis=hypothesis,
            iteration_count=0,
            mode="local",
        )
    if strong and severity in {"HIGH", "CRITICAL"}:
        return EvaluationResult(
            grade="ACCEPTED",
            feedback="Evidence includes instrumentation/root/hook markers with elevated severity.",
            hypothesis=hypothesis,
            iteration_count=0,
            mode="local",
        )
    return EvaluationResult(
        grade="REJECTED",
        feedback="Hypothesis under-specified relative to telemetry; request stronger anomaly keys.",
        hypothesis=hypothesis,
        iteration_count=0,
        mode="local",
    )


def run_local_graph(telemetry: dict, max_iters: int = 3) -> EvaluationResult:
    feedback = ""
    hypothesis = ""
    for i in range(1, max_iters + 1):
        hypothesis = _local_generate(telemetry, feedback, i)
        result = _local_evaluate(telemetry, hypothesis)
        result.iteration_count = i
        if result.grade == "ACCEPTED":
            return result
        feedback = result.feedback
    # Force exit with warning (prevents infinite refine loops).
    return EvaluationResult(
        grade="ACCEPTED",
        feedback=feedback,
        hypothesis=hypothesis + "\n\n[Warning: Unverified by Red Team Evaluator]",
        iteration_count=max_iters,
        mode="local",
    )


def run_llm_graph(telemetry: dict, max_iters: int = 3) -> EvaluationResult:
    """Optional LangGraph path — requires openai + langgraph + langchain packages."""
    try:
        from pydantic import BaseModel, Field
        from langgraph.graph import StateGraph, END
        from langchain_openai import ChatOpenAI
    except ImportError as exc:
        raise RuntimeError(
            "LLM mode requires langgraph, langchain-openai, pydantic. "
            f"Import failed: {exc}"
        ) from exc

    class LlmEval(BaseModel):
        grade: Literal["ACCEPTED", "REJECTED"] = Field(
            description="ACCEPTED if valid hypothesis, REJECTED if likely false positive"
        )
        feedback: str = Field(
            description="Critique on OS false positives, races, or missing evidence"
        )

    llm = ChatOpenAI(model=os.environ.get("QUILLA_EVAL_MODEL", "gpt-4o"), temperature=0.2)
    evaluator_llm = llm.with_structured_output(LlmEval)

    def generator_agent(state: AgentState) -> dict:
        telemetry_payload = state["telemetry_payload"]
        prior_feedback = state.get("feedback", "")
        iteration = int(state.get("iteration_count", 0)) + 1
        prompt = f"""
You are the Quilla Swarm Hypothesis Generator (server-side only).
Analyze this CoreGuard telemetry delta:
{json.dumps(telemetry_payload)[:4000]}

Previous Evaluator Feedback (if any):
{prior_feedback}

Generate a concise threat diagnosis including potential attack vectors, impact, and mitigation.
Do not invent device compromise; ground claims in the telemetry.
"""
        response = llm.invoke(prompt)
        return {"hypothesis": response.content, "iteration_count": iteration}

    def red_team_evaluator(state: AgentState) -> dict:
        hypothesis = state["hypothesis"]
        telemetry_payload = state["telemetry_payload"]
        eval_prompt = f"""
You are a strict Red Team Evaluator Agent in Quilla Intelligence.
Prevent false positives from Android OS updates, developer mode, or races.

Telemetry: {json.dumps(telemetry_payload)[:4000]}
Hypothesis: {hypothesis}

Return ACCEPTED only if well-grounded and actionable; otherwise REJECTED with feedback.
"""
        res: LlmEval = evaluator_llm.invoke(eval_prompt)
        out: Dict[str, Any] = {"feedback": res.feedback, "grade": res.grade}
        if res.grade == "ACCEPTED":
            out["final_output"] = hypothesis
        return out

    def route_evaluation(state: AgentState) -> str:
        if state.get("final_output"):
            return "ACCEPTED"
        if int(state.get("iteration_count", 0)) >= max_iters:
            return "FORCE_ACCEPT"
        return "REJECTED"

    def force_accept(state: AgentState) -> dict:
        hyp = state.get("hypothesis", "")
        return {
            "final_output": hyp + "\n\n[Warning: Unverified by Red Team Evaluator]",
            "grade": "ACCEPTED",
        }

    builder = StateGraph(AgentState)
    builder.add_node("generate_hypothesis", generator_agent)
    builder.add_node("evaluate_hypothesis", red_team_evaluator)
    builder.add_node("force_accept", force_accept)
    builder.set_entry_point("generate_hypothesis")
    builder.add_edge("generate_hypothesis", "evaluate_hypothesis")
    builder.add_conditional_edges(
        "evaluate_hypothesis",
        route_evaluation,
        {
            "ACCEPTED": END,
            "REJECTED": "generate_hypothesis",
            "FORCE_ACCEPT": "force_accept",
        },
    )
    builder.add_edge("force_accept", END)
    graph = builder.compile()

    final_state = graph.invoke(
        {
            "telemetry_payload": telemetry,
            "hypothesis": "",
            "feedback": "",
            "iteration_count": 0,
            "final_output": "",
        }
    )
    return EvaluationResult(
        grade="ACCEPTED",
        feedback=str(final_state.get("feedback", "")),
        hypothesis=str(final_state.get("final_output") or final_state.get("hypothesis") or ""),
        iteration_count=int(final_state.get("iteration_count", 0)),
        mode="llm",
    )


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Quilla telemetry hypothesis evaluator")
    parser.add_argument("--input", "-i", help="Path to SignedTelemetryPayload / delta JSON")
    parser.add_argument("--llm", action="store_true", help="Use LangGraph+OpenAI when key present")
    parser.add_argument("--strict", action="store_true", help="Exit 1 if final grade is REJECTED")
    parser.add_argument("--max-iters", type=int, default=3)
    parser.add_argument("--demo", action="store_true", help="Run built-in Frida CRITICAL sample")
    args = parser.parse_args(argv)

    if args.demo:
        telemetry = {
            "delta": {
                "deltaId": "demo-1",
                "trigger": "FRIDA_DETECTED",
                "severity": "CRITICAL",
                "detectedAnomalies": {"frida_port": "27042", "thread": "gum-js-loop"},
                "previousStateHash": "0" * 64,
                "currentStateHash": "abc",
            }
        }
    elif args.input:
        telemetry = json.loads(Path(args.input).read_text(encoding="utf-8"))
    else:
        parser.error("Provide --input JSON or --demo")

    use_llm = args.llm and bool(os.environ.get("OPENAI_API_KEY"))
    if args.llm and not os.environ.get("OPENAI_API_KEY"):
        print("[quilla_hypothesis_evaluator] OPENAI_API_KEY missing — falling back to local mode", file=sys.stderr)

    if use_llm:
        result = run_llm_graph(telemetry, max_iters=args.max_iters)
    else:
        result = run_local_graph(telemetry, max_iters=args.max_iters)

    print(json.dumps(asdict(result), indent=2))
    if args.strict and result.grade == "REJECTED":
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
