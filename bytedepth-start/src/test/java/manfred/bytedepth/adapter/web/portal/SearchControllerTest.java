package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.SearchHighlight;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.app.search.SearchPostsQryExe;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SearchController.class, excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, SearchHighlight.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private RateLimitPort rateLimitPort;
    @MockitoBean private RateLimitProperties rateLimitProperties;
    @MockitoBean private PersistentTokenRepository persistentTokenRepository;
    @MockitoBean private VisitRequestFilter visitRequestFilter;
    @MockitoBean private SearchPostsQryExe searchPostsQryExe;

    @Test
    void search_rendersHighlightedResultsWithoutUsingRestrictedBeanExpression() throws Exception {
        PostSearchDoc hit = PostSearchDoc.builder()
                .id(1L).slug("java-search").title("<em>Java</em> 搜索")
                .content("<em>Java</em> 内容").categoryName("后端").categorySlug("backend")
                .tags(List.of("java")).seriesName("").build();
        when(searchPostsQryExe.execute("java", 1)).thenReturn(new SearchResult(List.of(hit), 1, 1, 10));

        mockMvc.perform(get("/search").param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<em>Java</em> 搜索")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("@searchHighlight"))));
    }
}
