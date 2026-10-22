import { parseBridgeMessage, parsePostState, parsePublicContent } from "../mobile";

const identity = {
  postId: "9007199254740993",
  contentVersion: 1,
  projectionVersion: 1,
  digest: "document-digest",
  representationHash: "representation-hash",
  shellVersion: "1",
};
const content = {
  ...identity,
  schemaVersion: 1,
  slug: "hello",
  title: "Hello",
  publishedAt: "2026-10-22T00:00:00Z",
  category: null,
  series: null,
  html: "<p>Hello 😀</p>",
  document: {
    projectionVersion: 1,
    digest: "document-digest",
    text: "Hello 😀",
    segments: [{ id: "s1", blockId: "b1", kind: "paragraph", text: "Hello 😀", start: 0, end: 7 }],
  },
  resources: [
    {
      key: "image1",
      url: "https://staging-bytedepth.bytedepth.cn/a.png",
      role: "image",
      required: true,
      mediaType: null,
      sha256: null,
    },
  ],
};
const bridge = { type: "ready", postId: identity.postId, contentVersion: 1, generation: 2, payload: {} };
const context = { postId: identity.postId, contentVersion: 1, generation: 2 };

it("preservesLargeDecimalIds", () => {
  expect(parsePublicContent(JSON.parse(JSON.stringify(content))).postId).toBe("9007199254740993");
  expect(() => parsePublicContent({ ...content, postId: 9007199254740992 })).toThrow();
});
it("rejectsUnsupportedSchema", () => {
  expect(() => parsePublicContent({ ...content, schemaVersion: 2 })).toThrow();
  expect(() => parsePostState({ schemaVersion: 2, postId: identity.postId, availability: "UNAVAILABLE" })).toThrow();
});
it("rejectsMalformedBridgeEnvelope", () => {
  expect(parseBridgeMessage(JSON.stringify(bridge), context)).toEqual(bridge);
  for (const bad of [
    null,
    {},
    { ...bridge, type: "login" },
    { ...bridge, generation: 1 },
    { ...bridge, postId: "2" },
    { ...bridge, contentVersion: 2 },
    { ...bridge, payload: { token: "secret" } },
    { ...bridge, type: "position", payload: { progress: NaN } },
    { ...bridge, type: "selection", payload: {} },
    { ...bridge, type: "link", payload: { href: "javascript:alert(1)" } },
  ]) {
    expect(() => parseBridgeMessage(JSON.stringify(bad), context)).toThrow();
  }
  expect(() => parseBridgeMessage("a".repeat(65537), context)).toThrow();
  expect(() =>
    parseBridgeMessage(
      JSON.stringify({ ...bridge, type: "renderError", payload: { message: "汉".repeat(22000) } }),
      context,
    ),
  ).toThrow();
});
it("rejectsMissingFieldsAndCredentialLeaks", () => {
  for (const field of Object.keys(content)) {
    const incomplete: Record<string, unknown> = { ...content };
    delete incomplete[field];
    expect(() => parsePublicContent(incomplete)).toThrow();
  }
  for (const field of [
    "ownerHash",
    "userId",
    "token",
    "csrf",
    "session",
    "password",
    "cookie",
    "authorization",
    "X-App-Session",
  ]) {
    expect(() => parsePublicContent({ ...content, [field]: "secret" })).toThrow();
    expect(() => parsePublicContent({ ...content, document: { ...content.document, [field]: "secret" } })).toThrow();
  }
});
it("distinguishesUnavailableFromPublicContent", () => {
  expect(parsePostState({ schemaVersion: 1, postId: identity.postId, availability: "AVAILABLE", identity })).toEqual({
    schemaVersion: 1,
    postId: identity.postId,
    availability: "AVAILABLE",
    identity,
  });
  expect(parsePostState({ schemaVersion: 1, postId: identity.postId, availability: "UNAVAILABLE" })).toEqual({
    schemaVersion: 1,
    postId: identity.postId,
    availability: "UNAVAILABLE",
  });
  expect(() =>
    parsePostState({ schemaVersion: 1, postId: identity.postId, availability: "UNAVAILABLE", title: "hidden" }),
  ).toThrow();
  expect(() =>
    parsePostState({
      schemaVersion: 1,
      postId: identity.postId,
      availability: "AVAILABLE",
      identity: { ...identity, postId: "2" },
    }),
  ).toThrow();
});
it("validatesDocumentCoordinatesAndManifest", () => {
  expect(parsePublicContent(content)).toEqual(content);
  expect(() => parsePublicContent({ ...content, document: { ...content.document, projectionVersion: 2 } })).toThrow();
  expect(() =>
    parsePublicContent({
      ...content,
      document: { ...content.document, segments: [{ ...content.document.segments[0], end: 8 }] },
    }),
  ).toThrow();
  expect(
    parsePublicContent({ ...content, resources: [{ ...content.resources[0], url: "http://example.com/a" }] })
      .resources[0].url,
  ).toBe("http://example.com/a");
  expect(
    parsePublicContent({ ...content, resources: [{ ...content.resources[0], url: "https://user:pass@example.com/a" }] })
      .resources[0].url,
  ).toBe("https://user:pass@example.com/a");
  for (const url of ["file:///secret", "javascript:alert(1)"]) {
    expect(() => parsePublicContent({ ...content, resources: [{ ...content.resources[0], url }] })).toThrow();
  }
});
it("acceptsBridgePayloadsWithSegmentLocalCodePointOffsets", () => {
  const payloads = [
    {
      type: "position",
      payload: {
        postId: identity.postId,
        contentVersion: 1,
        projectionVersion: 1,
        segmentId: "s2",
        codePointOffset: 1,
        blockKind: "paragraph",
        blockText: "😀x",
        precedingHeadings: ["Intro"],
        viewportRatio: 0.25,
        progress: 0.5,
        updatedAt: "2026-10-22T00:00:00Z",
      },
    },
    { type: "toc", payload: { entries: [{ segmentId: "heading-1", title: "Intro", level: 1 }] } },
    {
      type: "selection",
      payload: {
        exact: "😀x",
        prefix: "",
        suffix: "",
        start: 100,
        end: 102,
        spans: [{ segmentId: "s2", start: 0, end: 2 }],
      },
    },
    { type: "link", payload: { href: "/posts/hello#intro" } },
    { type: "renderError", payload: { message: "Unable to render diagram" } },
  ];
  for (const message of payloads) {
    const value = { ...bridge, ...message };
    expect(parseBridgeMessage(JSON.stringify(value), context)).toEqual(value);
  }
});
it("rejectsInvalidBridgeCoordinatesAndPayloadFields", () => {
  const payloads = [
    { type: "toc", payload: { entries: [{ segmentId: "h", title: "heading", level: 7 }] } },
    { type: "selection", payload: { exact: "x", prefix: "", suffix: "", start: 5, end: 6, spans: [] } },
    {
      type: "selection",
      payload: { exact: "x", prefix: "", suffix: "", start: 5, end: 6, spans: [{ segmentId: "s", start: 2, end: 1 }] },
    },
    { type: "link", payload: { href: "https://user:secret@example.com" } },
    { type: "renderError", payload: { message: "" } },
  ];
  for (const message of payloads)
    expect(() => parseBridgeMessage(JSON.stringify({ ...bridge, ...message }), context)).toThrow();
});
it("acceptsExistingPublicCategoryAndSeriesProjection", () => {
  const seriesPost = { id: "2", slug: "hello", title: "Hello", seriesOrder: 1, estimatedReadingMinutes: 2 };
  const value = {
    ...content,
    category: { id: "1", name: "Java", slug: "java" },
    series: {
      id: "3",
      name: "Learning Java",
      slug: "learning-java",
      posts: [seriesPost],
      position: 1,
      total: 1,
      progressPercent: 100,
      previous: null,
      next: null,
    },
  };
  expect(parsePublicContent(value)).toEqual(value);
  expect(() => parsePublicContent({ ...value, category: { ...value.category, userId: "secret" } })).toThrow();
  expect(() =>
    parsePublicContent({ ...value, series: { ...value.series, posts: [{ ...seriesPost, ownerHash: "secret" }] } }),
  ).toThrow();
});
it("validatesResourceMetadataWithoutDiscardingUnknownNulls", () => {
  const resource = { ...content.resources[0], mediaType: "image/png", sha256: "a".repeat(64) };
  expect(parsePublicContent({ ...content, resources: [resource] }).resources[0]).toEqual(resource);
  for (const patch of [{ url: 5 }, { url: "not a url" }, { sha256: 5 }, { sha256: "short" }, { mediaType: "" }]) {
    expect(() => parsePublicContent({ ...content, resources: [{ ...resource, ...patch }] })).toThrow();
  }
});
it("retainsSeriesNavigationAndUnicodeBridgeText", () => {
  const post = { id: "2", slug: "part-2", title: "Part 2", seriesOrder: null, estimatedReadingMinutes: null };
  const value = {
    ...content,
    series: {
      id: "3",
      name: "Series",
      slug: "series",
      posts: [post],
      position: 2,
      total: 3,
      progressPercent: 66.7,
      previous: post,
      next: post,
    },
  };
  expect(parsePublicContent(value).series?.next?.id).toBe("2");
  const message = { ...bridge, type: "renderError", payload: { message: "é😀" } };
  expect(parseBridgeMessage(JSON.stringify(message), context)).toEqual(message);
});
it("acceptsProjectionSeparatorsBetweenSelectedSegments", () => {
  const message = {
    ...bridge,
    type: "selection",
    payload: {
      exact: "a\nb",
      prefix: "",
      suffix: "",
      start: 10,
      end: 13,
      spans: [
        { segmentId: "s1", start: 2, end: 3 },
        { segmentId: "s2", start: 0, end: 1 },
      ],
    },
  };
  expect(parseBridgeMessage(JSON.stringify(message), context)).toEqual(message);
});
