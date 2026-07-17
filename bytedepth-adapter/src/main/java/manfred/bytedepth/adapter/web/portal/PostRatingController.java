package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.rating.RatePostCmdExe;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@Controller
@RequestMapping("/posts/{slug}/rating")
@RequiredArgsConstructor
public class PostRatingController {
    public static final String VISITOR_COOKIE = "bytedepth-rating-visitor";

    private final GetPostQryExe getPostQryExe;
    private final RatePostCmdExe ratePostCmdExe;

    @PostMapping
    public String rate(@PathVariable String slug, @RequestParam int score,
                       HttpServletRequest request, HttpServletResponse response) {
        if (score < 1 || score > 5) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评分必须是 1 到 5 星");
        String token = visitorToken(request);
        if (token == null) {
            token = UUID.randomUUID().toString();
            response.addHeader("Set-Cookie", ResponseCookie.from(VISITOR_COOKIE, token)
                    .httpOnly(true).sameSite("Lax").path("/").maxAge(Duration.ofDays(365))
                    .secure(request.isSecure()).build().toString());
        }
        ratePostCmdExe.execute(getPostQryExe.executeBySlug(slug).getId(), token, score);
        return "redirect:/posts/" + slug + "#post-rating-end";
    }

    private String visitorToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) if (VISITOR_COOKIE.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
