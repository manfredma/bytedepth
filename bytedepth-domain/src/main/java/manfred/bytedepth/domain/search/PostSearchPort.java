package manfred.bytedepth.domain.search;

public interface PostSearchPort {
    /** Enumerate document IDs before reconciliation; a failed scan must not be treated as an empty index. */
    java.util.List<Long> findIndexedIds();

    void index(PostSearchDoc doc);

    void indexAll(java.util.List<PostSearchDoc> docs);

    void delete(Long postId);

    void deleteAll(java.util.List<Long> postIds);

    SearchResult search(String query, int page, int size);
}
