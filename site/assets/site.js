// The landing page's small behaviours. The page reads fine without any of this.
(() => {
  // Light or dark: follows the system until the toggle is pressed, then
  // remembers the choice in this browser only.
  const root = document.documentElement;
  const toggle = document.querySelector("[data-theme-toggle]");
  const systemLight = () => matchMedia("(prefers-color-scheme: light)").matches;
  const current = () => root.dataset.theme || (systemLight() ? "light" : "dark");
  const label = () => toggle?.setAttribute("aria-label", current() === "light" ? "Switch to dark theme" : "Switch to light theme");
  label();
  toggle?.addEventListener("click", () => {
    const next = current() === "light" ? "dark" : "light";
    root.dataset.theme = next;
    try { localStorage.setItem("tandem-theme", next); } catch {}
    label();
  });

  // Sections fade in as they scroll into view, unless motion is unwelcome.
  const calm = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const items = document.querySelectorAll(".reveal");
  if (calm || !("IntersectionObserver" in window)) {
    items.forEach((el) => el.classList.add("in"));
    return;
  }
  const seen = new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (!e.isIntersecting) continue;
      e.target.classList.add("in");
      seen.unobserve(e.target);
    }
  }, { rootMargin: "0px 0px -6% 0px", threshold: 0.06 });
  items.forEach((el) => seen.observe(el));
})();
