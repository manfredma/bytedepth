package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.category.CreateCategoryCmdExe;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
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
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AdminCategoryController.class, excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AdminCategoryControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean PasswordEncoder passwordEncoder;
    @MockitoBean VisitRequestFilter visitRequestFilter;
    @MockitoBean RateLimitPort rateLimitPort;
    @MockitoBean RateLimitProperties rateLimitProperties;
    @MockitoBean PersistentTokenRepository persistentTokenRepository;
    @MockitoBean ListCategoriesQryExe listCategoriesQryExe;
    @MockitoBean CreateCategoryCmdExe createCategoryCmdExe;

    @Test @WithMockUser(authorities = "admin:dashboard:view")
    void list_forwardsFiltersAndExposesFilterFields() throws Exception {
        mockMvc.perform(get("/admin/categories").param("name", "Java").param("slug", "backend"))
                .andExpect(status().isOk()).andExpect(view().name("admin/categories/list"))
                .andExpect(model().attributeExists("filterFields", "filterBaseUrl"))
                .andExpect(model().attribute("filterBaseUrl", "/admin/categories?"));
        verify(listCategoriesQryExe).executeFiltered(eq("Java"), eq("backend"));
    }
}
