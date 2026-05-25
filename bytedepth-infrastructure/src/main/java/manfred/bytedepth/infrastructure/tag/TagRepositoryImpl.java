package manfred.bytedepth.infrastructure.tag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import manfred.bytedepth.domain.tag.TagWithCount;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

    private final TagMapper tagMapper;

    @Override
    public Tag save(Tag tag) {
        TagDO tagDO = toDO(tag);
        if (tag.getId() == null) {
            tagMapper.insert(tagDO);
        } else {
            tagMapper.updateById(tagDO);
        }
        return toEntity(tagDO);
    }

    @Override
    public Optional<Tag> findBySlug(String slug) {
        return Optional.ofNullable(tagMapper.selectOne(
                new LambdaQueryWrapper<TagDO>().eq(TagDO::getSlug, slug)
        )).map(this::toEntity);
    }

    @Override
    public Optional<Tag> findById(Long id) {
        return Optional.ofNullable(tagMapper.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<Tag> findAll() {
        return tagMapper.selectList(null).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<Tag> findByPostId(Long postId) {
        return tagMapper.findByPostId(postId).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public void savePostTags(Long postId, List<Long> tagIds) {
        tagMapper.deletePostTags(postId);
        if (tagIds != null && !tagIds.isEmpty()) {
            tagMapper.insertPostTags(postId, tagIds);
        }
    }

    @Override
    public List<TagWithCount> findAllWithCount() {
        return tagMapper.findAllWithCount().stream()
                .map(d -> new TagWithCount(d.getId(), d.getName(), d.getSlug(), d.getPostCount()))
                .collect(Collectors.toList());
    }

    private TagDO toDO(Tag tag) {
        TagDO tagDO = new TagDO();
        tagDO.setId(tag.getId());
        tagDO.setName(tag.getName());
        tagDO.setSlug(tag.getSlug());
        return tagDO;
    }

    private Tag toEntity(TagDO tagDO) {
        return Tag.reconstruct(tagDO.getId(), tagDO.getName(), tagDO.getSlug());
    }
}
