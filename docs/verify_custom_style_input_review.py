"""Verify real client KeyMapping -> vanilla use -> server commits and exchange UI.

Usage: python docs/verify_custom_style_input_review.py path/to/client-review.log
The isolated review is enabled by -PcustomStyleInputReview, never by normal play.
"""
import collections
import re
import sys
from pathlib import Path

source = Path(sys.argv[1]).read_text(encoding='utf-8', errors='replace')
assert 'CUSTOM_CLIENT_REVIEW_FAIL' not in source, 'Client review failed'
assert 'CUSTOM_CLIENT_UI_PASS' in source, 'UI review did not finish'
assert 'CUSTOM_CLIENT_INPUT_REVIEW_DONE' in source, 'Input review did not finish'
assert len(re.findall(r'CUSTOM_KEY_TAP case=', source)) == 36, 'Expected 36 actual key taps'
cases = {blade: int(case) for case, blade in re.findall(r'CUSTOM_INPUT_CASE case=(\d+) blade=(\S+)', source)}
actual = collections.defaultdict(list)
for side, blade, result in re.findall(r'CUSTOM_CURSOR side=(\w+).*?blade=(\S+).*?result=(\S+)', source):
    if result != 'slashblade:none':
        actual[cases[blade], side].append(result.removeprefix('slashblade:'))
routes = [
    ['upperslash', 'upperslash', 'rapid_slash'],
    ['combo_a1', 'combo_b1', 'combo_a2'],
    ['combo_a1', 'combo_a1', 'combo_b1'],
    ['combo_a1', 'combo_b1', 'combo_a2', 'combo_b2', 'combo_a3', 'combo_c'],
    ['combo_a1'],
    ['rapid_slash', 'combo_a1'],
]
for case, route in enumerate(routes):
    expected = [route[index % len(route)] for index in range(6)]
    for side in ('client', 'server'):
        assert actual[case, side] == expected, (case, side, expected, actual[case, side])
print('PASS: 36 vanilla key taps; all six authored routes match client/server commits; exchange UI completed')
