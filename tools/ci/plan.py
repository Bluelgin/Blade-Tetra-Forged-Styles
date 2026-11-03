#!/usr/bin/env python3
"""Conservative, explainable native-test selection. No third-party dependencies."""
import argparse
import fnmatch
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def load_config():
    data = json.loads((ROOT / "tools/ci/suites.json").read_text())
    if data["version"] != 1:
        raise ValueError("Unsupported suite manifest version")
    for group in data["groups"].values():
        if not set(group) <= data["suites"].keys():
            raise ValueError("Unknown suite in group")
    for name, suite in data["suites"].items():
        if suite["kind"] not in {"client", "server"} or not suite["markers"]:
            raise ValueError(f"Invalid runner/proof markers: {name}")
    covered = set(data["groups"]["full"]) | set(data["groups"]["compat"])
    if covered != set(data["suites"]):
        raise ValueError("Every suite must belong to full or compat validation")
    return data


def expand(config, names):
    result = set()
    for name in names:
        if name.startswith("@"):
            result.update(config["groups"][name[1:]])
        elif name in config["suites"]:
            result.add(name)
        else:
            raise ValueError(f"Unknown suite: {name}")
    return result


def select(config, paths, mode="auto", fallback=None):
    chosen, reasons = set(), []
    quick = False
    if mode != "auto" or fallback or not paths:
        quick = True
        chosen.update(config["groups"]["full"])
        if mode == "compat":
            chosen.update(config["groups"]["compat"])
        reasons.append({"path": "*", "reason": fallback or ("Empty diff: full native fallback" if mode == "auto" else f"Requested {mode} validation")})
    else:
        for path in sorted(set(paths)):
            # Unit tests follow the production package's native dependencies.
            classified = path.replace("src/test/java/", "src/main/java/").replace(
                "src/integrationTest/java/", "src/main/java/")
            if path.startswith("src/test/java/"):
                classified = classified.removesuffix("Test.java") + ".java"
            if any(fnmatch.fnmatchcase(path, pattern) for pattern in config["docs"]):
                reasons.append({"path": path, "reason": "Documentation/source artwork only", "suites": []})
                continue
            quick = True
            rule = next((r for r in config["rules"] if any(
                fnmatch.fnmatchcase(classified, pattern) for pattern in r["paths"])), None)
            selected = expand(config, rule["suites"]) if rule else set(config["groups"]["full"])
            chosen.update(selected)
            reasons.append({"path": path, "reason": rule["reason"] if rule else
                            "Unclassified change: full native fallback", "suites": sorted(selected)})
    return {"version": 1, "mode": mode, "quick": quick, "suites": [name for name in config["suites"] if name in chosen], "reasons": reasons}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--mode", choices=["auto", "full", "compat"], default="auto")
    parser.add_argument("--reason")
    parser.add_argument("--output", default="build/ci-plan.json")
    parser.add_argument("--github-output")
    parser.add_argument("--summary")
    args = parser.parse_args()
    paths, fallback = [], args.reason
    if args.mode == "auto" and not fallback:
        try:
            if not args.base or set(args.base) == {"0"}:
                raise ValueError("Missing diff base")
            raw = subprocess.check_output(["git", "diff", "--name-only", "--no-renames", "-z",
                                           args.base, args.head, "--"], cwd=ROOT)
            paths = [p.decode("utf-8") for p in raw.split(b"\0") if p]
        except (subprocess.CalledProcessError, ValueError, UnicodeError) as error:
            fallback = f"Cannot determine changed paths: {type(error).__name__}"
    plan = select(load_config(), paths, args.mode, fallback)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(plan, indent=2) + "\n")
    if args.github_output:
        matrix = {"include": [{"suite": "tetra-" + v} for v in ("6.13.0", "6.17.0")]}
        with open(args.github_output, "a") as target:
            target.write(f"quick={str(plan['quick']).lower()}\nnative={str(bool(plan['suites'])).lower()}\n")
            target.write("matrix=" + json.dumps(matrix, separators=(",", ":")) + "\n")
    if args.summary:
        with open(args.summary, "a") as target:
            target.write("### Validation plan\n\nSelected native suites: " +
                         (", ".join(plan["suites"]) or "none (documentation only)") + "\n\n")
            for item in plan["reasons"]:
                target.write(f"- `{item['path']}`: {item['reason']}\n")
    print(f"Validation plan: quick={plan['quick']}; {len(plan['reasons'])} path/reason entries; suites={','.join(plan['suites']) or 'none'}")


if __name__ == "__main__":
    main()
