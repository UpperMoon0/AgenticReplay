# Changelog

## 0.6.0

- Preserve actor movement/turning timing and instantaneous teleports in playback with exact actor frames alongside indexed vanilla packets.
- Restore actor frames during Quick Mode seeks and exports; squash cut/split frame history to one boundary pose per surviving actor and omit unchanged idle motion.
- Combine native capture, hidden-window diagnostics and replay-scoped view distance from PR #3.
- Add 32 separately scripted film actors with independent identities, real-player skins, scenes and cancellation.
- Preserve actors and signed/default skins in replay packets; include a two-character Python filming/export example.
- Target Forge 1.20.1 throughout the implementation, packaging and CI.

## 0.5.0

- Preserve hidden, unfocused startup through Forge's native window handoff and expose actual GLFW focus/visibility diagnostics.

- Allow screen recovery while paused, retaining pause checks for every other action and sequence transition.
- Add API v2 live actor state, bounded native movement/looking/use/attack, hotbar selection, permitted flight, and dismounting.
- Add repeatable client-tick sequences with conditional waits, assertions, progress, cancellation, input cleanup, and tick/wall-time limits.
- Add native server connection with recording enabled before login, guarded container slot clicks, and screen closing.
- Release controlled inputs on completion, failure, stop, pause, world/player changes, death, disconnect, and physical keyboard/mouse-button takeover.
- Preserve existing replay/recording/camera/export methods and document live control separately from replay cameras.
- Add opt-in background startup without initial window focus, session-only auto-pause control, and native held mining without capturing the desktop cursor.
- Reject enabling flight while grounded and require an actual handled container screen for slot clicks.
- Add regression coverage for duration, cleanup, context loss, job isolation, condition timeouts, plan validation, and million-height coordinates.
