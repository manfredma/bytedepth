import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  fixtureContent,
  validateFixtureInput,
  validateOwnedArticle,
  prepareFixtures,
  cleanupFixtures,
} from "../../mobile-staging-fixtures.mjs";
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
  assert.equal(events.filter((event) => event.startsWith("saved:")).length, 4);
  assert.deepEqual(events.slice(-4), ["1", "2", "3", "4"]);
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
  await assert.rejects(cleanupFixtures(state, adapter), /Owned mobile fixture cleanup failed/);
});
test("instrumentation source asserts Android UI, WebView, SQLite, backend counters, network and upgrade rather than emitting unconditional PASS", () => {
  const source = fs.readFileSync(
    new URL("../../../apps/mobile/testing/android/MobileReaderStagingTest.kt", import.meta.url),
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
  const pending = { id: "42", account: { username: "mobile-owned", password: "private" } };
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
    prepareFixtures(withoutSecond, adapter, async (state) => events.push(`owned:${state.ownedUser.id}`)),
    /second login failed/,
  );
  assert.deepEqual(events, ["owned:null", "registered", "owned:42", "approved", "remove:42"]);
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
