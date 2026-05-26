package manfred.bytedepth.domain.search;

import java.util.List;

public interface PostSearchPort {
    void index(PostSearchDoc doc);
    void delete(Long postId);
    SearchResult search(String query, int page, int size);
}
