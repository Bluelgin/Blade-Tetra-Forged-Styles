#!/usr/bin/env python3
"""Run one manifest suite in an isolated build directory and require proof markers."""
import argparse
import hashlib
import json
import os
import signal
import re
import shutil
import subprocess
import threading
import urllib.request
from plan import ROOT, load_config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', choices=sorted(load_config()['suites']))
    parser.add_argument('--slashblade', choices=('1.9.63', '1.9.65'),
                        help='Select the provider build for the export client fixture')
    args = parser.parse_args()
    suite = load_config()['suites'][args.suite]
    directory = ROOT / 'build' / suite['directory']
    if not directory.resolve().is_relative_to((ROOT / 'build').resolve()) or directory == ROOT / 'build':
        raise ValueError('Suite directory must be below build/')
    if directory.exists():
        shutil.rmtree(directory)
    directory.mkdir(parents=True)
    if suite['kind'] == 'server':
        (directory / 'eula.txt').write_text('eula=true\n')
        terrain = {'biome': 'minecraft:plains', 'layers': [
            {'block': 'minecraft:bedrock', 'height': 1},
            {'block': 'minecraft:dirt', 'height': 2},
            {'block': 'minecraft:grass_block', 'height': 1}]}
        (directory / 'server.properties').write_text(
            'level-seed=158\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
            + 'generator-settings=' + json.dumps(terrain, separators=(',', ':')) + '\n')
    else:
        (directory / 'options.txt').write_text('lang:zh_cn\nguiScale:4\n')
    properties = [suite['property'], *suite['flags']]
    if args.slashblade is not None and args.suite != 'export_client':
        parser.error('--slashblade is only used by export_client')
    if args.slashblade == '1.9.65':
        properties.append('slashblade1965')
    if not all(re.fullmatch(r'[A-Za-z0-9_]+', prop) for prop in properties):
        raise ValueError('Invalid Gradle profile')
    if 'yakumoTest' in properties:
        jar = ROOT / 'run/test-libs/Yakumoblade-1.1.4-1.20.1_Forge.jar'
        jar.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen('https://edge.forgecdn.net/files/7264/677/Yakumoblade-1.1.4-1.20.1_Forge.jar', timeout=60) as response:
            jar.write_bytes(response.read())
        print('Yakumo fixture SHA-256:', hashlib.sha256(jar.read_bytes()).hexdigest(), flush=True)
    command = ['./gradlew', '--no-daemon', 'runClient' if suite['kind'] == 'client' else 'runServer']
    command.extend('-P' + prop for prop in properties)
    if suite['kind'] == 'client':
        command = ['xvfb-run', '-a', *command]
    log = ROOT / 'build' / (suite['directory'] + '.log')
    run(command, log, args.suite, suite['markers'])
    if args.suite in ('legacy_world_migration', 'legacy_world_migration_client'):
        reopen_log = log.with_name(log.stem + '-reopen.log')
        run([*command, '-PlegacyWorldMigrationReopen'], reopen_log, args.suite + ' reopen',
            ['LEGACY_WORLD_MIGRATION_REOPEN_PASS'])
    print(f'SUITE_PASS: {args.suite}')


def run(command, log, name, markers):
    with log.open('w') as target:
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True)
        expired = threading.Event()
        def terminate():
            expired.set()
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        watchdog = threading.Timer(600, terminate)
        watchdog.daemon = True
        watchdog.start()
        try:
            for line in process.stdout:
                print(line, end='', flush=True)
                target.write(line)
            status = process.wait()
        finally:
            watchdog.cancel()
        if expired.is_set():
            status = -1
    missing = [marker for marker in markers if marker not in log.read_text()]
    if status or missing:
        raise SystemExit(f'Suite {name} failed: process={status}; missing markers={missing}')


if __name__ == '__main__':
    main()
