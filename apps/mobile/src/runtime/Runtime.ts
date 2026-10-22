import { createContext, useContext, useSyncExternalStore } from "react";
import type { AuthStore } from "../auth/AuthStore";
import type { MobileApi, ReaderRepository, PublicContent, PostId } from "../contracts/mobile";
import type { Preferences } from "./Preferences";
export interface AppReader extends ReaderRepository {
  assetDataUris(content: PublicContent): Promise<Record<string, string>>;
  reportImageFailure(postId: PostId, key: string, representationHash: string): Promise<void>;
}
export interface Runtime {
  api: MobileApi;
  auth: AuthStore;
  reader: AppReader;
  preferences: Preferences;
  uuid(): string;
  isOnline(): boolean;
  subscribe(listener: () => void): () => void;
  dispose(): void;
}
export const RuntimeContext = createContext<Runtime | null>(null);
export function useRuntime(): Runtime {
  const value = useContext(RuntimeContext);
  if (!value) throw new Error("Runtime provider is required");
  return value;
}
export function useOnline() {
  const runtime = useRuntime();
  return useSyncExternalStore(runtime.subscribe, runtime.isOnline);
}
export function useUser() {
  const { auth } = useRuntime();
  return useSyncExternalStore(
    (fn) => auth.subscribe(fn),
    () => auth.getUser(),
  );
}
export function usePreferences() {
  const { preferences } = useRuntime();
  return useSyncExternalStore(preferences.subscribe, preferences.snapshot);
}
