package manfred.bytedepth.app.ops;

public record OpsDatabaseStatusDTO(boolean available, String databaseName, String error) {

    private static final String UNAVAILABLE_ERROR = "MySQL health check failed";

    public OpsDatabaseStatusDTO {
        error = available ? null : UNAVAILABLE_ERROR;
    }

    public OpsDatabaseStatusDTO(boolean available, String databaseName) {
        this(available, databaseName, null);
    }

    public static OpsDatabaseStatusDTO unavailable() {
        return new OpsDatabaseStatusDTO(false, null, UNAVAILABLE_ERROR);
    }
}
