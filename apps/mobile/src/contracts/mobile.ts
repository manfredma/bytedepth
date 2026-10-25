/** 传输 ID 保持十进制字符串形式；Java long 可能超出 Number 的精度。 */
export type PostId = string;
export type DatabaseId = string;
export type UtcTimestamp = string;
export const API_SCHEMA_VERSION = 1 as const;
export const BRIDGE_MAX_BYTES = 65_536;
export interface MobilePage<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
}
export type ApiErrorCode =
  | "INVALID_REQUEST"
  | "AUTHENTICATION_FAILED"
  | "CSRF_INVALID"
  | "FORBIDDEN"
  | "RATE_LIMITED"
  | "INTERNAL_ERROR"
  | "USERNAME_TAKEN"
  | "POST_UNAVAILABLE"
  | "STALE_REVISION"
  | "REQUEST_REUSED"
  | "DELETED_REQUEST"
  | "INVALID_ANCHOR";
export interface ApiError {
  code: string;
  message: string;
}
export interface CsrfToken {
  headerName: string;
  token: string;
}
export interface MobileUser {
  id: DatabaseId;
  username: string;
  permissions: string[];
}
export interface RegistrationResult {
  status: "PENDING";
}
export interface NamedCategory {
  id: DatabaseId;
  name: string;
  slug: string;
}
export interface DiscoveryCategory extends NamedCategory {
  parentId: DatabaseId | null;
  parentName: string | null;
  depth: number;
}
export interface TagSummary {
  id: DatabaseId;
  name: string;
  slug: string;
}
export interface SeriesPost {
  id: PostId;
  slug: string;
  title: string;
  seriesOrder: number | null;
  estimatedReadingMinutes: number | null;
}
export interface SeriesSummary {
  id: DatabaseId;
  name: string;
  slug: string;
  posts: SeriesPost[];
  position: number;
  total: number;
  progressPercent: number;
  previous: SeriesPost | null;
  next: SeriesPost | null;
}
export interface PostSummary {
  id: PostId;
  slug: string;
  title: string;
  summary: string;
  publishedAt: UtcTimestamp | null;
  estimatedReadingMinutes: number;
  category: NamedCategory | null;
}
export interface SearchResult {
  id: PostId;
  slug: string;
  title: string;
  snippet: string;
  categoryName: string | null;
  categorySlug: string | null;
  tags: string[] | null;
  seriesName: string | null;
}
export interface Discovery {
  featured: PostSummary[];
  latest: PostSummary[];
  categories: DiscoveryCategory[];
  tags: TagSummary[];
}
export interface ColumnSummary {
  id: DatabaseId;
  name: string;
  slug: string;
  description: string | null;
  postCount: number;
  firstSummary: string | null;
}
export interface ColumnPost {
  id: PostId;
  slug: string;
  title: string;
  seriesOrder: number | null;
  summary: string;
  publishedAt: UtcTimestamp | null;
}
export interface ColumnDetail {
  id: DatabaseId;
  name: string;
  slug: string;
  description: string | null;
  posts: MobilePage<ColumnPost>;
}
export interface ResolvedPost {
  postId: PostId;
  slug: string;
}
export interface ContentIdentity {
  postId: PostId;
  contentVersion: number;
  projectionVersion: number;
  digest: string;
  representationHash: string;
  shellVersion: string;
}
export interface DocumentSegment {
  id: string;
  blockId: string;
  kind: string;
  text: string;
  start: number;
  end: number;
}
export interface ArticleDocument {
  projectionVersion: number;
  digest: string;
  text: string;
  segments: DocumentSegment[];
}
export interface PublicResource {
  key: string;
  url: string;
  role: "image";
  required: true;
  mediaType: string | null;
  sha256: string | null;
}
export interface PublicContent extends ContentIdentity {
  schemaVersion: 1;
  slug: string;
  title: string;
  publishedAt: UtcTimestamp;
  category: NamedCategory | null;
  series: SeriesSummary | null;
  html: string;
  document: ArticleDocument;
  resources: PublicResource[];
}
export type PostState =
  | { schemaVersion: 1; postId: PostId; availability: "AVAILABLE"; identity: ContentIdentity }
  | { schemaVersion: 1; postId: PostId; availability: "UNAVAILABLE" };
export interface RatingSummary {
  averageScore: number;
  ratingCount: number;
  visitorScore: number | null;
}
export type RatingScore = 1 | 2 | 3 | 4 | 5;
export interface RatingRequest {
  score: RatingScore;
}
export interface CommentRequest {
  content: string;
}
export interface MobileComment {
  id: DatabaseId;
  postId: PostId;
  content: string;
  username: string;
  createdAt: UtcTimestamp;
}
export interface SegmentSpan {
  segmentId: string;
  start: number;
  end: number;
}
export interface AnnotationAnchor {
  schemaVersion: 1;
  sourceRevision: number;
  projectionVersion: number;
  digest: string;
  exact: string;
  prefix: string;
  suffix: string;
  start: number;
  end: number;
  spans: SegmentSpan[];
  requestId: string;
}
export type AnnotationVisibility = "PRIVATE" | "PUBLIC";
export interface AnnotationPlacement {
  annotationId: DatabaseId;
  targetRevision: number;
  projectionVersion: number;
  resolverVersion: number;
  status: "ANCHORED" | "UNRESOLVED" | "AMBIGUOUS" | "LEGACY_UNVERIFIED";
  spans: SegmentSpan[];
  reason:
    | "EXACT"
    | "MISSING_REVISION"
    | "MODEL_MISMATCH"
    | "QUOTE_CHANGED"
    | "TARGET_MISSING"
    | "DUPLICATE_TARGET"
    | "BUDGET_EXCEEDED"
    | "NO_VISIBLE_SOURCE"
    | "LEGACY_EVIDENCE_MISSING";
}
export interface MobileAnnotation {
  id: DatabaseId;
  postId: PostId;
  selectedText: string;
  annotationText: string;
  annotationHtml: string;
  color: string;
  visibility: AnnotationVisibility;
  createdAt: UtcTimestamp;
  ownedByCurrentVisitor: boolean;
  anchor: AnnotationAnchor | null;
  placement: AnnotationPlacement;
  targetDigest: string;
  migrationBaselineRevision: number | null;
}
export interface AnnotationEnvelope {
  schemaVersion: 1;
  postId: PostId;
  contentVersion: number;
  projectionVersion: number;
  digest: string;
  annotations: MobileAnnotation[];
}
export interface CreateAnnotationRequest {
  anchor: AnnotationAnchor;
  annotationText: string;
  color: string;
  visibility: AnnotationVisibility;
}
export interface UpdateAnnotationRequest {
  annotationText: string;
  visibility: AnnotationVisibility;
}
export interface MobileApiRequestOptions {
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  authenticated?: boolean;
  signal?: AbortSignal;
}
export interface MobileApi {
  request<T>(path: string, options: MobileApiRequestOptions): Promise<T>;
}
export interface ResumePosition {
  postId: PostId;
  contentVersion: number;
  projectionVersion: number;
  segmentId: string;
  codePointOffset: number;
  blockKind: string;
  blockText: string;
  precedingHeadings: string[];
  viewportRatio: number;
  progress: number;
  updatedAt: UtcTimestamp;
}
export type CopyState = "NONE" | "ONLINE_ONLY" | "OFFLINE_READY" | "UNAVAILABLE";
export type CheckState = "IDLE" | "CHECKING" | "CURRENT" | "UPDATE_AVAILABLE" | "FAILED";
export interface ReaderSnapshot {
  postId: PostId;
  copyState: CopyState;
  checkState: CheckState;
  content: PublicContent | null;
  lastConfirmedAt: UtcTimestamp | null;
}
export interface RecentEntry {
  postId: PostId;
  title: string;
  slug: string;
  position: ResumePosition | null;
  copyState: CopyState;
  updatedAt: UtcTimestamp;
}
export interface ReaderRepository {
  open(postId: PostId): AsyncIterable<ReaderSnapshot>;
  refresh(postId: PostId): Promise<void>;
  release(postId: PostId): void;
  savePosition(position: ResumePosition): Promise<void>;
  listRecent(): Promise<RecentEntry[]>;
}
export interface BridgeContext {
  postId: PostId;
  contentVersion: number;
  generation: number;
}
export interface TocEntry {
  segmentId: string;
  title: string;
  level: number;
}
export interface ReaderSelection {
  exact: string;
  prefix: string;
  suffix: string;
  start: number;
  end: number;
  spans: SegmentSpan[];
}
export type ReaderBridgeMessage = BridgeContext &
  (
    | { type: "ready"; payload: Record<string, never> }
    | { type: "position"; payload: ResumePosition }
    | { type: "toc"; payload: { entries: TocEntry[] } }
    | { type: "selection"; payload: ReaderSelection }
    | { type: "link"; payload: { href: string } }
    | { type: "renderError"; payload: { message: string } }
  );

function requireValid(condition: unknown): asserts condition {
  if (!condition) throw new Error("Invalid mobile contract");
}
function record(value: unknown): Record<string, unknown> {
  requireValid(value !== null && typeof value === "object" && !Array.isArray(value));
  return value as Record<string, unknown>;
}
function keys(value: Record<string, unknown>, fields: string[]): void {
  requireValid(Object.keys(value).length === fields.length && fields.every((field) => Object.hasOwn(value, field)));
}
function string(value: unknown): value is string {
  return typeof value === "string";
}
function nonempty(value: unknown): value is string {
  return string(value) && value.length > 0;
}
function integer(value: unknown, min = 0): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= min;
}
function ratio(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 && value <= 1;
}
function id(value: unknown): value is string {
  return string(value) && /^[1-9][0-9]*$/.test(value);
}
function utc(value: unknown): value is string {
  return (
    string(value) &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value) &&
    Number.isFinite(Date.parse(value))
  );
}
function identity(value: unknown): ContentIdentity {
  const v = record(value);
  keys(v, ["postId", "contentVersion", "projectionVersion", "digest", "representationHash", "shellVersion"]);
  requireValid(
    id(v.postId) &&
      integer(v.contentVersion, 1) &&
      integer(v.projectionVersion, 1) &&
      nonempty(v.digest) &&
      nonempty(v.representationHash) &&
      nonempty(v.shellVersion),
  );
  return v as unknown as ContentIdentity;
}
function category(value: unknown): void {
  if (value === null) return;
  const v = record(value);
  keys(v, ["id", "name", "slug"]);
  requireValid(id(v.id) && nonempty(v.name) && nonempty(v.slug));
}
function seriesPost(value: unknown): void {
  const v = record(value);
  keys(v, ["id", "slug", "title", "seriesOrder", "estimatedReadingMinutes"]);
  requireValid(
    id(v.id) &&
      nonempty(v.slug) &&
      nonempty(v.title) &&
      (v.seriesOrder === null || integer(v.seriesOrder)) &&
      (v.estimatedReadingMinutes === null || integer(v.estimatedReadingMinutes)),
  );
}
function series(value: unknown): void {
  if (value === null) return;
  const v = record(value);
  keys(v, ["id", "name", "slug", "posts", "position", "total", "progressPercent", "previous", "next"]);
  requireValid(
    id(v.id) &&
      nonempty(v.name) &&
      nonempty(v.slug) &&
      Array.isArray(v.posts) &&
      integer(v.position) &&
      integer(v.total) &&
      typeof v.progressPercent === "number" &&
      Number.isFinite(v.progressPercent) &&
      v.progressPercent >= 0 &&
      v.progressPercent <= 100,
  );
  v.posts.forEach(seriesPost);
  if (v.previous !== null) seriesPost(v.previous);
  if (v.next !== null) seriesPost(v.next);
}

/** 在信任边界严格解析，防止未知字段将私有状态带入磁盘或 WebView。 */
export function parsePublicContent(value: unknown): PublicContent {
  const v = record(value);
  keys(v, [
    "schemaVersion",
    "postId",
    "contentVersion",
    "projectionVersion",
    "digest",
    "representationHash",
    "shellVersion",
    "slug",
    "title",
    "publishedAt",
    "category",
    "series",
    "html",
    "document",
    "resources",
  ]);
  requireValid(v.schemaVersion === API_SCHEMA_VERSION);
  identity(
    Object.fromEntries(
      ["postId", "contentVersion", "projectionVersion", "digest", "representationHash", "shellVersion"].map((key) => [
        key,
        v[key],
      ]),
    ),
  );
  requireValid(nonempty(v.slug) && nonempty(v.title) && utc(v.publishedAt) && string(v.html));
  category(v.category);
  series(v.series);
  const doc = record(v.document);
  keys(doc, ["projectionVersion", "digest", "text", "segments"]);
  requireValid(
    doc.projectionVersion === v.projectionVersion &&
      doc.digest === v.digest &&
      string(doc.text) &&
      Array.isArray(doc.segments),
  );
  const codePoints = Array.from(doc.text);
  const segmentIds = new Set<string>();
  for (const item of doc.segments) {
    const s = record(item);
    keys(s, ["id", "blockId", "kind", "text", "start", "end"]);
    requireValid(
      nonempty(s.id) &&
        !segmentIds.has(s.id) &&
        nonempty(s.blockId) &&
        nonempty(s.kind) &&
        string(s.text) &&
        integer(s.start) &&
        integer(s.end) &&
        s.end >= s.start &&
        s.end <= codePoints.length &&
        codePoints.slice(s.start, s.end).join("") === s.text,
    );
    segmentIds.add(s.id);
  }
  requireValid(Array.isArray(v.resources));
  const resourceKeys = new Set<string>();
  for (const item of v.resources) {
    const r = record(item);
    keys(r, ["key", "url", "role", "required", "mediaType", "sha256"]);
    requireValid(
      nonempty(r.key) &&
        !resourceKeys.has(r.key) &&
        // 此处将文章图片 URL 视为不透明值；下载器负责拒绝不安全传输或用户信息。
        string(r.url) &&
        /^https?:\/\//i.test(r.url) &&
        r.role === "image" &&
        r.required === true &&
        (r.mediaType === null || nonempty(r.mediaType)) &&
        (r.sha256 === null || (string(r.sha256) && /^[a-fA-F0-9]{64}$/.test(r.sha256))),
    );
    resourceKeys.add(r.key);
  }
  return v as unknown as PublicContent;
}
export function parsePostState(value: unknown): PostState {
  const v = record(value);
  requireValid(v.schemaVersion === API_SCHEMA_VERSION && id(v.postId));
  if (v.availability === "UNAVAILABLE") keys(v, ["schemaVersion", "postId", "availability"]);
  else {
    keys(v, ["schemaVersion", "postId", "availability", "identity"]);
    requireValid(v.availability === "AVAILABLE" && identity(v.identity).postId === v.postId);
  }
  return v as unknown as PostState;
}
function spans(value: unknown, start: number, end: number): void {
  requireValid(Array.isArray(value) && value.length > 0);
  let length = 0;
  for (const item of value) {
    const s = record(item);
    keys(s, ["segmentId", "start", "end"]);
    requireValid(nonempty(s.segmentId) && integer(s.start) && integer(s.end) && s.end > s.start);
    length += s.end - s.start;
  }
  // 投影分隔符（换行和制表符）具有文档偏移，但不属于任何片段。
  requireValid(length <= end - start);
}
/** 按字节而非 UTF-16 长度计数，避免接收由多字节文本组成的超大消息。 */
function utf8Bytes(value: string): number {
  let size = 0;
  for (const point of value) {
    const n = point.codePointAt(0)!;
    size += n <= 0x7f ? 1 : n <= 0x7ff ? 2 : n <= 0xffff ? 3 : 4;
  }
  return size;
}
export function parseBridgeMessage(value: string, context: BridgeContext): ReaderBridgeMessage {
  requireValid(string(value) && utf8Bytes(value) <= BRIDGE_MAX_BYTES);
  const v = record(JSON.parse(value));
  keys(v, ["type", "postId", "contentVersion", "generation", "payload"]);
  requireValid(
    id(v.postId) &&
      integer(v.contentVersion, 1) &&
      integer(v.generation) &&
      v.postId === context.postId &&
      v.contentVersion === context.contentVersion &&
      v.generation === context.generation,
  );
  const p = record(v.payload);
  switch (v.type) {
    case "ready":
      keys(p, []);
      break;
    case "position":
      keys(p, [
        "postId",
        "contentVersion",
        "projectionVersion",
        "segmentId",
        "codePointOffset",
        "blockKind",
        "blockText",
        "precedingHeadings",
        "viewportRatio",
        "progress",
        "updatedAt",
      ]);
      requireValid(
        p.postId === v.postId &&
          p.contentVersion === v.contentVersion &&
          integer(p.projectionVersion, 1) &&
          nonempty(p.segmentId) &&
          integer(p.codePointOffset) &&
          nonempty(p.blockKind) &&
          string(p.blockText) &&
          p.codePointOffset <= Array.from(p.blockText).length &&
          Array.isArray(p.precedingHeadings) &&
          p.precedingHeadings.every(string) &&
          ratio(p.viewportRatio) &&
          ratio(p.progress) &&
          utc(p.updatedAt),
      );
      break;
    case "toc":
      keys(p, ["entries"]);
      requireValid(Array.isArray(p.entries));
      for (const item of p.entries) {
        const entry = record(item);
        keys(entry, ["segmentId", "title", "level"]);
        requireValid(nonempty(entry.segmentId) && string(entry.title) && integer(entry.level, 1) && entry.level <= 6);
      }
      break;
    case "selection":
      keys(p, ["exact", "prefix", "suffix", "start", "end", "spans"]);
      requireValid(
        nonempty(p.exact) &&
          Array.from(p.exact).length <= 500 &&
          string(p.prefix) &&
          string(p.suffix) &&
          integer(p.start) &&
          integer(p.end) &&
          p.end > p.start &&
          p.end - p.start === Array.from(p.exact).length,
      );
      spans(p.spans, p.start, p.end);
      break;
    case "link":
      keys(p, ["href"]);
      requireValid(nonempty(p.href));
      // 保留相对文章和片段链接的解析能力；可执行或自定义协议不得穿过桥接边界。
      const link = new URL(p.href, "https://staging-bytedepth.bytedepth.cn/");
      requireValid(link.protocol === "https:" && !link.username && !link.password);
      break;
    case "renderError":
      keys(p, ["message"]);
      requireValid(nonempty(p.message));
      break;
    default:
      throw new Error("Invalid mobile bridge type");
  }
  return v as unknown as ReaderBridgeMessage;
}
