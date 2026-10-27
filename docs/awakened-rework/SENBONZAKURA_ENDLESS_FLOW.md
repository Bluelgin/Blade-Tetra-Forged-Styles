> 本文保留 PR #42 的历史设计。当前千本樱2.1已改为B1–B6与实时碎刃池，见 [SAKURA_21.md](SAKURA_21.md)。

# Senbonzakura 2.0 — dispersed endless Rengeki

Stacked on **Draft PR #41**; no changes to other awakened swords or to normal Rengeki.

## Player loop (current 2.2)

1. Earn at least **A** native SlashBlade Rank while condensed.
2. Use native aerial Back+Sneak+Right Click (`slashblade:aerial_cleave`) to disperse, even on a miss. Only the real blade dissolves; the native sheath remains mounted.
3. Native B1–B6 loops while Rank burns. Entry Rank (A/S/SS/SSS) determines burn rate. All seven B6 hit callbacks finish before re-entry.
4. Burnout stops the loop but retains the rear crescent. Native grounded Back+Sneak+Right Click (`slashblade:upperslash`) condenses, even at zero Rank.
5. The same pool returns its idle and attacking shards to the real blade. Slash Arts do not switch forms. No dedicated key, alternate damage, or ring/fan/rain SA.

See [2.2 authority, animation and testing](SAKURA_22.md) and [2.1 shard architecture](SAKURA_21.md).

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
- [ ] At A+, native B1–B6 can loop; Rank exhaustion blocks renewal and preserves dispersed form; native Upper Slash still condenses.
- [ ] A and SSS have observably different total flow duration.
- [ ] Native Rank never increases net while dispersed, even on repeated attacks.
- [ ] Ordinary native stun, knockback and damage are untouched.
- [ ] No old extra SA array / petal countercuts.
- [ ] Weapon swap, logout and style change clear live flow.
- [ ] First-person readability and multiplayer VFX verified.

Compile/CI is not a substitute for the final Minecraft animation/render acceptance.
