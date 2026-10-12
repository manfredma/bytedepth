(function () {
  const nav = document.querySelector(".nav-bar");
  if (!nav) {
    return;
  }
  const toggle = nav.querySelector(".nav-toggle");
  const panel = nav.querySelector(".nav-panel");
  if (!toggle || !panel) {
    return;
  }

  const mobile = window.matchMedia("(max-width: 1100px)");

  function setOpen(open) {
    toggle.setAttribute("aria-expanded", String(open));
    panel.hidden = mobile.matches && !open;
    if (!open) {
      // Nested menus must not reopen unexpectedly after collapsing or resizing.
      panel.querySelectorAll("details[open]").forEach((menu) => {
        menu.open = false;
      });
      panel.querySelectorAll(".theme-switcher.open").forEach((menu) => {
        menu.classList.remove("open");
        menu.querySelector(".theme-trigger")?.setAttribute("aria-expanded", "false");
      });
    }
  }

  function resetLayout() {
    const active = document.activeElement;
    const focusWillHide = mobile.matches
      ? panel.contains(active)
      : active === toggle || (panel.contains(active) && active.closest(".theme-menu, .nav-about-menu__panel"));
    toggle.hidden = !mobile.matches;
    setOpen(false);
    if (focusWillHide) {
      (mobile.matches ? toggle : panel.querySelector("input")).focus();
    }
  }

  toggle.addEventListener("click", () => setOpen(toggle.getAttribute("aria-expanded") !== "true"));
  nav.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && mobile.matches && !panel.hidden) {
      setOpen(false);
      toggle.focus();
    }
  });
  mobile.addEventListener("change", resetLayout);
  resetLayout();
})();
