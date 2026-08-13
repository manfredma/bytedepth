package manfred.bytedepth.infrastructure.annotation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.annotation.AnnotationRepositoryPort;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MyBatisAnnotationRepository implements AnnotationRepositoryPort {

    private final PostAnnotationMapper mapper;

    @Override
    public PostAnnotation save(PostAnnotation annotation) {
        PostAnnotationDO data = toDO(annotation);
        mapper.insert(data);
        return toDomain(data);
    }

    @Override
    public PostAnnotation update(PostAnnotation annotation) {
        PostAnnotationDO data = toDO(annotation);
        mapper.updateById(data);
        return toDomain(data);
    }

    @Override
    public List<PostAnnotation> findVisibleByPostId(Long postId, Long userId, String ownerTokenHash) {
        return mapper.findVisibleByPostId(postId, userId, ownerTokenHash).stream()
                .map(MyBatisAnnotationRepository::toDomain).toList();
    }

    @Override
    public Optional<PostAnnotation> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MyBatisAnnotationRepository::toDomain);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<PostAnnotation> findByPostId(Long postId) {
        return mapper.selectList(new LambdaQueryWrapper<PostAnnotationDO>()
                        .eq(PostAnnotationDO::getPostId, postId))
                .stream().map(MyBatisAnnotationRepository::toDomain).toList();
    }

    private static PostAnnotationDO toDO(PostAnnotation annotation) {
        PostAnnotationDO data = new PostAnnotationDO();
        data.setId(annotation.id());
        data.setPostId(annotation.postId());
        data.setUserId(annotation.userId());
        data.setOwnerTokenHash(annotation.ownerTokenHash());
        data.setSelectedText(annotation.selectedText());
        data.setAnnotationText(annotation.annotationText());
        data.setColor(annotation.color());
        data.setVisibility(annotation.visibility().name());
        data.setStartOffset(annotation.startOffset());
        data.setEndOffset(annotation.endOffset());
        data.setCreatedAt(annotation.createdAt());
        data.setDeleted(annotation.deleted());
        return data;
    }

    private static PostAnnotation toDomain(PostAnnotationDO data) {
        return new PostAnnotation(
                data.getId(), data.getPostId(), data.getUserId(), data.getOwnerTokenHash(),
                data.getSelectedText(), data.getAnnotationText(), data.getColor(),
                AnnotationVisibility.valueOf(data.getVisibility()), data.getStartOffset(), data.getEndOffset(), data.getCreatedAt(),
                data.getDeleted() != null && data.getDeleted());
    }
}
