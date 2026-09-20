package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.analytics.PostViewLogPort;
import manfred.bytedepth.app.analytics.ViewLogRetentionPolicy;
import manfred.bytedepth.app.category.CreateCategoryCmdExe;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.comment.DeleteCommentCmdExe;
import manfred.bytedepth.app.project.CreateProjectCmdExe;
import manfred.bytedepth.app.search.ReindexAllPostsCmdExe;
import manfred.bytedepth.app.user.ActivateUserCmdExe;
import manfred.bytedepth.app.user.BanUserCmdExe;
import manfred.bytedepth.app.user.ListPendingUsersQryExe;
import manfred.bytedepth.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleAdminControllerCoverageTest {

    @Test
    void categoryAndCommentPagesDelegateAndExposeTheirModels() {
        ListCategoriesQryExe categories = mock(ListCategoriesQryExe.class);
        CreateCategoryCmdExe create = mock(CreateCategoryCmdExe.class);
        when(categories.executeFiltered(null, null)).thenReturn(List.of());
        AdminCategoryController categoryController = new AdminCategoryController(categories, create);
        ExtendedModelMap categoryModel = new ExtendedModelMap();
        assertThat(categoryController.list(categoryModel, null, null)).isEqualTo("admin/categories/list");
        assertThat(categoryModel).containsKey("categories");
        assertThat(categoryController.create("Java", "java", null)).isEqualTo("redirect:/admin/categories");
        verify(create).execute("Java", "java", null);

        ListCommentsQryExe comments = mock(ListCommentsQryExe.class);
        DeleteCommentCmdExe deleteComment = mock(DeleteCommentCmdExe.class);
        when(comments.findPage(2, 5, null, null)).thenReturn(new ListCommentsQryExe.PageResult(List.of(), 0));
        ExtendedModelMap commentModel = new ExtendedModelMap();
        assertThat(new AdminCommentController(comments, deleteComment).list(commentModel, 2, 5, null, null)).isEqualTo("admin/comments/list");
        assertThat(commentModel).containsKey("comments");
        assertThat(commentModel).containsKey("filterFields");
        assertThat(commentModel).containsKey("filterBaseUrl");
    }

    @Test
    void projectSearchAndUserActionsDelegateToTheirUseCases() {
        CreateProjectCmdExe createProject = mock(CreateProjectCmdExe.class);
        AdminProjectController projects = new AdminProjectController(createProject);
        assertThat(projects.newForm()).isEqualTo("admin/projects/edit");
        assertThat(projects.create("ByteDepth", null, null, null, null, 2)).isEqualTo("redirect:/projects");
        verify(createProject).execute("ByteDepth", null, null, null, null, 2);

        ReindexAllPostsCmdExe reindex = mock(ReindexAllPostsCmdExe.class);
        when(reindex.execute()).thenReturn(4);
        assertThat(new AdminSearchController(reindex).reindex().getBody()).containsEntry("indexed", 4).containsEntry("status", "ok");

        ListPendingUsersQryExe pending = mock(ListPendingUsersQryExe.class);
        ActivateUserCmdExe activate = mock(ActivateUserCmdExe.class);
        BanUserCmdExe ban = mock(BanUserCmdExe.class);
        UserRepository users = mock(UserRepository.class);
        when(pending.findPage(null, null, 1, 20)).thenReturn(new ListPendingUsersQryExe.UserPageResult(List.of(), 0));
        AdminUserController controller = new AdminUserController(pending, activate, ban, users);
        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(controller.list(model, null, null, 1, 20)).isEqualTo("admin/users/list");
        assertThat(model).containsKey("users");
        assertThat(controller.activate(8L)).isEqualTo("redirect:/admin/users");
        assertThat(controller.deletePending(9L)).isEqualTo("redirect:/admin/users");
        assertThat(controller.ban(10L)).isEqualTo("redirect:/admin/users");
        verify(activate).execute(8L);
        verify(users).deleteById(9L);
        verify(ban).execute(10L);
    }

    @Test
    void viewLogsCalculateOffsetAndPageCountForEmptyAndPartialPages() {
        PostViewLogPort logs = mock(PostViewLogPort.class);
        var now = LocalDateTime.of(2026, 9, 18, 17, 23);
        var zone = ZoneId.of("Asia/Shanghai");
        var policy = new ViewLogRetentionPolicy(7, zone);
        var clock = Clock.fixed(now.atZone(zone).toInstant(), zone);
        var cutoff = now.minusDays(7);
        when(logs.findPage(1L, 2L, cutoff, 40, 20)).thenReturn(List.of());
        when(logs.countPage(1L, 2L, cutoff)).thenReturn(41L);
        AdminViewLogController controller = new AdminViewLogController(logs, policy, clock);
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.list(model, 1L, 2L, 3)).isEqualTo("admin/view-logs/list");
        assertThat(model).containsEntry("currentPage", 3).containsEntry("totalPages", 3).containsEntry("pageSize", 20);
        verify(logs).findPage(1L, 2L, cutoff, 40, 20);

        when(logs.findPage(null, null, cutoff, 0, 20)).thenReturn(List.of());
        when(logs.countPage(null, null, cutoff)).thenReturn(0L);
        ExtendedModelMap emptyModel = new ExtendedModelMap();
        controller.list(emptyModel, null, null, 1);
        assertThat(emptyModel).containsEntry("totalPages", 1).containsKey("filterFields").containsEntry("filterBaseUrl", "/admin/view-logs?");
    }
}
