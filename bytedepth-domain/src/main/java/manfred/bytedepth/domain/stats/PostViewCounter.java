package manfred.bytedepth.domain.stats;

public interface PostViewCounter {
    void increment(Long postId);
    long getCount(Long postId);
}
