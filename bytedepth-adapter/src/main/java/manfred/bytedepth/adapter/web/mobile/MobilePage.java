package manfred.bytedepth.adapter.web.mobile;

import java.util.List;

public record MobilePage<T>(List<T> items, int page, int pageSize, long total, long totalPages, boolean hasNext) {
    public static void validate(int page, int size) {
        // Existing tag/search backends use int offsets; reject overflow before entering those ports.
        if (page < 1 || size < 1 || size > 50 || (long) (page - 1) * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid pagination");
    }

    public static void fixed(int page, int size) {
        validate(page, size);
        if (size != 10) throw new IllegalArgumentException("Page size must be 10");
    }

    public static <T> MobilePage<T> of(List<T> items, int page, int size, long total) {
        long pages = total / size + (total % size == 0 ? 0 : 1);
        return new MobilePage<>(items, page, size, total, pages, page < pages);
    }
}
