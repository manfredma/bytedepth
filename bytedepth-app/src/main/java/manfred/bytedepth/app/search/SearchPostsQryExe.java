package manfred.bytedepth.app.search;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.search.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SearchPostsQryExe {

    private static final int DEFAULT_SIZE = 10;
    private final PostSearchPort postSearchPort;

    public SearchResult execute(String query, int page) {
        if (query == null || query.isBlank())
            return new SearchResult(List.of(), 0, page, DEFAULT_SIZE);
        return postSearchPort.search(query.trim(), page, DEFAULT_SIZE);
    }
}
