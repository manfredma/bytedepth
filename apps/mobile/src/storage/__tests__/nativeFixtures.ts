import { emptyCache } from "../database";
/** 替换原生边界；迁移与事务仍由生产 SQLite 适配器负责。 */
export class SqliteDriver {
  version = 0;
  cache: string | null = null;
  rows = new Map<string, string>();
  failCommit = false;
  async execAsync(sql: string) {
    if (sql.includes("user_version = 1")) this.version = 1;
  }
  async getFirstAsync<T>(sql: string, id?: string): Promise<T | null> {
    if (sql.includes("PRAGMA")) return { user_version: this.version } as T;
    const value = sql.includes("local_history") ? this.rows.get(id!) : this.cache;
    return value ? ({ value } as T) : null;
  }
  async getAllAsync<T>(): Promise<T[]> {
    return [...this.rows.values()].map((value) => ({ value }) as T);
  }
  async runAsync(sql: string, ...args: string[]) {
    if (sql.includes("local_history")) this.rows.set(args[0], args[1]);
    else this.cache = args[0];
    return { lastInsertRowId: 1, changes: 1 };
  }
  async withExclusiveTransactionAsync(action: (driver: SqliteDriver) => Promise<void>) {
    const previous = { version: this.version, cache: this.cache, rows: new Map(this.rows) };
    try {
      await action(this);
      if (this.failCommit) throw new Error("commit failed");
    } catch (error) {
      Object.assign(this, previous);
      throw error;
    }
  }
  reset() {
    this.version = 0;
    this.cache = null;
    this.rows.clear();
    this.failCommit = false;
  }
  seed() {
    this.version = 1;
    this.cache = JSON.stringify(emptyCache());
  }
}
export const driver = new SqliteDriver();
export class TestDirectory {
  uri = "file:///local/";
  constructor(...paths: unknown[]) {
    this.uri = paths.join("/");
  }
  create() {}
  list() {
    return [...TestFile.values.keys()].map((name) => new TestFile(this, name));
  }
}
export class TestFile {
  static values = new Map<string, Uint8Array>();
  name: string;
  uri: string;
  constructor(_dir: TestDirectory, name: string) {
    this.name = name;
    this.uri = `file:///local/${name}`;
  }
  get exists() {
    return TestFile.values.has(this.name);
  }
  get size() {
    return TestFile.values.get(this.name)?.length ?? 0;
  }
  create() {
    if (this.exists) throw new Error("exists");
    TestFile.values.set(this.name, new Uint8Array());
  }
  async write(bytes: Uint8Array) {
    TestFile.values.set(this.name, bytes.slice());
  }
  async bytes() {
    const value = TestFile.values.get(this.name);
    if (!value) throw new Error("missing");
    return value.slice();
  }
  move(to: TestFile) {
    const value = TestFile.values.get(this.name);
    if (!value) throw new Error("missing");
    TestFile.values.set(to.name, value);
    TestFile.values.delete(this.name);
  }
  delete() {
    TestFile.values.delete(this.name);
  }
}
export const nativeFs = {
  Directory: TestDirectory,
  File: TestFile,
  Paths: { cache: "file:///cache/", document: "file:///document/" },
};
