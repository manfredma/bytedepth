import React from "react";
import { RuntimeContext, type Runtime } from "../../runtime/Runtime";
import { Preferences } from "../../runtime/Preferences";
import { AuthStore } from "../../auth/AuthStore";
import { SessionStore } from "../../auth/SessionStore";
import type { PublicContent, ReaderSnapshot, MobileUser } from "../../contracts/mobile";
export const content: PublicContent = {
  schemaVersion: 1,
  postId: "1",
  contentVersion: 1,
  projectionVersion: 1,
  digest: "digest",
  representationHash: "hash",
  shellVersion: "1",
  slug: "technical",
  title: "技术长文",
  publishedAt: "2026-10-22T00:00:00Z",
  category: null,
  series: null,
  html: "<p>文字</p>",
  document: {
    projectionVersion: 1,
    digest: "digest",
    text: "文字",
    segments: [{ id: "s", blockId: "b", kind: "prose", text: "文字", start: 0, end: 2 }],
  },
  resources: [],
};
export async function createFixture() {
  let online = true;
  const listeners = new Set<() => void>();
  const request = jest.fn(async (path: string, _options?: any): Promise<any> => {
    if (path === "/discovery")
      return {
        featured: [],
        latest: [{ id: "1", title: "技术长文", summary: "从原理开始", estimatedReadingMinutes: 8 }],
        categories: [],
        tags: [],
      };
    if (path.endsWith("/ratings")) return { averageScore: 4, ratingCount: 1, visitorScore: 4 };
    if (path.endsWith("/annotations"))
      return {
        schemaVersion: 1,
        postId: "1",
        contentVersion: 1,
        projectionVersion: 1,
        digest: "digest",
        annotations: [],
      };
    if (path.includes("/comments")) return { items: [], hasNext: false };
    if (path === "/me") return { id: "1", username: "reader", permissions: ["blog:comment:create"] };
    return undefined;
  });
  const auth = new AuthStore(
    { request: request as never, csrf: async () => {} },
    new SessionStore({ get: async () => null, set: async () => {}, remove: async () => {} }),
  );
  const preferences = await Preferences.open({ read: async () => null, write: async () => {} });
  let next: ((state: ReaderSnapshot | null) => void) | null = null;
  let initial: ReaderSnapshot = {
    postId: "1",
    copyState: "OFFLINE_READY",
    checkState: "CURRENT",
    content,
    lastConfirmedAt: "2026-10-22T00:00:00Z",
  };
  const reader = {
    listRecent: jest.fn(async (): Promise<any[]> => []),
    open: async function* () {
      yield initial;
      for (;;) {
        const state = await new Promise<ReaderSnapshot | null>((resolve) => {
          next = resolve;
        });
        if (!state) return;
        yield state;
      }
    },
    refresh: jest.fn(async () => {}),
    release: jest.fn(() => {
      next?.(null);
    }),
    savePosition: jest.fn(async () => {}),
    assetDataUris: jest.fn(async () => ({})),
    reportImageFailure: jest.fn(async () => {}),
  };
  const runtime = {
    api: { request },
    auth,
    preferences,
    reader,
    uuid: () => "request-uuid",
    isOnline: () => online,
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    dispose: jest.fn(),
  } as unknown as Runtime;
  return {
    runtime,
    request,
    auth,
    reader,
    setOnline: (value: boolean) => {
      online = value;
      if (!value) auth.clearPrivateState("offline");
      for (const listener of listeners) listener();
    },
    publish: (state: ReaderSnapshot) => next?.(state),
    setInitial: (state: ReaderSnapshot) => {
      initial = state;
    },
    provider: ({ children }: { children: React.ReactNode }) => (
      <RuntimeContext.Provider value={runtime}>{children}</RuntimeContext.Provider>
    ),
  };
}
export const navigation = { navigate: jest.fn(), push: jest.fn(), replace: jest.fn() };
export type TestUser = MobileUser;
