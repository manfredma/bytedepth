package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.app.post.document.ArticleRevisionService;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdatePostCmdExe {
    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;
    private final PostWriteTransaction transactions;
    private final ArticleRevisionService revisions;

    public void execute(Long id, String title, String content) {
        execute(id, title, content, null);
    }

    public void execute(Long id, String title, String content, Long categoryId) {
        transactions.withLockedPost(id, post -> {
            revisions.ensureCurrent(post);
            post.updateContent(title, content);
            post.assignCategory(categoryId);
            postRepository.save(post);
            revisions.capture(post);
            return null;
        });
        indexPostCmdExe.execute(id);
    }
}
