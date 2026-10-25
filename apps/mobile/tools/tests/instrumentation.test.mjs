import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  fixtureContent,
  fixtureBrowserOptions,
  validateFixtureInput,
  validateOwnedArticle,
  prepareFixtures,
  cleanupFixtures,
  removeOwnedUser,
} from "../fixtures.mjs";
const input = {
  schemaVersion: 1,
  origin: "https://staging-bytedepth.bytedepth.cn",
  runId: "11111111-1111-4111-8111-111111111111",
  backendCommitSha: "a".repeat(40),
  backendArtifactSha256: "b".repeat(64),
  username: "managed-admin",
  password: "secretA123",
  secondUsername: "managed-reader",
  secondPassword: "secretB123",
};
test("managed fixture input fails closed for identity, origin and absent/distinct accounts", () => {
  assert.equal(validateFixtureInput(input), input);
  for (const patch of [
    { origin: "https://bytedepth.cn" },
    { runId: "bad" },
    { backendCommitSha: "a" },
    { backendArtifactSha256: "b" },
    { schemaVersion: 2 },
    { password: "" },
    { secondPassword: "" },
    { secondUsername: input.username },
  ])
    assert.throws(() => validateFixtureInput({ ...input, ...patch }));
});
test("owned content exercises native rendered code/table/Mermaid/images, safe links and long resume", () => {
  const body = fixtureContent("/posts/owned-secondary", "owned-token");
  for (const marker of [
    "```mermaid",
    "```java",
    "| Key | Value |",
    "/icons/favicon.svg",
    "/icons/icon-192.png",
    "https://example.com/",
    "owned-token",
    "/posts/owned-secondary",
    "## Chapter 24",
  ])
    assert.ok(body.includes(marker));
  assert.ok(body.split("\n").length > 100);
  assert.ok(!body.includes("\\n"));
});
test("preparation records each owned article before publication and rolls back every owned row on failure", async () => {
  const events = [];
  let next = 0;
  const adapter = {
    login: async () => {},
    create: async (title) => ({
      id: String(++next),
      title,
      slug: "owned",
      editPath: `/admin/posts/${next}/edit`,
      postPath: "/posts/owned",
    }),
    publish: async () => {},
    update: async () => {},
    resolve: async () => {},
    annotate: async () => {
      throw Error("fixture unavailable");
    },
    authenticateSecond: async () => {},
    cookie: async () => ({ name: "SESSION", value: "private" }),
    remove: async (article) => events.push(article.id),
  };
  await assert.rejects(
    prepareFixtures(input, adapter, async (state) => events.push(`saved:${Object.keys(state.articles).length}`)),
    /fixture unavailable/,
  );
  assert.equal(new Set(events.filter((event) => event.startsWith("saved:"))).size, 4);
  assert.deepEqual(
    events.filter((event) => /^[0-9]+$/.test(event)),
    ["1", "2", "3", "4"],
  );
});
test("preparation supplies same-account credentials, distinct reader and owned ids without guessing", async () => {
  let n = 0;
  const removed = [];
  const adapter = {
    login: async () => {},
    create: async (title) => ({
      id: String(++n),
      title,
      slug: "owned",
      editPath: `/admin/posts/${n}/edit`,
      postPath: "/posts/owned",
    }),
    publish: async () => {},
    update: async () => {},
    resolve: async () => {},
    annotate: async () => {},
    authenticateSecond: async () => {},
    cookie: async () => ({ name: "SESSION", value: "private" }),
    remove: async (a) => removed.push(a.id),
  };
  const state = await prepareFixtures(input, adapter, async () => {});
  assert.equal(state.account.username, input.username);
  assert.equal(state.secondAccount.username, input.secondUsername);
  assert.equal(state.articles.primary.id, "4");
  assert.equal(state.complete, true);
  await cleanupFixtures(state, adapter);
  assert.deepEqual(removed, ["1", "2", "3", "4"]);
  adapter.remove = async () => {
    throw Error("cleanup unavailable");
  };
  const pending = structuredClone(state);
  for (const article of Object.values(pending.articles)) delete article.retired;
  await assert.rejects(cleanupFixtures(pending, adapter), /Owned mobile fixture cleanup failed/);
});
test("instrumentation source asserts Android UI, WebView, SQLite, backend counters, network and upgrade rather than emitting unconditional PASS", () => {
  const source = fs.readFileSync(
    new URL("../../../../apps/mobile/testing/android/MobileReaderStagingTest.kt", import.meta.url),
    "utf8",
  );
  for (const name of [
    "guest-reading",
    "public-content",
    "offline-recovery",
    "resume-rotation",
    "session-isolation",
    "anonymous-rating",
    "bridge-navigation",
    "no-history-upload",
    "upgrade-preservation",
    "upgrade-seed",
    "resume-seed",
  ])
    assert.ok(source.includes(`\"${name}\"`));
  for (const marker of [
    "SQLiteDatabase.OPEN_READONLY",
    "evaluateJavascript",
    "UiDevice",
    "ConnectivityManager",
    "reading-history-meta",
    "upgrade-sentinel.json",
    "MessageDigest",
    "getLongVersionCode",
    "MOBILE_CASE",
  ])
    assert.ok(source.includes(marker));
  assert.ok(source.indexOf("MOBILE_CASE") > source.indexOf("when (mobileCase)"));
  assert.equal((source.match(/MOBILE_CASE/g) ?? []).length, 1);
});

test("optional second account is created through owned identity recording before publication and cleaned on partial failure", async () => {
  const events = [];
  const withoutSecond = { ...input };
  delete withoutSecond.secondUsername;
  delete withoutSecond.secondPassword;
  assert.doesNotThrow(() => validateFixtureInput(withoutSecond));
  const pending = { id: "42", account: { username: `mobile-${input.runId.slice(0, 24)}`, password: "private" } };
  const adapter = {
    login: async () => {},
    createSecond: async (_id, record) => {
      await record({ ...pending, id: null });
      events.push("registered");
      await record(pending);
      events.push("approved");
      return pending;
    },
    authenticateSecond: async () => {
      throw Error("second login failed");
    },
    removeUser: async (user) => events.push(`remove:${user.id}`),
  };
  await assert.rejects(
    prepareFixtures(withoutSecond, adapter, async (state) =>
      events.push(
        `${state.ownedUser.retired ? "retired" : state.ownedUser.cleanupStarted ? "deleting" : "owned"}:${state.ownedUser.id}`,
      ),
    ),
    /second login failed/,
  );
  assert.deepEqual(events, [
    "owned:null",
    "registered",
    "owned:42",
    "approved",
    "deleting:42",
    "remove:42",
    "retired:42",
  ]);
  adapter.removeUser = async () => {
    throw Error("cleanup failed");
  };
  await assert.rejects(
    prepareFixtures(withoutSecond, adapter, async () => {}),
    /Owned mobile fixture cleanup failed/,
  );
});

test("cleanup rejects foreign post title/path/ID without touching it and still cleans other owned posts", async () => {
  const article = {
    id: "42",
    editPath: "/admin/posts/42/edit",
    postPath: "/posts/mobile-owned",
    title: `Mobile primary ${input.runId}`,
  };
  assert.doesNotThrow(() => validateOwnedArticle(article, "primary", input.runId));
  for (const patch of [
    { id: "41" },
    { editPath: "/admin/posts/43/edit" },
    { title: "unrelated user post" },
    { postPath: "https://example.com/" },
  ])
    assert.throws(() => validateOwnedArticle({ ...article, ...patch }, "primary", input.runId));
  const removed = [];
  const state = {
    runId: input.runId,
    articles: {
      primary: { ...article, title: "unrelated" },
      secondary: { ...article, id: "43", editPath: "/admin/posts/43/edit", title: `Mobile secondary ${input.runId}` },
    },
  };
  await assert.rejects(
    cleanupFixtures(state, { remove: async (a) => removed.push(a.id) }),
    /Owned mobile fixture cleanup failed/,
  );
  assert.deepEqual(removed, ["43"]);
});

test("owned Android instrumentation source has no trailing whitespace", () => {
  const source = fs.readFileSync(
    new URL("../../../../apps/mobile/testing/android/MobileReaderStagingTest.kt", import.meta.url),
    "utf8",
  );
  assert.equal(/[\t ]+$/m.test(source), false, "Android instrumentation must have no trailing whitespace");
});

test("Kotlin precise-resume projection retains a numeric revision through the actual annotation mapping runtime", async () => {
  const { JSDOM } = await import("jsdom");
  const source = fs.readFileSync(
    new URL("../../../../apps/mobile/testing/android/MobileReaderStagingTest.kt", import.meta.url),
    "utf8",
  );
  const accessor = /put\("revision", content\.(getString|getLong)\("contentVersion"\)\)/.exec(source);
  assert.ok(accessor, "exercise the actual JSONObject construction in the Kotlin assertion");
  const read = (relative) => fs.readFileSync(new URL(`../../../../${relative}`, import.meta.url), "utf8");
  const projection = JSON.parse(read("scripts/test-fixtures/annotation/unicode-document.json"));
  const content = { contentVersion: 7, document: projection };
  // JSONObject getString serializes a string; getLong serializes a JSON number.
  const model = {
    ...content.document,
    revision: accessor[1] === "getLong" ? Number(content.contentVersion) : String(content.contentVersion),
  };
  const dom = new JSDOM(read("scripts/test-fixtures/annotation/unicode-page.html"), { runScripts: "outside-only" });
  try {
    for (const file of [
      "vendor/hypothesis-anchoring/trim-range.js",
      "vendor/hypothesis-anchoring/text-range.js",
      "js/annotation-document.js",
    ])
      dom.window.eval(read(`bytedepth-start/src/main/resources/static/${file}`));
    const mapping = dom.window.AnnotationDocument.create(dom.window.document.body, model);
    try {
      const ranges = mapping.ranges([{ segmentId: "s0", start: 1, end: 2 }]);
      assert.ok(Array.isArray(ranges), `actual mapping failed: ${ranges.error}`);
      assert.equal(ranges[0].toString(), "😀");
    } finally {
      mapping.dispose();
    }
  } finally {
    dom.window.close();
  }
});

function finiteOwnedFixture() {
  const rows = new Set();
  const deleted = [];
  let next = 0;
  const adapter = {
    login: async () => {},
    authenticateSecond: async () => {},
    publish: async () => {},
    resolve: async () => {},
    create: async (title) => {
      const id = String(++next);
      rows.add(id);
      return { id, title, editPath: `/admin/posts/${id}/edit`, postPath: `/posts/owned-${id}` };
    },
    annotate: async () => {
      throw Error("injected preparation failure");
    },
    remove: async (article) => {
      if (!rows.delete(article.id)) throw Error("already deleted row cannot render an edit title");
      deleted.push(article.id);
    },
  };
  return { adapter, rows, deleted };
}
test("preparation failure durably retires owned rows so the Host-finally cleanup is idempotent", async () => {
  const { adapter, rows, deleted } = finiteOwnedFixture();
  let durable;
  const persist = async (state) => {
    durable = structuredClone(state);
  };
  await assert.rejects(prepareFixtures(input, adapter, persist), /injected preparation failure/);
  assert.equal(rows.size, 0);
  const hostState = structuredClone(durable);
  await assert.doesNotReject(cleanupFixtures(hostState, adapter, persist));
  assert.deepEqual(deleted, ["1", "2", "3", "4"]);
  assert.ok(Object.values(durable.articles).every((article) => article.retired === true));
});
test("partial cleanup persists each completed row/user and retries only unfinished ownership", async () => {
  const { adapter, deleted } = finiteOwnedFixture();
  adapter.annotate = async () => {};
  adapter.cookie = async () => ({ name: "SESSION", value: "private" });
  let durable;
  const persist = async (state) => {
    durable = structuredClone(state);
  };
  const state = await prepareFixtures(input, adapter, persist);
  state.ownedUser = { id: "42", username: `mobile-${input.runId.slice(0, 24)}` };
  const remove = adapter.remove;
  let failedRow = false,
    failedUser = false;
  adapter.remove = async (article) => {
    if (article.id === "2" && !failedRow) {
      failedRow = true;
      throw Error("temporary owned row failure");
    }
    return remove(article);
  };
  adapter.removeUser = async () => {
    if (!failedUser) {
      failedUser = true;
      throw Error("temporary user failure");
    }
    deleted.push("user:42");
  };
  await assert.rejects(cleanupFixtures(state, adapter, persist), /Owned mobile fixture cleanup failed/);
  assert.equal(durable.articles.secondary.retired, true);
  assert.equal(durable.articles.uncached.retired, undefined);
  await assert.doesNotReject(cleanupFixtures(structuredClone(durable), adapter, persist));
  assert.deepEqual(deleted, ["1", "3", "4", "2", "user:42"]);
  await assert.doesNotReject(cleanupFixtures(structuredClone(durable), adapter, persist));
  assert.equal(deleted.length, 5);
});

test("a lost completion write retries a previously owned deletion without accepting unrelated absence", async () => {
  const article = {
    id: "42",
    editPath: "/admin/posts/42/edit",
    postPath: "/posts/owned",
    title: `Mobile primary ${input.runId}`,
  };
  const state = { runId: input.runId, articles: { primary: article } };
  let durable;
  let exists = true;
  let writes = 0;
  const persist = async (current) => {
    if (++writes === 2) throw Error("injected lost completion write");
    durable = structuredClone(current);
  };
  const adapter = {
    remove: async (owned) => {
      assert.equal(owned.cleanupStarted, true, "deletion intent must already be persisted");
      assert.equal(durable.articles.primary.cleanupStarted, true);
      if (!exists) return; // real adapter accepts the exact previously owned edit path's 404
      exists = false;
    },
  };
  await assert.rejects(cleanupFixtures(state, adapter, persist), /Owned mobile fixture cleanup failed/);
  assert.equal(durable.articles.primary.retired, undefined);
  await assert.doesNotReject(cleanupFixtures(structuredClone(durable), adapter, persist));
  assert.equal(durable.articles.primary.retired, true);
  let foreignCalls = 0;
  await assert.rejects(
    cleanupFixtures(
      { runId: input.runId, articles: { primary: { ...article, title: "foreign", retired: true } } },
      { remove: async () => foreignCalls++ },
    ),
    /Owned mobile fixture cleanup failed/,
  );
  assert.equal(foreignCalls, 0);
});

async function userListBoundary({ status = 200, path, html = "", username }) {
  const { JSDOM } = await import("jsdom");
  const url = new URL(path ?? `/admin/users?username=${encodeURIComponent(username)}`, input.origin).href;
  const dom = new JSDOM(html, { url, runScripts: "outside-only" });
  const requests = [];
  dom.window.fetch = async (path, options) => {
    requests.push({ path, options });
    return { ok: true, url: `${input.origin}/admin/users` };
  };
  const locator = (nodes) => ({
    filter: ({ hasText }) => locator(nodes.filter((node) => node.textContent.includes(hasText))),
    locator: (selector) => locator(nodes.flatMap((node) => [...node.querySelectorAll(selector)])),
    count: async () => nodes.length,
    getAttribute: async (attribute) => {
      assert.equal(nodes.length, 1);
      return nodes[0].getAttribute(attribute);
    },
    text: () => {
      assert.equal(nodes.length, 1);
      return nodes[0].textContent.trim();
    },
  });
  const page = {
    goto: async () => ({ ok: () => status >= 200 && status < 300, status: () => status, url: () => url }),
    url: () => url,
    locator: (selector) => locator([...dom.window.document.querySelectorAll(selector)]),
    evaluate: (fn, argument) => dom.window.eval(`(${fn.toString()})(${JSON.stringify(argument)})`),
  };
  const expect = (actual) => ({
    toHaveCount: async (count) => assert.equal(await actual.count(), count),
    toHaveText: async (text) => assert.equal(actual.text(), text),
  });
  return { page, expect, requests, close: () => dom.window.close() };
}
const authenticatedUserList = `<form class="nav-logout" action="/logout"></form><div class="admin-main"><h1>用户管理</h1></div>`;
for (const sample of [
  { name: "HTTP 503", status: 503, html: authenticatedUserList },
  { name: "HTTP 500", status: 500, html: authenticatedUserList },
  { name: "login redirect", path: "/login", html: authenticatedUserList },
  { name: "unrelated successful page", path: "/admin/posts", html: authenticatedUserList },
  { name: "missing authenticated DOM", html: "<h1>Gateway page</h1>" },
])
  test(`actual user cleanup rejects ${sample.name} and retains unfinished ownership`, async () => {
    const owned = { id: "42", username: `mobile-${input.runId.slice(0, 24)}` };
    const boundary = await userListBoundary({ username: owned.username, ...sample });
    let durable;
    try {
      await assert.rejects(
        cleanupFixtures(
          { runId: input.runId, articles: {}, ownedUser: owned },
          { removeUser: (record) => removeOwnedUser(boundary.page, boundary.expect, record) },
          async (state) => {
            durable = structuredClone(state);
          },
        ),
        /Owned mobile fixture cleanup failed/,
      );
      assert.equal(durable.ownedUser.cleanupStarted, true);
      assert.equal(durable.ownedUser.retired, undefined);
      assert.equal(boundary.requests.length, 0);
    } finally {
      boundary.close();
    }
  });
test("actual user cleanup accepts a successful authenticated exact empty query only after recorded ownership", async () => {
  const owned = { id: "42", username: `mobile-${input.runId.slice(0, 24)}`, cleanupStarted: true };
  const boundary = await userListBoundary({ username: owned.username, html: authenticatedUserList });
  try {
    await assert.doesNotReject(removeOwnedUser(boundary.page, boundary.expect, owned));
    await assert.rejects(removeOwnedUser(boundary.page, boundary.expect, { ...owned, cleanupStarted: false }));
    assert.equal(boundary.requests.length, 0);
  } finally {
    boundary.close();
  }
});
test("actual user cleanup validates normal owned card ID and submits its existing form CSRF", async () => {
  const owned = { id: "42", username: `mobile-${input.runId.slice(0, 24)}`, cleanupStarted: true };
  const html =
    authenticatedUserList +
    `<div class="user-card"><div class="username">${owned.username}</div><form action="/admin/users/42/delete"><input name="_csrf" value="fixture-token"></form></div>`;
  const boundary = await userListBoundary({ username: owned.username, html });
  try {
    await assert.doesNotReject(removeOwnedUser(boundary.page, boundary.expect, owned));
    assert.equal(boundary.requests.length, 1);
    assert.equal(boundary.requests[0].path, "/admin/users/42/delete");
    assert.equal(boundary.requests[0].options.body.get("_csrf"), "fixture-token");
    await assert.rejects(removeOwnedUser(boundary.page, boundary.expect, { ...owned, id: "43" }), /identity mismatch/);
    assert.equal(boundary.requests.length, 1);
  } finally {
    boundary.close();
  }
});

test("fixture browser uses existing Mac Chrome or managed Linux Chrome and rejects other hosts", () => {
  assert.deepEqual(fixtureBrowserOptions("darwin"), {
    headless: true,
    executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  });
  assert.deepEqual(fixtureBrowserOptions("linux"), {
    headless: true,
    executablePath: "/opt/shared-e2e/chrome-linux64/chrome",
  });
  assert.throws(() => fixtureBrowserOptions("win32"), /Unsupported mobile fixture host/);
});
