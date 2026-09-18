import assert from "node:assert/strict";

const clicked = [];
const values = new Map();
const button = (id, active = false) => ({
  dataset: { id: String(id) },
  textContent: `Серия ${id}`,
  hidden: false,
  disabled: false,
  classList: { contains(name) { return name === "active" && active; } },
  getAttribute(name) { return name === "data-id" ? String(id) : null; },
  getBoundingClientRect() { return { width: 100, height: 30 }; },
  click() { clicked.push(id); }
});
const episodes = [button(1), button(2, true), button(3)];

globalThis.window = { dispatchEvent() {} };
globalThis.StorageEvent = class { constructor(type, init) { this.type = type; Object.assign(this, init); } };
globalThis.location = { hostname: "old.yummyani.me" };
globalThis.getComputedStyle = () => ({ display: "block", visibility: "visible", opacity: "1" });
globalThis.localStorage = {
  getItem(key) { return values.has(key) ? values.get(key) : null; },
  setItem(key, value) { values.set(key, value); }
};
globalThis.document = {
  title: "Блич",
  querySelector(selector) {
    if (selector === "h1") return { textContent: "Блич" };
    return null;
  },
  querySelectorAll(selector) {
    return selector.includes("video-button") ? episodes : [];
  }
};

await import(`../adapters/registry.js?test=${Date.now()}`);
await import(`../adapters/yummyanime.js?test=${Date.now()}`);

const adapter = globalThis.SmartRemoteVideo.findAdapter(location);
assert.equal(adapter.id, "yummyanime");
assert.deepEqual(adapter.readState(), {
  title: "Блич",
  episode: 2,
  episodeCount: 3,
  previousAvailable: true,
  nextAvailable: true,
  nativeAutoNext: true,
  nativeAutoMode: true
});
assert.equal(adapter.execute("next"), true);
assert.deepEqual(clicked, [3]);
assert.equal(adapter.execute("set_auto_mode", { enabled: false }), true);
assert.equal(values.get("autoSkipNextEp"), "false");
assert.equal(adapter.readState().nativeAutoMode, false);
console.log("YummyAnime adapter smoke test passed");
