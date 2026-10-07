package manfred.bytedepth.adapter.web.portal;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.reading.ListReadingHistoryQryExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/reading-history")
@RequiredArgsConstructor
public class ReadingHistoryController {

  private final ListReadingHistoryQryExe listReadingHistoryQryExe;
  private final PostRepository postRepository;

  @GetMapping
  public String page(
      Model model, @RequestParam(required = false) String cursor) {
    Long userId = SecurityUtils.extractUserId(SecurityUtils.currentUser());
    model.addAttribute(
        "readingHistory", userId == null ? List.of() : listReadingHistoryQryExe.execute(userId, cursor));
    model.addAttribute("readingHistoryCursor", cursor);
    return "public/reading-history";
  }

  @GetMapping("/available-posts")
  @org.springframework.web.bind.annotation.ResponseBody
  public List<AvailablePost> availablePosts(@RequestParam List<String> slugs) {
    if (slugs.size() > 20) {
      throw new IllegalArgumentException("too many post slugs");
    }
    List<AvailablePost> result = new ArrayList<>();
    for (String slug : slugs) {
      postRepository
          .findBySlug(slug)
          .filter(post -> post.getStatus().name().equals("PUBLISHED"))
          .ifPresent(post -> result.add(new AvailablePost(post.getSlug(), post.getTitle(), "/posts/" + post.getSlug())));
    }
    return result;
  }

  public record AvailablePost(String slug, String title, String path) {}
}
