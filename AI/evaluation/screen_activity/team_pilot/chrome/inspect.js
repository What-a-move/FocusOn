// Based on the product's inspectPage at 134f112. This function is serialized into the top frame.
export function inspectPage(maxLength) {
  if (document.visibilityState !== "visible" || !document.hasFocus()) return { unavailable: true };
  const sensitive = !!document.querySelector(
    [
      'input[type="password"]',
      'input[autocomplete="one-time-code"]',
      'input[autocomplete^="cc-"]',
      'input[name*="card" i]',
      'input[name*="otp" i]',
      'input[id*="otp" i]',
    ].join(","),
  );
  if (sensitive || !maxLength) return { sensitive, text: "" };

  const excluded =
    'form, pre, code, input, textarea, [contenteditable]:not([contenteditable="false"]), nav, footer, [aria-hidden="true"], [hidden]';
  const parts = [document.title];
  let length = document.title.length;
  const youtube = /(^|\.)youtube\.com$/.test(location.hostname);
  let selector = "h1, h2, h3, p, li, figcaption";
  if (youtube && location.pathname === "/watch") {
    // Recommendations, sidebar and comments are separate from the currently selected video.
    selector =
      "ytd-watch-metadata #description-inline-expander, ytd-video-secondary-info-renderer #description";
  } else if (youtube && location.pathname.startsWith("/shorts/")) {
    selector =
      "ytd-reel-video-renderer[is-active] h2, ytd-reel-video-renderer[is-active] #description";
  } else if (youtube && location.pathname === "/") {
    selector =
      "ytd-rich-item-renderer #video-title-link, ytd-rich-item-renderer #video-title, ytd-rich-item-renderer a.yt-lockup-metadata-view-model__title, ytd-rich-section-renderer h2";
  }
  const seen = new Set(parts);
  for (const node of document.querySelectorAll(selector)) {
    if (node.closest(excluded)) continue;
    if (node.getClientRects().length === 0) continue;
    if (youtube && location.pathname === "/") {
      const box = node.getBoundingClientRect();
      if (box.bottom <= 0 || box.top >= innerHeight || box.right <= 0 || box.left >= innerWidth)
        continue;
    }
    let text;
    if (node.querySelector(excluded)) {
      // A paragraph or video description may wrap editable or hidden child elements.
      const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
      const fragments = [];
      for (let part = walker.nextNode(); part; part = walker.nextNode()) {
        const el = part.parentElement;
        if (
          el &&
          !el.closest(excluded + ", script, style") &&
          el.getClientRects().length &&
          getComputedStyle(el).visibility === "visible"
        )
          fragments.push(part.nodeValue);
      }
      text = fragments.join(" ").trim();
    } else {
      text = node.innerText?.trim();
    }
    if (!text || seen.has(text)) continue;
    seen.add(text);
    parts.push(text);
    length += text.length + 1;
    if (length >= maxLength * 2) break;
  }
  return { sensitive: false, text: parts.join("\n").slice(0, maxLength * 2) };
}
