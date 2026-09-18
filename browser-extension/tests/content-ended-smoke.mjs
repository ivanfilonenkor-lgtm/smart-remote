import assert from "node:assert/strict";

const states = [];
const video = new EventTarget();
Object.assign(video, {
  paused: false,
  ended: false,
  seeking: false,
  duration: 100,
  currentTime: 99.7
});
video.play = async () => { video.paused = false; };
video.pause = () => { video.paused = true; };

globalThis.window = {};
window.top = window;
globalThis.location = {
  href: "https://animevost.org/frame5.php?play=test",
  hostname: "animevost.org"
};
globalThis.HTMLElement = class {};
globalThis.getComputedStyle = () => ({ display: "block", visibility: "visible", opacity: "1" });
globalThis.MutationObserver = class { observe() {} };
globalThis.document = {
  title: "Test",
  documentElement: {},
  querySelector(selector) {
    if (selector === "video") return video;
    return null;
  },
  querySelectorAll() { return []; },
  addEventListener() {}
};
globalThis.chrome = {
  runtime: {
    lastError: null,
    onMessage: { addListener() {} },
    sendMessage(state, callback) {
      states.push(state);
      callback?.();
    }
  }
};
globalThis.setInterval = () => 0;

await import(`../adapters/registry.js?test=${Date.now()}`);
await import(`../adapters/animevost.js?test=${Date.now()}`);
await import(`../content.js?test=${Date.now()}`);
video.dispatchEvent(new Event("timeupdate"));
await new Promise((resolve) => setTimeout(resolve, 10));
assert.equal(states.at(-1).ended, true, "near-end playback must latch the end signal");

video.currentTime = 0;
video.paused = true;
video.ended = false;
video.dispatchEvent(new Event("pause"));
await new Promise((resolve) => setTimeout(resolve, 10));
assert.equal(states.at(-1).ended, true, "player reset must not erase the latched end signal");
console.log("Content end-signal smoke test passed");
