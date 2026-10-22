import type { MobileApi as ApiPort, MobileUser } from "../contracts/mobile";
import type { SessionStore } from "./SessionStore";
import { MobileRequestError } from "../api/errors";
export type PrivateClearReason = "logout" | "account-change" | "expired" | "background" | "offline" | "page-destroyed";
export interface AccountApi extends ApiPort {
  csrf(): Promise<void>;
}
export class AuthStore {
  private user: MobileUser | null = null;
  private generation = 0;
  private accountGeneration = 0;
  private readonly listeners = new Set<() => void>();
  private readonly privateListeners = new Set<(reason: PrivateClearReason) => void>();
  constructor(
    private readonly api: AccountApi,
    private readonly sessions: SessionStore,
  ) {
    sessions.onClear(() => this.clearPrivateState("expired"));
  }
  getUser(): MobileUser | null {
    return this.user;
  }
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }
  onPrivateClear(listener: (reason: PrivateClearReason) => void): () => void {
    this.privateListeners.add(listener);
    return () => {
      this.privateListeners.delete(listener);
    };
  }
  clearPrivateState(reason: PrivateClearReason): void {
    if (reason === "account-change" || reason === "logout") this.accountGeneration++;
    this.generation++;
    this.sessions.invalidatePrivate();
    this.user = null;
    for (const listener of this.privateListeners) listener(reason);
    for (const listener of this.listeners) listener();
  }
  async login(username: string, password: string): Promise<void> {
    this.clearPrivateState("account-change");
    const generation = this.generation;
    await this.api.request("/login", { method: "POST", body: { username, password } });
    if (generation !== this.generation) return;
    await this.api.csrf();
    await this.loadUser(generation);
  }
  async logout(): Promise<void> {
    this.clearPrivateState("logout");
    const accountGeneration = this.accountGeneration;
    try {
      await this.api.request("/logout", { method: "POST", authenticated: true });
    } catch {
      /* Local credentials must be removed even if the server response is lost. */
    } finally {
      if (accountGeneration === this.accountGeneration) await this.sessions.clear();
    }
  }
  async revalidate(): Promise<void> {
    const generation = this.generation;
    try {
      await this.loadUser(generation);
    } catch (error) {
      if (generation === this.generation) this.clearPrivateState("expired");
      throw error;
    }
  }
  private async loadUser(generation: number): Promise<void> {
    const value = await this.api.request<unknown>("/me", { method: "GET", authenticated: true });
    if (generation !== this.generation) return;
    if (!validUser(value)) throw new MobileRequestError(0, "INVALID_RESPONSE", "账号响应无法读取");
    this.user = value;
    for (const listener of this.listeners) listener();
  }
}
function validUser(value: unknown): value is MobileUser {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const user = value as Record<string, unknown>;
  return (
    Object.keys(user).length === 3 &&
    typeof user.id === "string" &&
    /^[1-9][0-9]*$/.test(user.id) &&
    typeof user.username === "string" &&
    user.username.length > 0 &&
    Array.isArray(user.permissions) &&
    user.permissions.every((permission) => typeof permission === "string")
  );
}
