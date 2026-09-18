# Smart Remote

Smart Remote is a trusted-LAN Android remote for Windows. The phone automatically discovers RemoteCore computers on the local network through mDNS/DNS-SD and keeps manual IP entry as a fallback. The current build includes a touchpad, optional gyroscope-powered Air Mouse, Unicode phone keyboard input, mouse buttons and drag, scrolling, master volume and mute synchronization, media actions, and an optional contextual controller for supported video sites.

## Repository layout

- `android/` — Kotlin and Jetpack Compose phone client.
- `remote-core/` — Rust Windows WebSocket host and action executor.
- `browser-extension/` — adapter-based unpacked Chrome/Edge extension for AnimeVost, YummyAnime, and future supported sites.
- `protocol/` — protocol v1 specification, schema, and cross-language fixtures.
- `docs/` — setup, security, architecture, and manual acceptance checks.
- `scripts/` — Windows Firewall helper for trusted private networks.

## Security warning

MVP protocol v1 deliberately has no authentication or encryption. Any device that can reach the configured port can control the PC. Run it only on a trusted LAN and install the provided **Private + LocalSubnet** firewall rules for control and discovery. Do not expose port `8765` to the internet.

## Quick start

See [docs/setup.md](docs/setup.md) for toolchain installation, build commands, firewall setup, and launch instructions.
