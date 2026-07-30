package manfred.bytedepth.app.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsTableQryExeTest {

    @Test
    void execute_rejectsTableNamesOutsideTheWhitelist() {
        OpsTableDataPort port = table -> {
            throw new AssertionError("The data port must not receive an unapproved table");
        };

        assertThrows(IllegalArgumentException.class,
                () -> new OpsTableQryExe(port).execute("post; DROP TABLE user"));
    }
}
