const preview = {
  channel: "preview",
  name: "ByteDepth 试用",
  applicationId: "cn.bytedepth.reader.preview",
  origin: "https://staging-bytedepth.bytedepth.cn",
  versionCode: 3,
};
const production = {
  channel: "production",
  name: "ByteDepth",
  applicationId: "cn.bytedepth.reader",
  origin: "https://bytedepth.cn",
  versionCode: 1,
};
function load(channel: string | undefined) {
  const env = { ...process.env };
  if (channel === undefined) delete env.EXPO_PUBLIC_RELEASE_CHANNEL;
  else env.EXPO_PUBLIC_RELEASE_CHANNEL = channel;
  jest.replaceProperty(process, "env", env);
  let native: any;
  let runtime: any;
  jest.isolateModules(() => {
    native = jest.requireActual("../../app.config").default;
    runtime = jest.requireActual("../config");
  });
  return { native, runtime };
}
afterEach(() => jest.restoreAllMocks());
it.each([
  [undefined, preview],
  ["preview", preview],
  ["production", production],
] as const)("pairs native identity with the trusted API for %s", (channel, expected) => {
  const { native, runtime } = load(channel);
  expect(native.name).toBe(expected.name);
  expect(native.android.package).toBe(expected.applicationId);
  expect(native.ios.bundleIdentifier).toBe(expected.applicationId);
  expect(native.android.versionCode).toBe(expected.versionCode);
  expect(native.extra).toEqual({ releaseChannel: expected.channel, apiOrigin: expected.origin });
  expect(runtime.API_ORIGIN).toBe(expected.origin);
  expect(runtime.MOBILE_RELEASE).toEqual(expected);
  expect(native.plugins.includes("./plugins/withStagingInstrumentation.cjs")).toBe(expected.channel === "preview");
});
it.each(["prod", "", "https://other.example"])("rejects an unknown release channel %s", (channel) => {
  expect(() => load(channel)).toThrow("Unknown mobile release channel");
});
