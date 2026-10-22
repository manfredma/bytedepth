export interface ReadingPreferences {
  theme: "system" | "light" | "dark";
  fontScale: number;
}
export interface PreferenceFile {
  read(): Promise<string | null>;
  write(text: string): Promise<void>;
}
function valid(value: unknown): value is ReadingPreferences {
  if (!value || typeof value !== "object") return false;
  const p = value as ReadingPreferences;
  return (
    ["system", "light", "dark"].includes(p.theme) &&
    Number.isFinite(p.fontScale) &&
    p.fontScale >= 0.8 &&
    p.fontScale <= 1.8
  );
}
export class Preferences {
  private readonly listeners = new Set<() => void>();
  private writes: Promise<void> = Promise.resolve();
  private constructor(
    private readonly file: PreferenceFile,
    private value: ReadingPreferences,
  ) {}
  static async open(file: PreferenceFile): Promise<Preferences> {
    let value: ReadingPreferences = { theme: "system", fontScale: 1 };
    try {
      const text = await file.read();
      const parsed: unknown = text === null ? null : JSON.parse(text);
      if (valid(parsed)) value = { theme: parsed.theme, fontScale: parsed.fontScale };
    } catch {
      /* Unreadable preferences never block public reading. */
    }
    return new Preferences(file, value);
  }
  snapshot = () => this.value;
  subscribe = (fn: () => void) => {
    this.listeners.add(fn);
    return () => {
      this.listeners.delete(fn);
    };
  };
  async set(value: ReadingPreferences) {
    if (!valid(value)) throw new Error("阅读设置不正确");
    const next = { ...value };
    this.writes = this.writes.catch(() => undefined).then(() => this.file.write(JSON.stringify(next)));
    await this.writes;
    this.value = next;
    for (const fn of this.listeners) fn();
  }
}
