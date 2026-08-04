package manfred.bytedepth.infrastructure;

import manfred.bytedepth.infrastructure.category.CategoryDO;
import manfred.bytedepth.infrastructure.comment.CommentDO;
import manfred.bytedepth.infrastructure.post.HotPostDO;
import manfred.bytedepth.infrastructure.post.PostDO;
import manfred.bytedepth.infrastructure.project.ProjectDO;
import manfred.bytedepth.infrastructure.ratelimit.RateLimitRedisProperties;
import manfred.bytedepth.infrastructure.rating.PostRatingDO;
import manfred.bytedepth.infrastructure.rating.PostRatingStatsDO;
import manfred.bytedepth.infrastructure.series.SeriesDO;
import manfred.bytedepth.infrastructure.series.SeriesPostItemDO;
import manfred.bytedepth.infrastructure.stats.PostViewLogDO;
import manfred.bytedepth.infrastructure.tag.TagDO;
import manfred.bytedepth.infrastructure.tag.TagWithCountDO;
import manfred.bytedepth.infrastructure.user.RoleDO;
import manfred.bytedepth.infrastructure.user.UserDO;
import manfred.bytedepth.infrastructure.user.UserRoleDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies persistence transfer objects preserve every mapped property and value semantics. */
class PersistenceDataObjectsTest {

    @Test
    void mappedDataObjectsRoundTripAllPropertiesAndValueMethods() throws Exception {
        for (Class<?> type : new Class<?>[]{
                CategoryDO.class, CommentDO.class, PostDO.class, HotPostDO.class, ProjectDO.class,
                PostRatingDO.class, PostRatingStatsDO.class, SeriesDO.class, SeriesPostItemDO.class,
                PostViewLogDO.class, TagDO.class, TagWithCountDO.class, RoleDO.class, UserDO.class,
                UserRoleDO.class, RateLimitRedisProperties.class
        }) {
            assertBeanContract(type);
        }
    }

    private void assertBeanContract(Class<?> type) throws Exception {
        Object first = type.getDeclaredConstructor().newInstance();
        Object equal = type.getDeclaredConstructor().newInstance();
        Method[] setters = Arrays.stream(type.getMethods())
                .filter(method -> method.getName().startsWith("set") && method.getParameterCount() == 1)
                .toArray(Method[]::new);

        for (Method setter : setters) {
            Object value = valueFor(setter.getParameterTypes()[0]);
            setter.invoke(first, value);
            setter.invoke(equal, value);
            Method getter = type.getMethod("get" + setter.getName().substring(3));
            assertEquals(value, getter.invoke(first), type.getSimpleName() + "." + getter.getName());
        }

        if (first.getClass().getMethod("equals", Object.class).getDeclaringClass() != Object.class) {
            assertEquals(first, first);
            assertEquals(first, equal);
            assertEquals(equal, first);
            assertEquals(first.hashCode(), equal.hashCode());
            Object blank = type.getDeclaredConstructor().newInstance();
            Object equalBlank = type.getDeclaredConstructor().newInstance();
            assertEquals(blank, equalBlank);
            for (Method setter : setters) {
                Object original = valueFor(setter.getParameterTypes()[0]);
                setter.invoke(equal, differentValueFor(setter.getParameterTypes()[0]));
                if (setter.getDeclaringClass() == first.getClass().getMethod("equals", Object.class).getDeclaringClass()) {
                    assertNotEquals(first, equal, type.getSimpleName() + "." + setter.getName());
                }
                setter.invoke(equal, original);
            }
        } else {
            assertNotEquals(first, equal);
        }
        assertNotNull(first.toString(), type.getName() + " toString");
        assertFalse(first.equals(null));
        assertFalse(first.equals("not a " + type.getSimpleName()));

        if (setters.length > 0) {
            setters[0].invoke(equal, nullValueFor(setters[0].getParameterTypes()[0]));
            if (first.getClass().getMethod("equals", Object.class).getDeclaringClass() == type) {
                assertNotEquals(first, equal);
            }
        }
    }

    private Object valueFor(Class<?> type) {
        if (type == String.class) return "value";
        if (type == Long.class || type == long.class) return 7L;
        if (type == Integer.class || type == int.class) return 3;
        if (type == Double.class || type == double.class) return 4.5D;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == LocalDateTime.class) return LocalDateTime.of(2026, 8, 4, 12, 0);
        if (type == Duration.class) return Duration.ofSeconds(2);
        throw new IllegalArgumentException("No sample value for " + type);
    }

    private Object nullValueFor(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        throw new IllegalArgumentException("No default value for " + type);
    }

    private Object differentValueFor(Class<?> type) {
        if (type == String.class) return "other";
        if (type == Long.class || type == long.class) return 8L;
        if (type == Integer.class || type == int.class) return 4;
        if (type == Double.class || type == double.class) return 5.5D;
        if (type == Boolean.class || type == boolean.class) return false;
        if (type == LocalDateTime.class) return LocalDateTime.of(2026, 8, 5, 12, 0);
        if (type == Duration.class) return Duration.ofSeconds(3);
        throw new IllegalArgumentException("No distinct sample value for " + type);
    }
}
