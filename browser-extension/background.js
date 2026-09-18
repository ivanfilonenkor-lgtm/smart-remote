"use strict";

const HOST_URL = "ws://127.0.0.1:8765/v1/browser";
const PROTOCOL_VERSION = 1;
const EXTENSION_VERSION = "0.2.0";
const NEXT_DELAY_SECONDS = 5;

const frames = new Map();
const autoSkippedEpisodes = new Set();
const finishedEpisodes = new Set();
const pendingAutoplay = new Map();
const nativeSyncInFlight = new Map();
let activeTabId = null;
let autoMode = false;
let socket = null;
let reconnectTimer = 0;
let reconnectAttempt = 0;
let heartbeatSequence = 0;
let stateRevision = 0;
let lastHostState = "";
let countdown = null;

const frameKey = (tabId, frameId) => `${tabId}:${frameId}`;
const episodeKey = (candidate) =>
  `${candidate.siteId || "video"}:${candidate.tabId}:${candidate.episode ?? candidate.player?.url ?? "unknown"}`;

const sendSocket = (message) => {
  if (socket?.readyState !== WebSocket.OPEN) return false;
  socket.send(JSON.stringify(message));
  return true;
};

const scheduleReconnect = () => {
  if (reconnectTimer) return;
  const delay = Math.min(10_000, 750 * (2 ** Math.min(reconnectAttempt++, 4)));
  reconnectTimer = setTimeout(() => {
    reconnectTimer = 0;
    connectHost();
  }, delay);
};

function connectHost() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;
  try {
    socket = new WebSocket(HOST_URL);
  } catch (_) {
    scheduleReconnect();
    return;
  }
  socket.addEventListener("open", () => {
    reconnectAttempt = 0;
    sendSocket({
      type: "browser_hello",
      protocol: PROTOCOL_VERSION,
      extension_version: EXTENSION_VERSION
    });
    publishHostState(true);
  });
  socket.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === "anime_command") handleHostCommand(message);
    } catch (_) {
      // Ignore malformed host frames; RemoteCore only uses a small fixed schema.
    }
  });
  socket.addEventListener("close", () => {
    socket = null;
    scheduleReconnect();
  });
  socket.addEventListener("error", () => socket?.close());
}

setInterval(() => {
  if (!sendSocket({ type: "heartbeat", sequence: ++heartbeatSequence })) connectHost();
}, 1000);

const candidates = () => {
  const byTab = new Map();
  const now = Date.now();
  for (const frame of frames.values()) {
    if (now - frame.updatedAt > 10_000) continue;
    const candidate = byTab.get(frame.tabId) || {
      tabId: frame.tabId,
      page: null,
      players: [],
      skipFrames: [],
      updatedAt: 0,
      interactedAt: 0
    };
    if (frame.frameKind === "page") candidate.page = frame;
    if (frame.hasVideo) candidate.players.push(frame);
    if (frame.skipAvailable) candidate.skipFrames.push(frame);
    candidate.updatedAt = Math.max(candidate.updatedAt, frame.updatedAt || 0);
    candidate.interactedAt = Math.max(candidate.interactedAt, frame.interactedAt || 0);
    byTab.set(frame.tabId, candidate);
  }
  return Array.from(byTab.values()).map((candidate) => {
    const player = candidate.players.sort((a, b) => {
      if (a.playing !== b.playing) return a.playing ? -1 : 1;
      if (a.ended !== b.ended) return a.ended ? -1 : 1;
      return b.updatedAt - a.updatedAt;
    })[0] || null;
    const skipFrame = (player?.skipAvailable ? player : null) ||
      candidate.skipFrames.sort((a, b) => b.updatedAt - a.updatedAt)[0] || null;
    return {
      ...candidate,
      player,
      title: candidate.page?.title || null,
      siteId: candidate.page?.siteId || null,
      siteName: candidate.page?.siteName || null,
      episode: candidate.page?.episode ?? null,
      episodeCount: candidate.page?.episodeCount ?? null,
      previousAvailable: Boolean(candidate.page?.previousAvailable),
      nextAvailable: Boolean(candidate.page?.nextAvailable),
      nativeAutoNext: Boolean(candidate.page?.nativeAutoNext),
      nativeAutoMode: typeof candidate.page?.nativeAutoMode === "boolean"
        ? candidate.page.nativeAutoMode
        : null,
      playing: Boolean(player?.playing),
      ended: Boolean(player?.ended),
      skipAvailable: Boolean(skipFrame),
      skipFrame
    };
  }).filter((candidate) => candidate.player || candidate.episodeCount || candidate.siteId);
};

const selectedCandidate = () => candidates().sort((a, b) => {
  if (a.playing !== b.playing) return a.playing ? -1 : 1;
  const aActive = a.tabId === activeTabId;
  const bActive = b.tabId === activeTabId;
  if (aActive !== bActive) return aActive ? -1 : 1;
  if (a.interactedAt !== b.interactedAt) return b.interactedAt - a.interactedAt;
  return b.updatedAt - a.updatedAt;
})[0] || null;

const hostState = () => {
  const candidate = selectedCandidate();
  if (!candidate) {
    return {
      type: "anime_state",
      revision: ++stateRevision,
      available: false,
      site_id: null,
      site_name: null,
      title: null,
      episode: null,
      episode_count: null,
      playing: false,
      skip_available: false,
      previous_available: false,
      next_available: false,
      auto_mode: autoMode,
      countdown_seconds: null,
      message: null
    };
  }
  const isCountdownTab = countdown?.tabId === candidate.tabId;
  return {
    type: "anime_state",
    revision: ++stateRevision,
    available: true,
    site_id: candidate.siteId,
    site_name: candidate.siteName,
    title: candidate.title,
    episode: candidate.episode,
    episode_count: candidate.episodeCount,
    playing: candidate.playing,
    skip_available: candidate.skipAvailable,
    previous_available: candidate.previousAvailable,
    next_available: candidate.nextAvailable,
    auto_mode: autoMode,
    countdown_seconds: isCountdownTab ? countdown.remaining : null,
    message: isCountdownTab ? "next_episode" : null
  };
};

function publishHostState(force = false) {
  const state = hostState();
  const comparable = JSON.stringify({ ...state, revision: 0 });
  if (!force && comparable === lastHostState) return;
  lastHostState = comparable;
  sendSocket(state);
}

const sendToFrame = async (frame, action, payload = {}) => {
  if (!frame) return false;
  try {
    const response = await chrome.tabs.sendMessage(
      frame.tabId,
      { type: "anime_command", action, ...payload },
      { frameId: frame.frameId }
    );
    return Boolean(response?.ok);
  } catch (_) {
    return false;
  }
};

const cancelCountdown = (remember = false) => {
  if (!countdown) return;
  if (remember) finishedEpisodes.add(countdown.key);
  clearInterval(countdown.timer);
  countdown = null;
  publishHostState(true);
};

const startCountdown = (candidate) => {
  const key = episodeKey(candidate);
  if (countdown || finishedEpisodes.has(key) || !candidate.nextAvailable) return;
  countdown = { tabId: candidate.tabId, key, remaining: NEXT_DELAY_SECONDS, timer: 0 };
  publishHostState(true);
  countdown.timer = setInterval(async () => {
    if (!countdown) return;
    countdown.remaining -= 1;
    if (countdown.remaining > 0) {
      publishHostState(true);
      return;
    }
    const current = selectedCandidate();
    const targetTab = countdown.tabId;
    const targetKey = countdown.key;
    clearInterval(countdown.timer);
    countdown = null;
    finishedEpisodes.add(targetKey);
    if (current?.tabId === targetTab && current.nextAvailable) {
      const changed = await sendToFrame(current.page, "next");
      if (changed) pendingAutoplay.set(targetTab, Date.now() + 15_000);
    }
    publishHostState(true);
  }, 1000);
};

const applyAutomation = async (candidate) => {
  if (!candidate) return;
  if (candidate.nativeAutoNext && typeof candidate.nativeAutoMode === "boolean") {
    const desired = autoMode ? "on" : "off";
    if (candidate.nativeAutoMode === autoMode) {
      nativeSyncInFlight.delete(candidate.tabId);
    } else if (nativeSyncInFlight.get(candidate.tabId) !== desired) {
      nativeSyncInFlight.set(candidate.tabId, desired);
      await sendToFrame(candidate.page, "set_auto_mode", { enabled: autoMode });
      setTimeout(() => nativeSyncInFlight.delete(candidate.tabId), 2_000);
    }
  }
  if (!autoMode) return;
  const key = episodeKey(candidate);
  if (candidate.skipAvailable && !autoSkippedEpisodes.has(key)) {
    autoSkippedEpisodes.add(key);
    if (!await sendToFrame(candidate.skipFrame, "skip")) autoSkippedEpisodes.delete(key);
  }
  if (candidate.ended && !candidate.nativeAutoNext) startCountdown(candidate);
  if (pendingAutoplay.has(candidate.tabId) && candidate.player) {
    const expires = pendingAutoplay.get(candidate.tabId);
    if (Date.now() <= expires) {
      if (await sendToFrame(candidate.player, "play")) pendingAutoplay.delete(candidate.tabId);
    } else {
      pendingAutoplay.delete(candidate.tabId);
    }
  }
};

async function handleHostCommand(message) {
  const candidate = selectedCandidate();
  switch (message.action) {
    case "set_auto_mode":
      autoMode = Boolean(message.enabled);
      await chrome.storage.local.set({ autoMode });
      if (!autoMode) cancelCountdown(false);
      await applyAutomation(candidate);
      break;
    case "cancel_next":
      cancelCountdown(true);
      break;
    case "previous":
      if (candidate) await sendToFrame(candidate.page, "previous");
      break;
    case "next":
      if (candidate && await sendToFrame(candidate.page, "next")) {
        pendingAutoplay.set(candidate.tabId, Date.now() + 15_000);
      }
      break;
    case "play_pause":
      if (candidate) await sendToFrame(candidate.player, "play_pause");
      break;
    case "skip":
      if (candidate) await sendToFrame(candidate.skipFrame, "skip");
      break;
  }
  publishHostState(true);
}

chrome.runtime.onMessage.addListener((message, sender) => {
  if (message?.type !== "content_state" || sender.tab?.id == null || sender.frameId == null) return;
  const state = {
    ...message,
    tabId: sender.tab.id,
    frameId: sender.frameId,
    updatedAt: Date.now()
  };
  frames.set(frameKey(state.tabId, state.frameId), state);
  const current = selectedCandidate();
  if (countdown?.tabId === state.tabId && current?.playing) {
    cancelCountdown(false);
  }
  applyAutomation(current).finally(() => publishHostState());
});

chrome.tabs.onActivated.addListener(({ tabId }) => {
  activeTabId = tabId;
  publishHostState(true);
});
chrome.tabs.onRemoved.addListener((tabId) => {
  for (const [key, frame] of frames) if (frame.tabId === tabId) frames.delete(key);
  pendingAutoplay.delete(tabId);
  nativeSyncInFlight.delete(tabId);
  if (countdown?.tabId === tabId) cancelCountdown(false);
  publishHostState(true);
});
chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.status !== "loading") return;
  for (const [key, frame] of frames) if (frame.tabId === tabId) frames.delete(key);
  publishHostState(true);
});

chrome.storage.local.get({ autoMode: false }).then((stored) => {
  autoMode = Boolean(stored.autoMode);
  connectHost();
});
chrome.tabs.query({ active: true, currentWindow: true }).then(([tab]) => {
  activeTabId = tab?.id ?? null;
});
chrome.runtime.onStartup.addListener(connectHost);
chrome.runtime.onInstalled.addListener(connectHost);
