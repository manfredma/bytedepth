package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.series.SetPostSeriesCmdExe;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminSeriesController {

    private final SetPostSeriesCmdExe setPostSeriesCmdExe;

    /**
     * POST /admin/posts/{id}/series
     * 给文章绑定专栏。专栏不存在时自动创建。
     * @param seriesSlug  专栏标识（URL slug）
     * @param seriesName  专栏显示名（可选，默认 = slug）
     * @param seriesOrder 文章在专栏中的序号（从 1 开始）
     */
    @PostMapping("/{id}/series")
    public ResponseEntity<Void> setSeries(
            @PathVariable Long id,
            @RequestParam String seriesSlug,
            @RequestParam(required = false) String seriesName,
            @RequestParam Integer seriesOrder) {
        setPostSeriesCmdExe.execute(id, seriesSlug, seriesName, seriesOrder);
        return ResponseEntity.ok().build();
    }
}
