import { parseBridgeMessage, type PublicContent, type ResumePosition } from "../contracts/mobile";
import type { LocalDatabase } from "./database";
export class HistoryStore {
  constructor(private readonly db: LocalDatabase) {}
  async record(content: PublicContent, now: string): Promise<void> {
    await this.db.changeHistory(content.postId, (old) => ({
      postId: content.postId,
      title: content.title,
      slug: content.slug,
      position: old?.position ?? null,
      updatedAt: now,
    }));
  }
  async save(position: ResumePosition): Promise<void> {
    const parsed = parseBridgeMessage(
      JSON.stringify({
        type: "position",
        postId: position.postId,
        contentVersion: position.contentVersion,
        generation: 0,
        payload: position,
      }),
      { postId: position.postId, contentVersion: position.contentVersion, generation: 0 },
    );

    await this.db.changeHistory(position.postId, (old) => {
      if (!old) throw new Error("Article has not been opened");
      return { ...old, position: parsed.payload as ResumePosition, updatedAt: position.updatedAt };
    });
  }
  async list() {
    return (await this.db.history()).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }
}
