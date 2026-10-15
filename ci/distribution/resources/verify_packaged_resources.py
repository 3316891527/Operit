from __future__ import annotations

import hashlib
import os
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parents[3]


def main() -> None:
    apks = sorted((ROOT / "app/build/outputs/apk").rglob("*.apk"))
    if not apks:
        raise ValueError("No built APK was found")
    manifest = (ROOT / "app/config/offline-resources.properties").read_bytes()
    vad = next(
        line.split("|") for line in (ROOT / "app/config/stt-model-assets.properties").read_text().splitlines()
        if line.startswith("models/silero_vad.onnx|")
    )
    summaries = []
    for apk in apks:
        with zipfile.ZipFile(apk) as package:
            forbidden = [
                name for name in package.namelist()
                if name.startswith(("assets/subpack/", "assets/models/sherpa-ncnn-"))
                or name == "assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"
            ]
            if forbidden:
                raise ValueError(f"Large resources were bundled in {apk.name}: {forbidden}")
            if package.read("assets/offline-resources.properties") != manifest:
                raise ValueError("Packaged resource catalog differs from source")
            vad_content = package.read("assets/models/silero_vad.onnx")
            if len(vad_content) != int(vad[2]) or hashlib.sha256(vad_content).hexdigest() != vad[3]:
                raise ValueError("Shared Silero VAD is missing or failed verification")
        summary = f"{apk.relative_to(ROOT)}: {apk.stat().st_size:,} bytes ({apk.stat().st_size / 1048576:.2f} MiB); 外置资源未打包，共用 VAD 校验通过。"
        print(summary)
        summaries.append(summary)
    if step_summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(step_summary).open("a") as output:
            output.write("\n" + "\n\n".join(summaries) + "\n")


if __name__ == "__main__":
    main()
