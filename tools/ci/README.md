# Validation ownership and extension points

The production mod lives in `src/main`. JUnit tests live in `src/test`. Real Forge
smoke tests, screenshot tools, sample builders and benchmarks live in
`src/integrationTest`; they retain their Java packages for package-private access.
`NativeFusionPresentationAudit` is production animation data, not a test helper.

## Running checks

- `./gradlew build integrationTestClasses verifyProductionJar`: production build,
  all JUnit tests, development-tool compilation and actual JAR isolation check.
- Existing launches such as `./gradlew runServer -PnamedIngotSmoke` still work.
  A known development profile automatically includes the extra source set. Normal
  `runClient`, `runServer`, data generation and release JARs do not include it.
- `python3 tools/ci/run_suite.py forging`: run a single named suite, require a
  successful process and every expected proof marker. Client suites use Xvfb.
  These commands erase only their isolated directory below `build/`.
- Actions manual **Validation** input: `auto`, `full` (base suites), or `compat`
  (full base plus the pinned-version SJAP/Yakumo profiles).

## Planning rules

`tools/ci/suites.json` owns suite launch arguments, proof markers, dependency
families and ordered path rules. First matching rule wins for each changed file;
multiple files union their requirements. Put narrow rules before broad rules.
JUnit changes follow their production package; fixtures and unknown paths fall
back to the full base set. Git diff disables rename detection so removed and added
paths are both considered. Missing/unreadable diff bases also run the full base
set. If the planner itself cannot run, CI attempts full planning, then fails the
required gate if that is impossible; it must never produce a false green skip.

README/developer docs and source artwork have a small explicit cheap allowlist.
Game translations, Patchouli pages, models and recipes are runtime resources.
Shared damage, storage, registration, networking and core Mixins expand coverage.
Mikage is selected for its own changes or complete validation, not every ordinary
PR. Build/dependency changes include the configured third-party profiles.

To add a feature: add/reuse a suite, add narrow path rules AND its shared
upstream dependencies, and add a boundary case to `test_plan.py`. Include native
suites in `groups.full` when they belong to the base release contract. Optional
providers belong to `groups.compat`. Keep dependency families small; do not add a
new framework or a new native launch for every class. Never classify unknown
production paths as documentation.

## CI results

The plan artifact and Actions summary explain selected suites. JUnit remains
whole because its cost is small. Native suites are isolated matrix jobs, capped
at two concurrent game processes. A failed selected suite fails **Validation
gate**, even if another suite passes. Failure logs and screenshots are retained.
New PR commits cancel outdated runs. Required branch protection should target the
stable **Validation gate** name (changing repository protection is an owner task).

The initial infrastructure change runs the full base and configured addon suites
because it changes the launch machinery itself. Future resource or feature-only
PRs use their narrower plan. Full validation still does not demonstrate remote
multiplayer, AE2 compatibility or every provider unless explicitly exercised.

## Release boundary

Tooltip review input is a development-only client Mixin plus reflection in the
review tool. Production tooltip code reads actual Shift input and scroll events;
it has no reference to a review class. `verifyProductionJar` checks concrete helper
class names (including inner classes) and the development Mixin config in the JAR,
rather than banning words such as `Audit` that may name production behavior.

Existing behavior tests are retained. Source-string/line-budget assertions can be
reviewed separately; this change does not remove regression coverage to obtain a
faster pipeline. Tests sharing a world must not be combined until their cleanup
and event-listener isolation have been demonstrated.
