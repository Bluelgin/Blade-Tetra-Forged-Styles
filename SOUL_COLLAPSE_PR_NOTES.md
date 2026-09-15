# PR validation notes

This stacked branch depends on `feat/dead-thought-vfx` / PR #12. Keep the PR base on that branch until #12 is merged.

Automated checks expected before marking ready:

- `./gradlew build`
- existing Dead Thought erosion tests
- detached-collapse visual math tests
- event catalog uniqueness

Manual checks still required before merge:

- boss with ordinary damage cap survives first soul-zero handshake as SOUL_BROKEN
- no 0.01 HP tick clamp after SOUL_BROKEN
- next complete Final Scene requests terminal collapse once
- canceled terminal death does not enter a retry loop
- collapse VFX finishes after target death/removal
