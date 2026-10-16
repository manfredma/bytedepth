package manfred.bytedepth.infrastructure.annotation;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AnnotationPlacementMapper {
    @Select(
            "SELECT * FROM annotation_placement WHERE annotation_id=#{annotationId} AND target_revision=#{targetRevision} AND projection_version=#{projectionVersion} AND resolver_version=#{resolverVersion}")
    AnnotationPlacementDO find(
            @Param("annotationId") long annotationId,
            @Param("targetRevision") long targetRevision,
            @Param("projectionVersion") int projectionVersion,
            @Param("resolverVersion") int resolverVersion);

    @Insert(
            "INSERT INTO annotation_placement(annotation_id,target_revision,projection_version,resolver_version,placement_json) VALUES(#{annotationId},#{targetRevision},#{projectionVersion},#{resolverVersion},#{placementJson}) ON DUPLICATE KEY UPDATE placement_json=#{placementJson}")
    int save(AnnotationPlacementDO row);
}
