"""Report Kover line coverage without hiding untested production source files."""
import argparse
import json
import os
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


def line_counts(element):
    counter = element.find("counter[@type='LINE']")
    if counter is None:
        return 0, 0
    covered = int(counter.attrib["covered"])
    return covered, covered + int(counter.attrib["missed"])


def metric(covered, total):
    return {"covered": covered, "total": total,
            "percent": round(100 * covered / total, 2) if total else None}


def summarize(report):
    root = ET.parse(report).getroot()
    overall = metric(*line_counts(root))
    sources = []
    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            name = package.attrib["name"] + "/" + source.attrib["name"]
            covered, total = line_counts(source)
            # Production data/util code and all feature viewmodels are logic.
            logic = ("/data/" in name or "/util/" in name
                     or source.attrib["name"].endswith("ViewModel.kt"))
            sources.append({"source": name, "logic": logic, **metric(covered, total)})
    logic_sources = [source for source in sources if source["logic"]]
    logic = metric(sum(source["covered"] for source in logic_sources),
                   sum(source["total"] for source in logic_sources))
    return {"overall": overall, "logic": logic, "targets": {"overall": 80, "logic": 90},
            "sources": sorted(sources, key=lambda source: source["total"] - source["covered"], reverse=True)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--enforce", action="store_true", help="exit non-zero when a release target is not met")
    args = parser.parse_args()
    summary = summarize(args.report)
    if args.output:
        args.output.write_text(json.dumps(summary, indent=2) + "\n")
    rows = ["## Android coverage", "", "| Scope | Lines | Coverage | Release target |",
            "| --- | --- | --- | --- |"]
    for name in ("overall", "logic"):
        item = summary[name]
        percent = "unmeasured" if item["percent"] is None else f'{item["percent"]:.2f}%'
        rows.append(f'| {name} | {item["covered"]}/{item["total"]} | {percent} | {summary["targets"][name]}% |')
    failures = [
        f'{name} coverage {summary[name]["percent"]}% is below the {summary["targets"][name]}% gate'
        for name in ("overall", "logic")
        if summary[name]["percent"] is None or summary[name]["percent"] < summary["targets"][name]
    ]
    if args.enforce:
        rows += ["", "FAILED: " + "; ".join(failures) if failures else "Coverage gates met.", ""]
    else:
        rows += ["", "Targets remain release gates; reporting alone does not satisfy them.", ""]
    text = "\n".join(rows)
    print(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
            output.write(text)
    if args.enforce and failures:
        sys.exit("; ".join(failures))


if __name__ == "__main__":
    main()
