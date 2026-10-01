"""Report app-only iOS line coverage (overall and logic) from `xccov view --report` text.

Third-party packages (Supabase, Nuke, Firebase) are excluded: only files under
ios/FamilyApp/ count. Logic = Core/, view models, the deep-link router and the pure
feature helpers; everything else is SwiftUI view code.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

FILE_ROW = re.compile(r"^\s+(?P<path>\S.*?/ios/FamilyApp/(?P<source>\S+\.swift))\s+[\d.]+%\s+\((?P<covered>\d+)/(?P<total>\d+)\)")
LOGIC_SUFFIXES = ("Utils.swift", "Formatters.swift", "Moderation.swift", "Routes.swift", "PDF.swift", "QrCode.swift")
TARGETS = {"overall": 80, "logic": 90}


def is_logic(source):
    name = source.rsplit("/", 1)[-1]
    return (source.startswith("Core/") or "ViewModel" in name or source == "App/DeepLinkRouter.swift"
            or (name.endswith(LOGIC_SUFFIXES) and not name.startswith("Conversation")))


def metric(covered, total):
    return {"covered": covered, "total": total,
            "percent": round(100 * covered / total, 2) if total else None}


def summarize(report_text):
    sources = []
    for line in report_text.splitlines():
        match = FILE_ROW.match(line)
        if match:
            covered, total = int(match["covered"]), int(match["total"])
            sources.append({"source": match["source"], "logic": is_logic(match["source"]), **metric(covered, total)})

    def aggregate(items):
        return metric(sum(item["covered"] for item in items), sum(item["total"] for item in items))

    return {"overall": aggregate(sources), "logic": aggregate([s for s in sources if s["logic"]]),
            "targets": TARGETS,
            "sources": sorted(sources, key=lambda s: s["total"] - s["covered"], reverse=True)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report", type=Path, help="output of `xcrun xccov view --report`")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--enforce", action="store_true", help="exit non-zero when a target is missed")
    args = parser.parse_args()
    summary = summarize(args.report.read_text())
    if args.output:
        args.output.write_text(json.dumps(summary, indent=2) + "\n")
    rows = ["## iOS coverage (app sources only)", "", "| Scope | Lines | Coverage | Release target |",
            "| --- | --- | --- | --- |"]
    missed = []
    for name in ("overall", "logic"):
        item = summary[name]
        percent = "unmeasured" if item["percent"] is None else f'{item["percent"]:.2f}%'
        rows.append(f'| {name} | {item["covered"]}/{item["total"]} | {percent} | {TARGETS[name]}% |')
        if item["percent"] is None or item["percent"] < TARGETS[name]:
            missed.append(name)
    rows += ["", "Largest gaps:", ""]
    rows += [f'- {s["source"]}: {s["covered"]}/{s["total"]}' for s in summary["sources"][:10]]
    text = "\n".join(rows) + "\n"
    print(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
            output.write(text)
    if args.enforce and missed:
        print(f"Coverage below target: {', '.join(missed)}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
