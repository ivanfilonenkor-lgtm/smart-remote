(() => {
  "use strict";

  const core = globalThis.SmartRemoteVideo;
  const isTopFrame = window.top === window;
  const adapter = isTopFrame ? core?.findAdapter(location) : null;
  const attachedVideos = new WeakSet();
  let interactedAt = 0;
  let lastStateJson = "";
  let publishTimer = 0;
  let endedSignalUntil = 0;

  const normalize = core?.normalize || ((value) => String(value || "").trim().toLowerCase());
  const visible = core?.visible || (() => true);

  // A generic "Пропустить" can belong to an advertisement. Automatic clicks
  // are limited to controls that explicitly mention an intro/opening.
  const findSkipIntroButton = () => {
    const explicitSelectors = [
      "[data-skip-type='intro']",
      "[data-action='skip-intro']",
      "#skip-intro",
      ".skip-intro",
      ".skip-opening"
    ];
    for (const selector of explicitSelectors) {
      const candidate = document.querySelector(selector);
      if (visible(candidate)) return candidate;
    }

    for (const candidate of document.querySelectorAll("button, [role='button'], a")) {
      if (!visible(candidate)) continue;
      const text = normalize(candidate.textContent || candidate.getAttribute?.("aria-label"));
      const explicitRussian = text.includes("пропустить") &&
        (text.includes("опенинг") || text.includes("заставк") || text.includes("вступлен"));
      const explicitEnglish = text.includes("skip") &&
        (text.includes("intro") || text.includes("opening"));
      if (explicitRussian || explicitEnglish) return candidate;
    }
    return null;
  };

  const findVideo = () => document.querySelector("video");

  const attachVideoEvents = (video) => {
    if (!video || attachedVideos.has(video)) return;
    attachedVideos.add(video);
    video.addEventListener("ended", () => {
      // Players often reset or replace <video> immediately after `ended`.
      // Keep the edge long enough for the background worker to observe it.
      endedSignalUntil = Date.now() + 6_000;
      schedulePublish(0);
    }, { passive: true });
    video.addEventListener("timeupdate", () => {
      const remaining = video.duration - video.currentTime;
      if (!video.paused && !video.seeking && Number.isFinite(remaining) && remaining <= 0.5) {
        endedSignalUntil = Date.now() + 6_000;
        schedulePublish(0);
      }
    }, { passive: true });
    video.addEventListener("play", () => {
      if (!Number.isFinite(video.duration) || video.currentTime < video.duration - 1) {
        endedSignalUntil = 0;
      }
      schedulePublish(0);
    }, { passive: true });
    for (const eventName of ["pause", "loadedmetadata", "emptied", "durationchange"]) {
      video.addEventListener(eventName, () => schedulePublish(0), { passive: true });
    }
  };

  const adapterState = () => {
    if (!adapter?.readState) return {};
    try {
      return adapter.readState() || {};
    } catch (_) {
      return {};
    }
  };

  const buildState = () => {
    const video = findVideo();
    attachVideoEvents(video);
    const page = isTopFrame ? adapterState() : {};
    return {
      type: "content_state",
      frameKind: isTopFrame ? "page" : "player",
      url: location.href,
      updatedAt: Date.now(),
      interactedAt,
      siteId: adapter?.id || null,
      siteName: adapter?.name || null,
      title: page.title || null,
      episode: page.episode ?? null,
      episodeCount: page.episodeCount ?? null,
      previousAvailable: Boolean(page.previousAvailable),
      nextAvailable: Boolean(page.nextAvailable),
      nativeAutoNext: Boolean(page.nativeAutoNext),
      nativeAutoMode: typeof page.nativeAutoMode === "boolean" ? page.nativeAutoMode : null,
      hasVideo: Boolean(video),
      playing: Boolean(video && !video.paused && !video.ended),
      ended: Boolean(video?.ended || endedSignalUntil > Date.now()),
      skipAvailable: Boolean(findSkipIntroButton())
    };
  };

  const publish = () => {
    publishTimer = 0;
    const state = buildState();
    const comparable = { ...state, updatedAt: 0 };
    const json = JSON.stringify(comparable);
    if (json === lastStateJson) return;
    lastStateJson = json;
    try {
      chrome.runtime.sendMessage(state, () => void chrome.runtime.lastError);
    } catch (_) {
      // The extension may be reloading; the next observer/interval will retry.
    }
  };

  function schedulePublish(delay = 60) {
    if (publishTimer) clearTimeout(publishTimer);
    publishTimer = setTimeout(publish, delay);
  }

  const executeCommand = async (action, payload = {}) => {
    if (isTopFrame && adapter?.execute) {
      try {
        if (adapter.execute(action, payload)) {
          schedulePublish(150);
          return true;
        }
      } catch (_) {
        // Fall through to the generic player commands.
      }
    }

    const video = findVideo();
    switch (action) {
      case "play_pause":
        if (!video) return false;
        if (video.paused || video.ended) await video.play(); else video.pause();
        return true;
      case "play":
        if (!video || (!video.paused && !video.ended)) return Boolean(video);
        await video.play();
        return true;
      case "skip": {
        const button = findSkipIntroButton();
        if (!button) return false;
        button.click();
        schedulePublish(100);
        return true;
      }
      default:
        return false;
    }
  };

  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message?.type !== "anime_command") return false;
    executeCommand(message.action, { enabled: message.enabled })
      .then((ok) => sendResponse({ ok }))
      .catch((error) => sendResponse({ ok: false, error: String(error) }));
    return true;
  });

  document.addEventListener("pointerdown", () => {
    interactedAt = Date.now();
    schedulePublish(0);
  }, { capture: true, passive: true });
  document.addEventListener("visibilitychange", () => schedulePublish(0), { passive: true });
  new MutationObserver(() => schedulePublish()).observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ["class", "hidden", "style", "aria-label", "aria-current"]
  });
  setInterval(() => {
    lastStateJson = "";
    publish();
  }, 1500);
  publish();
})();
