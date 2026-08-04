package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.tag.DeleteTagCmdExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = AdminTagListController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class AdminTagListControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private ListTagsQryExe listTagsQryExe;
    @MockBean private DeleteTagCmdExe deleteTagCmdExe;

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void list_populatesTagsModel() throws Exception {
        when(listTagsQryExe.findAllWithCount()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/admin/tags"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tags/list"))
                .andExpect(model().attribute("tags", java.util.List.of()));
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void delete_delegatesAndReturnsToTagList() throws Exception {
        mockMvc.perform(post("/admin/tags/3/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tags"));

        verify(deleteTagCmdExe).execute(3L);
    }
}
