import assert from "node:assert/strict";

const baseUrl = process.env.SMART_REMOTE_TEST_URL || "ws://127.0.0.1:18765";

const openSocket = (path) => new Promise((resolve, reject) => {
  const socket = new WebSocket(`${baseUrl}${path}`);
  const timeout = setTimeout(() => reject(new Error(`Timed out opening ${path}`)), 3000);
  socket.addEventListener("open", () => {
    clearTimeout(timeout);
    resolve(socket);
  }, { once: true });
  socket.addEventListener("error", () => reject(new Error(`Failed to open ${path}`)), { once: true });
});

const nextJson = (socket) => new Promise((resolve, reject) => {
  const timeout = setTimeout(() => reject(new Error("Timed out waiting for JSON frame")), 3000);
  socket.addEventListener("message", (event) => {
    clearTimeout(timeout);
    resolve(JSON.parse(event.data));
  }, { once: true });
});

const browser = await openSocket("/v1/browser");
browser.send(JSON.stringify({
  type: "browser_hello",
  protocol: 1,
  extension_version: "smoke-test"
}));
assert.equal((await nextJson(browser)).type, "browser_hello");

const phone = await openSocket("/v1/ws");
phone.send(JSON.stringify({
  type: "client_hello",
  protocol: 1,
  client_id: "550e8400-e29b-41d4-a716-446655440000",
  device_name: "bridge smoke test",
  capabilities: []
}));
assert.equal((await nextJson(phone)).type, "server_hello");
assert.equal((await nextJson(phone)).type, "anime_state");

browser.send(JSON.stringify({
  type: "anime_state",
  revision: 1,
  available: true,
  site_id: "yummyanime",
  site_name: "YummyAnime",
  title: "Smoke fixture",
  episode: 2,
  episode_count: 3,
  playing: true,
  skip_available: false,
  previous_available: true,
  next_available: true,
  auto_mode: false,
  countdown_seconds: null,
  message: null
}));
const state = await nextJson(phone);
assert.equal(state.type, "anime_state");
assert.equal(state.site_name, "YummyAnime");
assert.equal(state.episode, 2);

phone.send(JSON.stringify({
  type: "control",
  request_id: "anime-next-smoke",
  action: "anime_next",
  payload: {}
}));
const [command, ack] = await Promise.all([nextJson(browser), nextJson(phone)]);
assert.deepEqual(command, {
  type: "anime_command",
  request_id: "anime-next-smoke",
  action: "next"
});
assert.equal(ack.type, "ack");
assert.equal(ack.ok, true);

phone.close();
browser.close();
console.log("RemoteCore browser bridge smoke test passed");
