import { AppState, type AppStateStatus } from "react-native";
import * as Network from "expo-network";
import { createNativeRuntime } from "../nativeRuntime";
import { MobileApi } from "../../api/MobileApi";
let mockNetwork: (state: Network.NetworkState) => void;
const mockNetworkRemove = jest.fn();
jest.mock("expo-network", () => ({
  addNetworkStateListener: jest.fn((fn) => {
    mockNetwork = fn;
    return { remove: mockNetworkRemove };
  }),
  getNetworkStateAsync: jest.fn(async () => ({ isConnected: true, isInternetReachable: true })),
}));
const mockRequest = jest.fn();
jest.mock("../../api/MobileApi", () => ({
  MobileApi: jest.fn().mockImplementation(() => ({ request: mockRequest, csrf: async () => {} })),
}));
jest.mock("../../reader/ReaderRepository", () => ({
  createNativeReaderRepository: jest.fn(async () => ({ listRecent: async () => [] })),
}));
let mockSession: string | null = "session";
const mockValues = new Map<string, string>();
jest.mock("../../auth/nativeCredentials", () => ({
  secureValues: {
    get: async (key: string) => (key === "mobile-session" ? mockSession : (mockValues.get(key) ?? null)),
    set: async (key: string, value: string) => {
      mockValues.set(key, value);
    },
    remove: async (key: string) => {
      mockValues.delete(key);
    },
  },
  installMarker: {
    get: async (key: string) => mockValues.get(key) ?? null,
    set: async (key: string, value: string) => {
      mockValues.set(key, value);
    },
  },
  installationUuid: () => "123e4567-e89b-42d3-a456-426614174000",
  sha256: async () => "hash",
}));
let mockPreferenceText: string | null = null;
jest.mock("expo-file-system", () => ({
  Paths: { document: "document" },
  Directory: class {
    create() {}
  },
  File: class {
    get exists() {
      return mockPreferenceText !== null;
    }
    async text() {
      return mockPreferenceText;
    }
    write(value: string) {
      mockPreferenceText = value;
    }
  },
}));
beforeEach(() => {
  mockSession = "session";
  mockValues.clear();
  mockPreferenceText = null;
  Object.defineProperty(AppState, "currentState", { value: "active", writable: true, configurable: true });
});
jest.mock("../../api/nativeTransport", () => ({ nativeTransport: jest.fn() }));
it("clears account immediately on background/offline, validates it again on foreground, and tears down listeners", async () => {
  mockRequest.mockResolvedValue({ id: "1", username: "reader", permissions: [] });
  let lifecycle!: (state: AppStateStatus) => void;
  const remove = jest.fn();
  jest.spyOn(AppState, "addEventListener").mockImplementation((_event, fn) => {
    lifecycle = fn;
    return { remove };
  });
  Object.defineProperty(AppState, "currentState", { value: "active", writable: true, configurable: true });
  const runtime = await createNativeRuntime();
  const notify = jest.fn();
  const unsubscribe = runtime.subscribe(notify);
  await runtime.auth.revalidate();
  expect(runtime.auth.getUser()?.username).toBe("reader");
  lifecycle("background");
  expect(runtime.auth.getUser()).toBeNull();
  expect(runtime.isOnline()).toBe(false);
  lifecycle("active");
  await Promise.resolve();
  await Promise.resolve();
  expect(mockRequest).toHaveBeenCalledWith("/me", expect.objectContaining({ authenticated: true }));
  mockNetwork({ isConnected: false });
  lifecycle("active");
  expect(runtime.auth.getUser()).toBeNull();
  expect(runtime.isOnline()).toBe(false);
  mockNetwork({ isConnected: true, isInternetReachable: false });
  expect(runtime.isOnline()).toBe(false);
  mockNetwork({ isConnected: true, isInternetReachable: true });
  expect(runtime.isOnline()).toBe(true);
  expect(notify).toHaveBeenCalled();
  unsubscribe();
  runtime.dispose();
  expect(remove).toHaveBeenCalled();
  expect(mockNetworkRemove).toHaveBeenCalled();
  mockNetwork({ isConnected: true });
  jest.restoreAllMocks();
});
it("keeps public reading available when initial connectivity cannot be determined", async () => {
  jest.mocked(Network.getNetworkStateAsync).mockRejectedValueOnce(new Error("network"));
  const runtime = await createNativeRuntime();
  expect(runtime.isOnline()).toBe(false);
  runtime.dispose();
});
it("wires an installation visitor and persists only public reading preferences", async () => {
  mockSession = null;
  const runtime = await createNativeRuntime();
  const visitor = jest.mocked(MobileApi).mock.calls.at(-1)![2];
  expect(await visitor()).toBe("app:123e4567-e89b-42d3-a456-426614174000");
  await runtime.preferences.set({ theme: "dark", fontScale: 1.2 });
  runtime.dispose();
  const next = await createNativeRuntime();
  expect(next.preferences.snapshot()).toEqual({ theme: "dark", fontScale: 1.2 });
  next.dispose();
});
it("keeps an unvalidated or expired session out of the visible account state", async () => {
  mockRequest.mockRejectedValue(new Error("expired"));
  const runtime = await createNativeRuntime();
  await Promise.resolve();
  await Promise.resolve();
  expect(runtime.auth.getUser()).toBeNull();
  runtime.dispose();
});
