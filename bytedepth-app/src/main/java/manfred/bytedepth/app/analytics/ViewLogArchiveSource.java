package manfred.bytedepth.app.analytics;

public enum ViewLogArchiveSource {
    POST("post"),
    PAGE("page");

    private final String value;

    ViewLogArchiveSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
