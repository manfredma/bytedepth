package manfred.bytedepth.app.annotation;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListAnnotationsQryExe {

    private final AnnotationRepositoryPort annotationRepository;

    public List<PostAnnotation> execute(Long postId, Long userId, String ownerTokenHash) {
        return annotationRepository.findVisibleByPostId(postId, userId, ownerTokenHash).stream()
                .sorted(Comparator.comparingInt(PostAnnotation::startOffset))
                .toList();
    }
}
