package manfred.bytedepth.app.post.command;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
import org.junit.jupiter.api.Test;

class UpdatePostSlugCmdExeTest {
    @Test
    void numericSlugCannotBypassReservedIdRouteThroughTheApplicationPort() {
        var posts = mock(PostRepository.class);
        var index = mock(IndexPostCmdExe.class);
        var command = new UpdatePostSlugCmdExe(posts, index);
        assertThrows(DomainException.class, () -> command.execute(1L, "1792212312345"));
        verifyNoInteractions(posts, index);
    }

    @Test
    void canonicalSlugStillUpdatesRepositoryAndSearch() {
        var posts = mock(PostRepository.class);
        var index = mock(IndexPostCmdExe.class);
        new UpdatePostSlugCmdExe(posts, index).execute(1L, "post-1792212312345");
        verify(posts).updateSlug(1L, "post-1792212312345");
        verify(index).execute(1L);
    }
}
