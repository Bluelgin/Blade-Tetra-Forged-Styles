# Named Blade Fusion Wave 2

This batch adds three ordered legacy fusions derived from the source blades' native Slash Art / Special Effect language.

## Tsukumo Cross / 付丧·十文字

- Agito saya + Yuzuki Tsukumo hilt
- Source signatures: `Wave Edge` + `Drive Horizontal`
- Real damage is authored by SlashBlade's native `WaveEdge.doSlash(...)` and `Drive.doSlash(...)` entities.
- BladeTetra only sequences the vertical wave, horizontal drive, and a cosmetic cross-close cue.
- The cosmetic close does not add a third damage packet.

## Withered Drive / 枯木·朽驱

- Tagayasan saya + Koseki hilt
- Source signatures: `Drive Vertical` + `Wither Edge`
- Fires SlashBlade's native `Drive Vertical` entity with its original hit and damage handling.
- The native drive carries Koseki's five-second Wither II effect, so only entities actually hit by the projectile are afflicted.

## Piercing Void Moon / 穿界·狐月

- Black Fox saya + Sange hilt
- Source signatures: `Piercing` + `Void Slash`
- A collision-safe short forward dash authors the piercing path and records only actually hit targets.
- The void closure resolves after a short delay and never retargets unrelated entities.

All custom delayed damage remains server-authoritative and respects the existing fusion target validation rules.
