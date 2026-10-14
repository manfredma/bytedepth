package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemovePostFromSeriesCmdExe {

    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    public void execute(Long postId) {
        postRepository.clearPostSeries(postId);
        indexPostCmdExe.execute(postId);
    }
}
