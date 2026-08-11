package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.HotPost;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostMapper postMapper;

    @Override
    public Post save(Post post) {
        PostDO postDO = toDO(post);
        if (post.getId() == null) {
            postMapper.insert(postDO);
        } else {
            postMapper.updateById(postDO);
        }
        return toEntity(postDO);
    }

    @Override
    public Optional<Post> findById(Long id) {
        return Optional.ofNullable(postMapper.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<Post> findPublished(int page, int size) {
        Page<PostDO> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .orderByDesc(PostDO::getPublishedAt);
        return postMapper.selectPage(pageParam, wrapper).getRecords()
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<HotPost> findPublishedByHotness(int page, int size) {
        int offset = (page - 1) * size;
        return postMapper.findPublishedByHotness(offset, size).stream()
                .map(row -> new HotPost(toEntity(row), row.getViewCount() == null ? 0L : row.getViewCount()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Post> findLatestPublishedExcluding(List<Long> excludedIds, int limit) {
        return postMapper.findLatestPublishedExcluding(excludedIds, limit).stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public long countPublished() {
        return postMapper.selectCount(new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name()));
    }

    @Override
    public List<Post> findPublishedByTag(String tagSlug, int page, int size) {
        int offset = (page - 1) * size;
        return postMapper.findPublishedByTagSlug(tagSlug, offset, size)
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countPublishedByTag(String tagSlug) {
        return postMapper.countPublishedByTagSlug(tagSlug);
    }

    @Override
    public List<Post> findPublishedByCategory(Long categoryId, int page, int size) {
        Page<PostDO> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .eq(PostDO::getCategoryId, categoryId)
                .orderByDesc(PostDO::getPublishedAt);
        return postMapper.selectPage(pageParam, wrapper).getRecords()
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countPublishedByCategory(Long categoryId) {
        return postMapper.selectCount(new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .eq(PostDO::getCategoryId, categoryId));
    }

    @Override
    public List<Post> findPage(int page, int size) {
        Page<PostDO> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<PostDO>()
                .ne(PostDO::getStatus, PostStatus.DELETED.name())
                .orderByDesc(PostDO::getCreatedAt);
        return postMapper.selectPage(pageParam, wrapper).getRecords()
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countAll() {
        return postMapper.selectCount(new LambdaQueryWrapper<PostDO>()
                .ne(PostDO::getStatus, PostStatus.DELETED.name()));
    }

    @Override
    public List<Post> findPage(int page, int size, String title, String status, Long seriesId, Long categoryId) {
        Page<PostDO> pageParam = new Page<>(page, size);
        return postMapper.selectPage(pageParam, filteredWrapper(title, status, seriesId, categoryId))
                .getRecords().stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countFiltered(String title, String status, Long seriesId, Long categoryId) {
        return postMapper.selectCount(filteredWrapper(title, status, seriesId, categoryId));
    }

    private LambdaQueryWrapper<PostDO> filteredWrapper(String title, String status, Long seriesId, Long categoryId) {
        LambdaQueryWrapper<PostDO> w = new LambdaQueryWrapper<PostDO>()
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

    @Override
    public Optional<Post> findPrevPublished(Long id) {
        return Optional.ofNullable(postMapper.findPrevPublished(id)).map(this::toEntity);
    }

    @Override
    public Optional<Post> findNextPublished(Long id) {
        return Optional.ofNullable(postMapper.findNextPublished(id)).map(this::toEntity);
    }

    @Override
    public void setPostSeries(Long postId, Long seriesId, Integer seriesOrder) {
        PostDO postDO = new PostDO();
        postDO.setId(postId);
        postDO.setSeriesId(seriesId);
        postDO.setSeriesOrder(seriesOrder);
        postMapper.updateById(postDO);
    }

    @Override
    public void clearPostSeries(Long postId) {
        postMapper.clearPostSeries(postId);
    }

    @Override
    public List<Post> findPublishedByAuthorId(Long authorId, int page, int size) {
        Page<PostDO> pageParam = new Page<>(page, size);
        return postMapper.selectPage(pageParam,
                new LambdaQueryWrapper<PostDO>()
                    .eq(PostDO::getAuthorId, authorId)
                    .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                    .orderByDesc(PostDO::getPublishedAt))
            .getRecords().stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countPublishedByAuthorId(Long authorId) {
        return postMapper.selectCount(new LambdaQueryWrapper<PostDO>()
            .eq(PostDO::getAuthorId, authorId)
            .eq(PostDO::getStatus, PostStatus.PUBLISHED.name()));
    }

    @Override
    public Optional<Post> findBySlug(String slug) {
        return Optional.ofNullable(postMapper.selectOne(
                new LambdaQueryWrapper<PostDO>().eq(PostDO::getSlug, slug)))
            .map(this::toEntity);
    }

    @Override
    public void updateSlug(Long id, String slug) {
        postMapper.updateSlug(id, slug);
    }

    @Override
    public List<Post> findAllPublished() {
        return postMapper.selectList(new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .orderByDesc(PostDO::getPublishedAt))
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    private PostDO toDO(Post post) {
        PostDO d = new PostDO();
        d.setId(post.getId());
        d.setSlug(post.getSlug());
        d.setAuthorId(post.getAuthorId());
        d.setTitle(post.getTitle());
        d.setContent(post.getContent());
        d.setStatus(post.getStatus().name());
        d.setFeatured(Boolean.TRUE.equals(post.getFeatured()));
        d.setCreatedAt(post.getCreatedAt());
        d.setPublishedAt(post.getPublishedAt());
        d.setUpdatedAt(post.getUpdatedAt());
        d.setCategoryId(post.getCategoryId());
        d.setSeriesId(post.getSeriesId());
        d.setSeriesOrder(post.getSeriesOrder());
        return d;
    }

    private Post toEntity(PostDO d) {
        Post post = Post.reconstruct(
            d.getId(), d.getSlug(), d.getTitle(), d.getContent(),
            PostStatus.valueOf(d.getStatus()),
            d.getCreatedAt(), d.getPublishedAt(), d.getUpdatedAt(),
            d.getCategoryId(),
            d.getAuthorId(),
            d.getFeatured()
        );
        if (d.getSeriesId() != null) {
            post.assignSeries(d.getSeriesId(), d.getSeriesOrder());
        }
        return post;
    }
}
