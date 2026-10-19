const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "../../main/resources");
const fragment = fs.readFileSync(path.join(root, "templates/fragments/nav.html"), "utf-8");

async function initialize(mobile = true) {
  document.body.innerHTML = fragment;
  let listener;
  const media = {
    matches: mobile,
    addEventListener: (_, callback) => {
      listener = callback;
    },
  };
  window.matchMedia = vi.fn(() => media);
  vi.resetModules();
  await import("../../main/resources/static/js/nav-menu.js");
  return {
    nav: document.querySelector(".nav-bar"),
    toggle: document.querySelector(".nav-toggle"),
    panel: document.querySelector(".nav-panel"),
    resize(matches) {
      media.matches = matches;
      listener();
    },
  };
}

describe("shared mobile navigation", () => {
  test("actual shared fragment provides an accessible menu control", () => {
    document.body.innerHTML = fragment;
    expect(document.querySelector(".nav-toggle")).not.toBeNull();
    expect(document.querySelector(".nav-panel")).not.toBeNull();
  });
  test("initializes before article parsing rather than waiting for deferred article scripts", () => {
    document.body.innerHTML = fragment;
    const nav = document.querySelector(".nav-bar");
    const script = document.querySelector('script[th\\:src="@{/js/nav-menu.js}"]');
    expect(script.defer).toBe(false);
    expect(nav.nextElementSibling).toBe(script);
  });

  test("starts compact on mobile and opens all actual navigation controls", async () => {
    const { toggle, panel } = await initialize();
    expect(toggle.hidden).toBe(false);
    expect(toggle.getAttribute("aria-expanded")).toBe("false");
    expect(panel.hidden).toBe(true);
    expect(toggle.getAttribute("aria-controls")).toBe(panel.id);
    toggle.click();
    expect(toggle.getAttribute("aria-expanded")).toBe("true");
    expect(panel.hidden).toBe(false);
    expect(panel.querySelector('a[th\\:href="@{/reading-history}"]')).not.toBeNull();
    expect(panel.querySelector(".nav-search input")).not.toBeNull();
    expect(panel.querySelector(".nav-auth")).not.toBeNull();
    toggle.click();
    expect(panel.hidden).toBe(true);
  });

  test("visible menu label reflects opening, Escape and breakpoint reset", async () => {
    const { nav, toggle, resize } = await initialize();
    expect(toggle.textContent.trim()).toBe("菜单");
    toggle.click();
    expect(toggle.textContent.trim()).toBe("收起");
    nav.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(toggle.textContent.trim()).toBe("菜单");
    toggle.click();
    resize(false);
    resize(true);
    expect(toggle.textContent.trim()).toBe("菜单");
  });

  test("Escape closes the mobile menu and returns keyboard focus", async () => {
    const { nav, toggle, panel } = await initialize();
    toggle.click();
    panel.querySelector("input").focus();
    nav.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(panel.hidden).toBe(true);
    expect(document.activeElement).toBe(toggle);
  });

  test("switching between desktop and mobile resets the collapsed state", async () => {
    const { toggle, panel, resize } = await initialize(false);
    expect(toggle.hidden).toBe(true);
    expect(panel.hidden).toBe(false);
    resize(true);
    expect(toggle.hidden).toBe(false);
    expect(panel.hidden).toBe(true);
    toggle.click();
    resize(false);
    expect(panel.hidden).toBe(false);
    expect(toggle.hidden).toBe(true);
    resize(true);
    expect(panel.hidden).toBe(true);
  });

  test("breakpoint changes move focus away from controls that become hidden", async () => {
    const { toggle, panel, resize } = await initialize(false);
    const input = panel.querySelector("input");
    input.focus();
    resize(true);
    expect(document.activeElement).toBe(toggle);
    resize(false);
    expect(document.activeElement).toBe(input);
    toggle.click();
    input.focus();
    resize(false);
    expect(document.activeElement).toBe(input);
  });

  test("only Escape on an open mobile menu changes visibility", async () => {
    const { nav, toggle, panel, resize } = await initialize();
    toggle.click();
    nav.dispatchEvent(new KeyboardEvent("keydown", { key: "Tab", bubbles: true }));
    expect(panel.hidden).toBe(false);
    toggle.click();
    nav.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(panel.hidden).toBe(true);
    resize(false);
    nav.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(panel.hidden).toBe(false);
  });

  test("resizing closes a nested popup without leaving focus inside it", async () => {
    const { toggle, panel, resize } = await initialize();
    toggle.click();
    panel.querySelector(".theme-switcher").classList.add("open");
    panel.querySelector(".theme-option").focus();
    resize(false);
    expect(document.activeElement).toBe(panel.querySelector("input"));
    expect(panel.querySelector(".theme-switcher").classList.contains("open")).toBe(false);
  });

  test.each([".nav-bar", ".nav-toggle", ".nav-panel"])(
    "does not initialize an incomplete component missing %s",
    async (selector) => {
      document.body.innerHTML = fragment;
      document.querySelector(selector).remove();
      window.matchMedia = vi.fn();
      vi.resetModules();
      await import("../../main/resources/static/js/nav-menu.js");
      expect(window.matchMedia).not.toHaveBeenCalled();
    },
  );

  test("collapse remains usable if a nested theme trigger is absent", async () => {
    const { toggle, panel } = await initialize();
    toggle.click();
    panel.querySelector(".theme-switcher").classList.add("open");
    panel.querySelector(".theme-trigger").remove();
    toggle.click();
    expect(panel.hidden).toBe(true);
  });

  test("collapsing closes nested about and theme menus", async () => {
    const { toggle, panel } = await initialize();
    toggle.click();
    panel.querySelector("details").open = true;
    panel.querySelector(".theme-switcher").classList.add("open");
    panel.querySelector(".theme-trigger").setAttribute("aria-expanded", "true");
    toggle.click();
    expect(panel.querySelector("details").open).toBe(false);
    expect(panel.querySelector(".theme-switcher").classList.contains("open")).toBe(false);
    expect(panel.querySelector(".theme-trigger").getAttribute("aria-expanded")).toBe("false");
  });
});
