package manfred.bytedepth.domain.post.document;

public record DocumentSegment(String id, String blockId, String kind, String text, int start, int end) {}
