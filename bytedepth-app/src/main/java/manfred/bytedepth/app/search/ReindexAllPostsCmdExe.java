package manfred.bytedepth.app.search;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReindexAllPostsCmdExe {

    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    public int execute() {
        int page = 1;
        int size = 50;
        int total = 0;
        while (true) {
            List<Post> posts = postRepository.findAll(page, size);
            if (posts.isEmpty()) break;
            for (Post post : posts) {
                if (post.getStatus() == PostStatus.PUBLISHED) {
                    indexPostCmdExe.execute(post.getId());
                    total++;
                }
            }
            if (posts.size() < size) break;
            page++;
        }
        return total;
    }
}
