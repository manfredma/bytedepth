import { AppState } from "react-native";
import * as Network from "expo-network";
import { Directory, File, Paths } from "expo-file-system";
import { MobileApi } from "../api/MobileApi";
import { AssetDownloader } from "../api/AssetDownloader";
import { nativeTransport } from "../api/nativeTransport";
import { AuthStore } from "../auth/AuthStore";
import { SessionStore } from "../auth/SessionStore";
import { InstallIdentity } from "../auth/InstallIdentity";
import { secureValues, installMarker, installationUuid, sha256 } from "../auth/nativeCredentials";
import { createNativeReaderRepository } from "../reader/ReaderRepository";
import { Preferences } from "./Preferences";
import type { Runtime } from "./Runtime";
export async function createNativeRuntime(): Promise<Runtime> {
  const sessions = new SessionStore(secureValues);
  await sessions.snapshot();
  const identity = new InstallIdentity(secureValues, installMarker, installationUuid);
  const api = new MobileApi(nativeTransport, sessions, () => identity.visitorId());
  const auth = new AuthStore(api, sessions);
  const reader = await createNativeReaderRepository(api, new AssetDownloader(nativeTransport, sha256));
  const directory = new Directory(Paths.document, "reading-preferences");
  const file = new File(directory, "preferences.json");
  const preferences = await Preferences.open({
    read: async () => (file.exists ? file.text() : null),
    write: async (text) => {
      directory.create({ intermediates: true, idempotent: true });
      file.write(text);
    },
  });
  let online = false;
  let foreground = AppState.currentState === "active";
  let active = true;
  let generation = 0;
  const listeners = new Set<() => void>();
  const restore = async () => {
    const current = ++generation;
    if (!online || !foreground) return;
    try {
      const session = await sessions.snapshot();
      if (active && current === generation && session.id) await auth.revalidate();
    } catch {
      /* AuthStore 清理过期身份；公开阅读继续。 */
    }
  };
  const changed = (state: Network.NetworkState) => {
    if (!active) return;
    const next = state.isConnected === true && state.isInternetReachable !== false;
    const becameOnline = !online && next;
    online = next;
    if (!online) {
      generation++;
      auth.clearPrivateState("offline");
    }
    for (const listener of listeners) listener();
    if (becameOnline) void restore();
  };
  const network = Network.addNetworkStateListener(changed);
  const lifecycle = AppState.addEventListener("change", (state) => {
    foreground = state === "active";
    if (foreground) void restore();
    else {
      generation++;
      auth.clearPrivateState("background");
    }
    for (const listener of listeners) listener();
  });
  try {
    changed(await Network.getNetworkStateAsync());
  } catch {
    changed({ isConnected: false });
  }
  return {
    api,
    auth,
    reader,
    preferences,
    uuid: installationUuid,
    isOnline: () => online && foreground,
    subscribe: (fn) => {
      listeners.add(fn);
      return () => {
        listeners.delete(fn);
      };
    },
    dispose: () => {
      active = false;
      generation++;
      network.remove();
      lifecycle.remove();
      auth.clearPrivateState("page-destroyed");
      listeners.clear();
    },
  };
}
