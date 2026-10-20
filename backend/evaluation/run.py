"""Opt-in live comparison. Stores only fixed synthetic cases, never headers/.env."""

import argparse
from dataclasses import asdict
from datetime import datetime, timezone
import hashlib
from importlib.metadata import version, PackageNotFoundError
import json
from pathlib import Path
import platform
import subprocess
import sys
import time

import requests

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent))
from ai_service import GroqProvider, SYSTEM_PROMPT
from analysis_service import AnalysisService
from config import get_settings
from domain import AnalysisError, AnalysisInput, ConversationMessage


def digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def evaluate(case, configuration, settings, *, post=None):
    """Uses the real adapter/service; only the historical payload is substituted."""
    transport = post if post is not None else requests.post
    evidence = {"case_id": case["id"], "version": configuration["name"],
                "input": {key: case[key] for key in ("description", "context", "duration_days", "intensity") if key in case},
                "expected": case["expected"], "attempts": []}

    def recorded_post(url, **kwargs):
        payload = kwargs["json"]
        payload["messages"][0]["content"] = configuration["system_prompt"]
        payload["max_tokens"] = configuration["max_tokens"]
        if not configuration.get("supports_details", False):
            payload["messages"][-1]["content"] = case["description"]
        if not configuration["supports_context"]:
            payload["messages"] = [payload["messages"][0], payload["messages"][-1]]
        attempt = {"number": len(evidence["attempts"]) + 1}
        evidence["attempts"].append(attempt)
        started = time.monotonic()
        try:
            response = transport(url, **kwargs)
            attempt["http_status"] = response.status_code
            if response.status_code == 200:
                try:
                    body = response.json()
                    choice = body["choices"][0]
                    attempt.update(model_returned=body.get("model"), usage=body.get("usage"),
                                   finish_reason=choice.get("finish_reason"),
                                   raw_content=choice["message"]["content"])
                except (ValueError, KeyError, IndexError, TypeError):
                    attempt["capture_error"] = "Invalid provider JSON envelope"
            return response
        except requests.exceptions.RequestException as exc:
            attempt["transport_error"] = type(exc).__name__
            raise
        finally:
            attempt["elapsed_seconds"] = round(time.monotonic() - started, 3)

    context = tuple(ConversationMessage(**turn) for turn in case.get("context", [])) if configuration["supports_context"] else ()
    try:
        supports_details = configuration.get("supports_details", False)
        result = AnalysisService(GroqProvider(settings, post=recorded_post)).analyze(AnalysisInput(
            case["description"], context, case.get("duration_days") if supports_details else None,
            case.get("intensity") if supports_details else None))
        evidence["result"] = asdict(result)
        # The legacy adapter admitted only its original category set.
        if result.category not in configuration["categories"]:
            evidence["result"]["category"] = "інше"
        evidence["valid_response"] = True
        checks = {key: evidence["result"][key] == expected
                  for key, expected in case["expected"].items() if key not in {"question_required", "explanation_contains"}}
        if "explanation_contains" in case["expected"]:
            checks["explanation_contains"] = all(value in result.explanation for value in case["expected"]["explanation_contains"])
        if case["expected"].get("question_required"):
            checks["question_required"] = bool(result.follow_up_question)
        evidence["checks"] = checks
        evidence["passed"] = all(checks.values())
    except AnalysisError as exc:
        evidence.update(valid_response=False, passed=False, error=type(exc).__name__)
    return evidence


def summarize(cases, rows, acceptance):
    groups = {case["id"]: case["group"] for case in cases}
    summary = {}
    for name in dict.fromkeys(row["version"] for row in rows):
        selected = [row for row in rows if row["version"] == name]
        counts = {group: sum(row["passed"] for row in selected if groups[row["case_id"]] == group)
                  for group in ("regression", "extension", "context")}
        counts["valid_responses"] = sum(row["valid_response"] for row in selected)
        summary[name] = {"counts": counts, "accepted": all(counts[key] >= needed for key, needed in acceptance.items())}
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=("legacy", "details"), default="details")
    parser.add_argument("--live", action="store_true", help="Explicitly allow 20 synthetic Groq calls")
    parser.add_argument("--output", type=Path, help="New result JSON path; existing files are never replaced")
    parser.add_argument("--delay", type=float, default=15.0, help="Pause between calls, seconds")
    parser.add_argument("--resume", type=Path, help="Explicit retry of failed transport/invalid responses into a NEW artifact; valid semantic mismatches are not retried")
    args = parser.parse_args()
    if args.delay < 0 or args.delay > 60:
        parser.error("delay must be between 0 and 60 seconds")
    suite = HERE / "details" if args.suite == "details" else HERE
    dataset = json.loads((suite / "cases.json").read_text(encoding="utf-8"))
    configurations = [json.loads((suite / f"{name}.json").read_text(encoding="utf-8")) for name in ("baseline", "current")]
    for cfg in configurations:
        if digest(cfg["system_prompt"]) != cfg["prompt_sha256"]:
            parser.error("Prompt snapshot hash mismatch")
    if args.suite == "details" and configurations[1]["system_prompt"] != SYSTEM_PROMPT:
        parser.error("Current prompt snapshot is stale; freeze a new configuration before evaluation")
    if not args.live:
        print(f"Validated {len(dataset['cases'])} synthetic cases, two configurations. No network calls. Add --live to run.")
        return 0
    settings = get_settings()
    if settings.provider != "groq" or not settings.api_key:
        parser.error("Live evaluation requires AI_PROVIDER=groq and a server-side GROQ_API_KEY")
    timestamp = datetime.now(timezone.utc)
    output = args.output or suite / "results" / (timestamp.strftime("%Y%m%dT%H%M%S%fZ") + ".json")
    output.parent.mkdir(parents=True, exist_ok=True)
    packages = {}
    for package in ("fastapi", "uvicorn", "pydantic", "requests", "python-dotenv", "httpx"):
        try:
            packages[package] = version(package)
        except PackageNotFoundError:
            packages[package] = None
    artifact = {
        "schema_version": 1, "suite": args.suite, "started_utc": timestamp.isoformat(), "synthetic_only": True,
        "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "python": platform.python_version(), "packages": packages,
        "model_requested": settings.model, "temperature": 0.1, "timeout_seconds": settings.timeout_seconds,
        "delay_seconds": args.delay,
        "dataset_sha256": hashlib.sha256((suite / "cases.json").read_bytes()).hexdigest(),
        "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "configurations": configurations, "acceptance": dataset["acceptance"], "rows": [],
        "scope": "Prompt/configuration comparison today, not historical replay or clinical validation. One attempt per row, no automatic retry.",
    }
    if args.resume:
        previous = json.loads(args.resume.read_text(encoding="utf-8"))
        for key in ("dataset_sha256", "configurations", "model_requested", "temperature", "timeout_seconds"):
            if previous[key] != artifact[key]:
                parser.error("Resume configuration differs from original measurement")
        artifact["resume_source"] = {"file": args.resume.name, "sha256": hashlib.sha256(args.resume.read_bytes()).hexdigest()}
        artifact["initial_summary"] = previous.get("initial_summary", previous.get("summary"))
        artifact["rows"] = previous["rows"]
        artifact["scope"] += " Explicit resume retains all earlier attempts; acceptance after retry is not first-attempt availability."
    # Reserve a unique file before the first billable call. Save partial evidence after each call.
    with output.open("x", encoding="utf-8") as stream:
        json.dump(artifact, stream, ensure_ascii=False, indent=2)
    try:
        for case in dataset["cases"]:
            for configuration in configurations:
                old = next((row for row in artifact["rows"] if row["case_id"] == case["id"] and row["version"] == configuration["name"]), None)
                if old is not None and old["valid_response"]:
                    continue
                row = evaluate(case, configuration, settings)
                if old is not None:
                    for attempt in row["attempts"]:
                        attempt["number"] += len(old["attempts"])
                    row["attempts"] = old["attempts"] + row["attempts"]
                    artifact["rows"].remove(old)
                artifact["rows"].append(row)
                output.write_text(json.dumps(artifact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                print(case["id"], configuration["name"], "PASS" if row["passed"] else "FAIL", flush=True)
                if case is not dataset["cases"][-1] or configuration is not configurations[-1]:
                    time.sleep(args.delay)
        artifact["summary"] = summarize(dataset["cases"], artifact["rows"], dataset["acceptance"])
        artifact["first_attempt_valid_responses"] = {
            name: sum(row["valid_response"] and len(row["attempts"]) == 1 for row in artifact["rows"] if row["version"] == name)
            for name in ("baseline", "current")}
        artifact["first_attempt_acceptance"] = {
            name: value["accepted"] and artifact["first_attempt_valid_responses"][name] == len(dataset["cases"])
            for name, value in artifact["summary"].items()}
        artifact["finished_utc"] = datetime.now(timezone.utc).isoformat()
    finally:
        output.write_text(json.dumps(artifact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(artifact["summary"], ensure_ascii=False))
    print("Evidence:", output)
    return 0 if artifact["summary"]["current"]["accepted"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
