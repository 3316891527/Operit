"""验证独立发行的版本选择、APK 身份和补丁字节还原。"""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

MODULE_PATH = Path(__file__).resolve().parents[1] / "publish_release.py"
spec = importlib.util.spec_from_file_location("distribution_publish_release", MODULE_PATH)
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


def release(version, *, draft=False, prerelease=False, apk=True):
    return {
        "tag_name": "v" + version, "draft": draft, "prerelease": prerelease,
        "assets": [{"name": publisher.APK_NAME}] if apk else [],
    }


class DistributionPublishingTest(unittest.TestCase):
    def test_version_order_and_supported_formats(self):
        self.assertLess(publisher.version_key("1.12.2+9"), publisher.version_key("v1.12.2+10"))
        self.assertEqual((1, 12, 2, 0), publisher.version_key("1.12.2"))
        for value in ["1.12.2-personal.1", "1.12", "1.12.2+bad"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                publisher.version_key(value)

    def test_baseline_is_latest_compatible_published_apk(self):
        chosen = release("1.12.2+10")
        values = [release("1.12.2+9"), release("1.12.2+11", draft=True),
                  release("1.12.2+12", prerelease=True), release("1.12.2+13", apk=False),
                  release("1.12.1+99"), release("1.12.3+1"), chosen]
        self.assertIs(chosen, publisher.select_previous_release(values, "1.12.2+14"))

    def test_first_release_and_explicit_baseline(self):
        values = [release("1.12.2+1"), release("1.12.2+2")]
        self.assertIsNone(publisher.select_previous_release([], "1.12.2+1"))
        self.assertEqual("v1.12.2+1", publisher.select_previous_release(values, "1.12.2+3", "v1.12.2+1")["tag_name"])
        self.assertIsNone(publisher.select_previous_release(values, "1.12.2+3", "v1.12.3+1"))

    def test_apk_identity_and_fixed_signature_are_checked(self):
        good = "package: name='com.ai.assistance.operit.kong' versionCode='54' versionName='1.12.2+2'\n"
        signed = "Signer #1 certificate SHA-256 digest: " + publisher.CERTIFICATE_SHA256.lower()
        with patch.object(publisher, "run", side_effect=[good, signed]):
            self.assertEqual(54, publisher.inspect_apk(Path("app-kong.apk"), Path("sdk"))["versionCode"])
        with patch.object(publisher, "run", return_value=good.replace(".kong", "")):
            with self.assertRaisesRegex(ValueError, "包名"):
                publisher.inspect_apk(Path("app-kong.apk"), Path("sdk"))
        with patch.object(publisher, "run", side_effect=[good, "Signer #1 certificate SHA-256 digest: abc"]):
            with self.assertRaisesRegex(ValueError, "固定签名"):
                publisher.inspect_apk(Path("app-kong.apk"), Path("sdk"))

    def test_raw_patch_reconstructs_exact_target_and_reuses_unchanged_model(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            base, target = root / "base.apk", root / "target.apk"
            model = bytes(range(256)) * 1024
            for path, entries, comment in [
                (base, [("assets/model.bin", model), ("classes.dex", b"old"), ("removed", b"delete")], b"old"),
                (target, [("classes.dex", b"new"), ("assets/model.bin", model), ("added", b"new file")], b"new tail"),
            ]:
                with zipfile.ZipFile(path, "w") as archive:
                    for name, content in entries:
                        info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
                        info.compress_type = zipfile.ZIP_DEFLATED
                        archive.writestr(info, content)
                    archive.comment = comment
            patch_file, metadata, summary = publisher.prepare_patch(base, target, root / "out", "1.12.2+1", "1.12.2+2")
            import json
            meta = json.loads(metadata.read_text())
            self.assertEqual(publisher.sha256(target), summary["targetSha256"])
            self.assertEqual(publisher.sha256(patch_file), summary["patchSha256"])
            self.assertIn({"name": "assets/model.bin", "mode": "copy"}, meta["apkRawEntries"])
            self.assertEqual(1, meta["fromPatchIndex"])
            self.assertEqual(2, meta["toPatchIndex"])
            rebuilt = root / "rebuilt.apk"
            publisher.hotbuild_module("apply_patch").apply_apkraw_patch(base, patch_file, rebuilt, meta)
            self.assertEqual(target.read_bytes(), rebuilt.read_bytes())

    def test_empty_or_json_notes_are_rejected_before_apk_validation(self):
        arguments = ["publish_release.py", "--apk", "app-kong.apk", "--build-tools", "sdk",
                     "--repository", publisher.REPOSITORY, "--commit", "a" * 40]
        for notes in ["", "   ", '{"releaseNotes":"更新"}', '[{"version":"1.12.2+7"}]']:
            with self.subTest(notes=notes), patch("sys.argv", arguments + ["--notes", notes]):
                with patch.object(publisher, "inspect_apk") as inspect, self.assertRaises(ValueError):
                    publisher.main()
                inspect.assert_not_called()

    def test_markdown_body_is_separate_from_uploaded_patch_metadata(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / publisher.APK_NAME
            apk.write_bytes(b"target apk")
            patch_file, metadata = root / "apkrawpatch_test.zip", root / "patch_test.json"
            patch_file.write_bytes(b"patch data")
            metadata.write_text('{}')
            output = root / "out"
            notes = "## 更新\n\n- 四态提示与对话列表动画\n"
            arguments = ["publish_release.py", "--apk", str(apk), "--build-tools", "sdk",
                         "--repository", publisher.REPOSITORY, "--commit", "a" * 40,
                         "--previous-tag", "v1.12.2+6", "--notes", notes, "--output", str(output)]
            target = {"versionName": "1.12.2+7", "versionCode": 63, "packageName": publisher.PACKAGE_NAME}
            baseline = {**target, "versionName": "1.12.2+6", "versionCode": 62}
            patch_summary = {"format": "apkraw-1", "fromPatchIndex": 6, "toPatchIndex": 7,
                             "patchFile": patch_file.name, "metaFile": metadata.name}
            with patch("sys.argv", arguments), patch.object(publisher, "inspect_apk", side_effect=[target, baseline]):
                with patch.object(publisher, "prepare_patch", return_value=(patch_file, metadata, patch_summary)):
                    with patch.object(publisher, "run", side_effect=[json.dumps([release("1.12.2+6")]), "", "", ""]) as execute:
                        publisher.main()
            commands = [call.args for call in execute.call_args_list]
            create = next(command for command in commands if command[:3] == ("gh", "release", "create"))
            notes_file = Path(create[create.index("--notes-file") + 1])
            manifest = output / "distribution-release.json"
            self.assertEqual(notes, notes_file.read_text())
            self.assertNotEqual(manifest, notes_file)
            self.assertEqual(notes, json.loads(manifest.read_text())["releaseNotes"])
            self.assertEqual(6, json.loads(manifest.read_text())["fromPatchIndex"])
            self.assertTrue({str(apk), str(patch_file), str(metadata), str(manifest)}.issubset(create))
            self.assertIn("--draft", create)
            self.assertEqual(("gh", "release", "edit"), commands[-1][:3])
            self.assertIn("--draft=false", commands[-1])

    def test_cross_base_or_nonincreasing_patch_is_rejected(self):
        for old, new in [("1.12.2+1", "1.12.3+1"), ("1.12.2+2", "1.12.2+2"), ("1.12.2+3", "1.12.2+2")]:
            with self.subTest(old=old, new=new), self.assertRaises(ValueError):
                publisher.prepare_patch(Path("missing"), Path("missing"), Path("unused"), old, new)


if __name__ == "__main__":
    unittest.main()