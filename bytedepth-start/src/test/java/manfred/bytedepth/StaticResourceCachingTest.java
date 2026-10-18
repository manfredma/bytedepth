package manfred.bytedepth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

@WebMvcTest(ServiceWorkerController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, SecurityMockMvcConfig.class})
class StaticResourceCachingTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ResourceUrlProvider urls;

    @MockitoBean
    private UserDetailsService users;

    @MockitoBean
    private VisitRequestFilter visitRequestFilter;

    @MockitoBean
    private RateLimitPort rateLimitPort;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void actualResourceChainValidatesFingerprintsAndEmitsImmutableHeaders() throws Exception {
        String url = urls.getForLookupPath("/vendor/mermaid/mermaid.min.js");
        assertThat(url).matches("/vendor/mermaid/mermaid.min-[a-f0-9]{32}\\.js");
        String modified = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andReturn()
                .getResponse()
                .getHeader("Last-Modified");
        mvc.perform(get(url).header("If-Modified-Since", modified))
                .andExpect(status().isNotModified())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        mvc.perform(get(url.replaceFirst("-[a-f0-9]{32}", "-00000000000000000000000000000000")))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void fontsAndIconsUseContentFingerprints() throws Exception {
        for (String path : java.util.List.of(
                "/icons/logo.svg", "/fonts/dm-serif-display/-nFnOHM81r4j6k0gjAW3mujVU2B2G_Bx0g.woff2")) {
            String url = urls.getForLookupPath(path);
            assertThat(url).isNotEqualTo(path).containsPattern("-[a-f0-9]{32}\\.");
            mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        }
    }

    @Test
    void stylesheetResponsesReferenceVersionedLocalFonts() throws Exception {
        String url = urls.getForLookupPath("/css/fonts.css");
        String body = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).containsPattern("/fonts/[^)]+-[a-f0-9]{32}\\.woff2");
    }

    @Test
    void mutableResourcesRevalidateWhileWorkerAlwaysReloads() throws Exception {
        String lastModified = mvc.perform(get("/icons/logo.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn()
                .getResponse()
                .getHeader("Last-Modified");
        mvc.perform(get("/icons/logo.svg").header("If-Modified-Since", lastModified))
                .andExpect(status().isNotModified())
                .andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/sw.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
