package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishPostCmdExe {

    private final PostRepository postRepository;
    private final manfred.bytedepth.app.post.PostWriteTransaction transactions;
    private final IndexPostCmdExe indexPostCmdExe;

    public void execute(Long postId) {
        transactions.withLockedPost(postId, post -> {
            if (post.getStatus() != PostStatus.PUBLISHED) {
                post.publish();
                postRepository.save(post);
            }
            return null;
        });
        indexPostCmdExe.execute(postId);
    }
}
