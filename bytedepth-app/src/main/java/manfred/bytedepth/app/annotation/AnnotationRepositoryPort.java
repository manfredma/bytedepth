package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;

import java.util.List;
import java.util.Optional;

/** 批注存储端口。 */
public interface AnnotationRepositoryPort {

    PostAnnotation save(PostAnnotation annotation);

    PostAnnotation update(PostAnnotation annotation);

    List<PostAnnotation> findVisibleByPostId(Long postId, Long userId, String ownerTokenHash);

    Optional<PostAnnotation> findById(Long id);

    void delete(Long id);
}
