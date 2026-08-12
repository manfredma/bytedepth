package manfred.bytedepth;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Service Worker 是静态资源版本化的入口，HTTP 层不能缓存旧脚本；
 * Worker 内部再负责带内容指纹资源的长期缓存。
 */
@Controller
public class ServiceWorkerController {

    @GetMapping(value = "/sw.js", produces = "application/javascript")
    public ResponseEntity<ClassPathResource> serviceWorker() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.valueOf("application/javascript"))
                .body(new ClassPathResource("static/sw.js"));
    }
}
