package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = NetworkMapController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@EnableConfigurationProperties(NetworkMapProperties.class)
@Import(ThymeleafSecurityHandlerConfig.class)
class NetworkMapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NetworkMapProperties properties;

    @MockitoBean
    private VisitRequestFilter visitRequestFilter;

    @Test
    void networkMapRendersCatalogGroups() throws Exception {
        mockMvc.perform(get("/network"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/network"))
                .andExpect(model().attribute("groups", properties.getGroups()))
                .andExpect(content().string(containsString("Career")))
                .andExpect(content().string(containsString("常用技术站点")));
    }

    @Test
    void networkMapRendersEightSafeExternalCards() throws Exception {
        mockMvc.perform(get("/network"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertEquals(8, Pattern.compile("class=\"network-card\"").matcher(body).results().count());
                    assertTrue(Pattern.compile("<a\\s+class=\"network-card\"\\s+href=\"https://bytedepth\\.cn\""
                                    + "\\s+target=\"_blank\"\\s+rel=\"noopener noreferrer\"")
                            .matcher(body).find());
                });
    }
}
