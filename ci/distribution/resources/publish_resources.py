from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
import shutil
from pathlib import Path
import subprocess
import tempfile
import urllib.request
import zipfile

from prepare_release import prepare, ROOT


TAG = "resources-v1"
SPEECH_DIRECTORY = "sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13"
UBUNTU_URL = "https://raw.githubusercontent.com/3316891527/OperitTerminalCore/1cdbfab990efeab93072bfbf727cbaa4886f1efa/src/main/assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"


def api(endpoint: str, payload: dict | None = None, method: str | None = None):
    command = ["gh", "api", endpoint]
    if payload is not None:
        command += ["--method", method or "POST", "--input", "-"]
    elif method:
        command += ["--method", method]
    result = subprocess.run(command, input=json.dumps(payload) if payload is not None else None, capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else None


def main(artifact_id: int) -> None:
    repo = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ["GITHUB_SHA"]
    release = api(f"repos/{repo}/releases/413574420")
    if release["tag_name"] != TAG:
        raise ValueError("Unexpected draft resource tag")
    release_id = release["id"]
    if not release["draft"]:
        raise ValueError("固定资源 Release 已发布，禁止重写；需要变更时创建新资源版本")
    catalog = []
    for line in (ROOT / "app/config/offline-resources.properties").read_text().splitlines():
        if line and not line.startswith("#"):
            fields = line.split("|")
            if len(fields) != 6 or Path(fields[1]).name != fields[1] or fields[1] in {".", ".."}:
                raise ValueError("Invalid pinned resource catalog")
            catalog.append(fields)
    with tempfile.TemporaryDirectory(prefix="operit-resources-") as directory:
        work = Path(directory)
        artifact = work / "source.zip"
        with artifact.open("wb") as output:
            subprocess.run(["gh", "api", f"repos/{repo}/actions/artifacts/{artifact_id}/zip"], stdout=output, check=True, timeout=600)
        apk = work / "source.apk"
        with zipfile.ZipFile(artifact) as archive:
            candidates = [name for name in archive.namelist() if name.endswith(".apk")]
            if len(candidates) != 1:
                raise ValueError("Source artifact must contain exactly one APK")
            with archive.open(candidates[0]) as source, apk.open("wb") as output:
                shutil.copyfileobj(source, output)
        sources = work / "sources"
        sources.mkdir()
        with zipfile.ZipFile(apk) as package:
            for group, name, size, digest, source_note, license_note in catalog:
                target = sources / name
                if group == "ubuntu":
                    with urllib.request.urlopen(UBUNTU_URL, timeout=60) as source, target.open("wb") as output:
                        shutil.copyfileobj(source, output)
                else:
                    asset = f"assets/models/{SPEECH_DIRECTORY}/{name}" if group == "speech" else f"assets/subpack/{name}"
                    with package.open(asset) as source, target.open("wb") as output:
                        shutil.copyfileobj(source, output)
        output = work / "release-assets"
        prepare(sources, output)
        existing = api(f"repos/{repo}/releases/{release_id}/assets?per_page=100")
        # 草稿只由本轮创建；清除未完成传输及旧 ZIP，正式资源版本始终不可改写。
        for asset in existing:
            target = output / asset["name"]
            if not target.is_file():
                raise ValueError(f"Unexpected asset in draft: {asset['name']}")
            with target.open("rb") as stream:
                digest = "sha256:" + hashlib.file_digest(stream, "sha256").hexdigest()
            if asset["state"] != "uploaded" or asset["size"] != target.stat().st_size or asset.get("digest") != digest:
                api(f"repos/{repo}/releases/assets/{asset['id']}", method="DELETE")
        existing = {asset["name"] for asset in api(f"repos/{repo}/releases/{release_id}/assets?per_page=100")}
        pending = [target for target in output.iterdir() if target.name not in existing]

        def upload(target: Path) -> None:
            subprocess.run(["gh", "release", "upload", TAG, str(target), "--repo", repo], check=True, timeout=900)
            print(f"Uploaded {target.name}", flush=True)

        with ThreadPoolExecutor(max_workers=3) as pool:
            list(pool.map(upload, pending))
        remote = {asset["name"]: asset for asset in api(f"repos/{repo}/releases/{release_id}/assets?per_page=100")}
        if set(remote) != {target.name for target in output.iterdir()}:
            raise ValueError("Resource release file list differs from prepared assets")
        for target in output.iterdir():
            asset = remote[target.name]
            with target.open("rb") as stream:
                digest = "sha256:" + hashlib.file_digest(stream, "sha256").hexdigest()
            if asset["state"] != "uploaded" or asset["size"] != target.stat().st_size or asset.get("digest") != digest:
                raise ValueError(f"Uploaded asset failed verification: {target.name}")
        published = api(f"repos/{repo}/releases/{release_id}", {"draft": False, "make_latest": "false", "target_commitish": commit}, method="PATCH")
        print("Published verified resources: " + published["html_url"], flush=True)
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
            summary.write(f"资源已完整上传并通过大小及 SHA-256 核对：{published['html_url']}\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-artifact-id", type=int, required=True)
    arguments = parser.parse_args()
    main(arguments.source_artifact_id)
