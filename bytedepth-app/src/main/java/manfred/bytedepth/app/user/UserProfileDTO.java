package manfred.bytedepth.app.user;

import lombok.Data;
import manfred.bytedepth.app.post.query.PostDTO;

import java.util.List;

@Data
public class UserProfileDTO {
    private Long id;
    private String username;
    private String bio;
    private String avatar;
    private int postCount;
    private List<PostDTO> recentPosts;
}
