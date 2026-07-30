package manfred.bytedepth.app.ops;

public record OpsDatabaseStatusDTO(boolean available, String databaseName) {

    public static OpsDatabaseStatusDTO unavailable() {
        return new OpsDatabaseStatusDTO(false, null);
    }
}
