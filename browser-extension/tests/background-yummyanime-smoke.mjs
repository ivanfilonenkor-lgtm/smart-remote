import assert from "node:assert/strict";

const socketMessages = [];
const frameCommands = [];
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
  send(value) { socketMessages.push(JSON.parse(value)); }
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
    async query() { return [{ id: 7 }]; },
    async sendMessage(tabId, message, options) {
      frameCommands.push({ tabId, message, options });
      return { ok: true };
    },
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
  siteId: "yummyanime",
  siteName: "YummyAnime",
  title: "Блич",
  episode: 2,
  episodeCount: 3,
  previousAvailable: true,
  nextAvailable: true,
  nativeAutoNext: true,
  nativeAutoMode: false,
  hasVideo: false,
  playing: false,
  ended: true,
  skipAvailable: false,
  updatedAt: Date.now()
}, { tab: { id: 7 }, frameId: 0 });

await new Promise((resolve) => setTimeout(resolve, 30));
assert.ok(frameCommands.some(({ message }) =>
  message.action === "set_auto_mode" && message.enabled === true
), "extension Auto watch preference must be synchronized to YummyAnime");
assert.ok(!socketMessages.some((message) => message.countdown_seconds != null),
  "YummyAnime native auto-next must not compete with the extension countdown");
const state = socketMessages.filter((message) => message.type === "anime_state").at(-1);
assert.equal(state.site_id, "yummyanime");
assert.equal(state.site_name, "YummyAnime");
console.log("YummyAnime background integration smoke test passed");
process.exit(0);
