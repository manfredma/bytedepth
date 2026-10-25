import type { MobileApi as ApiPort, MobileApiRequestOptions } from "../contracts/mobile";
import type { SessionSnapshot, SessionStore } from "../auth/SessionStore";
import { API_BASE_PATH, API_ORIGIN } from "../config";
import { MobileRequestError, StaleResponseError } from "./errors";
export interface ResponseHeaders {
  get(name: string): string | null;
}
export interface TransportResponse {
  status: number;
  headers: ResponseHeaders;
  text(): Promise<string>;
}
export interface TransportOptions {
  method: string;
  headers: Record<string, string>;
  body?: string;
  redirect: "error" | "manual";
  credentials: "omit";
  signal?: AbortSignal;
}
export type Transport = (url: string, options: TransportOptions) => Promise<TransportResponse>;
interface PreparedSession {
  snapshot: SessionSnapshot;
  headerName: string;
  token: string;
}
export class MobileApi implements ApiPort {
  private token: { headerName: string; token: string; generation: number } | null = null;
  private csrfPending: { generation: number; privateGeneration: number; promise: Promise<PreparedSession> } | null =
    null;
  constructor(
    private readonly transport: Transport,
    private readonly sessions: SessionStore,
    private readonly visitor: () => Promise<string>,
  ) {}
  async request<T>(path: string, options: MobileApiRequestOptions): Promise<T> {
    const url = this.url(path);
    const writing = options.method !== "GET";
    const prepared = writing ? await this.ensureCsrf() : null;
    const snapshot = prepared ? prepared.snapshot : await this.sessions.snapshot();
    const headers: Record<string, string> = { Accept: "application/json" };
    if ((writing || options.authenticated) && snapshot.id) headers["X-App-Session"] = snapshot.id;
    if (prepared) headers[prepared.headerName] = prepared.token;
    if (/^\/posts\/[1-9][0-9]*\/(rating|ratings)$/.test(path)) headers["X-App-Visitor"] = await this.visitor();
    let body: string | undefined;
    if (options.body !== undefined) {
      if (path === "/login" && writing) {
        const input = options.body as Record<string, unknown>;
        if (!input || typeof input.username !== "string" || typeof input.password !== "string")
          throw new MobileRequestError(0, "INVALID_REQUEST", "请填写账号和密码");
        headers["Content-Type"] = "application/x-www-form-urlencoded";
        body = `username=${encodeURIComponent(input.username)}&password=${encodeURIComponent(input.password)}`;
      } else {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(options.body);
      }
    }
    const privateRequest = Boolean(options.authenticated) || writing;
    if (!this.sessions.isCurrent(snapshot, privateRequest)) throw new StaleResponseError();
    const response = await this.transport(url, {
      method: options.method,
      headers,
      body,
      redirect: "error",
      credentials: "omit",
      signal: options.signal,
    });
    if (!this.sessions.isCurrent(snapshot, privateRequest)) throw new StaleResponseError();
    // 公开响应不得以匿名会话替换独立认证的账号。
    const current = privateRequest
      ? await this.sessions.accept(snapshot, response.headers.get("X-App-Session"), true)
      : snapshot;
    if (current.generation !== snapshot.generation) this.token = null;
    try {
      const result = await this.decode(response);
      if (!this.sessions.isCurrent(current, privateRequest)) throw new StaleResponseError();
      return result as T;
    } catch (error) {
      if (!this.sessions.isCurrent(current, privateRequest)) throw new StaleResponseError();
      if (error instanceof MobileRequestError && privateRequest) {
        if (error.status === 401) {
          this.token = null;
          await this.sessions.clear();
        }
        if (error.code === "CSRF_INVALID") this.token = null;
      }
      throw error;
    }
  }
  async csrf(): Promise<void> {
    await this.ensureCsrf();
  }
  private async ensureCsrf(): Promise<PreparedSession> {
    const snapshot = await this.sessions.snapshot();
    if (this.token?.generation === snapshot.generation)
      return { snapshot, headerName: this.token.headerName, token: this.token.token };
    if (
      this.csrfPending?.generation === snapshot.generation &&
      this.csrfPending.privateGeneration === snapshot.privateGeneration
    )
      return this.csrfPending.promise;
    const promise = this.loadCsrf(snapshot);
    this.csrfPending = { generation: snapshot.generation, privateGeneration: snapshot.privateGeneration, promise };
    try {
      return await promise;
    } finally {
      if (this.csrfPending?.promise === promise) this.csrfPending = null;
    }
  }
  private async loadCsrf(snapshot: SessionSnapshot): Promise<PreparedSession> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (snapshot.id) headers["X-App-Session"] = snapshot.id;
    const response = await this.transport(this.url("/csrf"), {
      method: "GET",
      headers,
      redirect: "error",
      credentials: "omit",
    });
    const value = (await this.decode(response)) as Record<string, unknown>;
    if (
      value.headerName !== "X-CSRF-TOKEN" ||
      typeof value.token !== "string" ||
      !value.token ||
      value.token.length > 4096
    )
      throw new MobileRequestError(0, "INVALID_RESPONSE", "无法建立安全会话");
    const current = await this.sessions.accept(snapshot, response.headers.get("X-App-Session"), true);
    if (!current.id) throw new MobileRequestError(0, "INVALID_RESPONSE", "无法建立安全会话");
    this.token = { headerName: value.headerName, token: value.token, generation: current.generation };
    return { snapshot: current, headerName: value.headerName, token: value.token };
  }
  private url(path: string): string {
    if (!path.startsWith("/") || path.startsWith("//") || path.includes("#") || path.includes("\\"))
      throw new MobileRequestError(0, "INVALID_REQUEST", "无效接口");
    const url = new URL(`${API_ORIGIN}${API_BASE_PATH}${path}`);
    if (url.origin !== API_ORIGIN || !url.pathname.startsWith(`${API_BASE_PATH}/`))
      throw new MobileRequestError(0, "INVALID_REQUEST", "无效接口");
    return url.href;
  }
  private async decode(response: TransportResponse): Promise<unknown> {
    if (response.status === 204) return undefined;
    if (!(response.headers.get("Content-Type") ?? "").toLowerCase().startsWith("application/json"))
      throw new MobileRequestError(response.status, "INVALID_RESPONSE", "服务暂时无法响应");
    let value: unknown;
    try {
      value = JSON.parse(await response.text());
    } catch (error) {
      if (error instanceof MobileRequestError) throw error;
      throw new MobileRequestError(response.status, "INVALID_RESPONSE", "服务响应无法读取");
    }
    if (value === null || typeof value !== "object" || Array.isArray(value))
      throw new MobileRequestError(response.status, "INVALID_RESPONSE", "服务响应无法读取");
    if (response.status >= 200 && response.status < 300) return value;
    const error = value as Record<string, unknown>;
    if (typeof error.code !== "string" || typeof error.message !== "string")
      throw new MobileRequestError(response.status, "INVALID_RESPONSE", "服务响应无法读取");
    const retry = Number(response.headers.get("Retry-After"));
    throw new MobileRequestError(
      response.status,
      error.code,
      error.message,
      Number.isFinite(retry) && retry > 0 ? retry * 1000 : null,
    );
  }
}
