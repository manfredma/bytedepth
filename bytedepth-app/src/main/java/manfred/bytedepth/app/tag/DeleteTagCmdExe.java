package manfred.bytedepth.app.tag;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteTagCmdExe {

    private final TagRepository tagRepository;
    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    public void execute(Long tagId) {
        List<Long> affectedPostIds = tagRepository
                .findById(tagId)
                .map(tag -> findPublishedPostIds(tag.getSlug()))
                .orElseGet(List::of);
        tagRepository.deleteWithPostAssociations(tagId);
        affectedPostIds.forEach(indexPostCmdExe::execute);
    }

    private List<Long> findPublishedPostIds(String tagSlug) {
        List<Long> ids = new ArrayList<>();
        int page = 1;
        List<Post> posts;
        while (!(posts = postRepository.findPublishedByTag(tagSlug, page++, 100)).isEmpty()) {
            posts.stream().map(Post::getId).forEach(ids::add);
        }
        return ids;
    }
}
