package manfred.bytedepth.app.series;

import lombok.Data;

@Data
public class CandidatePostDTO {
    private Long id;
    private String title;
    private String status;  // PUBLISHED / DRAFT
}
