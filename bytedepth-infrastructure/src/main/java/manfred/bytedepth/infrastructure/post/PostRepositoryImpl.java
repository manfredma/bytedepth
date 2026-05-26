package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
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
    public List<Post> findAll(int page, int size) {
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
    public java.util.Optional<Post> findPrevPublished(Long id) {
        return java.util.Optional.ofNullable(postMapper.findPrevPublished(id)).map(this::toEntity);
    }

    @Override
    public java.util.Optional<Post> findNextPublished(Long id) {
        return java.util.Optional.ofNullable(postMapper.findNextPublished(id)).map(this::toEntity);
    }

    private PostDO toDO(Post post) {
        PostDO postDO = new PostDO();
        postDO.setId(post.getId());
        postDO.setTitle(post.getTitle());
        postDO.setContent(post.getContent());
        postDO.setStatus(post.getStatus().name());
        postDO.setCreatedAt(post.getCreatedAt());
        postDO.setPublishedAt(post.getPublishedAt());
        postDO.setUpdatedAt(post.getUpdatedAt());
        postDO.setCategoryId(post.getCategoryId());
        return postDO;
    }

    private Post toEntity(PostDO postDO) {
        return Post.reconstruct(
                postDO.getId(),
                postDO.getTitle(),
                postDO.getContent(),
                PostStatus.valueOf(postDO.getStatus()),
                postDO.getCreatedAt(),
                postDO.getPublishedAt(),
                postDO.getUpdatedAt(),
                postDO.getCategoryId()
        );
    }
}
