package manfred.bytedepth.integration;

import manfred.bytedepth.app.annotation.AnnotationRepositoryPort;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.app.post.command.UpdatePostCmdExe;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.infrastructure.stats.RedisStatsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class AnnotationContentUpdateIT {

    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    static {
        // Testcontainers 1.21.x 的 JUnit 扩展尚未适配 JUnit 6；显式启动保持
        // Spring ServiceConnection 的真实容器边界，同时避免扩展发出兼容性 WARN。
        mysql.start();
    }

    @AfterAll
    static void stopMySql() {
        mysql.stop();
    }

    @MockitoBean
    private RedisStatsService redisStatsService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnnotationRepositoryPort annotationRepository;

    @Autowired
    private UpdatePostCmdExe updatePostCmdExe;

    @Test
    void updatePost_reanchorsAnnotationToRenderedTextAfterMarkdownParagraphInsertion() {
        String oldContent = "第一段\n\n目标句子";
        String newContent = "第一段\n\n新增段\n\n目标句子";
        Post post = saveDraft("annotation-rendered-text-it", oldContent);
        String oldReaderText = MarkdownTextExtractor.renderedText(oldContent);
        int start = oldReaderText.indexOf("目标句子");
        PostAnnotation saved = saveAnnotation(post, oldReaderText, start, start + "目标句子".length());

        updatePostCmdExe.execute(post.getId(), post.getTitle(), newContent);

        String newReaderText = MarkdownTextExtractor.renderedText(newContent);
        PostAnnotation reloaded = annotationRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.deleted()).isFalse();
        assertThat(reloaded.startOffset()).isEqualTo(newReaderText.indexOf("目标句子"));
        assertThat(reloaded.endOffset()).isEqualTo(newReaderText.indexOf("目标句子") + "目标句子".length());
        assertThat(reloaded.selectedText()).isEqualTo("目标句子");
        assertThat(annotationRepository.findVisibleByPostId(post.getId(), null, null))
                .containsExactly(reloaded);
    }

    @Test
    void updatePost_hidesAnnotationWhenSelectedRenderedTextIsDeleted() {
        String oldContent = "保留内容\n\n待删除内容";
        String newContent = "保留内容";
        Post post = saveDraft("annotation-deleted-text-it", oldContent);
        String oldReaderText = MarkdownTextExtractor.renderedText(oldContent);
        int start = oldReaderText.indexOf("待删除内容");
        PostAnnotation saved = saveAnnotation(post, oldReaderText, start, start + "待删除内容".length());

        updatePostCmdExe.execute(post.getId(), post.getTitle(), newContent);

        PostAnnotation reloaded = annotationRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.deleted()).isTrue();
        assertThat(annotationRepository.findVisibleByPostId(post.getId(), null, null)).isEmpty();
    }

    @Test
    void updatePost_hidesAnnotationWhenSelectedRenderedTextIsReplaced() {
        String oldContent = "保留内容\n\n旧方案";
        String newContent = "保留内容\n\n新方案";
        Post post = saveDraft("annotation-replaced-text-it", oldContent);
        String oldReaderText = MarkdownTextExtractor.renderedText(oldContent);
        int start = oldReaderText.indexOf("旧方案");
        PostAnnotation saved = saveAnnotation(post, oldReaderText, start, start + "旧方案".length());

        updatePostCmdExe.execute(post.getId(), post.getTitle(), newContent);

        PostAnnotation reloaded = annotationRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.deleted()).isTrue();
        assertThat(annotationRepository.findVisibleByPostId(post.getId(), null, null)).isEmpty();
    }

    private Post saveDraft(String slug, String content) {
        return postRepository.save(Post.create("批注集成测试", content, 1L,
                slug + "-" + System.nanoTime()));
    }

    private PostAnnotation saveAnnotation(Post post, String readerText, int start, int end) {
        return annotationRepository.save(new PostAnnotation(
                null, post.getId(), null, "integration-owner-" + post.getId(),
                readerText.substring(start, end), "集成测试评注", "yellow",
                AnnotationVisibility.PUBLIC, start, end, LocalDateTime.now(), false));
    }
}
