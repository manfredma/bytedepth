import type { SecureValues } from "./SessionStore";
export class InstallIdentity {
  private pending: Promise<string> | null = null;
  constructor(
    private readonly secure: SecureValues,
    private readonly marker: SecureValues,
    private readonly uuid: () => string,
  ) {}
  visitorId(): Promise<string> {
    this.pending ??= this.load().catch((error) => {
      this.pending = null;
      throw error;
    });
    return this.pending;
  }
  private async load(): Promise<string> {
    const marker = await this.marker.get("install-marker");
    const previous = await this.secure.get("rating-install");
    if (marker !== null && previous !== null && marker === previous && /^app:[a-f0-9-]{36}$/.test(previous))
      return previous;
    const id = `app:${this.uuid()}`;
    if (!/^app:[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(id))
      throw new Error("Invalid installation identity");
    // A missing non-backed-up marker means reinstall, even if iOS Keychain retained a previous UUID.
    await this.secure.set("rating-install", id);
    await this.marker.set("install-marker", id);
    return id;
  }
}
