package manfred.bytedepth.domain.tag;

import java.util.List;
import java.util.Optional;

public interface TagRepository {
    Tag save(Tag tag);
    Optional<Tag> findBySlug(String slug);
    Optional<Tag> findById(Long id);
    List<Tag> findAll();
    List<Tag> findByPostId(Long postId);
    void savePostTags(Long postId, List<Long> tagIds);
    List<TagWithCount> findAllWithCount();
}
