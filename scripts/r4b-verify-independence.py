#!/usr/bin/env python3
"""Compile shared contracts and classify SDK-free consumer diagnostics offline."""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile


def run(command, log):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    log.write_text(result.stdout)
    return result.returncode, result.stdout


def sdk_jar(path):
    try:
        with zipfile.ZipFile(path) as archive:
            return any(name.startswith("no/stelar7/api/r4j/") for name in archive.namelist())
    except (OSError, zipfile.BadZipFile):
        return False


def compile_sources(javac, sources, classpath, destination, log, processor=None):
    destination.mkdir(parents=True, exist_ok=True)
    arguments = destination.parent / (destination.name + "-sources.txt")
    arguments.write_text("\n".join('"' + str(source) + '"' for source in sources))
    command = [javac, "--release", "25", "-encoding", "UTF-8", "-Xmaxerrs", "10000",
               "-classpath", os.pathsep.join(map(str, classpath)), "-d", str(destination)]
    if processor:
        command += ["-processorpath", str(processor)]
    else:
        command += ["-proc:none"]
    return run(command + ["@" + str(arguments)], log)


def diagnostics(output, root):
    errors = []
    for match in re.finditer(r"(?m)^(.+?\.java):(\d+): error: (.+)$", output):
        path = Path(match.group(1)).resolve()
        try:
            name = str(path.relative_to(root))
        except ValueError:
            name = str(path)
        errors.append({"file": name, "line": int(match.group(2)), "message": match.group(3),
                       "integration": name.startswith("src/main/java/com/safjnest/r4j/")})
    return errors


def dependency_edges(output):
    return [(match.group(1), match.group(2)) for match in
            re.finditer(r"(?m)^\s+(\S+)\s+->\s+(\S+)\s+", output)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True,
                        help="File produced by mvn -o dependency:build-classpath -Dmdep.outputFile=<file>")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--jdk", type=Path, default=Path(os.environ.get("JAVA_HOME", "")))
    parser.add_argument("--classes", type=Path, help="Completed monolith build for bytecode closure inspection")
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    output_dir = args.output_dir or Path(tempfile.mkdtemp(prefix="r4b-independence-"))
    output_dir.mkdir(parents=True, exist_ok=True)
    cp = [Path(path) for path in Path(args.classpath).read_text().strip().split(os.pathsep) if path]
    jars = [path for path in cp if path.is_file() and path.suffix == ".jar"]
    tools = {}
    for name in ["javac", "jdeps"]:
        candidate = args.jdk / "bin" / name
        tools[name] = str(candidate) if candidate.is_file() else shutil.which(name)
        if not tools[name]:
            raise SystemExit("Missing JDK tool: " + name)
    sdk = [path for path in jars if sdk_jar(path)]
    neutral_cp = [path for path in jars if path not in sdk]
    json_cp = [path for path in jars if "/com/fasterxml/jackson/" in str(path)]
    source_root = root / "src/main/java"
    shared = sorted((source_root / "com/R4B").rglob("*.java"))
    hive = sorted((source_root / "com/hive").rglob("*.java"))
    sources = sorted(source_root.rglob("*.java"))
    summary = {"outputDirectory": str(output_dir), "sharedSources": len(shared),
               "hiveSources": len(hive), "mainSources": len(sources),
               "removedSdkJars": list(map(str, sdk)), "checks": {}}
    if not shared or not sdk:
        summary["error"] = "Shared sources or the declared SDK dependency were not found"
        summary["passed"] = False
        (output_dir / "summary.json").write_text(json.dumps(summary, indent=2))
        print(json.dumps(summary, indent=2))
        return 1
    shared_classes = output_dir / "shared-classes"
    code, output = compile_sources(tools["javac"], shared + hive, json_cp, shared_classes,
                                   output_dir / "shared-compile.log")
    summary["checks"]["sharedCompilation"] = {"passed": code == 0, "exitCode": code,
                                             "errors": diagnostics(output, root)}
    if code == 0:
        code, output = run([tools["jdeps"], "--multi-release", "25", "--recursive", "-verbose:class",
                            "--class-path", os.pathsep.join(map(str, json_cp)),
                            str(shared_classes)], output_dir / "shared-jdeps.log")
        forbidden = [{"from": source, "to": target} for source, target in dependency_edges(output)
                     if source.startswith("com.R4B.") and not target.startswith(
                         ("java.", "javax.", "com.R4B.", "com.hive.", "com.fasterxml.jackson."))]
        summary["checks"]["sharedBytecodeClosure"] = {"passed": code == 0 and not forbidden,
                                                     "exitCode": code, "forbidden": forbidden}
    lombok = next((path for path in jars if path.name.startswith("lombok-")), None)
    code, output = compile_sources(tools["javac"], sources, neutral_cp, output_dir / "sdk-free-classes",
                                   output_dir / "sdk-free-compile.log", lombok)
    errors = diagnostics(output, root)
    outside = [error for error in errors if not error["integration"]]
    expected = code != 0 and bool(errors) and not outside and "only showing the first" not in output
    summary["checks"]["sdkFreeMonolithDiagnostics"] = {"passed": code == 0 or expected,
        "exitCode": code, "integrationErrors": len(errors) - len(outside), "consumerErrors": outside,
        "diagnosticsComplete": "only showing the first" not in output}
    classes = args.classes or root / "target/classes"
    if not any(classes.rglob("*.class")):
        summary["checks"]["consumerBytecodeClosure"] = {"passed": False,
                                                       "error": "A completed monolith build is required"}
    else:
        code, output = run([tools["jdeps"], "--multi-release", "25", "--recursive", "--ignore-missing-deps", "-verbose:class",
                            "--class-path", os.pathsep.join(map(str, neutral_cp)), str(classes)],
                           output_dir / "consumer-jdeps.log")
        residual = [{"from": source, "to": target} for source, target in dependency_edges(output)
                    if target.startswith("no.stelar7.api.r4j.") and
                    not source.startswith("com.safjnest.r4j.")]
        summary["checks"]["consumerBytecodeClosure"] = {"passed": code == 0 and not residual,
                                                       "exitCode": code, "residualSdkEdges": residual}
    summary["passed"] = all(check["passed"] for check in summary["checks"].values())
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
