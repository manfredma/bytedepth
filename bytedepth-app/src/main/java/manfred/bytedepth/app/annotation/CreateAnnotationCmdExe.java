package manfred.bytedepth.app.annotation;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateAnnotationCmdExe {

    static final int MAX_SELECTED_TEXT_LENGTH = 500;
    static final int MAX_ANNOTATION_TEXT_LENGTH = 2000;
    static final int MAX_OFFSET = 1_000_000;

    private final AnnotationRepositoryPort annotationRepository;
    private final manfred.bytedepth.app.post.PostWriteTransaction transactions;
    private final manfred.bytedepth.app.post.document.ArticleRevisionService current;
    private final AnchorValidator validator;
    private final AnnotationRequestPort requests;
    private final AnnotationRequestLedger ledger;
    private final ListAnnotationsQryExe query;
    private final manfred.bytedepth.app.post.document.ArticleDocumentService documents;

    public AnnotationView execute(Long postId, Long userId, String ownerTokenHash, CreateAnnotationRequest request) {
        if (request == null || request.anchor() == null)
            throw new InvalidAnchorException(manfred.bytedepth.domain.annotation.PlacementReason.MODEL_MISMATCH);
        var anchor = request.anchor();
        validate(
                postId,
                userId,
                ownerTokenHash,
                anchor.exact(),
                request.annotationText(),
                request.color(),
                request.visibility(),
                anchor.start(),
                anchor.end());
        if (anchor.schemaVersion() != 1 || anchor.requestId() == null)
            throw new InvalidAnchorException(manfred.bytedepth.domain.annotation.PlacementReason.MODEL_MISMATCH);
        var scope = new AnnotationRequestPort.Scope(
                postId, userId == null ? "visitor:" + ownerTokenHash : "user:" + userId, anchor.requestId());
        String hash = payloadHash(request);
        return transactions.withLockedPost(postId, post -> {
            if (post.getStatus() == manfred.bytedepth.domain.post.PostStatus.DELETED)
                throw new java.util.NoSuchElementException("文章不存在：" + postId);
            var replay = ledger.lookup(scope, hash);
            if (replay.isPresent()) {
                var existing = annotationRepository
                        .findById(replay.get().annotationId())
                        .orElseThrow(() -> new AnnotationWriteConflictException(
                                AnnotationWriteConflictException.Kind.DELETED_REQUEST));
                return query.view(existing, current.ensureCurrent(post).revision());
            }
            if (anchor.sourceRevision() != post.getContentVersion())
                throw new AnnotationWriteConflictException(AnnotationWriteConflictException.Kind.STALE_REVISION);
            validator.validate(anchor, documents.render(post.getContent()).document());
            var target = current.ensureCurrent(post).revision();
            var saved = annotationRepository.save(new PostAnnotation(
                    null,
                    postId,
                    userId,
                    ownerTokenHash,
                    anchor.exact(),
                    normalizeAnnotationText(request.annotationText()),
                    request.color(),
                    request.visibility(),
                    anchor.start(),
                    anchor.end(),
                    LocalDateTime.now(),
                    false,
                    anchor,
                    null));
            requests.insert(scope, hash, saved.id());
            return query.view(saved, target);
        });
    }

    // Schema 1 hashes raw, length-framed UTF-16 code units: UTF-8 replacement of
    // malformed surrogates would let different retry payloads share an identity.
    private static String payloadHash(CreateAnnotationRequest request) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var a = request.anchor();
            var parts = new java.util.ArrayList<String>();
            java.util.Collections.addAll(
                    parts,
                    String.valueOf(a.schemaVersion()),
                    String.valueOf(a.sourceRevision()),
                    String.valueOf(a.projectionVersion()),
                    a.digest(),
                    a.exact(),
                    a.prefix(),
                    a.suffix(),
                    String.valueOf(a.start()),
                    String.valueOf(a.end()),
                    a.requestId().toString(),
                    request.annotationText(),
                    request.color(),
                    request.visibility().name());
            for (var span : a.spans())
                java.util.Collections.addAll(
                        parts, span.segmentId(), String.valueOf(span.start()), String.valueOf(span.end()));
            for (String part : parts) {
                if (part == null) {
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(-1).array());
                } else {
                    var buffer = java.nio.ByteBuffer.allocate(part.length() * 2);
                    for (int i = 0; i < part.length(); i++) buffer.putChar(part.charAt(i));
                    byte[] bytes = buffer.array();
                    digest.update(
                            java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    static void validate(
            Long postId,
            Long userId,
            String ownerTokenHash,
            String selectedText,
            String annotationText,
            String color,
            AnnotationVisibility visibility,
            int startOffset,
            int endOffset) {
        if (postId == null || (userId == null && (ownerTokenHash == null || ownerTokenHash.isBlank()))) {
            throw new DomainException("文章与批注身份不能为空");
        }
        if (selectedText == null
                || selectedText.isBlank()
                || selectedText.codePointCount(0, selectedText.length()) > MAX_SELECTED_TEXT_LENGTH) {
            throw new DomainException("批注文本不能为空且不超过 " + MAX_SELECTED_TEXT_LENGTH + " 字");
        }
        if (annotationText != null && annotationText.length() > MAX_ANNOTATION_TEXT_LENGTH) {
            throw new DomainException("批注内容不超过 " + MAX_ANNOTATION_TEXT_LENGTH + " 字");
        }
        if (color == null || !AnnotationColor.isSupported(color)) {
            throw new DomainException("不支持的批注颜色：" + color);
        }
        if (visibility == null) {
            throw new DomainException("批注可见范围不能为空");
        }
        if (startOffset < 0 || endOffset <= startOffset || endOffset > MAX_OFFSET) {
            throw new DomainException("批注偏移越界");
        }
    }

    private static String normalizeAnnotationText(String annotationText) {
        return annotationText == null || annotationText.isBlank() ? null : annotationText.trim();
    }
}
