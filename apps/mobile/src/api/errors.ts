export class MobileRequestError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly retryAfterMs: number | null = null,
  ) {
    super(message);
    this.name = "MobileRequestError";
  }
}
export class StaleResponseError extends MobileRequestError {
  constructor() {
    super(0, "STALE_RESPONSE", "页面或账号已变化");
  }
}
