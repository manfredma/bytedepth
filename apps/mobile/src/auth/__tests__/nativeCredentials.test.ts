import { secureValues, installMarker, installationUuid, sha256 } from "../nativeCredentials";
import { InstallIdentity } from "../InstallIdentity";
jest.mock("expo-secure-store", () => {
  const data = new Map<string, string>();
  return {
    WHEN_UNLOCKED_THIS_DEVICE_ONLY: 7,
    getItemAsync: async (key: string) => data.get(key) ?? null,
    setItemAsync: async (key: string, value: string, options: { keychainAccessible: number }) => {
      if (options.keychainAccessible !== 7) throw new Error("must not migrate credentials");
      data.set(key, value);
    },
    deleteItemAsync: async (key: string) => {
      data.delete(key);
    },
  };
});
jest.mock("expo-file-system", () => {
  const files = new Map<string, string>();
  class Directory {
    create() {}
  }
  class File {
    name: string;
    constructor(_directory: unknown, name: string) {
      this.name = name;
    }
    get exists() {
      return files.has(this.name);
    }
    async text() {
      return files.get(this.name)!;
    }
    write(value: string) {
      files.set(this.name, value);
    }
    delete() {
      files.delete(this.name);
    }
  }
  return { Directory, File, Paths: { document: "/private/documents" } };
});
jest.mock("expo-crypto", () => ({
  randomUUID: () => "33333333-3333-4333-8333-333333333333",
  CryptoDigestAlgorithm: { SHA256: "SHA-256" },
  digest: async () => new Uint8Array(Array.from({ length: 32 }, (_, i) => i)).buffer,
}));
it("persists only this-device native identity and honors reinstall marker loss", async () => {
  const first = new InstallIdentity(secureValues, installMarker, installationUuid);
  expect(await first.visitorId()).toBe("app:33333333-3333-4333-8333-333333333333");
  expect(
    await new InstallIdentity(secureValues, installMarker, () => {
      throw new Error("must restore");
    }).visitorId(),
  ).toBe("app:33333333-3333-4333-8333-333333333333");
  await installMarker.remove("install-marker");
  expect(
    await new InstallIdentity(secureValues, installMarker, () => "44444444-4444-4444-8444-444444444444").visitorId(),
  ).toBe("app:44444444-4444-4444-8444-444444444444");
  await secureValues.remove("rating-install");
  expect(await secureValues.get("rating-install")).toBeNull();
  await installMarker.remove("install-marker");
  await installMarker.remove("install-marker");
});
it("encodes every digest byte with two lowercase hexadecimal digits", async () => {
  expect(await sha256(new Uint8Array([1, 2, 3]))).toBe(
    "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
  );
});
