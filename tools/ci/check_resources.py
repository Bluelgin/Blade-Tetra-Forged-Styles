#!/usr/bin/env python3
"""Check runtime JSON syntax without starting Minecraft or optional providers."""
import json
from plan import ROOT


def main():
    count, errors = 0, []
    for directory in ['src/main/resources', 'src/generated/resources']:
        for path in (ROOT / directory).rglob('*'):
            if path.suffix not in {'.json', '.mcmeta'}:
                continue
            count += 1
            try:
                json.loads(path.read_text(encoding='utf-8-sig'))
            except (ValueError, UnicodeError) as error:
                errors.append(f'{path.relative_to(ROOT)}: {error}')
    if not count or errors:
        raise SystemExit('\n'.join(errors) or 'No runtime JSON resources found')
    print(f'Runtime JSON/metadata syntax: {count} files passed')


if __name__ == '__main__':
    main()
