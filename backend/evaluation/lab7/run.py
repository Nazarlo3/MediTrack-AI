"""Lab7 real HTTP evaluation. Fixed synthetic input, explicit opt-in, no automatic retries."""
import argparse
from collections import Counter
from datetime import datetime, timezone
from hashlib import sha256
from importlib.metadata import version
import json
from pathlib import Path
import subprocess
import sys
import time

import requests

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / "backend"))
from ai_service import SYSTEM_PROMPT, _validate_ai_result
from config import get_settings


def validate_dataset(cases):
    assert Counter(c["group"] for c in cases) == {"typical": 6, "boundary": 2, "invalid": 2}
    previous = json.loads((HERE.parent / "details/cases.json").read_text(encoding="utf-8"))["cases"]
    regressions = [c for c in cases if c["regression"]]
    assert len(regressions) == 5
    for old, current in zip(previous[:5], regressions):
        assert old["id"] == current["id"]
        assert {"description": old["description"]} == current["request"]
        assert old["expected"] == current["expected"]


def evaluate(case, base_url):
    row = {**case, "attempt": 1}
    started = time.perf_counter()
    try:
        response = requests.post(base_url + "/analyze-symptom", json=case["request"], timeout=45)
        body = response.json()
        row.update(http_status=response.status_code, response=body,
                   request_id=response.headers.get("X-Request-ID"))
        if case["group"] == "invalid":
            row["passed"] = response.status_code == case["expected"]["http_status"]
        elif response.status_code == 200:
            _validate_ai_result(body)
            expected = case["expected"]
            checks = {"category": body["category"] == expected["category"],
                      "usable": body.get("has_usable_result") is True,
                      "disclaimer": isinstance(body.get("disclaimer"), str) and bool(body["disclaimer"].strip())}
            if "explanation_contains" in expected:
                checks["details"] = body.get("details_used") is True and all(
                    part in body["explanation"] for part in expected["explanation_contains"])
            row.update(checks=checks, passed=all(checks.values()))
        else:
            row["passed"] = False
    except (requests.RequestException, ValueError, KeyError, TypeError) as exc:
        row.update(passed=False, error=type(exc).__name__)
    row["elapsed_seconds"] = round(time.perf_counter() - started, 3)
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--base-url", default="http://127.0.0.1:18765")
    parser.add_argument("--delay", type=float, default=8)
    parser.add_argument("--case", help="Explicit separate recheck of one case; never overwrites previous attempts")
    args = parser.parse_args()
    if args.base_url != "http://127.0.0.1:18765" or args.delay < 0:
        parser.error("Use the local test backend and nonnegative delay")
    cases = json.loads((HERE / "cases.json").read_text(encoding="utf-8"))["cases"]
    validate_dataset(cases)
    if args.case:
        cases = [case for case in cases if case["id"] == args.case]
        if not cases:
            parser.error("Unknown case ID")
    if not args.live:
        print("Validated 6 typical + 2 boundary + 2 invalid; 5 exact regressions. Selected", len(cases), "cases. No network calls.")
        return 0
    settings = get_settings()
    if settings.provider != "groq" or not settings.api_key:
        parser.error("Real Groq configuration required on backend")
    now = datetime.now(timezone.utc)
    folder = HERE / "results"
    folder.mkdir(exist_ok=True)
    path = folder / (now.strftime("%Y%m%dT%H%M%S%fZ") + ".json")
    result = {
        "started_utc": now.isoformat(), "base_url": args.base_url,
        "scope": "explicit single-case recheck" if args.case else "complete first-pass suite",
        "git_base": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "source_state": "Base commit plus working tree identified by source hashes; not a final commit",
        "source_sha256": {str(p.relative_to(ROOT)).replace("\\", "/"): sha256(p.read_bytes()).hexdigest()
                          for p in sorted((ROOT / "backend").glob("*.py"))},
        "dataset_sha256": sha256((HERE / "cases.json").read_bytes()).hexdigest(),
        "prompt_sha256": sha256(SYSTEM_PROMPT.encode()).hexdigest(),
        "model_configured": settings.model, "model_revision": "Provider does not expose a pinned model revision through this API",
        "temperature": 0.1, "max_tokens": 400, "timeout_seconds": settings.timeout_seconds,
        "packages": {name: version(name) for name in ("fastapi", "starlette", "pydantic", "requests", "uvicorn")},
        "timing": "Complete local HTTP request including real backend/Groq, excluding Android UI and Render",
        "rows": []}
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    requests.get(args.base_url + "/health", timeout=5).raise_for_status()
    for i, case in enumerate(cases):
        row = evaluate(case, args.base_url)
        result["rows"].append(row)
        path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(case["id"], "PASS" if row["passed"] else "FAIL", row["elapsed_seconds"], flush=True)
        if i < len(cases) - 1:
            time.sleep(args.delay)
    valid = [r for r in result["rows"] if r["group"] != "invalid"]
    result["summary"] = {
        "category_correct": sum(r.get("checks", {}).get("category", False) for r in valid),
        "category_denominator": len(valid),
        "valid_cases_all_criteria": sum(r["passed"] for r in valid),
        "regression_passed": sum(r["passed"] for r in valid if r["regression"]),
        "invalid_rejected": sum(r["passed"] for r in result["rows"] if r["group"] == "invalid"),
        "automatic_retries": 0, "clinical_accuracy_evaluated": False}
    result["finished_utc"] = datetime.now(timezone.utc).isoformat()
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["summary"]), "Evidence:", path)
    return 0 if all(r["passed"] for r in result["rows"]) else 1


if __name__ == "__main__":
    raise SystemExit(main())
