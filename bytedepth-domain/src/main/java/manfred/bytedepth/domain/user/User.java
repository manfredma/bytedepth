package manfred.bytedepth.domain.user;

import lombok.Getter;
import manfred.bytedepth.domain.common.DomainException;

import java.time.LocalDateTime;

@Getter
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String avatar;
    private String bio;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private User() {}

    public static User register(String username, String passwordHash) {
        User u = new User();
        u.username = username;
        u.passwordHash = passwordHash;
        u.status = UserStatus.PENDING;
        u.createdAt = LocalDateTime.now();
        u.updatedAt = LocalDateTime.now();
        return u;
    }

    public static User reconstruct(Long id, String username, String passwordHash,
                                   String email, String avatar, String bio,
                                   UserStatus status,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        User u = new User();
        u.id = id;
        u.username = username;
        u.passwordHash = passwordHash;
        u.email = email;
        u.avatar = avatar;
        u.bio = bio;
        u.status = status;
        u.createdAt = createdAt;
        u.updatedAt = updatedAt;
        return u;
    }

    public void activate() {
        if (this.status != UserStatus.PENDING) {
            throw new DomainException("只有待审核账号可激活，当前状态：" + this.status);
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void ban() {
        if (this.status == UserStatus.BANNED) {
            throw new DomainException("账号已被封禁");
        }
        this.status = UserStatus.BANNED;
        this.updatedAt = LocalDateTime.now();
    }
}
