package manfred.bytedepth.infrastructure.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UuidStringTypeHandlerTest {

    @Test
    void writesUuidAsCanonicalCharValue() throws SQLException {
        var statement = Mockito.mock(PreparedStatement.class);
        var value = UUID.fromString("11111111-1111-1111-1111-111111111111");

        new UuidStringTypeHandler().setNonNullParameter(statement, 1, value, null);

        Mockito.verify(statement).setString(1, value.toString());
    }

    @Test
    void readsUuidFromNullableCharValue() throws SQLException {
        var resultSet = Mockito.mock(ResultSet.class);
        var value = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Mockito.when(resultSet.getString("event_id")).thenReturn(value.toString());

        UUID actual = new UuidStringTypeHandler().getNullableResult(resultSet, "event_id");

        assertThat(actual).isEqualTo(value);
    }

    @Test
    void readsUuidByColumnIndexAndReturnsNullForNullValues() throws SQLException {
        var resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.getString(2)).thenReturn(null);

        UUID actual = new UuidStringTypeHandler().getNullableResult(resultSet, 2);

        assertThat(actual).isNull();
    }

    @Test
    void readsUuidFromCallableStatement() throws SQLException {
        var statement = Mockito.mock(CallableStatement.class);
        var value = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Mockito.when(statement.getString(3)).thenReturn(value.toString());

        UUID actual = new UuidStringTypeHandler().getNullableResult(statement, 3);

        assertThat(actual).isEqualTo(value);
    }
}
