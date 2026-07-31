package manfred.bytedepth.domain.post;

import java.util.List;

/** Personal-workspace queries that must be constrained to one author. */
public interface AuthorPostRepository {
    List<Post> findPageByAuthorId(Long authorId, int page, int size);
    long countByAuthorId(Long authorId);
}
