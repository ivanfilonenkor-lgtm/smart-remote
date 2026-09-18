# Smart Remote protocol v1

## Transport

- WebSocket endpoint: `ws://<host>:8765/v1/ws`.
- Text frames contain UTF-8 JSON control messages.
- Binary frames contain realtime pointer or scroll samples.
- Protocol v1 is intentionally unauthenticated and unencrypted. It is restricted operationally with a Windows Private/LocalSubnet firewall rule.

The first client frame must be `client_hello`. The server replies with `server_hello`. A peer receiving a different major `protocol` value sends an error acknowledgement when possible and closes with WebSocket policy code `1008`.

Unknown JSON fields are ignored. Unknown message or action names are rejected with a negative acknowledgement. All control messages are handled in arrival order. Every accepted control request receives exactly one `ack`.

## Client to server text messages

```json
{
  "type": "client_hello",
  "protocol": 1,
  "client_id": "018f08a5-7b42-7ee2-a53d-e2e2447839f7",
  "device_name": "Living room phone",
  "capabilities": ["pointer", "scroll", "volume", "media", "keyboard", "gyro_pointer"]
}
```

```json
{
  "type": "control",
  "request_id": "018f08a5-94ae-747e-9950-67bbd774ab23",
  "action": "mouse_button",
  "payload": { "button": "left", "state": "down" }
}
```

Supported actions and payloads:

| Action | Payload |
| --- | --- |
| `mouse_button` | `{ "button": "left" | "right", "state": "down" | "up" }` |
| `set_volume` | `{ "value": 0.0 .. 1.0 }` |
| `set_mute` | `{ "muted": true | false }` |
| `type_text` | `{ "text": "UTF-8 text, 1..4096 characters" }` |
| `key_press` | `{ "key": "backspace" | "enter" | "tab" | "escape" | "delete" }` |
| `back` | `{}` |
| `play_pause` | `{}` |
| `show_desktop` | `{}` |
| `anime_previous` | `{}` |
| `anime_play_pause` | `{}` |
| `anime_next` | `{}` |
| `anime_skip` | `{}`; clicks only a verified intro/opening control |
| `anime_cancel_next` | `{}` |
| `anime_set_auto_mode` | `{ "enabled": true | false }` |

Application heartbeat:

```json
{ "type": "heartbeat", "sequence": 42 }
```

Clients send a heartbeat every second. Any valid frame also proves liveness. The server closes a session and releases its held buttons after three seconds without a valid frame.

## Server to client text messages

```json
{
  "type": "server_hello",
  "protocol": 1,
  "server_id": "4d9d5d3c-a730-4c49-b08f-3a9b4d83c508",
  "server_name": "TV Laptop",
  "capabilities": ["pointer", "scroll", "volume", "mute", "keyboard", "back", "play_pause", "show_desktop"],
  "volume": 0.62,
  "muted": false
}
```

```json
{ "type": "ack", "request_id": "...", "ok": true }
```

```json
{
  "type": "ack",
  "request_id": "...",
  "ok": false,
  "error": { "code": "invalid_payload", "message": "volume must be between 0 and 1" }
}
```

```json
{ "type": "state", "revision": 7, "volume": 0.71, "muted": false }
```

Audio state is authoritative on the server. A `state` frame is broadcast after Core Audio reports a change, including changes made outside Smart Remote.

When the optional local browser extension is connected, RemoteCore also broadcasts:

```json
{
  "type": "anime_state",
  "revision": 3,
  "available": true,
  "site_id": "animevost",
  "site_name": "AnimeVost",
  "title": "Example title",
  "episode": 4,
  "episode_count": 12,
  "playing": true,
  "skip_available": false,
  "previous_available": true,
  "next_available": true,
  "auto_mode": true,
  "countdown_seconds": null,
  "message": null
}
```

This is an optional protocol-v1 extension: older clients ignore it. The extension connects to
`ws://127.0.0.1:8765/v1/browser`; RemoteCore rejects non-loopback connections to this path.
The browser preference is stored on the PC, so auto watch can continue while the phone sleeps.
`site_id` is a stable adapter identifier and `site_name` is its user-facing label. Both fields are
optional so the original AnimeVost-only protocol-v1 clients and extensions remain compatible.

## Binary realtime frame

All fields are little-endian. The fixed frame size is 30 bytes.

| Offset | Size | Type | Field |
| ---: | ---: | --- | --- |
| 0 | 1 | `u8` | protocol version (`1`) |
| 1 | 1 | `u8` | kind: `1` pointer, `2` scroll |
| 2 | 4 | `u32` | gesture stream ID |
| 6 | 4 | `u32` | monotonically increasing sequence within the stream |
| 10 | 8 | `u64` | client monotonic timestamp in microseconds |
| 18 | 4 | `f32` | cumulative X displacement |
| 22 | 4 | `f32` | cumulative Y displacement |
| 26 | 4 | `u32` | reserved, must be zero in v1 |

The cumulative displacement begins at zero for every new stream. The server keeps only the latest pending sample per client and kind, rejects stale sequences, and calculates the action delta from the last applied cumulative value. This preserves the final displacement even when an intermediate realtime sample is conflated.

Pointer displacement is expressed in logical Android pixels after client sensitivity is applied. Scroll displacement is expressed in logical pixels; the server converts it to Windows wheel units.

`gyro_pointer` is an optional client capability. A client advertising it converts device rotation into the same cumulative `pointer` frames used by a touchpad. It does not introduce a new realtime kind and remains compatible with protocol v1 servers.

`keyboard` is an optional capability implemented with ordered control messages. `type_text` is injected as UTF-16 Unicode input on Windows, while `key_press` handles non-text editing keys. As with all `SendInput` actions, UIPI can block delivery into elevated applications.
