module.exports = {
  clearMocks: true,
  watchman: false,
  projects: [
    {
      displayName: "native",
      preset: "jest-expo",
      testMatch: ["**/__tests__/**/*.test.[jt]s?(x)"],
      testPathIgnorePatterns: ["/node_modules/", "/web.test.ts$"],
      clearMocks: true,
    },
    {
      displayName: "reader-dom",
      testEnvironment: "jsdom",
      testMatch: ["**/__tests__/web.test.ts"],
      transform: { "^.+\\.[jt]sx?$": ["babel-jest", { presets: ["babel-preset-expo"] }] },
      clearMocks: true,
    },
  ],
};
