#!/usr/bin/env python3
"""维护工作区 TextMate 目录，校验离线资源并生成文件识别数据。"""
from pathlib import Path
import argparse
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/workspace-textmate'
GENERATED = ROOT / 'app/src/main/java/com/ai/assistance/operit/ui/features/conversation/workspace/editor/textmate/TextMateCatalogData.kt'


def references(value):
    if isinstance(value, dict):
        include = value.get('include')
        if isinstance(include, str) and include and not include.startswith(('#', '$')):
            yield include.split('#')[0]
        for child in value.values():
            yield from references(child)
    elif isinstance(value, list):
        for child in value:
            yield from references(child)


def rule_errors(rule, path):
    """按 TextMate 规则层级检查结构，避免把字符串或列表传给引擎规则接口。"""
    if not isinstance(rule, dict):
        return [path+' 必须是规则对象']
    errors = []
    for field in ['name', 'contentName', 'include', 'match', 'begin', 'end', 'while']:
        if field in rule and rule[field] is not None and not isinstance(rule[field], str):
            errors.append(path+'/'+field+' 必须是字符串')
    if 'patterns' in rule and rule['patterns'] is not None:
        patterns = rule['patterns']
        if not isinstance(patterns, list):
            errors.append(path+'/patterns 必须是规则列表')
        else:
            for index, pattern in enumerate(patterns):
                errors.extend(rule_errors(pattern, path+'/patterns/'+str(index)))
    for field in ['repository', 'injections']:
        if field in rule and rule[field] is not None:
            entries = rule[field]
            if not isinstance(entries, dict):
                errors.append(path+'/'+field+' 必须是规则字典')
            else:
                for name, entry in entries.items():
                    errors.extend(rule_errors(entry, path+'/'+field+'/'+name))
    for field in ['captures', 'beginCaptures', 'endCaptures', 'whileCaptures']:
        if field in rule and rule[field] is not None:
            captures = rule[field]
            if not isinstance(captures, dict):
                errors.append(path+'/'+field+' 必须是捕获字典')
            else:
                for capture_id, capture in captures.items():
                    if not capture_id.isascii() or not capture_id.isdecimal():
                        errors.append(path+'/'+field+'/'+capture_id+' 不是数字捕获组')
                    errors.extend(rule_errors(capture, path+'/'+field+'/'+capture_id))
    return errors


def generate(manifest):
    def block(name, rows):
        for row in rows:
            if any(char in row for char in ['"', '\\', '$']):
                raise ValueError('目录字段包含未转义的 Kotlin 字符串字符: '+row)
        return '    val '+name+' = """\n'+''.join('        '+row+'\n' for row in rows)+'    """.trimIndent()\n'
    injections = {}
    by_id = {grammar['name']: grammar for grammar in manifest['grammars']}
    for grammar in manifest['grammars']:
        for scope in grammar.get('injectTo', []):
            injections.setdefault(scope, []).append(grammar['scopeName'])
            if scope in manifest['scopeAliases']:
                canonical = by_id[manifest['scopeAliases'][scope]]['scopeName']
                injections.setdefault(canonical, []).append(grammar['scopeName'])
    rows = []
    for grammar in manifest['grammars']:
        aliases = ','.join(grammar.get('aliases', []))
        rows.append('|'.join([grammar['name'], grammar['scopeName'], aliases, '1' if grammar['isLanguage'] else '0']))
    body = block('grammars', rows)
    for name, key in [('aliases', 'aliases'), ('extensions', 'extensions'), ('fileNames', 'fileNames'), ('fileNamePrefixes', 'fileNamePrefixes'), ('scopeAliases', 'scopeAliases')]:
        body += '\n'+block(name, [key+'|'+value for key, value in sorted(manifest[key].items())])
    body += '\n'+block('injections', [scope+'|'+','.join(sorted(set(values))) for scope, values in sorted(injections.items())])
    body += '\n'+block('optionalScopes', sorted(manifest['optionalScopes']))
    return 'package com.ai.assistance.operit.ui.features.conversation.workspace.editor.textmate\n\n// 由 tools/workspace_textmate.py 从 catalog.json 生成；请修改目录后重新生成。\ninternal object TextMateCatalogData {\n'+body+'}\n'


def check(manifest):
    errors = []
    grammars = manifest['grammars']
    ids = {grammar['name'] for grammar in grammars}
    scopes = {grammar['scopeName'] for grammar in grammars}
    languages = {grammar['name'] for grammar in grammars if grammar['isLanguage']}
    if len(ids) != len(grammars) or len(scopes) != len(grammars):
        errors.append('语法标识或作用域重复')
    known_names = languages | set(manifest['aliases'])
    for mapping in ['extensions', 'fileNames', 'fileNamePrefixes']:
        for name, target in manifest[mapping].items():
            if target not in known_names:
                errors.append(mapping+' 存在未知语言: '+name+' -> '+target)
    for name, target in manifest['aliases'].items():
        if target not in languages | {'text'}:
            errors.append('语言别名目标不存在: '+name)
    for name, target in manifest['scopeAliases'].items():
        if target not in ids:
            errors.append('作用域别名目标不存在: '+name)
    actual_files = {path.stem for path in (ASSETS / 'grammars').glob('*.json')}
    if actual_files != ids:
        errors.append('语法文件清单与目录不一致')
    required_optional = set()
    for grammar in grammars:
        path = ASSETS / 'grammars' / (grammar['name']+'.json')
        if not path.is_file():
            errors.append('缺少语法: '+str(path))
            continue
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != grammar['sha256']:
            errors.append('语法校验值不一致: '+grammar['name'])
        parsed = json.loads(data)
        errors.extend(rule_errors(parsed, grammar['name']))
        if parsed['scopeName'] != grammar['scopeName']:
            errors.append('语法作用域不一致: '+grammar['name'])
        for scope in references(parsed):
            if scope not in scopes and scope not in manifest['scopeAliases']:
                required_optional.add(scope)
                if scope not in manifest['optionalScopes']:
                    errors.append('未登记的外部嵌入作用域: '+scope)
        for scope in grammar.get('injectTo', []):
            if scope not in scopes and scope not in manifest['scopeAliases']:
                errors.append('注入目标未登记: '+grammar['name']+' -> '+scope)
    if required_optional != set(manifest['optionalScopes']):
        errors.append('可选外部作用域清单过期')
    for license_file, record in manifest['noticeFiles'].items():
        path = ASSETS / license_file
        if not path.is_file():
            errors.append('缺少许可证: '+license_file)
        elif hashlib.sha256(path.read_bytes()).hexdigest() != record['sha256']:
            errors.append('许可证或通知校验值不一致: '+license_file)
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--generate', action='store_true', help='重新生成 Kotlin 文件识别目录')
    arguments = parser.parse_args()
    manifest = json.loads((ASSETS / 'catalog.json').read_text(encoding='utf-8'))
    errors = check(manifest)
    expected = generate(manifest)
    if arguments.generate and not errors:
        GENERATED.parent.mkdir(parents=True, exist_ok=True)
        GENERATED.write_text(expected, encoding='utf-8')
    elif not GENERATED.is_file() or GENERATED.read_text(encoding='utf-8') != expected:
        errors.append('生成目录与 catalog.json 不一致，请运行 --generate')
    for error in errors:
        print('ERROR:', error)
    print(json.dumps({'grammars': len(manifest['grammars']), 'languages': sum(grammar['isLanguage'] for grammar in manifest['grammars']), 'extensions': len(manifest['extensions']), 'fileNames': len(manifest['fileNames']), 'errors': len(errors)}, ensure_ascii=False))
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
