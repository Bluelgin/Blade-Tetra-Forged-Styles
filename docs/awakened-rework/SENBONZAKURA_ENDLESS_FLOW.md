> 本文保留 PR #42 的历史设计。当前千本樱2.1已改为B1–B6与实时碎刃池，见 [SAKURA_21.md](SAKURA_21.md)。

# Senbonzakura 2.0 — dispersed endless Rengeki

Stacked on **Draft PR #41**; no changes to other awakened swords or to normal Rengeki.

## Player loop

1. Earn at least **A** native SlashBlade Rank while condensed.
2. Sneak + successful Slash Art to disperse: sword and sheath fragment; combat render leaves a hilt and a rear half-moon.
3. Start native B1–B7 Rengeki while dispersed. Entry Rank (A/S/SS/SSS) is locked for burn rate.
4. Continue input after the final B7 native hit frame; if Rank remains, B7 loops to B1. Rank debits in 10-tick batches.
5. Stop or exhaust Rank: natural combo exit; the item remains dispersed with a persistent crescent. Condense voluntarily to earn Rank.

Native damage and stun callbacks are unchanged. The former standalone ring/fan/rain SA and automatic petal countercuts are retired in V2. No cosmetic fragment performs independent damage.

## Rank rules

The **native** Rank capability is the single source of truth. Server ticks cap dispersed Rank to a monotonically nonincreasing value, preventing hit gains from replenishing the budget. Normal Rank decay remains intact. No extra persisted score meter.

Default base is 120 points per second at A, configured by awakenedSakuraFlowDrainPerSecond. Rates by **entry** grade:

| Rank | Relative drain | Points per 10 ticks |
| --- | --- | --- |
| A | 100% | 60 |
| S | 88% | 53 |
| SS | 80% | 48 |
| SSS | 67% | 41 |

The legacy Sakura-array config key is retained for compatibility but unused in the V2 flow.

## Rendering

- Client-local rear half-moon follows nearby dispersed wielders every frame.
- Confirmed native hits send throttled cosmetic paths of 9–18 blade sprites from the rear crescent toward the actual hit target.
- Short trails, soft side arcs and overlapping bursts create a bounded Zenith-inspired stream.
- Existing VFX distance/quantity settings apply; no extra AI entities, collisions or damage budget.

## Focused real-game smoke

- [ ] Visible hilt only; sheath and sword body truly absent in dispersed first- and third-person; original item icon intact.
- [ ] Full original Tetra blade and sheath return in condensed form.
- [ ] At A+, native B1–B7 can loop; when Rank expires, early B7 restart is blocked and form stays dispersed.
- [ ] A and SSS have observably different total flow duration.
- [ ] Native Rank never increases net while dispersed, even on repeated attacks.
- [ ] Ordinary native stun, knockback and damage are untouched.
- [ ] No old extra SA array / petal countercuts.
- [ ] Weapon swap, logout and style change clear live flow.
- [ ] First-person readability and multiplayer VFX verified.

Compile/CI is not a substitute for the final Minecraft animation/render acceptance.
