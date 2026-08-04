package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = ReleaseController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class ReleaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarkdownRenderer markdownRenderer;

    @Test
    void releases_rendersTheVersionNotesBundledWithTheApplication() throws Exception {
        when(markdownRenderer.render(contains("v1.0.0"))).thenReturn("<h2>v1.0.0</h2>");

        mockMvc.perform(get("/releases"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/releases"))
                .andExpect(model().attribute("releaseNotesHtml", "<h2>v1.0.0</h2>"))
                .andExpect(content().string(containsString("版本更新")))
                .andExpect(content().string(containsString("v1.0.0")));
    }
}
