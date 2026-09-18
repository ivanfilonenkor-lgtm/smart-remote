(() => {
  "use strict";

  const core = globalThis.SmartRemoteVideo;
  if (!core) return;

  const episodeButtons = () => Array.from(document.querySelectorAll(".epizode"));

  core.register({
    id: "animevost",
    name: "AnimeVost",
    matches(locationLike) {
      const hostname = String(locationLike?.hostname || "").toLowerCase();
      return hostname === "animevost.org" || hostname.endsWith(".animevost.org");
    },
    readState() {
      const episodes = episodeButtons();
      const activeIndex = episodes.findIndex((item) => item.classList?.contains("active"));
      const heading = document.querySelector("h1")?.textContent || document.title;
      return {
        title: heading?.trim().slice(0, 160) || null,
        episode: activeIndex >= 0 ? activeIndex + 1 : null,
        episodeCount: episodes.length || null,
        previousAvailable: activeIndex > 0,
        nextAvailable: activeIndex >= 0 && activeIndex < episodes.length - 1,
        nativeAutoNext: false,
        nativeAutoMode: null
      };
    },
    execute(action) {
      if (action !== "previous" && action !== "next") return false;
      const episodes = episodeButtons();
      const activeIndex = episodes.findIndex((item) => item.classList?.contains("active"));
      const target = episodes[activeIndex + (action === "next" ? 1 : -1)];
      if (!target) return false;
      target.click();
      return true;
    }
  });
})();
