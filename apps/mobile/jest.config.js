module.exports = {
  clearMocks: true,
  watchman: false,
  collectCoverageFrom: ["App.tsx", "plugins/**/*.cjs", "src/**/*.{ts,tsx,js}", "!src/**/__tests__/**"],
  coverageThreshold: { global: { statements: 100, branches: 100, functions: 100, lines: 100 } },
  projects: [
    {
      displayName: "native",
      moduleNameMapper: { "^lucide-react-native$": require.resolve("lucide-react-native") },
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
