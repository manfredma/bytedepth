import fs from "node:fs";
import path from "node:path";
const root = path.resolve(process.argv[2] ?? "apps/mobile");
const report = JSON.parse(fs.readFileSync(path.join(root, "coverage/coverage-final.json"), "utf8"));
const source = [];
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === "__tests__") continue;
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(file);
    else if (/\.(?:ts|tsx|js|cjs)$/.test(entry.name)) source.push(file);
  }
}
walk(path.join(root, "src"));
walk(path.join(root, "plugins"));
source.push(path.join(root, "App.tsx"));
for (const file of source) {
  const covered = report[file];
  if (!covered) throw new Error(`Missing mobile coverage: ${path.relative(root, file)}`);
  for (const metric of ["s", "f", "b"]) {
    const values = Object.values(covered[metric]).flat();
    if (values.some((count) => typeof count !== "number" || count < 1))
      throw new Error(`Uncovered mobile ${metric}: ${path.relative(root, file)}`);
  }
}
console.log(`Mobile owned source coverage verified: ${source.length} files, 100% statements/branches/functions.`);
