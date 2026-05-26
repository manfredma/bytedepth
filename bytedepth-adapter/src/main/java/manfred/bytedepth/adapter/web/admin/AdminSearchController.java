package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.ReindexAllPostsCmdExe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
public class AdminSearchController {

    private final ReindexAllPostsCmdExe reindexAllPostsCmdExe;

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        int count = reindexAllPostsCmdExe.execute();
        return ResponseEntity.ok(Map.of("indexed", count, "status", "ok"));
    }
}
