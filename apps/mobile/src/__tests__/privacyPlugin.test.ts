const mockMkdir = jest.fn();
const mockWriteFile = jest.fn();
jest.mock("node:fs/promises", () => ({
  mkdir: (...args: unknown[]) => mockMkdir(...args),
  writeFile: (...args: unknown[]) => mockWriteFile(...args),
}));
jest.mock("expo/config-plugins", () => ({
  withAndroidManifest: (config: Record<string, unknown>, callback: unknown) => ({
    ...config,
    androidCallback: callback,
  }),
  withDangerousMod: (config: Record<string, unknown>, [, callback]: [string, unknown]) => ({
    ...config,
    dangerousCallback: callback,
  }),
}));
it("generated Android build excludes local data from backup and transfer", async () => {
  const plugin = jest.requireActual("../../plugins/withLocalDataPrivacy.cjs");
  const manifest = { manifest: { application: [{ $: {} }] } };
  const configured = plugin({});
  await configured.androidCallback({ modResults: manifest });
  expect(manifest.manifest.application[0].$).toEqual({
    "android:allowBackup": "false",
    "android:fullBackupContent": "false",
    "android:dataExtractionRules": "@xml/mobile_data_extraction_rules",
  });
  await configured.dangerousCallback({ modRequest: { platformProjectRoot: "/isolated/android" } });
  expect(mockMkdir).toHaveBeenCalledWith("/isolated/android/app/src/main/res/xml", { recursive: true });
  expect(mockWriteFile).toHaveBeenCalledWith(
    "/isolated/android/app/src/main/res/xml/mobile_data_extraction_rules.xml",
    expect.stringContaining("<device-transfer>"),
  );
});
