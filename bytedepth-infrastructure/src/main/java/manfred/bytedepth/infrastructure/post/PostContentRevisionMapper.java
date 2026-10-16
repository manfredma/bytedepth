package manfred.bytedepth.infrastructure.post;

import org.apache.ibatis.annotations.*;

@Mapper
public interface PostContentRevisionMapper {
    @Select("SELECT * FROM post_content_revision WHERE post_id=#{postId} AND content_version=#{revision}")
    PostContentRevisionDO find(@Param("postId") long postId, @Param("revision") long revision);

    @Insert(
            "INSERT INTO post_content_revision(post_id,content_version,markdown,document_json,baseline) VALUES(#{postId},#{contentVersion},#{markdown},#{documentJson},#{baseline})")
    int insert(PostContentRevisionDO row);
}
