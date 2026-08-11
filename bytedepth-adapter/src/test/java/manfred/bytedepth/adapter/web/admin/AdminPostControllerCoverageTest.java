package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.adapter.web.security.ContentOwnershipGuard;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.category.CategoryDTO;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.DeletePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.command.SetPostTagsCmdExe;
import manfred.bytedepth.app.post.command.UpdatePostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListAllPostsQryExe;
import manfred.bytedepth.app.series.AppendPostToSeriesCmdExe;
import manfred.bytedepth.app.series.RemovePostFromSeriesCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ConcurrentModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminPostControllerCoverageTest {

    private final ListAllPostsQryExe posts = mock(ListAllPostsQryExe.class);
    private final ListCategoriesQryExe categories = mock(ListCategoriesQryExe.class);
    private final SeriesRepository seriesRepository = mock(SeriesRepository.class);
    private final ContentOwnershipGuard ownershipGuard = mock(ContentOwnershipGuard.class);
    private final AdminPostController controller = new AdminPostController(posts, mock(GetPostQryExe.class),
            mock(CreatePostCmdExe.class), mock(UpdatePostCmdExe.class), mock(PublishPostCmdExe.class),
            mock(DeletePostCmdExe.class), categories, mock(SetPostTagsCmdExe.class), seriesRepository,
            mock(AppendPostToSeriesCmdExe.class), mock(RemovePostFromSeriesCmdExe.class), mock(PostRepository.class),
            ownershipGuard);
    private final TestingAuthenticationToken authentication = new TestingAuthenticationToken("author", null);

    @Test
    void list_coversAdministratorAndAuthorFilteredPaths() {
        when(ownershipGuard.canManagePosts(authentication)).thenReturn(true, false, true);
        when(ownershipGuard.currentUserId(authentication)).thenReturn(7L);
        when(posts.execute(2, 10, " Java ", "PUBLISHED", 3L, 4L))
                .thenReturn(new ListAllPostsQryExe.PageResult(List.of(), 11L));
        when(posts.executeByAuthor(7L, 1, 20, null, null, null, null))
                .thenReturn(new ListAllPostsQryExe.PageResult(List.of(), 0L));
        when(posts.execute(1, 20, " ", "", null, null))
                .thenReturn(new ListAllPostsQryExe.PageResult(List.of(), 0L));
        when(seriesRepository.findAll()).thenReturn(List.of(Series.reconstruct(3L, "Java", "java", null, 7L)));
        when(seriesRepository.findByAuthorId(7L)).thenReturn(List.of());
        CategoryDTO category = new CategoryDTO();
        category.setId(4L);
        category.setName("后端");
        when(categories.execute()).thenReturn(List.of(category));

        ConcurrentModel administratorModel = new ConcurrentModel();
        assertEquals("admin/posts/list", controller.list(authentication, administratorModel, 2, 10,
                " Java ", "PUBLISHED", 3L, 4L));
        assertEquals("/admin/posts?title=Java&status=PUBLISHED&seriesId=3&categoryId=4&",
                administratorModel.getAttribute("filterBaseUrl"));

        ConcurrentModel authorModel = new ConcurrentModel();
        assertEquals("admin/posts/list", controller.list(authentication, authorModel, 1, 20,
                null, null, null, null));
        assertEquals("/admin/posts?", authorModel.getAttribute("filterBaseUrl"));

        ConcurrentModel blankFilterModel = new ConcurrentModel();
        assertEquals("admin/posts/list", controller.list(authentication, blankFilterModel, 1, 20,
                " ", "", null, null));
        assertEquals("/admin/posts?", blankFilterModel.getAttribute("filterBaseUrl"));
    }
}
