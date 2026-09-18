(() => {
  "use strict";

  const core = globalThis.SmartRemoteVideo;
  if (!core) return;

  const episodeButtons = () => {
    const preferred = Array.from(document.querySelectorAll(
      "#video .episodes-container .video-button, #video .block-episodes .video-button"
    ));
    const candidates = preferred.length
      ? preferred
      : Array.from(document.querySelectorAll(".episodes-container .video-button, .block-episodes .video-button"));
    const visible = candidates.filter(core.visible);
    return visible.length ? visible : candidates;
  };

  const episodeNumber = (button, index) => {
    const raw = button?.dataset?.id || button?.getAttribute?.("data-id") || button?.textContent;
    const matched = String(raw || "").match(/\d+/);
    const value = matched ? Number(matched[0]) : index + 1;
    return Number.isSafeInteger(value) && value > 0 ? value : index + 1;
  };

  const activeIndex = (episodes) => episodes.findIndex((item) =>
    item.classList?.contains("active") || item.getAttribute?.("aria-current") === "true"
  );

  const readNativeAutoMode = () => {
    try {
      return localStorage.getItem("autoSkipNextEp") !== "false";
    } catch (_) {
      return null;
    }
  };

  core.register({
    id: "yummyanime",
    name: "YummyAnime",
    matches(locationLike) {
      const hostname = String(locationLike?.hostname || "").toLowerCase();
      return hostname === "old.yummyani.me" || hostname.endsWith(".yummyani.me");
    },
    readState() {
      const episodes = episodeButtons();
      const selected = activeIndex(episodes);
      const heading = document.querySelector("h1")?.textContent || document.title;
      return {
        title: heading?.trim().slice(0, 160) || null,
        episode: selected >= 0 ? episodeNumber(episodes[selected], selected) : null,
        episodeCount: episodes.length || null,
        previousAvailable: selected > 0,
        nextAvailable: selected >= 0 && selected < episodes.length - 1,
        nativeAutoNext: true,
        nativeAutoMode: readNativeAutoMode()
      };
    },
    execute(action, payload = {}) {
      if (action === "set_auto_mode") {
        try {
          localStorage.setItem("autoSkipNextEp", payload.enabled ? "true" : "false");
          window.dispatchEvent(new StorageEvent("storage", {
            key: "autoSkipNextEp",
            newValue: payload.enabled ? "true" : "false"
          }));
          return true;
        } catch (_) {
          return false;
        }
      }
      if (action !== "previous" && action !== "next") return false;
      const episodes = episodeButtons();
      const selected = activeIndex(episodes);
      const target = episodes[selected + (action === "next" ? 1 : -1)];
      if (!target) return false;
      target.click();
      return true;
    }
  });
})();
