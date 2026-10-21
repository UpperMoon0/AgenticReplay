# Historical 1.7.10 replay playback

The combined AgenticReplay distribution owns the replay engine. Remove the separate ReplayMod jar before installing it; keep the instance's existing UniMixins and rendering dependencies.

Historical files can put the synthetic recording-player spawn before JoinGame. The maintained sender buffers that spawn until the join packet has been delivered. It resets the buffer for replay restarts and bounds it to avoid an unbounded malformed-file buffer.

Offline seeks also need delivery order, not just a reported replay timestamp. Vanilla limits received packets per tick and GTNH's Hodgepodge changes that limit into a time budget. A bulk seek can leave more than a million vanilla packets waiting while mod payloads observe an older world. The fork drains synchronous receive queues after each delivered packet, through the network manager's current handler, before applying camera paths. Paused replay controls also drain delivered packets on render ticks. Ordinary live-network packet limits remain intact.

Older recordings may reference a missing player in a bed packet. The replay-only bed guard skips that impossible action. After a replay world and camera exist, Forge custom-payload rejection is logged without closing the offline channel; connection setup and live multiplayer retain Forge's normal rejection behavior. This preserves subsequent replay data rather than leaving a silently frozen old scene. A rejected packet remains a visual validation concern, not proof that every mod's historical data was recovered.

For Angelica 2.2.28, offline shader frame time follows exported frame count and FPS. The normal clock still runs outside export, and other Angelica versions do not inherit that integration. See [the shader workflow](SHADER_WORKFLOW.md) for remaining shader and render-mode acceptance requirements.

Regression tests cover the pinned engine constructor and synchronous sender, the actual Forge dispatcher and vanilla bed handler, the legacy receive queue and embedded-manager inheritance, bounded bootstrap order, clock phase across frame rates, and transformer idempotency. Native validation must additionally compare the rendered scene and world time to the intended recording interval; a completed encoder and a correct frame count alone cannot establish this.
