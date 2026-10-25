import fs from "node:fs/promises";
import path from "node:path";
import { randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";
const origin = "https://staging-bytedepth.bytedepth.cn";
export function validateFixtureInput(value) {
  if (
    value.schemaVersion !== 1 ||
    value.origin !== origin ||
    !/^[a-f0-9-]{36}$/.test(value.runId) ||
    !/^[a-f0-9]{40}$/.test(value.backendCommitSha) ||
    !/^[a-f0-9]{64}$/.test(value.backendArtifactSha256)
  )
    throw Error("Invalid managed mobile fixture binding");
  for (const key of ["username", "password"])
    if (typeof value[key] !== "string" || !value[key]) throw Error("Managed mobile fixture account missing");
  if (value.secondUsername !== undefined || value.secondPassword !== undefined) {
    if (!value.secondUsername || !value.secondPassword || value.secondUsername === value.username)
      throw Error("Distinct managed second account required");
  }
  return value;
}
export function fixtureContent(link, token) {
  return (
    `# Mobile fixture ${token}\n\nNative reader owned quote ${token}.\n\n[Owned article](${link}) [External browser](https://example.com/) [Rejected scheme](javascript:alert(1))\n\n\`\`\`java\nclass MobileFixture { int value = 42; }\n\`\`\`\n\n| Key | Value |\n| --- | --- |\n| native | reader |\n\n\`\`\`mermaid\ngraph LR\n  Reader-->Cache\n\`\`\`\n\n![Mobile PNG](/icons/icon-192.png)\n\n![Mobile SVG](/icons/favicon.svg)\n\n` +
    Array.from(
      { length: 24 },
      (_, i) =>
        `## Chapter ${i + 1}\n\n${Array(12)
          .fill(`Long native continuation paragraph ${i + 1} ${token}.`)
          .join(" ")}\n\n`,
    ).join("")
  );
}
export function validateOwnedArticle(article, key, runId) {
  if (
    !/^[a-f0-9-]{36}$/.test(runId) ||
    !["primary", "secondary", "uncached", "broken"].includes(key) ||
    !/^[0-9]+$/.test(article.id) ||
    article.editPath !== `/admin/posts/${article.id}/edit` ||
    article.title !== `Mobile ${key} ${runId}` ||
    !/^\/posts\/[^/]+$/.test(article.postPath)
  )
    throw Error("Owned mobile article identity mismatch");
}
export async function cleanupFixtures(state, adapter, persist = async () => {}) {
  const failures = [];
  for (const [key, article] of Object.entries(state.articles)) {
    try {
      validateOwnedArticle(article, key, state.runId);
      if (article.retired === true) continue;
      article.cleanupStarted = true;
      await persist(state);
      await adapter.remove(article);
      article.retired = true;
      await persist(state);
    } catch {
      failures.push(article.id);
    }
  }
  if (state.ownedUser) {
    try {
      const owned = state.ownedUser;
      if (owned.username !== `mobile-${state.runId.slice(0, 24)}` || (owned.id !== null && !/^[0-9]+$/.test(owned.id)))
        throw Error("Owned mobile user identity mismatch");
      if (owned.retired !== true) {
        owned.cleanupStarted = true;
        await persist(state);
        await adapter.removeUser(owned);
        owned.retired = true;
        await persist(state);
      }
    } catch {
      failures.push("owned-user");
    }
  }
  if (failures.length) throw Error(`Owned mobile fixture cleanup failed (${failures.length} operations)`);
}
export async function prepareFixtures(input, adapter, persist) {
  validateFixtureInput(input);
  const state = {
    schemaVersion: 1,
    origin,
    runId: input.runId,
    backendCommitSha: input.backendCommitSha,
    backendArtifactSha256: input.backendArtifactSha256,
    account: { username: input.username, password: input.password },
    secondAccount: { username: input.secondUsername, password: input.secondPassword },
    articles: {},
    complete: false,
    privateNote: `Private A ${input.runId}`,
    secondPrivateNote: `Private B ${input.runId}`,
    exactQuote: `Native reader owned quote ${input.runId}.`,
  };
  await adapter.login(state.account);
  try {
    if (!input.secondUsername) {
      const owned = await adapter.createSecond(input.runId, async (pending) => {
        state.secondAccount = pending.account;
        state.ownedUser = { id: pending.id, username: pending.account.username };
        await persist(state);
      });
      state.secondAccount = owned.account;
      state.ownedUser = { id: owned.id, username: owned.account.username };
    }
    await adapter.authenticateSecond(state.secondAccount);
    for (const key of ["secondary", "uncached", "broken", "primary"]) {
      const content =
        key === "primary"
          ? fixtureContent(state.articles.secondary.postPath, input.runId)
          : key === "broken"
            ? `# Broken required image\n\n![Required missing image](/images/mobile-missing-${input.runId}.png)`
            : `# Mobile ${key}\n\nOwned ${key} ${input.runId}.`;
      const article = await adapter.create(`Mobile ${key} ${input.runId}`, content);
      validateOwnedArticle(article, key, input.runId);
      state.articles[key] = article;
      await persist(state);
      await adapter.publish(article);
      await adapter.resolve(article);
    }
    await adapter.annotate(
      state.articles.primary,
      state.exactQuote,
      state.privateNote,
      state.secondAccount,
      state.secondPrivateNote,
    );
    state.webCookie = await adapter.cookie();
    state.complete = true;
    await persist(state);
    return state;
  } catch (error) {
    await cleanupFixtures(state, adapter, persist);
    throw error;
  }
}
export async function removeOwnedUser(page, expect, owned) {
  if (!/^mobile-[a-f0-9-]{24}$/.test(owned.username)) throw Error("Invalid owned user name");
  const expected = new URL(`/admin/users?username=${encodeURIComponent(owned.username)}`, origin);
  const response = await page.goto(expected.href, { waitUntil: "domcontentloaded" });
  if (!response || response.status() !== 200 || response.url() !== expected.href || page.url() !== expected.href)
    throw Error("Authenticated owned user list unavailable");
  await expect(page.locator('form.nav-logout[action="/logout"]')).toHaveCount(1);
  await expect(page.locator(".admin-main h1")).toHaveText("用户管理");
  const card = page.locator(".user-card").filter({ hasText: owned.username });
  if ((await card.count()) === 0 && owned.cleanupStarted === true) return;
  await expect(card).toHaveCount(1);
  await expect(card.locator(".username")).toHaveText(owned.username);
  const action = await card.locator('form[action$="/delete"]').getAttribute("action");
  const match = /^\/admin\/users\/([0-9]+)\/delete$/.exec(action);
  if (!match || (owned.id !== null && match[1] !== owned.id)) throw Error("Owned user cleanup identity mismatch");
  const id = match[1];
  const status = await page.evaluate(async (id) => {
    const csrf =
      document.querySelector(`form[action="/admin/users/${id}/delete"] input[name="_csrf"]`)?.value ||
      document.querySelector('meta[name="_csrf"]')?.content;
    if (!csrf) throw Error("Owned user cleanup CSRF missing");
    const response = await fetch(`/admin/users/${id}/delete`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ _csrf: csrf }),
    });
    return response.ok && new URL(response.url).pathname === "/admin/users";
  }, id);
  if (!status) throw Error("Owned user cleanup failed");
}
export function fixtureBrowserOptions(platform = process.platform) {
  if (platform !== "darwin" && platform !== "linux") throw Error("Unsupported mobile fixture host");
  return {
    headless: true,
    executablePath:
      platform === "darwin"
        ? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        : "/opt/shared-e2e/chrome-linux64/chrome",
  };
}
async function realAdapter(baseURL) {
  const { chromium, expect } = await import("@playwright/test");
  const { createArticle, deleteArticle, createAnnotation, ready } = await import(
    "../../../tests/e2e/helpers/annotation-fixture.js"
  );
  const { authenticationCookies } = await import("../../../tests/e2e/helpers/admin-auth.js");
  const { submitOwnedForm } = await import("../../../tests/e2e/helpers/owned-form.js");
  const browser = await chromium.launch(fixtureBrowserOptions());
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  let secondContext;
  const login = async (target, account) => {
    await target.goto("/login", { waitUntil: "domcontentloaded" });
    await target.locator('input[name="username"]').fill(account.username);
    await target.locator('input[name="password"]').fill(account.password);
    const [response] = await Promise.all([
      target.waitForResponse((r) => r.request().method() === "POST" && new URL(r.url()).pathname === "/login"),
      target.getByRole("button", { name: "登录", exact: true }).click(),
    ]);
    expect(response.status(), "Managed fixture login").toBe(302);
  };
  return {
    close: () => browser.close(),
    login: async (account) => {
      await login(page, account);
      await page.goto("/admin/posts/new");
      await expect(page.locator("form.post-editor-form")).toBeVisible();
    },
    createSecond: async (runId, recordOwned) => {
      const account = {
        username: `mobile-${runId.slice(0, 24)}`,
        password: `Mobile${randomBytes(18).toString("hex")}9`,
      };
      await page.goto(`/admin/users?username=${encodeURIComponent(account.username)}`);
      if (await page.locator(".username").filter({ hasText: account.username }).count())
        throw Error("Owned user already exists; reconcile prior fixture first");
      await recordOwned({ id: null, account });
      const registerContext = await browser.newContext({ baseURL });
      try {
        const register = await registerContext.newPage();
        await register.goto("/register");
        await register.locator('input[name="username"]').fill(account.username);
        await register.locator('input[name="password"]').fill(account.password);
        await Promise.all([
          register.waitForURL("**/login?registered=1"),
          register.locator('button[type="submit"]').click(),
        ]);
        await page.goto(`/admin/users?username=${encodeURIComponent(account.username)}&status=PENDING`);
        const card = page.locator(".user-card").filter({ hasText: account.username });
        await expect(card).toHaveCount(1);
        const action = await card.locator("form").first().getAttribute("action");
        if (!/^\/admin\/users\/[0-9]+\/activate$/.test(action)) throw Error("Owned user identity missing");
        const id = action.split("/")[3];
        // Preserve exact ownership before activation so partial failures can be cleaned.
        await recordOwned({ id, account });
        await card.locator(".btn-approve").click();
        return { id, account };
      } finally {
        await registerContext.close();
      }
    },
    authenticateSecond: async (account) => {
      secondContext = await browser.newContext({ baseURL });
      await login(await secondContext.newPage(), account);
    },
    create: async (title, content) => {
      const article = await createArticle(page, title, content);
      const match = /^\/admin\/posts\/([0-9]+)\/edit$/.exec(article.editPath);
      if (!match) throw Error("Owned post identity unavailable");
      return { ...article, id: match[1] };
    },
    publish: (article) => submitOwnedForm(page, article.editPath.replace(/\/edit$/, "/publish"), {}),
    resolve: async (article) => {
      const response = await context.request.get(
        `/api/mobile/v1/posts/resolve?slug=${encodeURIComponent(decodeURIComponent(article.postPath.split("/").pop()))}`,
      );
      if (response.status() !== 200) throw Error("Owned mobile article unavailable");
      const resolved = await response.json();
      if (!/^[0-9]+$/.test(resolved.postId) || resolved.postId !== article.id)
        throw Error("Owned mobile post ID missing");
      article.id = resolved.postId;
      article.slug = resolved.slug;
      await expect
        .poll(
          async () => {
            const response = await context.request.get(
              `/api/mobile/v1/search?query=${encodeURIComponent(article.title)}&page=1`,
            );
            if (response.status() !== 200) return false;
            return (await response.json()).items.some((item) => item.id === article.id);
          },
          { timeout: 30000, message: "Owned article search index must be available" },
        )
        .toBe(true);
    },
    annotate: async (article, quote, note, _second, secondNote) => {
      await ready(page, article.postPath);
      await createAnnotation(page, quote, note, "PRIVATE");
      const other = await secondContext.newPage();
      await ready(other, article.postPath);
      await createAnnotation(other, quote, secondNote, "PRIVATE");
    },
    cookie: async () => authenticationCookies(await context.storageState())[0],
    remove: async (article) => {
      if (!/^[0-9]+$/.test(article.id) || article.editPath !== `/admin/posts/${article.id}/edit`)
        throw Error("Owned post cleanup identity mismatch");
      const response = await page.goto(article.editPath, { waitUntil: "domcontentloaded" });
      // Only an exact, durably recorded owned deletion attempt may accept 404.
      if (response?.status() === 404 && article.cleanupStarted === true) return;
      await expect(page.locator('input[name="title"]')).toHaveValue(article.title);
      await deleteArticle(page, article);
    },
    removeUser: (owned) => removeOwnedUser(page, expect, owned),
  };
}
async function persistManaged(file, state) {
  const temporary = `${file}.next-${randomBytes(12).toString("hex")}`;
  const handle = await fs.open(temporary, "wx", 0o600);
  try {
    try {
      await handle.writeFile(JSON.stringify(state), "utf8");
      await handle.sync();
    } finally {
      await handle.close();
    }
    await fs.rename(temporary, file);
    const directory = await fs.open(path.dirname(file), "r");
    try {
      await directory.sync();
    } finally {
      await directory.close();
    }
  } finally {
    await fs.rm(temporary, { force: true });
  }
}
async function readManaged(file) {
  const stat = await fs.lstat(file);
  if (
    !stat.isFile() ||
    stat.isSymbolicLink() ||
    stat.nlink !== 1 ||
    stat.uid !== process.getuid() ||
    (stat.mode & 0o077) !== 0 ||
    stat.size > 65536
  )
    throw Error("Expected bounded private managed fixture file");
  return JSON.parse(await fs.readFile(file, "utf8"));
}
export async function runFixtures(args) {
  const [command, ...rest] = args;
  const flags = new Map();
  for (let i = 0; i < rest.length; i += 2) {
    if (!["--input", "--output"].includes(rest[i]) || !rest[i + 1] || flags.has(rest[i]))
      throw Error("Invalid fixture command");
    flags.set(rest[i], rest[i + 1]);
  }
  if (
    !["prepare", "cleanup"].includes(command) ||
    !flags.has("--input") ||
    (command === "prepare") !== flags.has("--output")
  )
    throw Error("Invalid fixture command");
  const input = await readManaged(flags.get("--input"));
  const adapter = await realAdapter(origin);
  try {
    if (command === "prepare") {
      const destination = path.resolve(flags.get("--output"));
      // Refuse symlinks and existing files, including files not owned by this run.
      const handle = await fs.open(destination, "wx", 0o600);
      await handle.close();
      await prepareFixtures(input, adapter, (state) => persistManaged(destination, state));
    } else {
      if (input.origin !== origin || input.schemaVersion !== 1) throw Error("Invalid owned cleanup binding");
      await adapter.login(input.account);
      await cleanupFixtures(input, adapter, (state) => persistManaged(flags.get("--input"), state));
      await fs.unlink(flags.get("--input"));
    }
  } finally {
    await adapter.close();
  }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url))
  runFixtures(process.argv.slice(2)).catch(() => {
    console.error("Managed mobile fixture operation failed; private input retained for owned cleanup");
    process.exitCode = 1;
  });
