# Changelog

## 0.6.0

- Port the authenticated agent API and live-player controls to the `main` NeoForge 1.20.4 branch.
- Add up to 32 independent film actors with separate identities, real-player/signed skins, scripts and job state.
- Add movement, teleport, look, pose, equipment, swing, hurt and wait actions, plus atomic synchronized scenes.
- Record actors as ordinary player packets so their skins and behavior survive replay and video export.
- Add actor status/cancellation/despawn, asynchronous bounded skin loading, and world-transition cleanup.
- Preserve a copied player's built-in default avatar independently of actor UUIDs, including after replay reopen.
- Include a runnable two-character recording/export example and actor regression coverage.
- Enable pull-request CI, update the NeoForge Maven URL, and consistently use the bundled ReplayStudio slim dependency.
