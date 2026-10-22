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
  async accept(expected: SessionSnapshot, id: string | null): Promise<SessionSnapshot> {
    if (!this.isCurrent(expected)) throw new StaleResponseError();
    if (id !== null && id !== this.id) {
      this.id = id || null;
      this.generation++;
      this.privateGeneration++;
      await this.persist();
    }
    return this.snapshot();
  }
  async clear(): Promise<void> {
    await this.ready;
    // Invalidate synchronously before storage I/O: late responses must not restore a logged-out session.
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
  isCurrent(snapshot: SessionSnapshot, privateRequest = false): boolean {
    return (
      snapshot.generation === this.generation &&
      (!privateRequest || snapshot.privateGeneration === this.privateGeneration)
    );
  }
  private persist(): Promise<void> {
    const id = this.id;
    // Serialize writes so a slow SecureStore set cannot finish after a later logout delete.
    const next = this.writes
      .catch(() => undefined)
      .then(() => (id === null ? this.values.remove("mobile-session") : this.values.set("mobile-session", id)));
    this.writes = next;
    return next;
  }
}
