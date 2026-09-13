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
- First vertical pressure wave records targets; a weaker delayed withered second drive only resolves against valid first-stage targets still near the authored path.

## Piercing Void Moon / 穿界·狐月

- Black Fox saya + Sange hilt
- Source signatures: `Piercing` + `Void Slash`
- A collision-safe short forward dash authors the piercing path and records only actually hit targets.
- The void closure resolves after a short delay and never retargets unrelated entities.

All custom delayed damage remains server-authoritative and respects the existing fusion target validation rules.
