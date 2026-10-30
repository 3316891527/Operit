from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from package_assets import package_assets, verify_assets


class DistributionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="operit patch '$ ")
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.payload = self.directory / 'source/apply_patch'
        self.payload.parent.mkdir()
        shutil.copyfile(BINARY, self.payload)
        self.digest = hashlib.sha256(self.payload.read_bytes()).hexdigest()
        self.command_dir = self.directory / 'ubuntu/usr/local/bin'
        self.installed = self.command_dir / 'apply_patch'

    def install(self, digest=None):
        return subprocess.run(
            ['bash', str(ROOT / 'install.sh'), str(self.payload), self.digest if digest is None else digest, str(self.command_dir)],
            capture_output=True, text=True, timeout=30,
        )

    def require_install(self):
        result = self.install()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.installed.read_bytes(), self.payload.read_bytes())
        self.assertEqual(self.installed.stat().st_mode & 0o777, 0o755)

    def run_command(self, arguments=(), stdin=''):
        return subprocess.run(
            ['qemu-aarch64', '-L', SYSROOT, str(self.installed), *arguments],
            input=stdin, cwd=self.directory, capture_output=True, text=True, timeout=30,
        )

    def test_first_install_and_path(self):
        self.require_install()
        env = dict(os.environ, PATH=str(self.command_dir) + os.pathsep + os.environ['PATH'])
        result = subprocess.run(['bash', '-c', 'command -v apply_patch'], env=env, capture_output=True, text=True, timeout=10, check=True)
        self.assertEqual(result.stdout.strip(), str(self.installed))
        result = self.run_command(stdin='*** Begin Patch\n*** Add File: stdin.txt\n+正文\n*** End Patch')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, 'Success. Updated the following files:\nA stdin.txt\n')
        self.assertEqual((self.directory / 'stdin.txt').read_text(), '正文\n')

    def test_same_version_keeps_inode(self):
        self.require_install()
        before = self.installed.stat().st_ino
        self.require_install()
        self.assertEqual(self.installed.stat().st_ino, before)

    def test_upgrade_replaces_corrupted_copy(self):
        self.require_install()
        self.installed.write_bytes(b'old command')
        before = self.installed.stat().st_ino
        self.require_install()
        self.assertNotEqual(self.installed.stat().st_ino, before)
        result = self.run_command()
        self.assertEqual(result.returncode, 2, result.stderr)

    def test_invalid_payload_leaves_installed_copy(self):
        self.require_install()
        original = self.installed.read_bytes()
        self.payload.write_bytes(b'corrupted payload')
        self.assertNotEqual(self.install().returncode, 0)
        self.assertEqual(self.installed.read_bytes(), original)
        self.assertNotEqual(self.install('invalid').returncode, 0)

    def test_destination_symlink_is_replaced(self):
        victim = self.directory / 'unrelated.txt'
        victim.write_text('keep')
        self.command_dir.mkdir(parents=True)
        self.installed.symlink_to(victim)
        self.require_install()
        self.assertFalse(self.installed.is_symlink())
        self.assertEqual(victim.read_text(), 'keep')

    def test_concurrent_installation(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
            results = list(executor.map(lambda _: self.install(), range(6)))
        for result in results:
            self.assertEqual(result.returncode, 0, result.stderr)
        self.require_install()
        self.assertFalse(list(self.command_dir.glob('.apply_patch.*')))

    def test_asset_packaging_and_tamper_detection(self):
        output = self.directory / 'assets'
        package_assets(self.payload, output)
        verify_assets(output)
        binary = output / 'terminal_commands/aarch64/apply_patch'
        binary.write_bytes(binary.read_bytes() + b'tampered')
        with self.assertRaisesRegex(ValueError, '二进制校验失败'):
            verify_assets(output)

    def test_arm64_exit_codes_and_absolute_path(self):
        self.require_install()
        self.assertEqual(self.run_command().returncode, 2)
        self.assertEqual(self.run_command(['one', 'two']).returncode, 2)
        self.assertEqual(self.run_command(['invalid patch']).returncode, 1)
        target = self.directory / 'absolute.txt'
        result = self.run_command([f'*** Begin Patch\n*** Add File: {target}\n+absolute\n*** End Patch'])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(target.read_text(), 'absolute\n')

    def test_all_original_scenarios_on_arm64(self):
        self.require_install()
        for scenario in sorted((ROOT / 'tests/fixtures/scenarios').iterdir()):
            if not scenario.is_dir():
                continue
            with self.subTest(scenario=scenario.name):
                work = self.directory / scenario.name
                work.mkdir()
                source = scenario / 'input'
                if source.is_dir():
                    shutil.copytree(source, work, dirs_exist_ok=True)
                result = subprocess.run(
                    ['qemu-aarch64', '-L', SYSROOT, str(self.installed), (scenario / 'patch.txt').read_text()],
                    cwd=work, capture_output=True, timeout=30,
                )
                def snapshot(directory):
                    return {str(path.relative_to(directory)): None if path.is_dir() else path.read_bytes() for path in directory.rglob('*')}
                self.assertEqual(snapshot(work), snapshot(scenario / 'expected'), result.stderr)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--binary', type=Path, required=True)
    parser.add_argument('--sysroot', required=True)
    options = parser.parse_args()
    BINARY = options.binary.resolve(strict=True)
    SYSROOT = options.sysroot
    unittest.main(argv=[sys.argv[0]], verbosity=2)
