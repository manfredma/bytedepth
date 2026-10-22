package manfred.bytedepth.adapter.web.mobile;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.interaction.MobileInteractionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1/posts/{postId}")
@RequiredArgsConstructor
public class MobileRatingController {
    private final MobileInteractionService interactions;

    @GetMapping("/ratings")
    public RatingResponse ratings(
            @PathVariable Long postId, @RequestHeader(value = "X-App-Visitor", required = false) String visitor) {
        var result = interactions.ratings(postId, visitor);
        return new RatingResponse(result.averageRating(), result.ratingCount(), result.visitorScore());
    }

    @PutMapping("/rating")
    public org.springframework.http.ResponseEntity<Void> rate(
            @PathVariable Long postId,
            @RequestHeader(value = "X-App-Visitor", required = false) String visitor,
            @RequestBody RatingRequest request) {
        interactions.rate(postId, visitor, request.score());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    public record RatingRequest(int score) {}

    public record RatingResponse(double averageScore, long ratingCount, Integer visitorScore) {}
}
