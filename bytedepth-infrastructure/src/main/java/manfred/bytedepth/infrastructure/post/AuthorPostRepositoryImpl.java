package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.AuthorPostRepository;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Author-scoped post queries used by the personal management workspace. */
@Repository
@RequiredArgsConstructor
public class AuthorPostRepositoryImpl implements AuthorPostRepository {

    private final PostMapper postMapper;

    @Override
    public List<Post> findPageByAuthorId(Long authorId, int page, int size) {
        Page<PostDO> pageParam = new Page<>(page, size);
        return postMapper.selectPage(pageParam, new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getAuthorId, authorId)
                .ne(PostDO::getStatus, PostStatus.DELETED.name())
                .orderByDesc(PostDO::getCreatedAt))
                .getRecords().stream().map(this::toEntity).toList();
    }

    @Override
    public long countByAuthorId(Long authorId) {
        return postMapper.selectCount(new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getAuthorId, authorId)
                .ne(PostDO::getStatus, PostStatus.DELETED.name()));
    }

    @Override
    public List<Post> findPageByAuthorId(Long authorId, int page, int size,
                                         String title, String status, Long seriesId, Long categoryId) {
        Page<PostDO> pageParam = new Page<>(page, size);
        return postMapper.selectPage(pageParam, filteredWrapper(authorId, title, status, seriesId, categoryId))
                .getRecords().stream().map(this::toEntity).toList();
    }

    @Override
    public long countByAuthorIdFiltered(Long authorId, String title, String status, Long seriesId, Long categoryId) {
        return postMapper.selectCount(filteredWrapper(authorId, title, status, seriesId, categoryId));
    }

    private LambdaQueryWrapper<PostDO> filteredWrapper(Long authorId, String title, String status, Long seriesId, Long categoryId) {
        LambdaQueryWrapper<PostDO> w = new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getAuthorId, authorId)
                .ne(PostDO::getStatus, PostStatus.DELETED.name())
                .orderByDesc(PostDO::getCreatedAt);
        if (title != null && !title.isBlank()) {
            w.like(PostDO::getTitle, title.trim());
        }
        if (status != null && !status.isBlank()) {
            w.eq(PostDO::getStatus, status);
        }
        if (seriesId != null) {
            w.eq(PostDO::getSeriesId, seriesId);
        }
        if (categoryId != null) {
            w.eq(PostDO::getCategoryId, categoryId);
        }
        return w;
    }

    private Post toEntity(PostDO d) {
        Post post = Post.reconstruct(d.getId(), d.getSlug(), d.getTitle(), d.getContent(),
                PostStatus.valueOf(d.getStatus()), d.getCreatedAt(), d.getPublishedAt(), d.getUpdatedAt(),
                d.getCategoryId(), d.getAuthorId(), d.getFeatured());
        if (d.getSeriesId() != null) {
            post.assignSeries(d.getSeriesId(), d.getSeriesOrder());
        }
        return post;
    }
}
