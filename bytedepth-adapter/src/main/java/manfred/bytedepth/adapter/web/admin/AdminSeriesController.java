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
     * 给文章绑定系列。系列不存在时自动创建。
     * @param seriesSlug  系列标识（URL slug）
     * @param seriesName  系列显示名（可选，默认 = slug）
     * @param seriesOrder 文章在系列中的序号（从 1 开始）
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
