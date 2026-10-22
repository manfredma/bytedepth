import { validateBridgeMessage, matchingAnnotations } from "../bridge";
import type { AnnotationEnvelope, ContentIdentity } from "../../contracts/mobile";
const identity: ContentIdentity = {
  postId: "9007199254740993",
  contentVersion: 7,
  projectionVersion: 1,
  digest: "a".repeat(64),
  representationHash: "b",
  shellVersion: "1",
};
const expected = { ...identity, generation: 3 };
const message = (type = "ready", payload: unknown = {}) =>
  JSON.stringify({ type, postId: identity.postId, contentVersion: 7, generation: 3, payload });
test("fixed schema, byte limit, content identity and generation fail closed", () => {
  expect(validateBridgeMessage(message(), expected)?.type).toBe("ready");
  for (const raw of [
    "{",
    message("unknown"),
    message("ready", { extra: true }),
    message().replace('"generation":3', '"generation":4'),
    message().replace(identity.postId, "1"),
    message("renderError", { message: "😀".repeat(17000) }),
  ])
    expect(validateBridgeMessage(raw, expected)).toBeNull();
  expect(validateBridgeMessage(message("link", { href: "javascript:alert(1)" }), expected)).toBeNull();
  expect(validateBridgeMessage(message("link", { href: "file:///private/token" }), expected)).toBeNull();
  expect(validateBridgeMessage(message("link", { href: "/posts/article" }), expected)?.type).toBe("link");
});
test("cross-block selection separators need not belong to a span", () => {
  expect(
    validateBridgeMessage(
      message("selection", {
        exact: "中\n😀",
        prefix: "",
        suffix: "",
        start: 0,
        end: 3,
        spans: [
          { segmentId: "s0", start: 0, end: 1 },
          { segmentId: "s1", start: 0, end: 1 },
        ],
      }),
      expected,
    )?.type,
  ).toBe("selection");
});
test("only matching public identity may supply temporary annotation layers", () => {
  const envelope: AnnotationEnvelope = {
    schemaVersion: 1,
    postId: identity.postId,
    contentVersion: 7,
    projectionVersion: 1,
    digest: identity.digest,
    annotations: [],
  };
  expect(matchingAnnotations(envelope, identity)).toBe(envelope);
  for (const change of [{ postId: "1" }, { contentVersion: 8 }, { projectionVersion: 2 }, { digest: "wrong" }])
    expect(matchingAnnotations({ ...envelope, ...change }, identity)).toBeNull();
  expect(matchingAnnotations(null, identity)).toBeNull();
});
test("position projection is checked against the public document identity", () => {
  const payload = {
    postId: identity.postId,
    contentVersion: 7,
    projectionVersion: 1,
    segmentId: "s0",
    codePointOffset: 1,
    blockKind: "prose",
    blockText: "中文",
    precedingHeadings: [],
    viewportRatio: 0,
    progress: 0,
    updatedAt: "2026-10-22T00:00:00Z",
  };
  expect(validateBridgeMessage(message("position", payload), expected)?.type).toBe("position");
  expect(validateBridgeMessage(message("position", { ...payload, projectionVersion: 2 }), expected)).toBeNull();
});
