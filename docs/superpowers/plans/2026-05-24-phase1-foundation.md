# bytedepth Phase 1: Maven 多模块脚手架 + 博文核心功能

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建完整的 5 模块 COLA Maven 项目结构，实现博文（Post）的创建与展示，产出可本地运行的 Spring Boot Web 应用。

**Architecture:** 五模块 COLA（domain/app/infrastructure/adapter/start），domain 模块无 Spring/MyBatis 依赖，Flyway 管理 DDL，MyBatis-Plus 实现持久化，Thymeleaf 渲染前端。依赖方向：adapter → app → domain ← infrastructure，start 聚合 adapter + infrastructure。

**Tech Stack:** Java 17, Spring Boot 3.2.5, COLA 4.3.2, MyBatis-Plus 3.5.5, Flyway 9.x, Thymeleaf 3, MySQL 8, Lombok

---

## 文件结构总览

```
bytedepth/
├── pom.xml                                                     # 根 POM，管理依赖版本
├── bytedepth-domain/
│   ├── pom.xml
│   └── src/main/java/manfred/bytedepth/domain/
│       ├── common/DomainException.java
│       └── post/
│           ├── Post.java                                       # 聚合根
│           ├── PostStatus.java                                 # 枚举
│           └── PostRepository.java                            # Repository 接口
├── bytedepth-app/
│   ├── pom.xml
│   └── src/main/java/manfred/bytedepth/app/post/
│       ├── command/
│       │   ├── CreatePostCmd.java
│       │   ├── CreatePostCmdExe.java
│       │   └── PublishPostCmdExe.java
│       └── query/
│           ├── PostDTO.java
│           ├── ListPostsQryExe.java
│           └── GetPostQryExe.java
├── bytedepth-infrastructure/
│   ├── pom.xml
│   └── src/main/java/manfred/bytedepth/infrastructure/
│       ├── config/MybatisPlusConfig.java
│       └── post/
│           ├── PostDO.java
│           ├── PostMapper.java
│           └── PostRepositoryImpl.java
├── bytedepth-adapter/
│   ├── pom.xml
│   └── src/main/java/manfred/bytedepth/adapter/web/portal/
│       ├── HomeController.java
│       └── PostController.java
└── bytedepth-start/
    ├── pom.xml
    └── src/main/
        ├── java/manfred/bytedepth/BytedepthApplication.java
        └── resources/
            ├── application.yml
            ├── db/migration/V1__init_tables.sql
            └── templates/
                ├── fragments/nav.html
                ├── public/index.html
                ├── public/posts/list.html
                ├── public/posts/detail.html
                └── admin/posts/edit.html
```

---

### Task 1: Maven 多模块骨架

**Files:**
- Create/Replace: `pom.xml`
- Create: `bytedepth-domain/pom.xml`
- Create: `bytedepth-app/pom.xml`
- Create: `bytedepth-infrastructure/pom.xml`
- Create: `bytedepth-adapter/pom.xml`
- Create: `bytedepth-start/pom.xml`

- [ ] **Step 1: 替换根 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>manfred.bytedepth</groupId>
    <artifactId>bytedepth</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>bytedepth</name>

    <modules>
        <module>bytedepth-domain</module>
        <module>bytedepth-app</module>
        <module>bytedepth-infrastructure</module>
        <module>bytedepth-adapter</module>
        <module>bytedepth-start</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.boot.version>3.2.5</spring.boot.version>
        <cola.version>4.3.2</cola.version>
        <mybatis.plus.version>3.5.5</mybatis.plus.version>
        <sort.skip>true</sort.skip>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cola</groupId>
                <artifactId>cola-component-dto</artifactId>
                <version>${cola.version}</version>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cola</groupId>
                <artifactId>cola-component-exception</artifactId>
                <version>${cola.version}</version>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis.plus.version}</version>
            </dependency>
            <dependency>
                <groupId>manfred.bytedepth</groupId>
                <artifactId>bytedepth-domain</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>manfred.bytedepth</groupId>
                <artifactId>bytedepth-app</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>manfred.bytedepth</groupId>
                <artifactId>bytedepth-infrastructure</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>manfred.bytedepth</groupId>
                <artifactId>bytedepth-adapter</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>17</source>
                        <target>17</target>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <version>1.18.30</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: 创建各子模块目录结构**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mkdir -p bytedepth-domain/src/{main,test}/java/manfred/bytedepth/domain/{common,post}
mkdir -p bytedepth-app/src/{main,test}/java/manfred/bytedepth/app/post/{command,query}
mkdir -p bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/{config,post}
mkdir -p bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal
mkdir -p bytedepth-start/src/main/java/manfred/bytedepth
mkdir -p bytedepth-start/src/main/resources/{db/migration,templates/{fragments,public/posts,admin/posts}}
mkdir -p bytedepth-start/src/test/java/manfred/bytedepth
```

- [ ] **Step 3: 创建 bytedepth-domain/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>manfred.bytedepth</groupId>
        <artifactId>bytedepth</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>bytedepth-domain</artifactId>
    <name>bytedepth-domain</name>
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: 创建 bytedepth-app/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>manfred.bytedepth</groupId>
        <artifactId>bytedepth</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>bytedepth-app</artifactId>
    <name>bytedepth-app</name>
    <dependencies>
        <dependency>
            <groupId>manfred.bytedepth</groupId>
            <artifactId>bytedepth-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cola</groupId>
            <artifactId>cola-component-dto</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cola</groupId>
            <artifactId>cola-component-exception</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: 创建 bytedepth-infrastructure/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>manfred.bytedepth</groupId>
        <artifactId>bytedepth</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>bytedepth-infrastructure</artifactId>
    <name>bytedepth-infrastructure</name>
    <dependencies>
        <dependency>
            <groupId>manfred.bytedepth</groupId>
            <artifactId>bytedepth-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 6: 创建 bytedepth-adapter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>manfred.bytedepth</groupId>
        <artifactId>bytedepth</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>bytedepth-adapter</artifactId>
    <name>bytedepth-adapter</name>
    <dependencies>
        <dependency>
            <groupId>manfred.bytedepth</groupId>
            <artifactId>bytedepth-app</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 7: 创建 bytedepth-start/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>manfred.bytedepth</groupId>
        <artifactId>bytedepth</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>bytedepth-start</artifactId>
    <name>bytedepth-start</name>
    <dependencies>
        <dependency>
            <groupId>manfred.bytedepth</groupId>
            <artifactId>bytedepth-adapter</artifactId>
        </dependency>
        <dependency>
            <groupId>manfred.bytedepth</groupId>
            <artifactId>bytedepth-infrastructure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring.boot.version}</version>
                <executions>
                    <execution>
                        <goals><goal>repackage</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 8: 验证 Maven 多模块编译（仅验证 pom 合法性）**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mvn clean compile -Dsort.skip=true 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`（此时各模块为空，编译通过即可）

- [ ] **Step 9: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add .
git commit -m "chore: 初始化 Maven 5模块 COLA 结构"
```

---

### Task 2: Domain 模块 — Post 聚合根

**Files:**
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/common/DomainException.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostStatus.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/Post.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java`
- Test: `bytedepth-domain/src/test/java/manfred/bytedepth/domain/post/PostTest.java`

- [ ] **Step 1: 写失败测试 PostTest.java**

```java
package manfred.bytedepth.domain.post;

import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostTest {

    @Test
    void create_shouldSetDraftStatus() {
        Post post = Post.create("标题", "内容");
        assertEquals(PostStatus.DRAFT, post.getStatus());
        assertNotNull(post.getCreatedAt());
        assertNull(post.getPublishedAt());
        assertNull(post.getId());
    }

    @Test
    void publish_shouldChangeStatusAndSetPublishedAt() {
        Post post = Post.create("标题", "内容");
        post.publish();
        assertEquals(PostStatus.PUBLISHED, post.getStatus());
        assertNotNull(post.getPublishedAt());
    }

    @Test
    void publish_shouldThrow_whenAlreadyPublished() {
        Post post = Post.create("标题", "内容");
        post.publish();
        assertThrows(DomainException.class, post::publish);
    }

    @Test
    void delete_shouldChangeStatusToDeleted() {
        Post post = Post.create("标题", "内容");
        post.delete();
        assertEquals(PostStatus.DELETED, post.getStatus());
    }
}
```

- [ ] **Step 2: 运行测试验证它失败（类不存在）**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mvn clean test -pl bytedepth-domain -Dsort.skip=true 2>&1 | tail -10
```

Expected: COMPILATION ERROR（Post 类不存在）

- [ ] **Step 3: 创建 DomainException.java**

```java
package manfred.bytedepth.domain.common;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: 创建 PostStatus.java**

```java
package manfred.bytedepth.domain.post;

public enum PostStatus {
    DRAFT, PUBLISHED, DELETED
}
```

- [ ] **Step 5: 创建 Post.java**

```java
package manfred.bytedepth.domain.post;

import lombok.Getter;
import manfred.bytedepth.domain.common.DomainException;

import java.time.LocalDateTime;

@Getter
public class Post {

    private Long id;
    private String title;
    private String content;
    private PostStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;

    private Post() {}

    public static Post create(String title, String content) {
        Post post = new Post();
        post.title = title;
        post.content = content;
        post.status = PostStatus.DRAFT;
        post.createdAt = LocalDateTime.now();
        post.updatedAt = LocalDateTime.now();
        return post;
    }

    public static Post reconstruct(Long id, String title, String content, PostStatus status,
                                   LocalDateTime createdAt, LocalDateTime publishedAt,
                                   LocalDateTime updatedAt) {
        Post post = new Post();
        post.id = id;
        post.title = title;
        post.content = content;
        post.status = status;
        post.createdAt = createdAt;
        post.publishedAt = publishedAt;
        post.updatedAt = updatedAt;
        return post;
    }

    public void publish() {
        if (this.status != PostStatus.DRAFT) {
            throw new DomainException("只有草稿才能发布，当前状态：" + this.status);
        }
        this.status = PostStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateContent(String title, String content) {
        this.title = title;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.status = PostStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 6: 创建 PostRepository.java**

```java
package manfred.bytedepth.domain.post;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    Post save(Post post);
    Optional<Post> findById(Long id);
    List<Post> findPublished(int page, int size);
    long countPublished();
}
```

- [ ] **Step 7: 运行测试验证通过**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mvn clean test -pl bytedepth-domain -Dsort.skip=true 2>&1 | tail -10
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add bytedepth-domain/
git commit -m "feat: 实现 Post 聚合根及领域规则（domain 模块）"
```

---

### Task 3: Infrastructure 模块 — MyBatis-Plus + MybatisPlusConfig

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/config/MybatisPlusConfig.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostDO.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostMapper.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java`

- [ ] **Step 1: 创建 MybatisPlusConfig.java**（开启分页插件）

```java
package manfred.bytedepth.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
```

- [ ] **Step 2: 创建 PostDO.java**

```java
package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post")
public class PostDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: 创建 PostMapper.java**

```java
package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper extends BaseMapper<PostDO> {}
```

- [ ] **Step 4: 创建 PostRepositoryImpl.java**

```java
package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostMapper postMapper;

    @Override
    public Post save(Post post) {
        PostDO postDO = toDO(post);
        if (post.getId() == null) {
            postMapper.insert(postDO);
        } else {
            postMapper.updateById(postDO);
        }
        return toEntity(postDO);
    }

    @Override
    public Optional<Post> findById(Long id) {
        return Optional.ofNullable(postMapper.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<Post> findPublished(int page, int size) {
        Page<PostDO> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name())
                .orderByDesc(PostDO::getPublishedAt);
        return postMapper.selectPage(pageParam, wrapper).getRecords()
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public long countPublished() {
        return postMapper.selectCount(new LambdaQueryWrapper<PostDO>()
                .eq(PostDO::getStatus, PostStatus.PUBLISHED.name()));
    }

    private PostDO toDO(Post post) {
        PostDO postDO = new PostDO();
        postDO.setId(post.getId());
        postDO.setTitle(post.getTitle());
        postDO.setContent(post.getContent());
        postDO.setStatus(post.getStatus().name());
        postDO.setCreatedAt(post.getCreatedAt());
        postDO.setPublishedAt(post.getPublishedAt());
        postDO.setUpdatedAt(post.getUpdatedAt());
        return postDO;
    }

    private Post toEntity(PostDO postDO) {
        return Post.reconstruct(
                postDO.getId(),
                postDO.getTitle(),
                postDO.getContent(),
                PostStatus.valueOf(postDO.getStatus()),
                postDO.getCreatedAt(),
                postDO.getPublishedAt(),
                postDO.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 5: 验证 infrastructure 模块编译**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mvn clean compile -pl bytedepth-domain,bytedepth-infrastructure -Dsort.skip=true 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add bytedepth-infrastructure/
git commit -m "feat: 实现 PostRepositoryImpl 及 MyBatis-Plus 配置（infrastructure 模块）"
```

---

### Task 4: Flyway 迁移脚本

**Files:**
- Create: `bytedepth-start/src/main/resources/db/migration/V1__init_tables.sql`

- [ ] **Step 1: 创建 V1__init_tables.sql**

```sql
CREATE TABLE IF NOT EXISTS `post` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title`        VARCHAR(255) NOT NULL COMMENT '标题',
    `content`      LONGTEXT COMMENT 'Markdown 正文',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/DELETED',
    `created_at`   DATETIME     NOT NULL COMMENT '创建时间',
    `published_at` DATETIME              COMMENT '发布时间',
    `updated_at`   DATETIME     NOT NULL COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='博文表';
```

- [ ] **Step 2: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add bytedepth-start/src/main/resources/db/
git commit -m "feat: 添加 Flyway V1 建表脚本（post 表）"
```

---

### Task 5: App 模块 — Post CRUD Command/Query

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/command/CreatePostCmd.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/command/CreatePostCmdExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/command/PublishPostCmdExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/ListPostsQryExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/GetPostQryExe.java`

- [ ] **Step 1: 创建 CreatePostCmd.java**

```java
package manfred.bytedepth.app.post.command;

import com.alibaba.cola.dto.Command;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreatePostCmd extends Command {
    private String title;
    private String content;
}
```

- [ ] **Step 2: 创建 CreatePostCmdExe.java**

```java
package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreatePostCmdExe {

    private final PostRepository postRepository;

    public Long execute(CreatePostCmd cmd) {
        Post post = Post.create(cmd.getTitle(), cmd.getContent());
        Post saved = postRepository.save(post);
        return saved.getId();
    }
}
```

- [ ] **Step 3: 创建 PublishPostCmdExe.java**

```java
package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishPostCmdExe {

    private final PostRepository postRepository;

    public void execute(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("博文不存在：" + postId));
        post.publish();
        postRepository.save(post);
    }
}
```

- [ ] **Step 4: 创建 PostDTO.java**

```java
package manfred.bytedepth.app.post.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostDTO {
    private Long id;
    private String title;
    private String content;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: 创建 ListPostsQryExe.java**

```java
package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListPostsQryExe {

    private final PostRepository postRepository;

    public List<PostDTO> execute(int page, int size) {
        List<Post> posts = postRepository.findPublished(page, size);
        return posts.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private PostDTO toDTO(Post post) {
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setStatus(post.getStatus().name());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setCreatedAt(post.getCreatedAt());
        return dto;
    }
}
```

- [ ] **Step 6: 创建 GetPostQryExe.java**

```java
package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetPostQryExe {

    private final PostRepository postRepository;

    public PostDTO execute(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("博文不存在：" + postId));
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setStatus(post.getStatus().name());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setCreatedAt(post.getCreatedAt());
        return dto;
    }
}
```

- [ ] **Step 7: 验证 app 模块编译**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mvn clean compile -pl bytedepth-domain,bytedepth-app -Dsort.skip=true 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add bytedepth-app/
git commit -m "feat: 实现 Post CRUD Command/Query Executor（app 模块）"
```

---

### Task 6: Adapter 模块 — Controller

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/HomeController.java`
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java`

- [ ] **Step 1: 创建 HomeController.java**

```java
package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ListPostsQryExe listPostsQryExe;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("recentPosts", listPostsQryExe.execute(1, 5));
        return "public/index";
    }
}
```

- [ ] **Step 2: 创建 PostController.java**

```java
package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final ListPostsQryExe listPostsQryExe;
    private final GetPostQryExe getPostQryExe;
    private final CreatePostCmdExe createPostCmdExe;
    private final PublishPostCmdExe publishPostCmdExe;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size) {
        model.addAttribute("posts", listPostsQryExe.execute(page, size));
        model.addAttribute("currentPage", page);
        return "public/posts/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("post", getPostQryExe.execute(id));
        return "public/posts/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("cmd", new CreatePostCmd());
        return "admin/posts/edit";
    }

    @PostMapping
    public String create(@ModelAttribute CreatePostCmd cmd) {
        Long id = createPostCmdExe.execute(cmd);
        return "redirect:/posts/" + id;
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id) {
        publishPostCmdExe.execute(id);
        return "redirect:/posts/" + id;
    }
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add bytedepth-adapter/
git commit -m "feat: 实现 HomeController 和 PostController（adapter 模块）"
```

---

### Task 7: Start 模块 — 启动入口 + 配置

**Files:**
- Create: `bytedepth-start/src/main/java/manfred/bytedepth/BytedepthApplication.java`
- Create: `bytedepth-start/src/main/resources/application.yml`

- [ ] **Step 1: 创建 BytedepthApplication.java**

```java
package manfred.bytedepth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "manfred.bytedepth")
@MapperScan("manfred.bytedepth.infrastructure")
public class BytedepthApplication {
    public static void main(String[] args) {
        SpringApplication.run(BytedepthApplication.class, args);
    }
}
```

- [ ] **Step 2: 创建 application.yml**

```yaml
spring:
  application:
    name: bytedepth
  datasource:
    url: jdbc:mysql://localhost:3306/bytedepth?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
    username: root
    password: benchmark
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    locations: classpath:db/migration
    baseline-on-migrate: true
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    cache: false

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

server:
  port: 8080
```

- [ ] **Step 3: 初始化本地 MySQL 数据库**

```bash
# 使用 Docker 启动 MySQL（推荐）
docker run -d --name bytedepth-mysql \
  -e MYSQL_ROOT_PASSWORD=benchmark \
  -e MYSQL_DATABASE=bytedepth \
  -p 3306:3306 mysql:8.0
sleep 30
docker exec bytedepth-mysql mysql -uroot -pbenchmark -e "SHOW DATABASES;" 2>/dev/null
```

Expected: 输出包含 `bytedepth` 数据库

- [ ] **Step 4: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add bytedepth-start/src/main/java/ bytedepth-start/src/main/resources/application.yml
git commit -m "feat: 添加 Spring Boot 启动入口及 application.yml 配置"
```

---

### Task 8: Thymeleaf 模板

**Files:**
- Create: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Create: `bytedepth-start/src/main/resources/templates/public/index.html`
- Create: `bytedepth-start/src/main/resources/templates/public/posts/list.html`
- Create: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Create: `bytedepth-start/src/main/resources/templates/admin/posts/edit.html`

- [ ] **Step 1: 创建 fragments/nav.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<nav th:fragment="navbar" style="padding:16px;background:#1a1a2e;color:#eee;display:flex;gap:24px;align-items:center;">
    <a th:href="@{/}" style="color:#e94560;font-weight:bold;font-size:1.2em;text-decoration:none;">bytedepth</a>
    <a th:href="@{/posts}" style="color:#eee;text-decoration:none;">文章</a>
    <a th:href="@{/posts/new}" style="color:#eee;text-decoration:none;margin-left:auto;">写文章</a>
</nav>
</body>
</html>
```

- [ ] **Step 2: 创建 public/index.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <title>bytedepth - 字节深处</title>
    <style>
        body { font-family: sans-serif; margin: 0; background: #f5f5f5; }
        .container { max-width: 800px; margin: 40px auto; padding: 0 20px; }
        .post-card { background: white; padding: 20px; margin: 16px 0; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,.1); }
        .post-card h2 { margin: 0 0 8px; }
        .post-card a { color: #e94560; text-decoration: none; }
        .meta { color: #888; font-size: 0.9em; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <h1>最新文章</h1>
    <div th:if="${#lists.isEmpty(recentPosts)}" style="color:#888;">暂无文章</div>
    <div th:each="post : ${recentPosts}" class="post-card">
        <h2><a th:href="@{/posts/{id}(id=${post.id})}" th:text="${post.title}">文章标题</a></h2>
        <p class="meta" th:text="${#temporals.format(post.publishedAt, 'yyyy-MM-dd')}">发布时间</p>
    </div>
    <a th:href="@{/posts}">查看全部文章 →</a>
</div>
</body>
</html>
```

- [ ] **Step 3: 创建 public/posts/list.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <title>文章列表 - bytedepth</title>
    <style>
        body { font-family: sans-serif; margin: 0; background: #f5f5f5; }
        .container { max-width: 800px; margin: 40px auto; padding: 0 20px; }
        .post-item { background: white; padding: 20px; margin: 12px 0; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,.1); }
        .post-item h2 { margin: 0 0 8px; }
        .post-item a { color: #e94560; text-decoration: none; }
        .meta { color: #888; font-size: 0.9em; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <h1>全部文章</h1>
    <div th:if="${#lists.isEmpty(posts)}" style="color:#888;">暂无发布的文章</div>
    <div th:each="post : ${posts}" class="post-item">
        <h2><a th:href="@{/posts/{id}(id=${post.id})}" th:text="${post.title}">文章标题</a></h2>
        <p class="meta" th:text="${#temporals.format(post.publishedAt, 'yyyy-MM-dd HH:mm')}">发布时间</p>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 4: 创建 public/posts/detail.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <title th:text="${post.title} + ' - bytedepth'">文章详情</title>
    <style>
        body { font-family: sans-serif; margin: 0; background: #f5f5f5; }
        .container { max-width: 800px; margin: 40px auto; padding: 0 20px; background: white; padding: 40px; border-radius: 8px; }
        h1 { color: #1a1a2e; }
        .meta { color: #888; font-size: 0.9em; margin-bottom: 24px; }
        .content { line-height: 1.8; white-space: pre-wrap; }
        .actions { margin-top: 24px; }
        .btn { padding: 8px 16px; background: #e94560; color: white; border: none; border-radius: 4px; cursor: pointer; text-decoration: none; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <h1 th:text="${post.title}">文章标题</h1>
    <p class="meta">
        <span th:if="${post.publishedAt != null}" th:text="'发布于 ' + ${#temporals.format(post.publishedAt, 'yyyy-MM-dd')}"></span>
        <span th:if="${post.publishedAt == null}">草稿</span>
    </p>
    <div class="content" th:text="${post.content}">文章内容</div>
    <div class="actions" th:if="${post.status == 'DRAFT'}">
        <form th:action="@{/posts/{id}/publish(id=${post.id})}" method="post">
            <button type="submit" class="btn">发布此文章</button>
        </form>
    </div>
    <p><a th:href="@{/posts}">← 返回列表</a></p>
</div>
</body>
</html>
```

- [ ] **Step 5: 创建 admin/posts/edit.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head>
    <meta charset="UTF-8">
    <title>写文章 - bytedepth</title>
    <style>
        body { font-family: sans-serif; margin: 0; background: #f5f5f5; }
        .container { max-width: 800px; margin: 40px auto; padding: 0 20px; }
        .form-card { background: white; padding: 32px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,.1); }
        input[name=title] { width: 100%; padding: 10px; font-size: 1.2em; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; margin-bottom: 16px; }
        textarea[name=content] { width: 100%; height: 400px; padding: 10px; font-family: monospace; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; }
        .btn { margin-top: 16px; padding: 10px 24px; background: #e94560; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 1em; }
    </style>
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar}"></nav>
<div class="container">
    <div class="form-card">
        <h1>写文章</h1>
        <form th:action="@{/posts}" method="post" th:object="${cmd}">
            <input type="text" name="title" placeholder="文章标题" th:field="*{title}" required>
            <textarea name="content" placeholder="支持 Markdown 格式..." th:field="*{content}"></textarea>
            <button type="submit" class="btn">保存草稿</button>
        </form>
    </div>
</div>
</body>
</html>
```

- [ ] **Step 6: Commit**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git add bytedepth-start/src/main/resources/templates/
git commit -m "feat: 添加 Thymeleaf 模板（首页、文章列表、详情、编辑）"
```

---

### Task 9: 全量构建 + 本地启动验证

- [ ] **Step 1: 全量编译**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mvn clean compile -Dsort.skip=true 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: 打包**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
mvn clean package -DskipTests -Dsort.skip=true 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`，`bytedepth-start/target/bytedepth-start-1.0.0-SNAPSHOT.jar` 存在

- [ ] **Step 3: 启动应用**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth/bytedepth-start
java -jar target/bytedepth-start-1.0.0-SNAPSHOT.jar &
sleep 10
```

- [ ] **Step 4: 验证 Flyway 建表**

```bash
docker exec bytedepth-mysql mysql -uroot -pbenchmark bytedepth \
  -e "SHOW TABLES; DESCRIBE post;" 2>/dev/null
```

Expected: 输出 `post` 表结构及 `flyway_schema_history` 表

- [ ] **Step 5: 验证首页可访问**

```bash
curl -s http://localhost:8080/ | grep -o "bytedepth"
```

Expected: 输出 `bytedepth`

- [ ] **Step 6: 验证创建文章流程**

```bash
curl -s -X POST http://localhost:8080/posts \
  -d "title=Hello+bytedepth&content=这是第一篇文章" \
  -L | grep -o "Hello"
```

Expected: 输出 `Hello`（跳转到文章详情页后显示标题）

- [ ] **Step 7: Push 到 GitHub**

```bash
cd /Users/maxingfang/IdeaProjects/github/bytedepth
git push origin main
```

- [ ] **Step 8: 停止本地测试 MySQL**（可选，保留供后续开发用）

```bash
# 如需停止：docker stop bytedepth-mysql
# 如需保留：什么都不做
echo "Phase 1 完成！访问 http://localhost:8080 查看效果"
```

---

## Phase 2 预告

Phase 1 产出：可运行的博客应用（发文 + 列表 + 详情）。

Phase 2 将实现：
- 分类、标签（Tag、Category 聚合根）
- 评论系统（Comment 聚合根 + 审核流程）
- Spring Security 保护管理后台
- 项目展示模块（Project 聚合根）
- 访客统计（Redis 计数 + 定时刷写）
