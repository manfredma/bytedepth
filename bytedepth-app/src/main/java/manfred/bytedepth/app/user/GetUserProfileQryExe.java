package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetUserProfileQryExe {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public UserProfileDTO execute(String username) {
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new DomainException("用户不存在：" + username));

        var posts = postRepository.findPublishedByAuthorId(user.getId(), 1, 10);
        long count = postRepository.countPublishedByAuthorId(user.getId());

        var recentPosts = posts.stream()
            .map(p -> {
                PostDTO dto = new PostDTO();
                dto.setId(p.getId());
                dto.setSlug(p.getSlug());
                dto.setAuthorId(p.getAuthorId());
                dto.setTitle(p.getTitle());
                dto.setPublishedAt(p.getPublishedAt());
                dto.setStatus(p.getStatus().name());
                return dto;
            })
            .collect(Collectors.toList());

        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setBio(user.getBio());
        dto.setAvatar(user.getAvatar());
        dto.setPostCount((int) count);
        dto.setRecentPosts(recentPosts);
        return dto;
    }
}
