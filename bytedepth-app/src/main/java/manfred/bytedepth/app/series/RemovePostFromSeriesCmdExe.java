package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemovePostFromSeriesCmdExe {

    private final PostRepository postRepository;

    public void execute(Long postId) {
        postRepository.clearPostSeries(postId);
    }
}
