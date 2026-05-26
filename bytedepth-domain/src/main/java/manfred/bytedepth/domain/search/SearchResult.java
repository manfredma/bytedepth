package manfred.bytedepth.domain.search;

import lombok.Getter;

import java.util.List;

@Getter
public class SearchResult {
    private final List<PostSearchDoc> hits;
    private final long totalHits;
    private final int page;
    private final int size;

    public SearchResult(List<PostSearchDoc> hits, long totalHits, int page, int size) {
        this.hits = hits;
        this.totalHits = totalHits;
        this.page = page;
        this.size = size;
    }

    public long totalPages() {
        return (totalHits + size - 1) / size;
    }

    public boolean hasPrev() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }
}
