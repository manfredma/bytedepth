package manfred.bytedepth.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByStatus(UserStatus status);
    List<User> findPage(String username, String status, int page, int size);
    long countFiltered(String username, String status);
    User save(User user);
    void deleteById(Long id);
    /** 为用户赋角色，由基础设施层实现（操作 user_role 表）*/
    void assignRole(Long userId, String roleName);
}
