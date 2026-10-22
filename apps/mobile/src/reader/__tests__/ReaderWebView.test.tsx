import React from "react";
import { ARTICLE_BUDGET_BYTES, ARTICLE_BODY_BUDGET_BYTES } from "../../config";
import { render } from "@testing-library/react-native";
import { buildReaderHtml, ReaderWebView } from "../ReaderWebView";
import type { PublicContent, ResumePosition } from "../../contracts/mobile";
let mockProps: any;
const mockInject = jest.fn();
jest.mock("react-native-webview", () => {
  const React = jest.requireActual("react");
  return {
    WebView: React.forwardRef(function MockWebView(props: any, ref: any) {
      React.useImperativeHandle(ref, () => ({ injectJavaScript: mockInject }));
      mockProps = props;
      return null;
    }),
  };
});
const content: PublicContent = {
  schemaVersion: 1,
  postId: "1",
  contentVersion: 7,
  projectionVersion: 1,
  digest: "a".repeat(64),
  representationHash: "b",
  shellVersion: "1",
  slug: "article",
  title: "标题",
  publishedAt: "2026-10-22T00:00:00Z",
  category: null,
  series: null,
  html: '<p><span data-annotation-segment="s0">中文😀</span></p>',
  document: {
    projectionVersion: 1,
    digest: "a".repeat(64),
    text: "中文😀",
    segments: [{ id: "s0", blockId: "b0", kind: "prose", text: "中文😀", start: 0, end: 3 }],
  },
  resources: [],
};
const position: ResumePosition = {
  postId: "1",
  contentVersion: 7,
  projectionVersion: 1,
  segmentId: "s0",
  codePointOffset: 1,
  blockKind: "prose",
  blockText: "中文😀",
  precedingHeadings: [],
  viewportRatio: 0.2,
  progress: 0,
  updatedAt: "2026-10-22T00:00:00Z",
};
test("in-memory shell has fixed nonce/CSP and never interpolates authored markup as executable HTML", () => {
  const html = buildReaderHtml({
    content: { ...content, html: content.html + 'é</script><script>alert("$&")</script>\u2028\u2029' },
    generation: 0,
    imageDataUris: {},
  });
  expect(html).toContain("connect-src 'none'");
  expect(html).toContain("form-action 'none'");
  expect(html).toContain("frame-src 'none'");
  expect(html).toContain("img-src data:");
  expect(html).not.toContain("</script><script>alert");
  expect(html).toContain('alert(\\"$&\\")');
  expect(html).toContain("\\u2028\\u2029");
  expect(html).not.toContain("__READER_SCRIPTS__");
});
test("only manifest-owned image data, never file/remote values, and bounded base64 envelope", () => {
  const withImage = {
    ...content,
    resources: [
      {
        key: "img",
        url: "https://images.example/a.png",
        role: "image" as const,
        required: true as const,
        mediaType: null,
        sha256: null,
      },
    ],
  };
  for (const data of ["file:///token", "https://example/a.png", "data:text/html;base64,YQ=="]) {
    const html = buildReaderHtml({
      content: withImage,
      generation: 1,
      imageDataUris: { img: data, unlisted: "file:///secret" },
      theme: "dark",
      fontScale: 1.4,
    });
    expect(html).not.toContain(data);
    expect(html).not.toContain("file:///secret");
  }
  expect(
    buildReaderHtml({
      content: withImage,
      generation: 1,
      imageDataUris: { img: "data:image/png;base64,YQ==" },
      resume: position,
      previousDocument: content.document,
    }),
  ).toContain('"kind":"exact"');
  expect(
    buildReaderHtml({ content, generation: 1, imageDataUris: {}, resume: { ...position, postId: "2" } }),
  ).toContain('"kind":"start"');
  const validRawImage =
    "data:image/png;base64," + "YQ==".replace(/=/g, "A").repeat(Math.floor((ARTICLE_BUDGET_BYTES - 4096) / 3));
  expect(
    buildReaderHtml({ content: withImage, generation: 1, imageDataUris: { img: validRawImage } }).length,
  ).toBeGreaterThan(ARTICLE_BUDGET_BYTES);
  expect(() => buildReaderHtml({ content, generation: -1, imageDataUris: {} })).toThrow("generation");
  expect(() =>
    buildReaderHtml({ content: { ...content, html: "😀".repeat(13 * 1024 * 1024) }, generation: 1, imageDataUris: {} }),
  ).toThrow("2 MiB");
});
test("native WebView denies files, cookies, windows and every navigation except blank bootstrap; validates callbacks", async () => {
  const onMessage = jest.fn();
  const view = await render(
    <ReaderWebView content={content} generation={2} imageDataUris={{}} onMessage={onMessage} />,
  );
  expect(mockProps.source).toEqual({ html: expect.any(String) });
  for (const key of [
    "domStorageEnabled",
    "cacheEnabled",
    "sharedCookiesEnabled",
    "thirdPartyCookiesEnabled",
    "allowFileAccess",
    "allowFileAccessFromFileURLs",
    "allowUniversalAccessFromFileURLs",
    "javaScriptCanOpenWindowsAutomatically",
    "setSupportMultipleWindows",
    "webviewDebuggingEnabled",
    "allowsInlineMediaPlayback",
  ])
    expect(mockProps[key]).toBe(false);
  expect(mockProps.mixedContentMode).toBe("never");
  expect(mockProps.incognito).toBe(true);
  expect(mockProps.onShouldStartLoadWithRequest({ url: "about:blank" })).toBe(true);
  expect(mockProps.onShouldStartLoadWithRequest({ url: "about:blank", isTopFrame: false })).toBe(false);
  for (const url of [
    "file:///token",
    "javascript:alert(1)",
    "https://evil.test",
    "content://token",
    "about:blank#redirect",
  ])
    expect(mockProps.onShouldStartLoadWithRequest({ url, isTopFrame: true })).toBe(false);
  mockProps.onOpenWindow();
  mockProps.onMessage({
    nativeEvent: { data: '{"type":"ready","postId":"1","contentVersion":7,"generation":2,"payload":{}}' },
  });
  mockProps.onMessage({
    nativeEvent: { data: '{"type":"ready","postId":"1","contentVersion":7,"generation":1,"payload":{}}' },
  });
  expect(onMessage).toHaveBeenCalledTimes(1);
  mockProps.onError();
  expect(onMessage.mock.calls.at(-1)[0].payload.message).toBe("READER_LOAD_FAILED");
  await view.unmount();
});
test("annotation privacy/theme/scale and callback updates do not reload source or reset reading position", async () => {
  const images = {};
  const view = await render(
    <ReaderWebView content={content} generation={2} imageDataUris={images} onMessage={() => {}} />,
  );
  const source = mockProps.source;
  const annotations = {
    schemaVersion: 1 as const,
    postId: "1",
    contentVersion: 7,
    projectionVersion: 1,
    digest: content.digest,
    annotations: [],
  };
  await view.rerender(
    <ReaderWebView
      content={content}
      generation={2}
      imageDataUris={images}
      annotations={annotations}
      theme="dark"
      fontScale={NaN}
      onMessage={() => {}}
    />,
  );
  expect(mockProps.source).toBe(source);
  expect(mockInject.mock.calls.at(-1)[0]).toContain('"theme":"dark","fontScale":1');
  await view.rerender(
    <ReaderWebView content={content} generation={2} imageDataUris={images} annotations={null} onMessage={() => {}} />,
  );
  expect(mockProps.source).toBe(source);
  expect(mockInject.mock.calls.at(-1)[0]).toContain("setAnnotations(null)");
  mockProps.onMessage({
    nativeEvent: {
      data: '{"type":"link","postId":"1","contentVersion":7,"generation":2,"payload":{"href":"https://example.com"}}',
    },
  });
  await view.unmount();
});

test("worst-case less-than HTML escaping stays inside derived envelope for the maximum public body", () => {
  const remaining = ARTICLE_BODY_BUDGET_BYTES - Buffer.byteLength(JSON.stringify({ ...content, html: "" }), "utf8");
  const body = { ...content, html: "<".repeat(remaining) };
  expect(buildReaderHtml({ content: body, generation: 1, imageDataUris: {} })).toContain("\\u003c");
  const withImage = {
    ...content,
    resources: [
      {
        key: "x",
        url: "https://images.example/x.png",
        role: "image" as const,
        required: true as const,
        mediaType: null,
        sha256: null,
      },
    ],
  };
  expect(() =>
    buildReaderHtml({
      content: withImage,
      generation: 1,
      imageDataUris: { x: "data:image/png;base64," + "A".repeat(55 * 1024 * 1024) },
    }),
  ).toThrow("image map");
});
test("background image availability updates only the verified image channel, keeping stable WebView source", async () => {
  const withImage = {
    ...content,
    resources: [
      {
        key: "x",
        url: "https://images.example/x.png",
        role: "image" as const,
        required: true as const,
        mediaType: null,
        sha256: null,
      },
    ],
  };
  const view = await render(
    <ReaderWebView content={withImage} generation={3} imageDataUris={{}} onMessage={() => {}} />,
  );
  const source = mockProps.source;
  await view.rerender(
    <ReaderWebView
      content={withImage}
      generation={3}
      imageDataUris={{ x: "data:image/png;base64,YQ==" }}
      onMessage={() => {}}
    />,
  );
  expect(mockProps.source).toBe(source);
  expect(mockInject.mock.calls.at(-1)[0]).toContain(
    'setImages({"x":"data:image/png;base64,YQ=="},{"postId":"1","contentVersion":7,"generation":3})',
  );
  await view.unmount();
});
test("entire data envelope is bounded even when temporary annotation data is unusually large", () => {
  expect(() =>
    buildReaderHtml({
      content,
      generation: 1,
      imageDataUris: {},
      annotations: {
        schemaVersion: 1,
        postId: "1",
        contentVersion: 7,
        projectionVersion: 1,
        digest: content.digest,
        annotations: [{ annotationHtml: "<".repeat(10 * 1024 * 1024) } as any],
      },
    }),
  ).toThrow("envelope");
});
test("TOC jump injects a bounded action without replacing the page", async () => {
  const view = await render(<ReaderWebView content={content} generation={2} imageDataUris={{}} onMessage={() => {}} />);
  const source = mockProps.source;
  await view.rerender(
    <ReaderWebView
      content={content}
      generation={2}
      imageDataUris={{}}
      jumpTo={{ segmentId: "s0", request: 1 }}
      onMessage={() => {}}
    />,
  );
  expect(mockProps.source).toBe(source);
  expect(mockInject.mock.calls.at(-1)[0]).toContain(
    'jumpToSegment("s0",{"postId":"1","contentVersion":7,"generation":2})',
  );
  mockProps.onMessage({
    nativeEvent: {
      data: JSON.stringify({ type: "ready", postId: "1", contentVersion: 7, generation: 2, payload: {} }),
    },
  });
  expect(mockInject.mock.calls.at(-1)[0]).toContain("jumpToSegment");
  await view.unmount();
});
