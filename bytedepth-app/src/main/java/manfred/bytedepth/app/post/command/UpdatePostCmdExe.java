package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.annotation.AnnotationRecalculator;
import manfred.bytedepth.app.annotation.AnnotationRepositoryPort;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class UpdatePostCmdExe {

    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;
    private final AnnotationRepositoryPort annotationRepository;
    private final AnnotationRecalculator annotationRecalculator;

    public void execute(Long id, String title, String content) {
        execute(id, title, content, null);
    }

    public void execute(Long id, String title, String content, Long categoryId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("博文不存在：" + id));
        String oldContent = post.getContent();
        post.updateContent(title, content);
        post.assignCategory(categoryId);

        // 内容变更时，重算该文所有批注的偏移量
        if (!Objects.equals(oldContent, content)) {
            List<PostAnnotation> annotations = annotationRepository.findByPostId(id);
            if (!annotations.isEmpty()) {
                List<PostAnnotation> recalculated = annotationRecalculator.recalculate(oldContent, content, annotations);
                recalculated.forEach(annotationRepository::update);
            }
        }

        postRepository.save(post);
        if (post.getStatus() == PostStatus.PUBLISHED) {
            indexPostCmdExe.execute(id);
        }
    }
}