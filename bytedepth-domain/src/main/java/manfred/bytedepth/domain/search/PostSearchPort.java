package manfred.bytedepth.domain.search;

import java.util.List;

public interface PostSearchPort {
    void index(PostSearchDoc doc);
    void delete(Long postId);
    List<PostSearchDoc> search(String query);
}
