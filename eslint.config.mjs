import js from "@eslint/js";
import globals from "globals";

export default [
  {
    ignores: ["node_modules/**", "bytedepth-*/target/**"],
  },
  js.configs.recommended,
  {
    files: ["tests/e2e/**/*.js"],
    ignores: ["tests/e2e/helpers/verification-traffic.js"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector: "CallExpression[callee.property.name='newContext']",
          message:
            "Use newVerificationContext so validation traffic is excluded; statistics tests use an explicitly unmarked fixture.",
        },
      ],
    },
  },
  {
    files: ["bytedepth-start/src/main/resources/static/js/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "script",
      globals: { ...globals.browser, Vditor: "readonly" },
    },
    rules: {
      "no-var": "error",
      "prefer-const": "error",
      "no-console": "error",
      "no-unused-vars": ["error", { args: "none" }],
      eqeqeq: ["error", "always"],
      curly: "error",
      "no-eval": "error",
    },
  },
  {
    files: ["**/src/test/js/**/*.test.js", "tests/**/*.js"],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node, ...globals.jest },
    },
    rules: {
      "no-eval": "off",
    },
  },
  {
    files: ["playwright.config.mjs"],
    languageOptions: {
      globals: globals.node,
    },
  },
];
