import { MobileRequestError } from "../api/errors";
import type {
  AnnotationEnvelope,
  CreateAnnotationRequest,
  MobileApi,
  MobileApiRequestOptions,
  MobileComment,
  MobilePage,
  MobileUser,
  PublicContent,
  RatingScore,
  RatingSummary,
  ReaderSelection,
  AnnotationVisibility,
  MobileAnnotation,
} from "../contracts/mobile";
export interface InteractionAuth {
  getUser(): MobileUser | null;
  onPrivateClear(listener: () => void): () => void;
}
export interface InteractionState {
  rating: RatingSummary | null;
  comments: MobileComment[];
  hasMoreComments: boolean;
  annotations: AnnotationEnvelope | null;
  draft: CreateAnnotationRequest | null;
}
/** 在线交互内存归属于特定文章、页面及账号代次。 */
export class InteractionStore {
  private value: InteractionState = {
    rating: null,
    comments: [],
    hasMoreComments: false,
    annotations: null,
    draft: null,
  };
  private generation = 0;
  private retryAt = 0;
  private cooldown: MobileRequestError | null = null;
  private online = true;
  private disposed = false;
  private readonly listeners = new Set<() => void>();
  private readonly unsubscribe: () => void;
  constructor(
    private readonly api: MobileApi,
    private readonly auth: InteractionAuth,
    private readonly uuid: () => string,
    private readonly content: PublicContent,
  ) {
    this.unsubscribe = auth.onPrivateClear(() => this.clear());
  }
  snapshot = () => this.value;
  subscribe = (fn: () => void) => {
    this.listeners.add(fn);
    return () => {
      this.listeners.delete(fn);
    };
  };
  private patch(value: Partial<InteractionState>) {
    this.value = { ...this.value, ...value };
    for (const listener of this.listeners) listener();
  }
  clear() {
    this.generation++;
    this.patch({ annotations: null, draft: null, comments: [] });
  }
  dispose() {
    this.disposed = true;
    this.clear();
    this.unsubscribe();
    this.listeners.clear();
  }
  setOnline(online: boolean) {
    this.online = online;
    if (!online) this.clear();
  }
  private async request<T>(path: string, options: MobileApiRequestOptions): Promise<T> {
    this.guard();
    try {
      return await this.api.request<T>(path, options);
    } catch (error) {
      if (error instanceof MobileRequestError && error.status === 429 && error.retryAfterMs !== null) {
        this.cooldown = error;
        this.retryAt = Date.now() + error.retryAfterMs;
      }
      throw error;
    }
  }
  private guard() {
    if (this.cooldown && Date.now() < this.retryAt) throw this.cooldown;
    if (!this.online || this.disposed) throw new Error("此操作需要联网");
  }
  private authenticated(permission?: string) {
    this.guard();
    const user = this.auth.getUser();
    if (!user) throw new Error("请先登录");
    if (permission && !user.permissions.includes(permission)) throw new Error("当前账号没有评论权限");
  }
  private path(suffix: string) {
    return `/posts/${this.content.postId}/${suffix}`;
  }
  async loadRating() {
    this.guard();
    const generation = this.generation;
    const rating = await this.request<RatingSummary>(this.path("ratings"), { method: "GET" });
    if (generation === this.generation) this.patch({ rating });
  }
  async rate(score: RatingScore) {
    this.guard();
    if (![1, 2, 3, 4, 5].includes(score)) throw new Error("请选择 1–5 分");
    const generation = this.generation;
    await this.request(this.path("rating"), { method: "PUT", body: { score } });
    if (generation === this.generation) await this.loadRating();
  }
  async loadComments(page = 1) {
    this.guard();
    const generation = this.generation;
    const result = await this.request<MobilePage<MobileComment>>(this.path(`comments?page=${page}`), {
      method: "GET",
    });
    if (generation === this.generation)
      this.patch({
        comments: [
          ...new Map(
            (page === 1 ? result.items : [...this.value.comments, ...result.items]).map((comment) => [
              comment.id,
              comment,
            ]),
          ).values(),
        ],
        hasMoreComments: result.hasNext,
      });
  }
  async comment(text: string) {
    this.authenticated("blog:comment:create");
    const content = text.trim();
    if (!content || content.length > 2000) throw new Error("评论需要 1–2000 字");
    const generation = this.generation;
    await this.request(this.path("comments"), { method: "POST", authenticated: true, body: { content } });
    if (generation === this.generation) await this.loadComments();
  }
  async loadAnnotations() {
    this.guard();
    const generation = this.generation;
    const annotations = await this.request<AnnotationEnvelope>(this.path("annotations"), {
      method: "GET",
      authenticated: !!this.auth.getUser(),
    });
    if (generation === this.generation) this.patch({ annotations });
  }
  select(selection: ReaderSelection, current: boolean) {
    this.authenticated();
    if (!current) throw new Error("请先确认更新，再新建批注");
    this.patch({
      draft: {
        anchor: {
          schemaVersion: 1,
          sourceRevision: this.content.contentVersion,
          projectionVersion: this.content.projectionVersion,
          digest: this.content.digest,
          ...selection,
          requestId: this.uuid(),
        },
        annotationText: "",
        color: "yellow",
        visibility: "PRIVATE",
      },
    });
  }
  editDraft(annotationText: string, visibility: AnnotationVisibility, color: string) {
    if (!this.value.draft) return;
    this.patch({ draft: { ...this.value.draft, annotationText, visibility, color } });
  }
  cancelDraft() {
    this.patch({ draft: null });
  }
  async saveDraft() {
    this.authenticated();
    const draft = this.value.draft;
    if (!draft) throw new Error("请先选择原文");
    if (draft.annotationText.length > 2000) throw new Error("评注最多 2000 字");
    const generation = this.generation;
    await this.request(this.path("annotations"), { method: "POST", authenticated: true, body: draft });
    if (generation === this.generation) {
      this.patch({ draft: null });
      await this.loadAnnotations();
    }
  }
  async update(annotation: MobileAnnotation, text: string, visibility: AnnotationVisibility) {
    this.authenticated();
    if (!annotation.ownedByCurrentVisitor) throw new Error("只能修改本人批注");
    if (text.length > 2000) throw new Error("评注最多 2000 字");
    const generation = this.generation;
    await this.request(this.path(`annotations/${annotation.id}`), {
      method: "PATCH",
      authenticated: true,
      body: { annotationText: text, visibility },
    });
    if (generation === this.generation) await this.loadAnnotations();
  }
  async remove(annotation: MobileAnnotation) {
    this.authenticated();
    if (!annotation.ownedByCurrentVisitor) throw new Error("只能删除本人批注");
    const generation = this.generation;
    await this.request(this.path(`annotations/${annotation.id}`), { method: "DELETE", authenticated: true });
    if (generation === this.generation) await this.loadAnnotations();
  }
}
