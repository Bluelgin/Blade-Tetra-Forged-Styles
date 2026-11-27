# Blade Studio: inscription ceremony

The receipt opens only after `StandaloneBladeExport.write` returns successfully.
The screen owns presentation only: no archive writer, authorization request,
recipe mutation, texture modification or world particle entities. Escape first
shows the completed receipt; a second Escape returns to the existing editor.
Replay reuses the same receipt and baked viewport, with a fresh presentation clock.
Removing the screen releases dynamic textures and stops its UI sound instances.

## Choreography

`BladeInscriptionTimeline` samples monotonic elapsed milliseconds. Playback begins
when the model is ready, rather than charging asset loading against the ceremony.

| Milliseconds | Presentation |
| --- | --- |
| 0–1200 | Settle into the inspection angle against a dark indigo background |
| 1200–3000 | Withdraw the sheath and gather curved light strands |
| 3000–4500 | Reveal the screen inscription and sweep the blade |
| 4500–6000 | Finish the inscription, strike the seal, scatter sparks and gather the light |
| 6000–8000 | Hold the blade name and inscription before exposing result actions |

Camera position, text reveal, particles and UI sound cues share this clock.
The final inscription boundary and impact are both 4500 ms; the name begins to
appear at 4900 ms. Sound cues are consumed once per playback. A stalled frame
coalesces overdue cues to the latest one instead of emitting several stale sounds.
No full-screen flash, camera shake, shader dependency or post-processing is used.

The actual exported OBJ and atlas are baked once per appearance/resource revision.
Blade colour comes from the exported effect colour, softened for legibility;
a secondary colour is sampled once from the baked atlas, with neutral fallbacks.
PNG sampling uses the stream decoder to avoid putting large compressed atlases
on LWJGL's small native stack.

The viewport fits projected model bounds, including the displaced sheath, at the
current camera angle. A cached 49-station centreline is intersected from the baked
blade geometry, beginning near the sheath mouth and ending at the blade tip.
The light sweep follows this curve rather than crossing the handle or cutting
across a curved blade. GUI energy quads are submitted in a managed batch.

Inscription text uses Minecraft's font fallback and Unicode layout. A clipped
reveal follows each laid-out line; this is a light-pen reveal, not a semantic
Chinese handwriting/stroke-order engine. Long inscriptions remain intact and
can be scrolled on the result page. Names scale to the available width. Dragging
the finished blade rotates it; the wheel over the model zooms it. Scrolling over
the text reads the inscription. No title/lore data or exported texture is rewritten.

## Acceptance evidence

`exportSealClientSmoke` exercises the real editor, actual archive writing,
full playback, replay, skip, result rotation and text scrolling. SHA-256 hashes
of all produced archives must remain identical across presentation actions.
It captures the five stages and the result directly from the game framebuffer.
Additional receipts cover an ordinary blade with long bilingual text and a rare
glyph/emoji fallback, and a long fish blade with a salvaged named guard. Each
additional fixture writes its own matching archive before displaying a receipt.

`BladeInscriptionTimelineTest` covers stage boundaries, the final-stroke/impact
boundary, replay, overdue sound coalescing and sampling at 15/30/60/144 FPS.
Frame-rate sampling tests do not claim a native GPU benchmark at all four rates.

Local visual prototypes and logs live under `build/inscription-review/` (not
published). Prototype A used dim points and straight bounding-box endpoints;
actual frames showed poor contrast and a sweep crossing the handle. Prototype B
uses softened colours, connected strands and the mesh centreline. Its unbatched
GUI render averaged 2.15 ms over 476 frames, compared with 1.17 ms for A's sparse
points. Managed batching reduced the richer presentation to 0.66 ms over
477 frames in the final native run (3.55 ms maximum). The bilingual and long-fish
fixtures averaged 0.71 and 0.94 ms respectively. These are instrumented screen-render CPU
submission times on this machine, excluding baking and screenshot writes, not
whole-client frame times or measurements on representative low-end hardware.

The developer launch uses Minecraft 1.20.1, Forge 47.4.0 and Java 17. Native frames
are acceptance evidence; final artistic approval remains with the user. This work
does not claim a new packaged-client or multiplayer acceptance run.

Final checks: 459 JUnit tests passed, 2 were skipped, with no failures or errors;
integration fixtures compiled; the reobfuscated production JAR passed its isolation
audit; all 486 runtime JSON/metadata files parsed; export-runtime isolation passed
for 33 sources. Final native proof markers include
`BLADE_INSCRIPTION_REPLAY_SKIP_ARCHIVE_PASS` and
`BLADE_INSCRIPTION_MODEL_TEXT_REVIEW_PASS`. Screenshots and the native log are
retained in `build/inscription-review/final/`.
