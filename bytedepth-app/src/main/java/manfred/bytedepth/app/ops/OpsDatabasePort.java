package manfred.bytedepth.app.ops;

/** Queries the database health without exposing database implementation details to the application. */
public interface OpsDatabasePort {

    OpsDatabaseStatusDTO inspect();
}
