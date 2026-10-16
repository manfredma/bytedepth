package manfred.bytedepth.infrastructure.annotation;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AnnotationRequestMapper {
    @Select(
            "SELECT * FROM annotation_request WHERE post_id=#{postId} AND owner_key=#{ownerKey} AND request_id=#{requestId}")
    AnnotationRequestDO find(
            @Param("postId") long postId, @Param("ownerKey") String ownerKey, @Param("requestId") String requestId);

    @Insert(
            "INSERT INTO annotation_request(post_id,owner_key,request_id,payload_hash,annotation_id,deleted) VALUES(#{postId},#{ownerKey},#{requestId},#{payloadHash},#{annotationId},FALSE)")
    int insert(AnnotationRequestDO row);

    @Update("UPDATE annotation_request SET deleted=TRUE WHERE annotation_id=#{annotationId}")
    int markDeleted(@Param("annotationId") long annotationId);
}
