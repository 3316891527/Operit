"""固定资源的源码输入摘要；开发构建不把当前分支冒充已发布的资源源码。"""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import re
import subprocess

SHARED_INPUTS = ('examples/types', 'examples/tsconfig.json',
    'tools/example_packages/sync_example_packages.py', 'tools/example_packages/packages_whitelist.txt')


SOURCE_PATHS = (('examples/', 'plugins/examples/'),
    ('tools/example_packages/', 'plugins/tooling/example_packages/'))
def source_inputs(source: str) -> tuple[str, ...]:
    return ('examples/' + source, 'examples/' + source.removesuffix('.js') + '.ts', *SHARED_INPUTS)
def physical_input_paths(logical_path: str) -> tuple[str, ...]:
    for logical_root, physical_root in SOURCE_PATHS:
        if logical_path.startswith(logical_root):
            return logical_path, physical_root + logical_path[len(logical_root):]
    return (logical_path,)
def logical_input_path(physical_path: str) -> str:
    for logical_root, physical_root in SOURCE_PATHS:
        if physical_path.startswith(physical_root):
            return logical_root + physical_path[len(physical_root):]
    return physical_path
def fingerprint(files: dict[str, str], source: str) -> str:
    selected = [(name, value) for name, value in sorted(files.items())
        if any(name == root or name.startswith(root + '/') for root in source_inputs(source))]
    if not selected:
        raise ValueError('内置扩展缺少源码摘要：' + source)
    return hashlib.sha256(json.dumps(selected, ensure_ascii=False).encode()).hexdigest()


def load_snapshot(path: Path, release_tag: str) -> dict:
    snapshot = json.loads(path.read_text())
    if snapshot['schemaVersion'] != 1 or snapshot['releaseTag'] != release_tag:
        raise ValueError('固定资源源码快照与资源标签不一致')
    if re.fullmatch(r'[0-9a-f]{40}', snapshot['sourceRevision']) is None:
        raise ValueError('固定资源源码提交格式无效')
    sources = snapshot['sources']
    if not sources or len(sources) != len(set(sources)):
        raise ValueError('固定资源源码列表为空或重复')
    files = snapshot['files']
    if not files or len(dict(files)) != len(files):
        raise ValueError('固定资源源码摘要为空或重复')
    for name, value in files:
        if not name.startswith(('examples/', 'tools/example_packages/')) or '..' in Path(name).parts:
            raise ValueError('固定资源源码摘要路径无效：' + name)
        if re.fullmatch(r'[0-9a-f]{64}', value) is None:
            raise ValueError('固定资源源码摘要格式无效：' + name)
    return snapshot


def create_snapshot(root: Path, sources: list[str], release_tag: str, revision: str) -> dict:
    logical_inputs = sorted({name for source in sources for name in source_inputs(source)})
    inputs = sorted({path for name in logical_inputs for path in physical_input_paths(name)})
    entries = subprocess.check_output(['git', 'ls-tree', '-r', '-z', revision, '--', *inputs], cwd=root).split(b'\0')
    objects = {}
    for entry in entries:
        if not entry:
            continue
        metadata, name = entry.split(b'\t', 1)
        _, kind, object_id = metadata.split()
        if kind != b'blob':
            raise ValueError('固定资源源码输入不是文件：' + name.decode())
        logical_name = logical_input_path(name.decode())
        if logical_name in objects:
            raise ValueError('固定资源源码同时存在新旧路径：' + logical_name)
        objects[logical_name] = object_id
    names = sorted(objects)
    raw = subprocess.check_output(['git', 'cat-file', '--batch'], cwd=root,
        input=b'\n'.join(objects[name] for name in names) + b'\n')
    stream = io.BytesIO(raw)
    files = []
    for name in names:
        header = stream.readline().split()
        if len(header) != 3 or header[0] != objects[name] or header[1] != b'blob':
            raise ValueError('固定资源源码对象不可读：' + name)
        size = int(header[2])
        content = stream.read(size)
        if len(content) != size or stream.read(1) != b'\n':
            raise ValueError('固定资源源码对象不完整：' + name)
        files.append((name, hashlib.sha256(content).hexdigest()))
    return {'schemaVersion': 1, 'releaseTag': release_tag, 'sourceRevision': revision,
        'sources': sources, 'files': files}
