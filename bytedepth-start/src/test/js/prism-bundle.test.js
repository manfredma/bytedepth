const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "../../../..");
const template = fs.readFileSync(
  path.join(root, "bytedepth-start/src/main/resources/templates/public/posts/detail.html"),
  "utf8",
);
const prismScripts = [...template.matchAll(/<script th:src="@\{(\/vendor\/prism\/[^}]+)\}"/g)].map((match) => match[1]);
const loadPrism = () => {
  const context = vm.createContext({});
  for (const script of prismScripts) {
    vm.runInContext(
      fs.readFileSync(path.join(root, "bytedepth-start/src/main/resources/static", script), "utf8"),
      context,
    );
  }
  return context.Prism;
};

// Missing modules must fail on actual token output, even if language labels render correctly.
test.each([
  ["c", "#include <stdint.h>\nuint64_t read_value(const unsigned char *bytes) { return bytes[0]; }", "keyword"],
  ["rust", "fn main() { let value: u64 = 42; }", "keyword"],
  ["go", 'package main\nfunc main() { println("go") }', "keyword"],
  ["coq", "Theorem identity : forall P : Prop, P -> P. Proof. auto. Qed.", "keyword"],
  ["scheme", "(define (square x) (* x x))", "keyword"],
  ["yaml", "name: bytedepth\nenabled: true", "key"],
  ["dockerfile", "FROM eclipse-temurin:25\nRUN echo hello", "keyword"],
  ["make", "all:\n\techo hello", "target"],
  ["proto", 'syntax = "proto3";\nmessage Value { string name = 1; }', "keyword"],
])("article highlights %s with the shipped scripts", (language, source, token) => {
  const prism = loadPrism();
  expect(prism.languages[language]).toBeDefined();
  expect(prism.highlight(source, prism.languages[language], language)).toContain(`token ${token}`);
});

test("article uses one self-contained Prism script before code block initialization", () => {
  expect(prismScripts).toHaveLength(1);
  expect(template.indexOf(prismScripts[0])).toBeLessThan(template.indexOf("/js/code-blocks.js"));
});

test("unknown language keeps its readable source when highlighted", () => {
  const prism = loadPrism();
  const code = document.createElement("code");
  code.className = "language-unknown-example";
  code.textContent = "value < 5 && value > 1";
  prism.highlightElement(code);
  expect(code.textContent).toBe("value < 5 && value > 1");
  expect(code.querySelector(".token")).toBeNull();
});

test("committed bundle matches the pinned language manifest and dependency graph", () => {
  const { execFileSync } = require("node:child_process");
  expect(() => execFileSync(process.execPath, ["scripts/build-prism.mjs", "--check"], { cwd: root })).not.toThrow();
  const manifest = JSON.parse(fs.readFileSync(path.join(root, "scripts/prism-languages.json"), "utf8"));
  const prism = loadPrism();
  for (const language of manifest.languages) {
    expect(prism.languages[language], language).toBeDefined();
  }
  for (const [alias, language] of Object.entries(manifest.aliases)) {
    expect(prism.languages[alias]).toBe(prism.languages[language]);
  }
});
