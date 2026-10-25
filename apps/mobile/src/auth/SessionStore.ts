import { StaleResponseError } from "../api/errors";
export interface SecureValues {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}
export interface SessionSnapshot {
  id: string | null;
  generation: number;
  privateGeneration: number;
}
export class SessionStore {
  private id: string | null = null;
  private generation = 0;
  private privateGeneration = 0;
  private readonly ready: Promise<void>;
  private writes: Promise<void> = Promise.resolve();
  private readonly clearListeners = new Set<() => void>();
  constructor(private readonly values: SecureValues) {
    this.ready = values.get("mobile-session").then((value) => {
      this.id = value;
    });
  }
  async snapshot(): Promise<SessionSnapshot> {
    await this.ready;
    return { id: this.id, generation: this.generation, privateGeneration: this.privateGeneration };
  }
  async accept(expected: SessionSnapshot, id: string | null, privateRequest = false): Promise<SessionSnapshot> {
    if (!this.isCurrent(expected, privateRequest)) throw new StaleResponseError();
    if (id !== null && id !== this.id) {
      this.id = id || null;
      this.generation++;
      this.privateGeneration++;
      const accepted = { id: this.id, generation: this.generation, privateGeneration: this.privateGeneration };
      await this.persist();
      if (!this.isCurrent(accepted, privateRequest)) throw new StaleResponseError();
      return accepted;
    }
    return { id: this.id, generation: this.generation, privateGeneration: this.privateGeneration };
  }
  async clear(): Promise<void> {
    await this.ready;
    // 存储 I/O 前同步使会话失效，防止延迟响应恢复已退出的会话。
    this.generation++;
    this.privateGeneration++;
    this.id = null;
    for (const listener of this.clearListeners) listener();
    await this.persist();
  }
  onClear(listener: () => void): () => void {
    this.clearListeners.add(listener);
    return () => {
      this.clearListeners.delete(listener);
    };
  }
  invalidatePrivate(): void {
    this.privateGeneration++;
  }
  isCurrent(snapshot: SessionSnapshot, privateRequest: boolean): boolean {
    return (
      snapshot.generation === this.generation &&
      (!privateRequest || snapshot.privateGeneration === this.privateGeneration)
    );
  }
  private persist(): Promise<void> {
    const id = this.id;
    // 串行执行写入，避免缓慢的 SecureStore 写入在后续退出删除之后完成。
    const next = this.writes
      .catch(() => undefined)
      .then(() => (id === null ? this.values.remove("mobile-session") : this.values.set("mobile-session", id)));
    this.writes = next;
    return next;
  }
}
