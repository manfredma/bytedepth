package manfred.bytedepth.adapter.web.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void rejectsNonImageContentEvenWhenFilenameUsesImageExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "payload.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> controller.upload(file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private byte[] pngBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
}
