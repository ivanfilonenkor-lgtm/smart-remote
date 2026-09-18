(() => {
  "use strict";

  const adapters = [];

  const normalize = (value) => String(value || "")
    .replace(/\s+/g, " ")
    .trim()
    .toLocaleLowerCase("ru");

  const visible = (element) => {
    if (!element || element.hidden || element.disabled) return false;
    const style = typeof getComputedStyle === "function" ? getComputedStyle(element) : null;
    if (style && (style.display === "none" || style.visibility === "hidden" || Number(style.opacity) === 0)) {
      return false;
    }
    const rect = typeof element.getBoundingClientRect === "function"
      ? element.getBoundingClientRect()
      : { width: 1, height: 1 };
    return rect.width > 0 && rect.height > 0;
  };

  const register = (adapter) => {
    if (!adapter || typeof adapter.id !== "string" || typeof adapter.matches !== "function") {
      throw new TypeError("Invalid Smart Remote site adapter");
    }
    if (adapters.some((current) => current.id === adapter.id)) {
      throw new Error(`Duplicate Smart Remote site adapter: ${adapter.id}`);
    }
    adapters.push(Object.freeze(adapter));
  };

  const findAdapter = (locationLike = globalThis.location) => {
    for (const adapter of adapters) {
      try {
        if (adapter.matches(locationLike)) return adapter;
      } catch (_) {
        // A broken adapter must not prevent generic player control.
      }
    }
    return null;
  };

  globalThis.SmartRemoteVideo = Object.freeze({
    register,
    findAdapter,
    normalize,
    visible,
    adapters
  });
})();
