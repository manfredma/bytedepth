package manfred.bytedepth.infrastructure.rating;

import lombok.Data;

@Data
public class PostRatingStatsDO {
    private Double averageRating;
    private Long ratingCount;
}
