package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeletePostCmdExe {

    private final PostRepository postRepository;
    private final manfred.bytedepth.app.post.PostWriteTransaction transactions;
    private final IndexPostCmdExe indexPostCmdExe;

    public void execute(Long id) {
        transactions.withLockedPost(id, post -> {
            post.delete();
            postRepository.save(post);
            return null;
        });
        indexPostCmdExe.execute(id);
    }
}
