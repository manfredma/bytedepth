package manfred.bytedepth.app.search;

import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.search.PostSearchPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReindexAllPostsCmdExe {
    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;
    private final PostSearchPort postSearchPort;

    public int execute() {
        // Finish enumeration before deleting anything: document offsets shift after deletion.
        var remainingIds = new LinkedHashSet<>(postSearchPort.findIndexedIds());
        // Snapshot source IDs instead of paging by updated_at: concurrent edits/deletions
        // move rows between offset pages and can silently skip documents missing from the index.
        var publishedIds =
                postRepository.findAllPublished().stream().map(Post::getId).toList();
        for (Long id : publishedIds) {
            indexPostCmdExe.execute(id);
            remainingIds.remove(id);
        }
        // Re-read remaining IDs so concurrent publications are refreshed rather than deleted.
        remainingIds.forEach(indexPostCmdExe::execute);
        return publishedIds.size();
    }
}
