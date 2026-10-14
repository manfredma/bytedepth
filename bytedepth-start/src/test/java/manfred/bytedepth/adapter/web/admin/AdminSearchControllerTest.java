package manfred.bytedepth.adapter.web.admin;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import javax.sql.DataSource;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.app.search.ReindexAllPostsCmdExe;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = AdminSearchController.class, excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AdminSearchControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private VisitRequestFilter visitRequestFilter;

    @MockitoBean
    private RateLimitPort rateLimitPort;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @MockitoBean
    private ReindexAllPostsCmdExe reindex;

    @Test
    void anonymousRequestDoesNotRunReindex() throws Exception {
        mvc.perform(post("/admin/search/reindex").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(reindex);
    }

    @Test
    @WithMockUser
    void readerCannotRunReindex() throws Exception {
        mvc.perform(post("/admin/search/reindex").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(reindex);
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void administratorGetsSuccessOnlyAfterReconciliationCompletes() throws Exception {
        when(reindex.execute()).thenReturn(3);
        mvc.perform(post("/admin/search/reindex").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.indexed").value(3));
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void failedReconciliationIsNotReportedAsSuccess() throws Exception {
        doThrow(new IllegalStateException("search task failed")).when(reindex).execute();
        mvc.perform(post("/admin/search/reindex").with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }
}
