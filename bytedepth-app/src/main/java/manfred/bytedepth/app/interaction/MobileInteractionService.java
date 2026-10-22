package manfred.bytedepth.app.interaction;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import manfred.bytedepth.app.post.document.ArticleRevisionService;
import manfred.bytedepth.app.post.query.PublicPostAccess;
import manfred.bytedepth.app.rating.*;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.comment.*;
import manfred.bytedepth.domain.post.PostContentRevision;
import org.springframework.stereotype.Component;

/** Mobile-only visibility boundary: hold the post lock through shared, Web-compatible use cases. */
@Component
@RequiredArgsConstructor
public class MobileInteractionService {
    private final PublicPostAccess access;
    private final RatePostCmdExe rate;
    private final GetPostRatingQryExe rating;
    private final SubmitCommentCmdExe submit;
    private final CommentRepository comments;
    private final CreateAnnotationCmdExe create;
    private final UpdateAnnotationCmdExe update;
    private final DeleteAnnotationCmdExe delete;
    private final AnnotationRepositoryPort annotations;
    private final ArticleRevisionService current;
    private final ListAnnotationsQryExe query;

    public PostRatingDTO ratings(Long id, String visitor) {
        String token = visitor == null ? null : visitor(visitor);
        return access.withPublishedPost(id, post -> rating.execute(id, token));
    }

    public void rate(Long id, String visitor, int score) {
        String token = visitor(visitor);
        if (score < 1 || score > 5) throw new IllegalArgumentException();
        access.withPublishedPost(id, post -> {
            rate.execute(id, token, score);
            return null;
        });
    }

    public PageResult comments(Long id, int page, int size) {
        pagination(page, size);
        return access.withPublishedPost(
                id,
                post -> new PageResult(
                        comments.findApprovedByPostId(id, page, size), comments.countApprovedByPostId(id)));
    }

    public void comment(Long id, String username, String content) {
        if (content == null || content.trim().isEmpty() || content.trim().length() > 2000)
            throw new IllegalArgumentException();
        access.withPublishedPost(id, post -> {
            submit.execute(id, username, content.trim());
            return null;
        });
    }

    public AnnotationPage annotations(Long id, Long user) {
        return access.withPublishedPost(id, post -> {
            var target = current.ensureCurrent(post).revision();
            return new AnnotationPage(target, query.viewsFor(target, user, null));
        });
    }

    public AnnotationView create(Long id, Long user, CreateAnnotationRequest request) {
        account(user);
        return access.withPublishedPost(id, post -> create.execute(id, user, null, request));
    }

    public AnnotationView update(Long id, Long annotation, Long user, String text, AnnotationVisibility visibility) {
        account(user);
        return access.withPublishedPost(id, post -> {
            owned(id, annotation, user);
            var changed = update.execute(annotation, id, user, null, text, visibility);
            return query.view(changed, current.ensureCurrent(post).revision());
        });
    }

    public void delete(Long id, Long annotation, Long user) {
        account(user);
        access.withPublishedPost(id, post -> {
            owned(id, annotation, user);
            delete.execute(annotation, id, user, null);
            return null;
        });
    }

    private void owned(Long post, Long id, Long user) {
        var found = annotations.findById(id).orElseThrow(AnnotationOwnershipException::new);
        if (!post.equals(found.postId()) || !user.equals(found.userId())) throw new AnnotationOwnershipException();
    }

    private static void account(Long user) {
        if (user == null) throw new AnnotationOwnershipException();
    }

    public static void pagination(int page, int size) {
        if (page < 1 || size < 1 || size > 50 || (long) (page - 1) * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid pagination");
    }

    private static String visitor(String value) {
        if (value == null
                || !value.matches("app:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Invalid installation identity");
        return "app:" + java.util.UUID.fromString(value.substring(4));
    }

    public record PageResult(List<Comment> items, long total) {}

    public record AnnotationPage(PostContentRevision target, List<AnnotationView> annotations) {}
}
