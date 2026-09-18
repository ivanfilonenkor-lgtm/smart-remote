# Manual acceptance checklist

## Connection and safety

- Connect two physical Android devices over the same private LAN.
- Confirm both receive the same volume and mute state.
- Hold a drag on both devices, disconnect one, and confirm the remaining owner keeps the button pressed.
- Disable Wi-Fi during a drag and confirm RemoteCore releases the button within three seconds.
- Confirm manual reconnect is required and the saved endpoint remains filled in.

## Remote controls

- Use the touchpad continuously for 30 minutes; confirm no increasing cursor lag.
- Verify single tap, two-finger tap, two-finger vertical scroll, and double-tap/hold drag.
- Enable Air Mouse in settings and verify the Touchpad/Air Mouse selector appears.
- In Air Mouse mode, verify hold-to-aim, continuous lock, direction mapping, sensitivity, vertical inversion, and one-handed left-button drag.
- Release the aim control, switch modes, open settings, and background the app; the cursor must stop immediately and any held mouse button must be released.
- On a device without a game rotation vector or gyroscope, verify Air Mouse is disabled with a localized explanation while touchpad remains usable.
- Verify Back maps to Alt+Left, Play/Pause controls active media, and Desktop maps to Win+D.
- Move and mute Windows volume from the phone, then from Windows; confirm every connected client converges to the Windows state.
- Change the default audio output device and confirm synchronization resumes on the new endpoint.

## Network boundary

- Confirm the inbound rule is limited to Private profile and LocalSubnet.
- Switch the active network to Public and confirm a second LAN device cannot open port 8765.
