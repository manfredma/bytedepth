package manfred.bytedepth.domain.post;

import lombok.Getter;
import manfred.bytedepth.domain.common.DomainException;

import java.time.LocalDateTime;

@Getter
public class Post {

    private Long id;
    private String slug;
    private Long authorId;
    private String title;
    private String content;
    private PostStatus status;
    private Boolean featured = false;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
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
        return post;
    }

    public static Post reconstruct(Long id, String title, String content, PostStatus status,
                                   LocalDateTime createdAt, LocalDateTime publishedAt,
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
        return post;
    }

    public static Post reconstruct(Long id, String title, String content, PostStatus status,
                                   LocalDateTime createdAt, LocalDateTime publishedAt,
                                   LocalDateTime updatedAt, Long categoryId) {
        Post post = reconstruct(id, title, content, status, createdAt, publishedAt, updatedAt);
        post.categoryId = categoryId;
        return post;
    }

    /** 含 authorId 和 featured 的完整重建（向后兼容，slug = null） */
    public static Post reconstruct(Long id, String title, String content, PostStatus status,
                                   LocalDateTime createdAt, LocalDateTime publishedAt,
                                   LocalDateTime updatedAt, Long categoryId,
                                   Long authorId, Boolean featured) {
        Post post = reconstruct(id, title, content, status, createdAt, publishedAt, updatedAt, categoryId);
        post.authorId = authorId;
        post.featured = Boolean.TRUE.equals(featured);
        return post;
    }

    /** 含 slug 的完整重建（持久层首选） */
    public static Post reconstruct(Long id, String slug, String title, String content, PostStatus status,
                                   LocalDateTime createdAt, LocalDateTime publishedAt,
                                   LocalDateTime updatedAt, Long categoryId,
                                   Long authorId, Boolean featured) {
        Post post = new Post();
        post.id = id;
        post.slug = slug;
        post.title = title;
        post.content = content;
        post.status = status;
        post.authorId = authorId;
        post.featured = Boolean.TRUE.equals(featured);
        post.createdAt = createdAt;
        post.publishedAt = publishedAt;
        post.updatedAt = updatedAt;
        post.categoryId = categoryId;
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
        this.title = title;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.status = PostStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.authorId != null && this.authorId.equals(userId);
    }

    public void feature() {
        this.featured = true;
    }

    public void unfeature() {
        this.featured = false;
    }
}
