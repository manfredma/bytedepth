package manfred.bytedepth.app.search;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SearchPostsQryExe {

    private final PostSearchPort postSearchPort;

    public List<PostSearchDoc> execute(String query) {
        if (query == null || query.isBlank()) return List.of();
        return postSearchPort.search(query.trim());
    }
}
