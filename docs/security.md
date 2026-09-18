# Security model

Protocol v1 is a trusted-LAN MVP. It uses cleartext WebSockets and does not authenticate clients. Any reachable client can move the pointer, click, change volume, invoke media shortcuts, and type arbitrary text into the active application. This is not suitable for guest Wi-Fi, public networks, port forwarding, or exposure through a tunnel with untrusted peers.

Required mitigations:

1. Set the active Windows network to **Private** only when the LAN is trusted.
2. Install the repository firewall rules, restricted to the RemoteCore executable, TCP port 8765 and discovery UDP port 5353, Private profile, and LocalSubnet remote addresses.
3. Do not select Public when Windows prompts for network access.
4. Remove the rule when RemoteCore is no longer used.

The M5 security milestone must replace this model with identity-bound pairing, authentication, and encrypted transport.

## Windows integrity boundary

`SendInput` is subject to Windows User Interface Privilege Isolation. A normally launched RemoteCore cannot reliably inject input into applications running at a higher integrity level. MVP keeps RemoteCore non-elevated; users should interact with elevated applications locally.
