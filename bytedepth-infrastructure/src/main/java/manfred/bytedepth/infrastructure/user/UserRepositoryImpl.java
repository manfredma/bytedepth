package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(
            userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username))
        ).map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getUsername, username)) > 0;
    }

    @Override
    public List<User> findByStatus(UserStatus status) {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getStatus, status.name())
            .orderByAsc(UserDO::getCreatedAt))
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public User save(User user) {
        UserDO d = toDO(user);
        if (user.getId() == null) {
            userMapper.insert(d);
        } else {
            userMapper.updateById(d);
        }
        return toDomain(d);
    }

    @Override
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public void assignRole(Long userId, String roleName) {
        RoleDO role = roleMapper.selectOne(
            new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getName, roleName));
        if (role == null) return;
        UserRoleDO ur = new UserRoleDO();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
    }

    private User toDomain(UserDO d) {
        return User.reconstruct(d.getId(), d.getUsername(), d.getPassword(),
            d.getEmail(), d.getAvatar(), d.getBio(),
            UserStatus.valueOf(d.getStatus()), d.getCreatedAt(), d.getUpdatedAt());
    }

    private UserDO toDO(User u) {
        UserDO d = new UserDO();
        d.setId(u.getId());
        d.setUsername(u.getUsername());
        d.setPassword(u.getPasswordHash());
        d.setEmail(u.getEmail());
        d.setAvatar(u.getAvatar());
        d.setBio(u.getBio());
        d.setStatus(u.getStatus().name());
        d.setCreatedAt(u.getCreatedAt() != null ? u.getCreatedAt() : LocalDateTime.now());
        d.setUpdatedAt(u.getUpdatedAt() != null ? u.getUpdatedAt() : LocalDateTime.now());
        return d;
    }
}
