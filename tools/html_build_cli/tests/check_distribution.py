import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import queue
import shutil
import socket
import subprocess
import tempfile
import threading
import unittest

parser = argparse.ArgumentParser()
parser.add_argument('--binary', type=Path, required=True)
parser.add_argument('--sysroot', type=Path, required=True)
arguments, remaining = parser.parse_known_args()
BINARY = arguments.binary.resolve()
QEMU = shutil.which('qemu-aarch64')
if QEMU is None:
    raise RuntimeError('缺少 ARM64 分发回归所需的 qemu-aarch64')
ROOT = Path(__file__).resolve().parents[1]
NEXT = 0


class BuildDistributionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="operit build '$ 中文 ")
        self.directory = Path(self.temporary.name)
        self.project = self.directory / 'web project'
        self.project.mkdir()
        (self.project / 'index.html').write_bytes(b'<h1>demo</h1>\n')
        self.output = self.directory / 'dist'
        self.errors = []
        self.workers = []

    def tearDown(self):
        for worker in self.workers:
            worker.join(15)
            self.assertFalse(worker.is_alive(), '构建控制模拟服务没有结束')
        self.temporary.cleanup()
        self.assertEqual([], self.errors)

    def command(self, binary=BINARY):
        return [QEMU, '-L', str(arguments.sysroot), str(binary)]

    def server(self, action, target='apk'):
        global NEXT
        NEXT += 1
        channel = f'operit.build.{os.getpid()}{NEXT:04d}'
        listener = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        listener.bind('\0' + channel)
        listener.listen(1)
        listener.settimeout(15)

        def run():
            try:
                with listener, listener.accept()[0] as connection:
                    connection.settimeout(15)
                    stream = connection.makefile('rb')
                    request = json.loads(stream.readline())
                    self.assertEqual(1, request['version'])
                    self.assertEqual(target, request['target'])
                    action(connection, stream, request)
            except (BrokenPipeError, ConnectionResetError):
                pass
            except Exception as failure:
                self.errors.append(repr(failure))

        worker = threading.Thread(target=run, daemon=True)
        self.workers.append(worker)
        worker.start()
        return channel

    @staticmethod
    def frame(connection, **data):
        connection.sendall((json.dumps({'version': 1, **data}, ensure_ascii=False) + '\n').encode())

    def upload(self, stream, request):
        paths = []
        for _ in range(request['files']):
            info = json.loads(stream.readline())
            content = stream.read(info['size'])
            self.assertEqual(info['size'], len(content))
            paths.append(info['path'])
        if request['iconBytes']:
            self.assertEqual(request['iconBytes'], len(stream.read(request['iconBytes'])))
        self.assertEqual(b'', stream.read(1))
        self.assertIn('index.html', paths)
        return paths

    def test_real_time_progress_and_binary_artifacts_keep_their_contents(self):
        release = threading.Event()
        payload = b'APK\0\n\xff\x01binary'

        def action(connection, stream, request):
            self.upload(stream, request)
            self.frame(connection, kind='progress', progress=0.5, message='正在打包，等待签名')
            self.assertTrue(release.wait(10), '客户端没有实时刷新进度')
            self.frame(connection, kind='artifact', path='Demo.apk', size=len(payload))
            connection.sendall(payload)
            self.frame(connection, kind='result', success=True, message='完成', outputs=['Demo.apk'])

        channel = self.server(action)
        environment = dict(os.environ, OPERIT_BUILD_SOCKET=channel)
        process = subprocess.Popen(self.command() + ['html', str(self.project), '--name', 'Demo', '--output', str(self.output)],
                                   env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        lines = queue.Queue()
        collected = []

        def read_lines():
            for line in process.stdout:
                collected.append(line)
                lines.put(line)

        reader = threading.Thread(target=read_lines, daemon=True)
        reader.start()
        try:
            while True:
                if '正在打包' in lines.get(timeout=10):
                    break
            self.assertFalse((self.output / 'Demo.apk').exists())
            release.set()
            self.assertEqual(0, process.wait(timeout=15), process.stderr.read())
            reader.join(5)
            self.assertEqual(payload, (self.output / 'Demo.apk').read_bytes())
            self.assertIn('构建完成', ''.join(collected))
            self.assertFalse(list(self.output.glob('.operit-build-*')))
        finally:
            release.set()
            if process.poll() is None:
                process.kill()
                process.wait()

    def test_windows_artifacts_keep_the_complete_runtime_directory(self):
        payloads = {
            'Demo/Demo.exe': b'MZ\0demo',
            'Demo/flutter_windows.dll': b'dll bytes',
            'Demo/data/flutter_assets/assets/web_content/index.html': b'<h1>demo</h1>',
        }
        def action(connection, stream, request):
            self.upload(stream, request)
            self.frame(connection, kind='directory', path='Demo/data/empty')
            for path, content in payloads.items():
                self.frame(connection, kind='artifact', path=path, size=len(content))
                connection.sendall(content)
            self.frame(connection, kind='result', success=True, message='完成', outputs=['Demo/Demo.exe'])
        result = subprocess.run(self.command() + ['html', str(self.project), '--name', 'Demo', '--target', 'exe', '--output', str(self.output)],
                                env=dict(os.environ, OPERIT_BUILD_SOCKET=self.server(action, target='exe')), capture_output=True, text=True, timeout=20)
        self.assertEqual(0, result.returncode, result.stderr)
        for path, content in payloads.items():
            self.assertEqual(content, (self.output / path).read_bytes())
        self.assertTrue((self.output / 'Demo/data/empty').is_dir())

    def test_default_output_inside_project_is_excluded_from_upload(self):
        output = self.project / 'dist'
        output.mkdir()
        (output / 'old.apk').write_bytes(b'old artifact')
        def action(connection, stream, request):
            self.assertEqual(['index.html'], self.upload(stream, request))
            self.frame(connection, kind='artifact', path='Demo.apk', size=3)
            connection.sendall(b'apk')
            self.frame(connection, kind='result', success=True, message='完成', outputs=['Demo.apk'])
        result = subprocess.run(self.command() + ['html', '.', '--name', 'Demo'], cwd=self.project,
                                env=dict(os.environ, OPERIT_BUILD_SOCKET=self.server(action)), capture_output=True, text=True, timeout=20)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b'apk', (output / 'Demo.apk').read_bytes())
        self.assertEqual(b'old artifact', (output / 'old.apk').read_bytes())

    def test_output_created_during_transfer_is_never_replaced(self):
        def action(connection, stream, request):
            self.upload(stream, request)
            self.output.mkdir(exist_ok=True)
            (self.output / 'Demo.apk').write_bytes(b'another task')
            self.frame(connection, kind='artifact', path='Demo.apk', size=3)
            connection.sendall(b'apk')
            self.frame(connection, kind='result', success=True, message='完成', outputs=['Demo.apk'])
        result = subprocess.run(self.command() + ['html', str(self.project), '--name', 'Demo', '--output', str(self.output)],
                                env=dict(os.environ, OPERIT_BUILD_SOCKET=self.server(action)), capture_output=True, text=True, timeout=20)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'another task', (self.output / 'Demo.apk').read_bytes())
        self.assertFalse(list(self.output.glob('.operit-build-*')))

    def test_service_errors_do_not_publish_partial_artifacts(self):
        def action(connection, stream, request):
            self.upload(stream, request)
            self.frame(connection, kind='result', success=False, message='缺少 Android 模板', outputs=[])
        result = subprocess.run(self.command() + ['html', str(self.project), '--name', 'Demo', '--output', str(self.output)],
                                env=dict(os.environ, OPERIT_BUILD_SOCKET=self.server(action)), capture_output=True, text=True, timeout=20)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('缺少 Android 模板', result.stderr)
        self.assertFalse((self.output / 'Demo.apk').exists())
        self.assertFalse(list(self.output.glob('.operit-build-*')))

    def test_existing_outputs_are_not_replaced(self):
        self.output.mkdir()
        artifact = self.output / 'Demo.apk'
        artifact.write_bytes(b'previous')
        result = subprocess.run(self.command() + ['html', str(self.project), '--name', 'Demo', '--output', str(self.output)],
                                capture_output=True, text=True, timeout=10)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'previous', artifact.read_bytes())

    def test_installation_pins_the_host_channel_and_verifies_payload(self):
        command_directory = self.directory / 'bin with spaces'
        digest = hashlib.sha256(BINARY.read_bytes()).hexdigest()
        subprocess.run(['bash', str(ROOT / 'install.sh'), str(BINARY), digest, str(command_directory), 'operit.build.10234'], check=True)
        wrapper = command_directory / 'build'
        self.assertIn('OPERIT_BUILD_SOCKET=operit.build.10234', wrapper.read_text())
        executable = command_directory / ('.build-bin-' + digest)
        self.assertEqual(BINARY.read_bytes(), executable.read_bytes())
        result = subprocess.run(self.command(executable) + ['--help'], capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode)
        self.assertIn('build html', result.stdout)
        failed = subprocess.run(['bash', str(ROOT / 'install.sh'), str(BINARY), '0' * 64, str(command_directory), 'operit.build.10234'], capture_output=True)
        self.assertNotEqual(0, failed.returncode)
        self.assertEqual(BINARY.read_bytes(), executable.read_bytes())

    def test_distribution_manifest_verifies_the_actual_arm64_binary(self):
        spec = importlib.util.spec_from_file_location('html_build_assets', ROOT / 'package_assets.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        output = self.directory / 'assets'
        module.package_assets(BINARY, output)
        module.verify_assets(output)
        (output / 'html_build_command/aarch64/build').write_bytes(b'broken')
        with self.assertRaises(ValueError):
            module.verify_assets(output)


if __name__ == '__main__':
    unittest.main(argv=[__file__, *remaining])
