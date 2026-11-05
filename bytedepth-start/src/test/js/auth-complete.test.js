async function load(result, embedded) {
  document.body.innerHTML = result ? '<span id="auth-complete" data-outcome="success"></span>' : "";
  const parent = { postMessage: vi.fn() };
  const browser = { location: { origin: "https://staging-bytedepth.bytedepth.cn" } };
  browser.parent = embedded ? parent : browser;
  vi.stubGlobal("window", browser);
  vi.resetModules();
  await import("../../main/resources/static/js/auth-complete.js");
  vi.unstubAllGlobals();
  return parent.postMessage;
}

test("only an embedded completion sends a fixed result to the exact same origin", async () => {
  const send = await load(true, true);
  expect(send).toHaveBeenCalledWith(
    { type: "bytedepth-auth", outcome: "success" },
    "https://staging-bytedepth.bytedepth.cn",
  );
  expect(send.mock.calls[0][0]).not.toHaveProperty("token");
});
test("direct visits and missing result documents do not notify another window", async () => {
  expect(await load(true, false)).not.toHaveBeenCalled();
  expect(await load(false, true)).not.toHaveBeenCalled();
});
