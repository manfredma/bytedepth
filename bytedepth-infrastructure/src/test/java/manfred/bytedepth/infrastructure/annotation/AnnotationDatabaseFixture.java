package manfred.bytedepth.infrastructure.annotation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import manfred.bytedepth.infrastructure.post.*;
import org.apache.ibatis.mapping.Environment;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** In-process SQL contract fixture; real Flyway/MySQL isolation is verified on staging. */
public final class AnnotationDatabaseFixture implements AutoCloseable {
    public final DataSource dataSource;
    public final JdbcTemplate jdbc;
    public final JdbcTransactionManager transactions;
    public final SqlSessionTemplate session;
    private final Connection keeper;

    public AnnotationDatabaseFixture() throws SQLException {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:annotation_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        dataSource = ds;
        keeper = ds.getConnection();
        jdbc = new JdbcTemplate(ds);
        transactions = new JdbcTransactionManager(ds);
        jdbc.execute(
                "CREATE TABLE post(id BIGINT PRIMARY KEY,slug VARCHAR(100),title VARCHAR(100),content LONGTEXT,status VARCHAR(16),created_at TIMESTAMP,updated_at TIMESTAMP,content_version INT)");
        jdbc.update(
                "INSERT INTO post VALUES(1,'fixture','title','原文','PUBLISHED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,7)");
        jdbc.execute(
                "CREATE TABLE post_content_revision(post_id BIGINT NOT NULL,content_version BIGINT NOT NULL,markdown LONGTEXT NOT NULL,document_json LONGTEXT NOT NULL,baseline BOOLEAN NOT NULL,PRIMARY KEY(post_id,content_version))");
        jdbc.execute(
                "CREATE TABLE annotation_request(post_id BIGINT NOT NULL,owner_key VARCHAR(80) NOT NULL,request_id CHAR(36) NOT NULL,payload_hash CHAR(64) NOT NULL,annotation_id BIGINT NOT NULL,deleted BOOLEAN NOT NULL DEFAULT FALSE,PRIMARY KEY(post_id,owner_key,request_id))");
        jdbc.execute(
                "CREATE TABLE annotation_placement(annotation_id BIGINT NOT NULL,target_revision BIGINT NOT NULL,projection_version INT NOT NULL,resolver_version INT NOT NULL,placement_json LONGTEXT NOT NULL,PRIMARY KEY(annotation_id,target_revision,projection_version,resolver_version))");
        jdbc.execute(
                "CREATE TABLE post_annotation(id BIGINT AUTO_INCREMENT PRIMARY KEY,post_id BIGINT NOT NULL,user_id BIGINT,owner_token_hash VARCHAR(64),selected_text VARCHAR(500),annotation_text LONGTEXT,color VARCHAR(20),visibility VARCHAR(16),start_offset INT,end_offset INT,created_at TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,anchor_json LONGTEXT,migration_baseline_version BIGINT)");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("in-process-unit", new SpringManagedTransactionFactory(), ds));
        for (Class<?> mapper : new Class<?>[] {
            PostMapper.class,
            PostContentRevisionMapper.class,
            AnnotationRequestMapper.class,
            AnnotationPlacementMapper.class,
            PostAnnotationMapper.class
        }) configuration.addMapper(mapper);
        session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
    }

    public <T> T mapper(Class<T> type) {
        return session.getMapper(type);
    }

    @Override
    public void close() throws SQLException {
        keeper.close();
    }
}
