package manfred.bytedepth.domain.post;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import manfred.bytedepth.domain.common.DomainException;

@Getter
public class Post {

  private Long id;
  private String slug;
  private Long authorId;
  private String title;
  private String content;
  private PostStatus status;
  private Boolean featured = false;
  private LocalDateTime featuredAt;
  private String featuredReason;
  private LocalDateTime createdAt;
  private LocalDateTime publishedAt;
  private LocalDateTime updatedAt;
  private Integer contentVersion;
  private Long categoryId;
  private Long seriesId;
  private Integer seriesOrder;

  private Post() {}

  /** 向后兼容旧调用（authorId = null，slug 由 CmdExe 填入） */
  public static Post create(String title, String content) {
    return create(title, content, null, null);
  }

  /** 向后兼容（slug 由 CmdExe 填入） */
  public static Post create(String title, String content, Long authorId) {
    return create(title, content, authorId, null);
  }

  public static Post create(String title, String content, Long authorId, String slug) {
    Post post = new Post();
    post.title = title;
    post.slug = slug;
    post.content = content;
    post.authorId = authorId;
    post.status = PostStatus.DRAFT;
    post.featured = false;
    post.createdAt = LocalDateTime.now();
    post.updatedAt = LocalDateTime.now();
    post.contentVersion = 1;
    return post;
  }

  public static Post reconstruct(
      Long id,
      String title,
      String content,
      PostStatus status,
      LocalDateTime createdAt,
      LocalDateTime publishedAt,
      LocalDateTime updatedAt) {
    Post post = new Post();
    post.id = id;
    post.title = title;
    post.content = content;
    post.status = status;
    post.featured = false;
    post.createdAt = createdAt;
    post.publishedAt = publishedAt;
    post.updatedAt = updatedAt;
    post.contentVersion = 1;
    return post;
  }

  public static Post reconstruct(
      Long id,
      String title,
      String content,
      PostStatus status,
      LocalDateTime createdAt,
      LocalDateTime publishedAt,
      LocalDateTime updatedAt,
      Long categoryId) {
    Post post = reconstruct(id, title, content, status, createdAt, publishedAt, updatedAt);
    post.categoryId = categoryId;
    return post;
  }

  /** 含 authorId 和 featured 的完整重建（向后兼容，slug = null） */
  public static Post reconstruct(
      Long id,
      String title,
      String content,
      PostStatus status,
      LocalDateTime createdAt,
      LocalDateTime publishedAt,
      LocalDateTime updatedAt,
      Long categoryId,
      Long authorId,
      Boolean featured) {
    Post post =
        reconstruct(id, title, content, status, createdAt, publishedAt, updatedAt, categoryId);
    post.authorId = authorId;
    post.featured = Boolean.TRUE.equals(featured);
    return post;
  }

  /** 含 slug 的完整重建（持久层首选） */
  public static Post reconstruct(
      Long id,
      String slug,
      String title,
      String content,
      PostStatus status,
      LocalDateTime createdAt,
      LocalDateTime publishedAt,
      LocalDateTime updatedAt,
      Long categoryId,
      Long authorId,
      Boolean featured) {
    return reconstruct(
        id,
        slug,
        title,
        content,
        status,
        createdAt,
        publishedAt,
        updatedAt,
        categoryId,
        authorId,
        featured,
        1);
  }

  /** 含内容版本号的完整重建（持久层使用）。 */
  public static Post reconstruct(
      Long id,
      String slug,
      String title,
      String content,
      PostStatus status,
      LocalDateTime createdAt,
      LocalDateTime publishedAt,
      LocalDateTime updatedAt,
      Long categoryId,
      Long authorId,
      Boolean featured,
      Integer contentVersion) {
    return reconstruct(
        id,
        slug,
        title,
        content,
        status,
        createdAt,
        publishedAt,
        updatedAt,
        categoryId,
        authorId,
        featured,
        null,
        null,
        contentVersion);
  }

  public static Post reconstruct(
      Long id,
      String slug,
      String title,
      String content,
      PostStatus status,
      LocalDateTime createdAt,
      LocalDateTime publishedAt,
      LocalDateTime updatedAt,
      Long categoryId,
      Long authorId,
      Boolean featured,
      LocalDateTime featuredAt,
      String featuredReason,
      Integer contentVersion) {
    Post post = new Post();
    post.id = id;
    post.slug = slug;
    post.title = title;
    post.content = content;
    post.status = status;
    post.authorId = authorId;
    post.featured = Boolean.TRUE.equals(featured);
    post.featuredAt = featuredAt;
    post.featuredReason = featuredReason;
    post.createdAt = createdAt;
    post.publishedAt = publishedAt;
    post.updatedAt = updatedAt;
    post.categoryId = categoryId;
    post.contentVersion = contentVersion == null ? 1 : contentVersion;
    return post;
  }

  public void assignCategory(Long categoryId) {
    this.categoryId = categoryId;
  }

  public void assignSeries(Long seriesId, Integer seriesOrder) {
    this.seriesId = seriesId;
    this.seriesOrder = seriesOrder;
  }

  public void publish() {
    if (this.status != PostStatus.DRAFT) {
      throw new DomainException("只有草稿才能发布，当前状态：" + this.status);
    }
    this.status = PostStatus.PUBLISHED;
    this.publishedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  public void updateContent(String title, String content) {
    boolean contentChanged =
        !Objects.equals(this.title, title) || !Objects.equals(this.content, content);
    this.title = title;
    this.content = content;
    this.updatedAt = LocalDateTime.now();
    if (contentChanged) {
      this.contentVersion++;
    }
  }

  public void delete() {
    this.status = PostStatus.DELETED;
    this.updatedAt = LocalDateTime.now();
  }

  public boolean isOwnedBy(Long userId) {
    return this.authorId != null && this.authorId.equals(userId);
  }

  public void feature() {
    feature(null);
  }

  public void feature(String reason) {
    this.featured = true;
    this.featuredAt = LocalDateTime.now();
    this.featuredReason = reason == null || reason.isBlank() ? null : reason.trim();
  }

  public void unfeature() {
    this.featured = false;
    this.featuredAt = null;
    this.featuredReason = null;
  }
}
