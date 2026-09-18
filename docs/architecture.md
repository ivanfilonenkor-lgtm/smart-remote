# Architecture

Smart Remote has two independently buildable applications joined by protocol v1.

```text
Android input/UI
  |  WebSocket: realtime binary + ordered JSON control
  v
RemoteCore session manager
  |-- realtime lane (latest pending sample per client/kind)
  |-- control lane (bounded FIFO, never silently dropped)
  |-- Action router
      |-- Win32 SendInput executor
      `-- Core Audio worker

Chrome/Edge video-site extension + site adapters
  |  loopback-only WebSocket (/v1/browser)
  v
RemoteCore browser bridge -> optional anime_state/control messages -> Android panel
```

Android never calls a Windows API and RemoteCore never interprets touch gestures. Android converts gestures into protocol actions; RemoteCore owns session safety and native execution.

Phone keyboard input uses the same ordered control lane as buttons and media actions. The client sends complete Unicode text batches or named editing keys; RemoteCore converts text to UTF-16 `KEYEVENTF_UNICODE` pairs and editing keys to ordinary virtual-key presses.

Air Mouse is an Android-only pointer source. While the user holds the aim control or enables its session lock, the client converts relative device rotation into cumulative protocol v1 pointer frames. It uses the game rotation vector when available and falls back to the calibrated gyroscope. The sensor is unregistered whenever movement is inactive or the remote screen leaves the foreground.

The internal `Action` enum is deliberately independent of network messages. Future voice, AI, gesture, or web bridges can produce the same actions without joining the latency-sensitive phone-to-PC path.

Video-site commands intentionally stay outside the Windows `Action` enum: they are routed to one
local MV3 extension connection. A shared content coordinator combines page episode state with the
player iframe state; small domain-specific adapters provide episode selection and native auto-next
integration. The wire names remain `anime_state`/`anime_*` for protocol-v1 compatibility, while
optional `site_id` and `site_name` identify AnimeVost, YummyAnime, or a future adapter. The
extension stores Auto watch on the PC and only treats controls explicitly labelled as
intro/opening skips as safe for automation.

## Concurrency invariants

- Each connection has one client ID and independent held-button ownership.
- A button is physically pressed on the first owner and released after the final owner releases or disconnects.
- Pointer and scroll have independent conflated realtime workers per session.
- Touchpad and Air Mouse allocate stream IDs from one client-wide sequence, so switching sources always resets the server accumulator cleanly.
- Ordered control frames are acknowledged only after they have been accepted by the action router.
- A dead session cannot leave an owned button pressed.
