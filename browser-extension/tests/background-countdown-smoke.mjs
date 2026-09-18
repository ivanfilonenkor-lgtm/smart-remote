import assert from "node:assert/strict";

const sent = [];
let runtimeListener = null;

class FakeWebSocket {
  static OPEN = 1;
  constructor() {
    this.readyState = FakeWebSocket.OPEN;
    this.listeners = new Map();
    queueMicrotask(() => this.listeners.get("open")?.forEach((listener) => listener({})));
  }
  addEventListener(type, listener) {
    const listeners = this.listeners.get(type) || [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }
  send(value) { sent.push(JSON.parse(value)); }
  close() { this.readyState = 3; }
}

globalThis.WebSocket = FakeWebSocket;
globalThis.chrome = {
  runtime: {
    onMessage: { addListener(listener) { runtimeListener = listener; } },
    onStartup: { addListener() {} },
    onInstalled: { addListener() {} }
  },
  storage: { local: {
    async get() { return { autoMode: true }; },
    async set() {}
  } },
  tabs: {
    async query() { return [{ id: 1 }]; },
    async sendMessage() { return { ok: true }; },
    onActivated: { addListener() {} },
    onRemoved: { addListener() {} },
    onUpdated: { addListener() {} }
  }
};

await import(`../background.js?test=${Date.now()}`);
await new Promise((resolve) => setTimeout(resolve, 10));

runtimeListener({
  type: "content_state",
  frameKind: "page",
  siteId: "animevost",
  siteName: "AnimeVost",
  episode: 3,
  episodeCount: 10,
  previousAvailable: true,
  nextAvailable: true,
  hasVideo: false,
  playing: false,
  ended: false,
  skipAvailable: false,
  nativeAutoNext: false,
  nativeAutoMode: null,
  updatedAt: Date.now()
}, { tab: { id: 1 }, frameId: 0 });

// This auxiliary iframe used to overwrite the real player's ended state.
runtimeListener({
  type: "content_state",
  frameKind: "player",
  hasVideo: false,
  playing: false,
  ended: false,
  skipAvailable: false,
  updatedAt: Date.now()
}, { tab: { id: 1 }, frameId: 1 });

runtimeListener({
  type: "content_state",
  frameKind: "player",
  url: "https://animevost.org/frame5.php?play=test",
  hasVideo: true,
  playing: false,
  ended: true,
  skipAvailable: false,
  updatedAt: Date.now()
}, { tab: { id: 1 }, frameId: 2 });

await new Promise((resolve) => setTimeout(resolve, 30));
const countdownState = sent.find((message) =>
  message.type === "anime_state" && message.countdown_seconds === 5
);
assert.ok(countdownState, "real player end must start the five-second countdown");
console.log("Background countdown smoke test passed");
process.exit(0);
