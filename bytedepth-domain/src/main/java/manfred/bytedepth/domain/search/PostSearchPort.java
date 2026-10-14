package manfred.bytedepth.domain.search;

public interface PostSearchPort {
    /** Enumerate document IDs before reconciliation; a failed scan must not be treated as an empty index. */
    java.util.List<Long> findIndexedIds();

    void index(PostSearchDoc doc);

    void delete(Long postId);

    SearchResult search(String query, int page, int size);
}
