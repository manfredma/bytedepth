package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.command.SetPostTagsCmdExe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminTagController {

    private final SetPostTagsCmdExe setPostTagsCmdExe;

    /**
     * POST /admin/posts/{id}/tags
     * 参数 tags：多值列表，每项格式为 "slug:显示名"（无冒号则 name = slug）。
     */
    @PostMapping("/{id}/tags")
    public ResponseEntity<Void> setTags(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") List<String> tags) {
        setPostTagsCmdExe.execute(id, tags);
        return ResponseEntity.ok().build();
    }
}
