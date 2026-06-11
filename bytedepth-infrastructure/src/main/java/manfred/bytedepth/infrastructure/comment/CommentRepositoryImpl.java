package manfred.bytedepth.infrastructure.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import manfred.bytedepth.domain.comment.CommentStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepository {

    private final CommentMapper commentMapper;

    @Override
    public Comment save(Comment comment) {
        CommentDO d = toDO(comment);
        if (comment.getId() == null) {
            commentMapper.insert(d);
        } else {
            commentMapper.updateById(d);
        }
        return toEntity(d);
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return Optional.ofNullable(commentMapper.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<Comment> findApprovedByPostId(Long postId) {
        return commentMapper.selectList(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getPostId, postId)
                .eq(CommentDO::getStatus, CommentStatus.APPROVED.name())
                .orderByAsc(CommentDO::getCreatedAt))
            .stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<Comment> findAll(int page, int size) {
        Page<CommentDO> pageParam = new Page<>(page, size);
        return commentMapper.selectPage(pageParam,
                new LambdaQueryWrapper<CommentDO>()
                    .orderByDesc(CommentDO::getCreatedAt))
            .getRecords().stream().map(this::toEntity).collect(Collectors.toList());
    }

    private CommentDO toDO(Comment c) {
        CommentDO d = new CommentDO();
        d.setId(c.getId());
        d.setPostId(c.getPostId());
        d.setAuthorId(c.getAuthorId());
        d.setAuthorName(c.getAuthorName());
        d.setContent(c.getContent());
        d.setStatus(c.getStatus().name());
        d.setCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt() : LocalDateTime.now());
        return d;
    }

    private Comment toEntity(CommentDO d) {
        return Comment.reconstruct(
            d.getId(), d.getPostId(),
            d.getAuthorId(),    // 可为 null（旧评论）
            d.getAuthorName(),
            d.getContent(),
            CommentStatus.valueOf(d.getStatus()),
            d.getCreatedAt()
        );
    }
}
