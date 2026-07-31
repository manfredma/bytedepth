package manfred.bytedepth.adapter.web.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageControllerTest {

    @TempDir Path imageDir;
    private ImageController controller;

    @BeforeEach
    void setUp() {
        controller = new ImageController();
        ReflectionTestUtils.setField(controller, "imageDir", imageDir.toString());
    }

    @Test
    void storesDetectedPngWithGeneratedSafeName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "misleading.jpg", "image/jpeg", pngBytes());

        var response = controller.upload(file);
        String filename = response.getBody().get("filename");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filename).matches("[a-f0-9]{32}\\.png");
        assertThat(response.getBody().get("url")).isEqualTo("/images/" + filename);
        assertThat(Files.exists(imageDir.resolve(filename))).isTrue();
    }

    @Test
    void storesDetectedJpegWithJpgExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", jpegBytes());

        var response = controller.upload(file);

        assertThat(response.getBody().get("filename")).matches("[a-f0-9]{32}\\.jpg");
    }

    @Test
    void storesSvgWithGeneratedSafeName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "diagram.drawio.SVG", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes());

        var response = controller.upload(file);
        String filename = response.getBody().get("filename");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filename).matches("[a-f0-9]{32}\\.svg");
        assertThat(Files.readString(imageDir.resolve(filename))).contains("<svg");
    }

    @Test
    void rejectsNonImageContentEvenWhenFilenameUsesImageExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "payload.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> controller.upload(file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsUnsupportedRasterImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.bmp", "image/bmp", bmpBytes());

        assertBadRequest(file, "仅支持 PNG、JPEG、GIF 和 SVG 图片");
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertBadRequest(new MockMultipartFile("file", new byte[0]), "图片必须大于 0 且不超过 10MB");
        assertBadRequest(new MockMultipartFile("file", "large.png", "image/png", new byte[10 * 1024 * 1024 + 1]),
                "图片必须大于 0 且不超过 10MB");
    }

    @Test
    void rejectsRasterImageExceedingPixelLimit() {
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", oversizedPngHeader());

        assertBadRequest(file, "图片尺寸超过 4000 万像素限制");
    }

    @Test
    void rejectsRasterImageWithNonPositivePixelCount() {
        assertThatThrownBy(() -> ImageController.validatePixelCount(0))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getReason())
                .isEqualTo("图片尺寸超过 4000 万像素限制");
    }

    @Test
    void rejectsUnreadableImageStream() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getInputStream()).thenThrow(new IOException("read failed"));

        assertBadRequest(file, "无法读取图片内容");
    }

    private void assertBadRequest(MultipartFile file, String reason) {
        assertThatThrownBy(() -> controller.upload(file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException exception = (ResponseStatusException) e;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo(reason);
                });
    }

    private byte[] pngBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private byte[] bmpBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "bmp", output);
        return output.toByteArray();
    }

    private byte[] jpegBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        return output.toByteArray();
    }

    private byte[] oversizedPngHeader() {
        return pngHeader(40_000_001, 1);
    }

    private byte[] pngHeader(int width, int height) {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n',
                0, 0, 0, 13, 'I', 'H', 'D', 'R',
                (byte) (width >>> 24), (byte) (width >>> 16), (byte) (width >>> 8), (byte) width,
                (byte) (height >>> 24), (byte) (height >>> 16), (byte) (height >>> 8), (byte) height,
                8, 2, 0, 0, 0,
                0, 0, 0, 0
        };
    }
}
