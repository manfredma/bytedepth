package manfred.bytedepth.infrastructure.post;

import java.util.function.Function;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransactionalPostWriter implements PostWriteTransaction {
    private final PostMapper posts;
    private final TransactionTemplate transaction;

    public TransactionalPostWriter(PostMapper posts, PlatformTransactionManager manager) {
        this.posts = posts;
        transaction = new TransactionTemplate(manager);
    }

    @Override
    public <T> T withLockedPost(Long postId, Function<Post, T> action) {
        return transaction.execute(status -> {
            var row = posts.selectForUpdate(postId);
            if (row == null) throw new DomainException("文章不存在");
            return action.apply(PostRepositoryImpl.toEntity(row));
        });
    }
}
