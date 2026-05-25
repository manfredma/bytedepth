package manfred.bytedepth.infrastructure.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import manfred.bytedepth.domain.comment.CommentStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepository {

    private final CommentMapper commentMapper;

    @Override
    public Comment save(Comment comment) {
        CommentDO commentDO = toDO(comment);
        if (comment.getId() == null) {
            commentMapper.insert(commentDO);
        } else {
            commentMapper.updateById(commentDO);
        }
        return toEntity(commentDO);
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
    public List<Comment> findPending() {
        return commentMapper.selectList(new LambdaQueryWrapper<CommentDO>()
                .eq(CommentDO::getStatus, CommentStatus.PENDING.name())
                .orderByAsc(CommentDO::getCreatedAt))
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    private CommentDO toDO(Comment c) {
        CommentDO d = new CommentDO();
        d.setId(c.getId());
        d.setPostId(c.getPostId());
        d.setAuthorName(c.getAuthorName());
        d.setAuthorEmail(c.getAuthorEmail());
        d.setContent(c.getContent());
        d.setStatus(c.getStatus().name());
        d.setCreatedAt(c.getCreatedAt());
        return d;
    }

    private Comment toEntity(CommentDO d) {
        return Comment.reconstruct(d.getId(), d.getPostId(), d.getAuthorName(),
                d.getAuthorEmail(), d.getContent(),
                CommentStatus.valueOf(d.getStatus()), d.getCreatedAt());
    }
}
