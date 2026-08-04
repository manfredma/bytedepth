package manfred.bytedepth.app.post.query;

import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.HotPost;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListPostsQryExeTest {

    @Mock private PostRepository postRepository;
    @Mock private CategoryRepository categoryRepository;
    private ListPostsQryExe exe;

    @BeforeEach
    void setUp() {
        exe = new ListPostsQryExe(postRepository, categoryRepository);
    }

    // --- countPublished ---

    @Test
    void countPublished_delegatesToRepository() {
        when(postRepository.countPublished()).thenReturn(42L);
        assertEquals(42L, exe.countPublished());
    }

    // --- countByTag ---

    @Test
    void countByTag_delegatesToRepository() {
        when(postRepository.countPublishedByTag("java")).thenReturn(7L);
        assertEquals(7L, exe.countByTag("java"));
    }

    // --- countByCategory ---

    @Test
    void countByCategory_categoryFound_returnsCount() {
        Category cat = Category.reconstruct(3L, "Tech", "tech", null);
        when(categoryRepository.findBySlug("tech")).thenReturn(Optional.of(cat));
        when(postRepository.countPublishedByCategory(3L)).thenReturn(9L);

        assertEquals(9L, exe.countByCategory("tech"));
    }

    @Test
    void countByCategory_categoryNotFound_returnsZero() {
        when(categoryRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertEquals(0L, exe.countByCategory("missing"));
        verify(postRepository, never()).countPublishedByCategory(any());
    }

    // --- execute(page, size) ---

    @Test
    void execute_mapsPublishedPostsToDTOs() {
        Post post = postWithoutCategory(1L, "slug-1", "Title 1");
        when(postRepository.findPublished(0, 10)).thenReturn(List.of(post));

        List<PostDTO> result = exe.execute(0, 10);

        assertEquals(1, result.size());
        PostDTO dto = result.get(0);
        assertEquals(1L, dto.getId());
        assertEquals("slug-1", dto.getSlug());
        assertEquals("Title 1", dto.getTitle());
        assertEquals("PUBLISHED", dto.getStatus());
        assertNull(dto.getCategoryId());
        assertNull(dto.getCategoryName());
    }

    @Test
    void execute_emptyPage_returnsEmptyList() {
        when(postRepository.findPublished(0, 10)).thenReturn(List.of());

        assertTrue(exe.execute(0, 10).isEmpty());
    }

    // --- executeByHotness ---

    @Test
    void executeByHotness_mapsHotPostsToDTOsWithViewCount() {
        Post post = postWithoutCategory(1L, "slug-1", "Hot Title");
        HotPost hotPost = new HotPost(post, 999L);
        when(postRepository.findPublishedByHotness(0, 5)).thenReturn(List.of(hotPost));

        List<PostDTO> result = exe.executeByHotness(0, 5);

        assertEquals(1, result.size());
        PostDTO dto = result.get(0);
        assertEquals(1L, dto.getId());
        assertEquals("Hot Title", dto.getTitle());
        assertEquals(999L, dto.getViewCount());
    }

    @Test
    void executeByHotness_emptyResult_returnsEmptyList() {
        when(postRepository.findPublishedByHotness(0, 5)).thenReturn(List.of());

        assertTrue(exe.executeByHotness(0, 5).isEmpty());
    }

    // --- executeLatestExcluding ---

    @Test
    void executeLatestExcluding_mapsPostsToDTOs() {
        Post post = postWithoutCategory(5L, "slug-5", "Latest");
        when(postRepository.findLatestPublishedExcluding(List.of(1L, 2L), 3)).thenReturn(List.of(post));

        List<PostDTO> result = exe.executeLatestExcluding(List.of(1L, 2L), 3);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getId());
        assertEquals("Latest", result.get(0).getTitle());
    }

    @Test
    void executeLatestExcluding_emptyResult_returnsEmptyList() {
        when(postRepository.findLatestPublishedExcluding(List.of(), 3)).thenReturn(List.of());

        assertTrue(exe.executeLatestExcluding(List.of(), 3).isEmpty());
    }

    // --- executeByTag ---

    @Test
    void executeByTag_mapsPostsToDTOs() {
        Post post = postWithoutCategory(7L, "slug-7", "Tagged");
        when(postRepository.findPublishedByTag("java", 0, 10)).thenReturn(List.of(post));

        List<PostDTO> result = exe.executeByTag("java", 0, 10);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        assertEquals("Tagged", result.get(0).getTitle());
    }

    @Test
    void executeByTag_emptyResult_returnsEmptyList() {
        when(postRepository.findPublishedByTag("java", 0, 10)).thenReturn(List.of());

        assertTrue(exe.executeByTag("java", 0, 10).isEmpty());
    }

    // --- executeByCategory ---

    @Test
    void executeByCategory_categoryFound_mapsPostsToDTOs() {
        Category cat = Category.reconstruct(3L, "Tech", "tech", null);
        when(categoryRepository.findBySlug("tech")).thenReturn(Optional.of(cat));
        Post post = postWithCategory(8L, "slug-8", "Categorized", 3L);
        when(postRepository.findPublishedByCategory(3L, 0, 10)).thenReturn(List.of(post));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(cat));

        List<PostDTO> result = exe.executeByCategory("tech", 0, 10);

        assertEquals(1, result.size());
        PostDTO dto = result.get(0);
        assertEquals(8L, dto.getId());
        assertEquals(3L, dto.getCategoryId());
        assertEquals("Tech", dto.getCategoryName());
        assertEquals("tech", dto.getCategorySlug());
    }

    @Test
    void executeByCategory_categoryNotFound_returnsEmptyList() {
        when(categoryRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertTrue(exe.executeByCategory("missing", 0, 10).isEmpty());
        verify(postRepository, never()).findPublishedByCategory(any(), anyInt(), anyInt());
    }

    @Test
    void executeByCategory_categoryFoundButNoPosts_returnsEmptyList() {
        Category cat = Category.reconstruct(3L, "Tech", "tech", null);
        when(categoryRepository.findBySlug("tech")).thenReturn(Optional.of(cat));
        when(postRepository.findPublishedByCategory(3L, 0, 10)).thenReturn(List.of());

        assertTrue(exe.executeByCategory("tech", 0, 10).isEmpty());
    }

    // --- toDTO(Post) with categoryId ---

    @Test
    void toDTO_postWithCategoryId_fillsCategoryNameAndSlug() {
        Post post = postWithCategory(1L, "slug-1", "Title", 5L);
        Category cat = Category.reconstruct(5L, "DevOps", "devops", null);
        when(postRepository.findPublished(0, 10)).thenReturn(List.of(post));
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(cat));

        List<PostDTO> result = exe.execute(0, 10);

        PostDTO dto = result.get(0);
        assertEquals(5L, dto.getCategoryId());
        assertEquals("DevOps", dto.getCategoryName());
        assertEquals("devops", dto.getCategorySlug());
    }

    @Test
    void toDTO_postWithCategoryIdButCategoryNotFound_leavesCategoryFieldsNull() {
        Post post = postWithCategory(1L, "slug-1", "Title", 5L);
        when(postRepository.findPublished(0, 10)).thenReturn(List.of(post));
        when(categoryRepository.findById(5L)).thenReturn(Optional.empty());

        List<PostDTO> result = exe.execute(0, 10);

        PostDTO dto = result.get(0);
        assertEquals(5L, dto.getCategoryId());
        assertNull(dto.getCategoryName());
        assertNull(dto.getCategorySlug());
    }

    @Test
    void toDTO_postWithoutCategoryId_skipsCategoryLookup() {
        Post post = postWithoutCategory(1L, "slug-1", "Title");
        when(postRepository.findPublished(0, 10)).thenReturn(List.of(post));

        exe.execute(0, 10);

        verify(categoryRepository, never()).findById(any());
    }

    // --- helper methods ---

    private Post postWithoutCategory(Long id, String slug, String title) {
        return Post.reconstruct(id, slug, title, "content", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, false);
    }

    private Post postWithCategory(Long id, String slug, String title, Long categoryId) {
        return Post.reconstruct(id, slug, title, "content", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                categoryId, null, false);
    }
}
