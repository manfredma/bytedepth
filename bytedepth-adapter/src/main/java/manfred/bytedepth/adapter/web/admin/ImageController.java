package manfred.bytedepth.adapter.web.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/images")
public class ImageController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "jpeg", "gif");

    @Value("${bytedepth.upload.image-dir}")
    private String imageDir;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        ImageMetadata image = inspect(file);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + image.extension();

        Path dir = Paths.get(imageDir);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename));

        return ResponseEntity.ok(Map.of(
                "url", "/images/" + filename,
                "filename", filename
        ));
    }

    private ImageMetadata inspect(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("图片必须大于 0 且不超过 10MB");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) throw badRequest("无法识别图片格式");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw badRequest("仅支持 PNG、JPEG 和 GIF 图片");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!SUPPORTED_FORMATS.contains(format)) throw badRequest("仅支持 PNG、JPEG 和 GIF 图片");
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) throw badRequest("图片尺寸超过 4000 万像素限制");
                return new ImageMetadata("jpeg".equals(format) ? "jpg" : format);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw badRequest("无法读取图片内容");
        }
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private record ImageMetadata(String extension) { }
}
