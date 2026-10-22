package manfred.bytedepth.infrastructure.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<CommentDO> {
    @org.apache.ibatis.annotations.Select(
            "SELECT * FROM comment WHERE post_id=#{postId} AND status='APPROVED' ORDER BY created_at ASC,id ASC LIMIT #{size} OFFSET #{offset}")
    java.util.List<CommentDO> findApprovedPage(
            @org.apache.ibatis.annotations.Param("postId") Long postId,
            @org.apache.ibatis.annotations.Param("offset") long offset,
            @org.apache.ibatis.annotations.Param("size") int size);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM comment WHERE post_id=#{postId} AND status='APPROVED'")
    long countApproved(@org.apache.ibatis.annotations.Param("postId") Long postId);
}
