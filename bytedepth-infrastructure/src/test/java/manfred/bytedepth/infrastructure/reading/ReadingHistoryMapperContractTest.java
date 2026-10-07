package manfred.bytedepth.infrastructure.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ReadingHistoryMapperContractTest {

  @Test
  void mapperSqlUsesIdempotentInsertAndUserBoundHistoryQueries() throws IOException {
    try (var stream = getClass().getResourceAsStream("/mapper/ReadingHistoryMapper.xml")) {
      assertThat(stream).isNotNull();
      String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(xml)
          .contains(
              "INSERT INTO post_reading_event",
              "ON DUPLICATE KEY UPDATE event_id = event_id",
              "WHERE h.user_id = #{userId}",
              "ORDER BY h.last_read_at DESC, h.post_id DESC",
              "typeHandler=\"manfred.bytedepth.infrastructure.reading.UuidStringTypeHandler\"",
              "javaType=\"java.util.UUID\" jdbcType=\"CHAR\"");
    }
  }

  @Test
  void mapperXmlParsesWithUuidCharHandler() throws IOException {
    try (var stream = getClass().getResourceAsStream("/mapper/ReadingHistoryMapper.xml")) {
      var configuration = new Configuration();
      new XMLMapperBuilder(
              stream,
              configuration,
              "mapper/ReadingHistoryMapper.xml",
              configuration.getSqlFragments())
          .parse();

      assertThat(
              configuration.hasStatement(
                  "manfred.bytedepth.infrastructure.reading.ReadingHistoryMapper.findUnprojected"))
          .isTrue();
    }
  }
}
