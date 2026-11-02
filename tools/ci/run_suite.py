#!/usr/bin/env python3
"""Run one manifest suite in an isolated build directory and require proof markers."""
import argparse
import hashlib
import re
import shutil
import subprocess
import urllib.request
from plan import ROOT, load_config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', choices=sorted(load_config()['suites']))
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
    else:
        (directory / 'options.txt').write_text('lang:zh_cn\nguiScale:4\n')
    properties = [suite['property'], *suite['flags']]
    if not all(re.fullmatch(r'[A-Za-z0-9_]+', prop) for prop in properties):
        raise ValueError('Invalid Gradle profile')
    if 'yakumoTest' in properties:
        jar = ROOT / 'run/test-libs/Yakumoblade-1.1.4-1.20.1_Forge.jar'
        jar.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen('https://edge.forgecdn.net/files/7264/677/Yakumoblade-1.1.4-1.20.1_Forge.jar', timeout=60) as response:
            jar.write_bytes(response.read())
        print('Yakumo fixture SHA-256:', hashlib.sha256(jar.read_bytes()).hexdigest(), flush=True)
    command = ['./gradlew', 'runClient' if suite['kind'] == 'client' else 'runServer']
    command.extend('-P' + prop for prop in properties)
    if suite['kind'] == 'client':
        command = ['xvfb-run', '-a', *command]
    log = ROOT / 'build' / (suite['directory'] + '.log')
    with log.open('w') as target:
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            print(line, end='', flush=True)
            target.write(line)
        status = process.wait()
    missing = [marker for marker in suite['markers'] if marker not in log.read_text()]
    if status or missing:
        raise SystemExit(f'Suite {args.suite} failed: process={status}; missing markers={missing}')
    print(f'SUITE_PASS: {args.suite}')


if __name__ == '__main__':
    main()
