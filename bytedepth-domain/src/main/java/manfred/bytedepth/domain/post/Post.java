package manfred.bytedepth.domain.post;

import lombok.Getter;
import manfred.bytedepth.domain.common.DomainException;

import java.time.LocalDateTime;

@Getter
public class Post {

    private Long id;
    private String title;
    private String content;
    private PostStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private Long categoryId;

    private Post() {}

    public static Post create(String title, String content) {
        Post post = new Post();
        post.title = title;
        post.content = content;
        post.status = PostStatus.DRAFT;
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

    public void assignCategory(Long categoryId) {
        this.categoryId = categoryId;
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
}
