import type { SecureValues } from "./SessionStore";
import * as Crypto from "expo-crypto";
import * as SecureStore from "expo-secure-store";
import { Directory, File, Paths } from "expo-file-system";
export const secureValues: SecureValues = {
  get: (key) => SecureStore.getItemAsync(key),
  set: (key, value) =>
    SecureStore.setItemAsync(key, value, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY }),
  remove: (key) => SecureStore.deleteItemAsync(key),
};
const directory = new Directory(Paths.document, ".installation");
const marker = new File(directory, "marker");
// 文章被逐出后仍持久保留；不参与设备迁移，应用数据删除时重置。
export const installMarker: SecureValues = {
  get: async () => (marker.exists ? marker.text() : null),
  set: async (_key, value) => {
    directory.create({ intermediates: true, idempotent: true });
    marker.write(value);
  },
  remove: async () => {
    if (marker.exists) marker.delete();
  },
};
export function installationUuid(): string {
  return Crypto.randomUUID();
}
export async function sha256(bytes: Uint8Array): Promise<string> {
  const digest = await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, Uint8Array.from(bytes));
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, "0")).join("");
}
