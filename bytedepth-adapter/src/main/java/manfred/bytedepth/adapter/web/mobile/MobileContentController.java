package manfred.bytedepth.adapter.web.mobile;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.document.MobileContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1/posts")
@RequiredArgsConstructor
public class MobileContentController {
    private final MobileContentService content;

    @GetMapping("/{id}/state")
    public ResponseEntity<?> state(@PathVariable("id") Long id) {
        var result = content.state(id);
        Object body = result.identity() == null
                ? new UnavailableState(result.schemaVersion(), result.postId(), result.availability())
                : result;
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<MobileContentService.PublicContent> content(
            @PathVariable("id") Long id, @RequestHeader(value = "If-None-Match", required = false) String condition) {
        return content.withContent(id, representation -> {
            String etag = "\"" + representation.representationHash() + "\"";
            if (matches(condition, etag))
                return ResponseEntity.status(304)
                        .header("Cache-Control", "no-cache")
                        .eTag(etag)
                        .build();
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-cache")
                    .eTag(etag)
                    .body(representation);
        });
    }

    public record UnavailableState(int schemaVersion, String postId, String availability) {}

    private static boolean matches(String condition, String etag) {
        if (condition == null) return false;
        for (String part : condition.split(",")) {
            String token = part.trim();
            if (token.equals("*") || token.equals(etag) || token.equals("W/" + etag)) return true;
        }
        return false;
    }
}
