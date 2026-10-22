import { AuthStore, type AccountApi } from "../AuthStore";
import { SessionStore } from "../SessionStore";
function store() {
  const values = new Map<string, string>();
  return new SessionStore({
    get: async (key) => values.get(key) ?? null,
    set: async (key, value) => {
      values.set(key, value);
    },
    remove: async (key) => {
      values.delete(key);
    },
  });
}
const user = { id: "9007199254740993", username: "reader", permissions: ["blog:comment:create"] };
function accountApi(values: unknown[]): AccountApi {
  return {
    csrf: async () => undefined,
    request: async <T>() => {
      const next = values.shift();
      if (next instanceof Error) throw next;
      return next as T;
    },
  };
}
it("loads a whitelisted existing account after login with fresh CSRF", async () => {
  const calls: string[] = [];
  const api: AccountApi = {
    csrf: async () => {
      calls.push("csrf");
    },
    request: async <T>(path: string) => {
      calls.push(path);
      return (path === "/me" ? user : undefined) as T;
    },
  };
  const auth = new AuthStore(api, store());
  await auth.login("reader", "secret123");
  expect(calls).toEqual(["/login", "csrf", "/me"]);
  expect(auth.getUser()).toEqual(user);
});
it("clears private data immediately on background while retaining secure session", async () => {
  const sessions = store();
  await sessions.accept(await sessions.snapshot(), "valid-session");
  const auth = new AuthStore(accountApi([user]), sessions);
  await auth.revalidate();
  const reasons: string[] = [];
  auth.onPrivateClear((reason) => reasons.push(reason));
  auth.clearPrivateState("background");
  expect(reasons).toEqual(["background"]);
  expect(auth.getUser()).toBeNull();
  expect((await sessions.snapshot()).id).toBe("valid-session");
});
it("removes local session and private data even when logout network request fails", async () => {
  const sessions = store();
  await sessions.accept(await sessions.snapshot(), "valid-session");
  const auth = new AuthStore(accountApi([user, new Error("network")]), sessions);
  await auth.revalidate();
  await auth.logout();
  expect(auth.getUser()).toBeNull();
  expect((await sessions.snapshot()).id).toBeNull();
});
it("rejects account payloads containing credentials or unsafe IDs", async () => {
  for (const value of [
    { ...user, password: "hash" },
    { ...user, id: 9007199254740992 },
    { ...user, permissions: [1] },
    null,
  ]) {
    const auth = new AuthStore(accountApi([value]), store());
    await expect(auth.revalidate()).rejects.toThrow();
    expect(auth.getUser()).toBeNull();
  }
});
it("prevents old private account result from restoring UI after page invalidation", async () => {
  let finish!: (value: unknown) => void;
  let started!: () => void;
  const ready = new Promise<void>((resolve) => {
    started = resolve;
  });
  const api: AccountApi = {
    csrf: async () => undefined,
    request: async <T>() =>
      new Promise<T>((resolve) => {
        finish = (value) => resolve(value as T);
        started();
      }),
  };
  const auth = new AuthStore(api, store());
  const pending = auth.revalidate();
  await ready;
  auth.clearPrivateState("offline");
  finish(user);
  await pending;
  expect(auth.getUser()).toBeNull();
});
it("notifies subscribers and allows unsubscribe without preserving private data", async () => {
  const auth = new AuthStore(accountApi([user]), store());
  let changes = 0;
  let clears = 0;
  const unsub = auth.subscribe(() => changes++);
  const unprivate = auth.onPrivateClear(() => clears++);
  await auth.revalidate();
  expect(changes).toBeGreaterThan(0);
  unsub();
  unprivate();
  const before = changes;
  auth.clearPrivateState("page-destroyed");
  expect(changes).toBe(before);
  expect(clears).toBe(0);
  expect(auth.getUser()).toBeNull();
});
it("does not continue an old login after private lifecycle invalidation", async () => {
  let finish!: () => void;
  const calls: string[] = [];
  const api: AccountApi = {
    csrf: async () => {
      calls.push("csrf");
    },
    request: async <T>(path: string) => {
      calls.push(path);
      await new Promise<void>((resolve) => {
        finish = resolve;
      });
      return undefined as T;
    },
  };
  const auth = new AuthStore(api, store());
  const pending = auth.login("reader", "secret123");
  auth.clearPrivateState("background");
  finish();
  await pending;
  expect(calls).toEqual(["/login"]);
  expect(auth.getUser()).toBeNull();
});
it("does not clear a newer account when a previous logout response arrives late", async () => {
  const sessions = store();
  await sessions.accept(await sessions.snapshot(), "new-session");
  let finish!: () => void;
  const api: AccountApi = {
    csrf: async () => undefined,
    request: async <T>(path: string) => {
      if (path === "/logout")
        await new Promise<void>((resolve) => {
          finish = resolve;
        });
      return (path === "/me" ? user : undefined) as T;
    },
  };
  const auth = new AuthStore(api, sessions);
  const pending = auth.logout();
  await auth.login("reader", "secret123");
  finish();
  await pending;
  expect(auth.getUser()).toEqual(user);
  expect((await sessions.snapshot()).id).toBe("new-session");
});
it("does not erase new private state when an old account check fails later", async () => {
  let reject!: (error: Error) => void;
  let requests = 0;
  const api: AccountApi = {
    csrf: async () => undefined,
    request: async <T>() => {
      requests++;
      if (requests === 1)
        return new Promise<T>((_, fail) => {
          reject = fail;
        });
      return user as T;
    },
  };
  const auth = new AuthStore(api, store());
  const old = auth.revalidate();
  const rejected = expect(old).rejects.toThrow("expired");
  auth.clearPrivateState("account-change");
  await auth.revalidate();
  reject(new Error("expired"));
  await rejected;
  expect(auth.getUser()).toEqual(user);
});
it("notifies active account subscribers when private state is cleared", async () => {
  const auth = new AuthStore(accountApi([user]), store());
  await auth.revalidate();
  let observed: unknown = "unchanged";
  auth.subscribe(() => {
    observed = auth.getUser();
  });
  auth.clearPrivateState("offline");
  expect(observed).toBeNull();
});
