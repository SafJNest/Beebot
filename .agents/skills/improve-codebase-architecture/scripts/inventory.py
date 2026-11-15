#!/usr/bin/env python3
"""Export architecture metadata through Git and CodeGraph; never mark code reviewed."""

import argparse
import json
import subprocess
from collections import Counter
from pathlib import Path


MAIN = "src/main/java/com/safjnest/"
KINDS = ("class", "interface", "enum", "method", "function")
SOURCE_SUFFIXES = {".java", ".js", ".ts", ".tsx", ".jsx", ".php", ".py", ".sql", ".sh"}


def run(repo, *args):
    return subprocess.run(args, cwd=repo, capture_output=True, text=True, check=True).stdout


def query(repo, kind):
    result = json.loads(run(repo, "codegraph", "query", "", "--kind", kind, "--limit", "100000", "--json"))
    return [item["node"] for item in result]


def area(path):
    if path.startswith(MAIN):
        relative = path[len(MAIN):]
        return relative.split("/")[0] if "/" in relative else "(root)"
    if path.startswith("src/test/"):
        return "tests"
    if path.startswith("rsc/backup/"):
        return "archive"
    return path.split("/")[0]


def inventory(repo, include_symbols):
    tracked = sorted(filter(None, run(repo, "git", "ls-files", "-z").split("\0")))
    tracked_set = set(tracked)
    files = json.loads(run(repo, "codegraph", "files", "--json"))
    indexed = {item["path"] for item in files}
    declarations = query(repo, "import")
    imports = []
    connections = Counter()
    for node in declarations:
        path, target = node["filePath"], node["name"]
        if not path.startswith(MAIN):
            continue
        source_area = area(path)
        imports.append({"path": path, "line": node["startLine"], "target": target})
        if target.startswith("com.safjnest."):
            target_area = target.split(".")[2]
            if target_area == "App":
                target_area = "(root)"
            if target_area != source_area:
                connections[(source_area, target_area)] += 1
    result = {
        "baseline_commit": run(repo, "git", "rev-parse", "HEAD").strip(),
        "working_tree_status": run(repo, "git", "status", "--short"),
        "index_status": run(repo, "codegraph", "status"),
        "coverage_note": "Metadata inventory only. No source body or runtime behavior is reviewed by this script.",
        "tracked_file_count": len(tracked),
        "tracked_top_level_counts": dict(sorted(Counter(p.split("/")[0] for p in tracked).items())),
        "indexed_file_count": len(files),
        "main_java_count": sum(f["path"].startswith("src/main/java/") and f["path"].endswith(".java") for f in files),
        "beebot_java_count": sum(f["path"].startswith(MAIN) and f["path"].endswith(".java") for f in files),
        "external_main_java_paths": [f["path"] for f in files if f["path"].startswith("src/main/java/") and not f["path"].startswith(MAIN) and f["path"].endswith(".java")],
        "test_java_count": sum(f["path"].startswith("src/test/") and f["path"].endswith(".java") for f in files),
        "main_area_counts": dict(sorted(Counter(area(f["path"]) for f in files if f["path"].startswith(MAIN)).items())),
        "files": [dict(f, area=area(f["path"]), tracked=f["path"] in tracked_set, review_status="inventoried") for f in sorted(files, key=lambda f: f["path"])],
        "unindexed_tracked_source_paths": [p for p in tracked if p not in indexed and Path(p).suffix in SOURCE_SUFFIXES],
        "imports": imports,
        "cross_area_import_counts": [{"source": a, "target": b, "import_declarations": count} for (a, b), count in sorted(connections.items())],
        "dependency_note": "Explicit main-source imports, not a runtime call graph. Root packages are a navigation aid, not ownership decisions. Wildcard and transitive imports need separate exploration.",
    }
    if include_symbols:
        result["symbols"] = [
            {key: node.get(key) for key in ("id", "kind", "qualifiedName", "filePath", "signature", "startLine", "endLine")}
            for kind in KINDS for node in query(repo, kind)
        ]
        result["symbol_note"] = "AST declarations include nested/anonymous classes and constructors. Generated members and dynamic registrations may require additional evidence."
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    parser.add_argument("--symbols", action="store_true", help="Include AST declarations for an exhaustive-review ledger.")
    args = parser.parse_args()
    try:
        result = inventory(args.repo.resolve(), args.symbols)
    except (subprocess.CalledProcessError, FileNotFoundError, json.JSONDecodeError) as error:
        parser.exit(1, "Inventory failed; check Git/CodeGraph availability and index status: " + str(error) + "\n")
    payload = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
        print(str(args.output.resolve()))
    else:
        print(payload, end="")


if __name__ == "__main__":
    main()
