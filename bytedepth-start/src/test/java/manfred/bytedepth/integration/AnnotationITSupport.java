package manfred.bytedepth.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.post.command.DeletePostCmdExe;
import manfred.bytedepth.app.post.command.UpdatePostCmdExe;
import manfred.bytedepth.app.post.document.ArticleRevisionService;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.document.SegmentSpan;
import manfred.bytedepth.infrastructure.stats.RedisStatsService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
abstract class AnnotationITSupport {
    @MockitoBean
    RedisStatsService redisStatsService;

    @Autowired
    PostRepository posts;

    @Autowired
    AnnotationRepositoryPort annotations;

    @Autowired
    CreateAnnotationCmdExe create;

    @Autowired
    ListAnnotationsQryExe query;

    @Autowired
    ArticleRevisionService current;

    @Autowired
    UpdatePostCmdExe update;

    @Autowired
    DeletePostCmdExe deletePost;

    @Autowired
    JdbcTemplate jdbc;

    final List<Long> ownedPosts = new ArrayList<>();
    final String owner = "a".repeat(64);

    Post draft(String body) {
        var post = posts.save(Post.create("批注集成测试", body, 1L, "annotation-it-" + UUID.randomUUID()));
        ownedPosts.add(post.getId());
        return post;
    }

    AnnotationAnchor anchor(Post post, String quote) {
        var revision = current.current(post.getId()).revision();
        var model = revision.document();
        int utf16 = model.text().indexOf(quote);
        if (utf16 < 0) throw new IllegalArgumentException("Quote absent from fixture");
        int start = model.text().codePointCount(0, utf16);
        int end = start + quote.codePointCount(0, quote.length());
        var points = model.text().codePoints().toArray();
        return new AnnotationAnchor(
                1,
                revision.contentVersion(),
                model.projectionVersion(),
                model.digest(),
                quote,
                new String(points, Math.max(0, start - 128), Math.min(start, 128)),
                new String(points, end, Math.min(points.length - end, 128)),
                start,
                end,
                model.segments().stream()
                        .filter(s -> s.start() < end && s.end() > start)
                        .map(s -> new SegmentSpan(
                                s.id(), Math.max(start, s.start()) - s.start(), Math.min(end, s.end()) - s.start()))
                        .toList(),
                UUID.randomUUID());
    }

    AnnotationView annotate(Post post, String quote, AnnotationVisibility visibility) {
        return create.execute(
                post.getId(),
                null,
                owner,
                new CreateAnnotationRequest(anchor(post, quote), "原始评注", "yellow", visibility));
    }

    @AfterEach
    void cleanupOwnedRows() {
        for (long id : ownedPosts) {
            deletePost.execute(id);
            jdbc.update(
                    "DELETE FROM annotation_placement WHERE annotation_id IN (SELECT id FROM post_annotation WHERE post_id=?)",
                    id);
            jdbc.update("DELETE FROM annotation_request WHERE post_id=?", id);
            jdbc.update("DELETE FROM post_annotation WHERE post_id=?", id);
            jdbc.update("DELETE FROM post_content_revision WHERE post_id=?", id);
            jdbc.update("DELETE FROM post WHERE id=?", id);
        }
    }
}
