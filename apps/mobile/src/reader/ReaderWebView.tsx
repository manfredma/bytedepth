import React, { useMemo, useRef, useEffect, useCallback } from "react";
import { WebView } from "react-native-webview";
import type {
  ArticleDocument,
  AnnotationEnvelope,
  PublicContent,
  ReaderBridgeMessage,
  ResumePosition,
} from "../contracts/mobile";
import { parsePublicContent } from "../contracts/mobile";
import { matchingAnnotations, validateBridgeMessage } from "./bridge";
import { resolveResume } from "./resume";
import { API_ORIGIN, ARTICLE_BUDGET_BYTES, ARTICLE_BODY_BUDGET_BYTES } from "../config";
import runtime from "../../assets/reader/runtime.json";

export interface ReaderWebViewProps {
  content: PublicContent;
  generation: number;
  /** 仅接受 ReaderRepository.assetDataUris 提供的已验证被动图片数据，不接受文件或远程 URI。 */
  imageDataUris: Record<string, string>;
  resume?: ResumePosition | null;
  previousDocument?: ArticleDocument | null;
  annotations?: AnnotationEnvelope | null;
  theme?: "light" | "dark";
  fontScale?: number;
  jumpTo?: { segmentId: string; request: number };
  onMessage: (message: ReaderBridgeMessage) => void;
}
// 缓存限制原始字节；内存信封还需计入 base64 膨胀及 JSON 开销。
const READER_ENVELOPE_BYTES = Math.ceil((ARTICLE_BUDGET_BYTES * 4) / 3) + 5 * ARTICLE_BODY_BUDGET_BYTES;
const inlineScript = (source: string) =>
  `<script nonce="bd-reader-v1">${source.replace(/<\/script/gi, "<\\/script")}</script>`;
const inlineJson = (value: unknown) =>
  JSON.stringify(value)
    .replace(/</g, "\\u003c")
    .replace(/\u2028/g, "\\u2028")
    .replace(/\u2029/g, "\\u2029");
/** 阅读器内存分配限制无需为 Hermes 引入 TextEncoder 补丁。 */
function utf8Bytes(data: string): number {
  let bytes = 0;
  for (const point of data) {
    const code = point.codePointAt(0)!;
    bytes += code <= 0x7f ? 1 : code <= 0x7ff ? 2 : code <= 0xffff ? 3 : 4;
  }
  return bytes;
}
function verifiedImages(content: PublicContent, supplied: Record<string, string>): Record<string, string> {
  const images: Record<string, string> = Object.create(null);
  for (const resource of content.resources) {
    const data = supplied[resource.key];
    if (
      typeof data === "string" &&
      /^data:image\/(?:png|jpeg|gif|webp|svg\+xml);base64,[A-Za-z0-9+/]+={0,2}$/.test(data)
    )
      images[resource.key] = data;
  }
  if (JSON.stringify(images).length > READER_ENVELOPE_BYTES)
    throw new Error("Reader image map exceeds bounded base64 budget");
  return images;
}
export function buildReaderHtml(props: Omit<ReaderWebViewProps, "onMessage">): string {
  const content = parsePublicContent(props.content);
  if (utf8Bytes(JSON.stringify(content)) > ARTICLE_BODY_BUDGET_BYTES)
    throw new Error("Reader public body exceeds 2 MiB budget");
  if (!Number.isSafeInteger(props.generation) || props.generation < 0) throw new Error("Invalid reader generation");
  const images = verifiedImages(content, props.imageDataUris);
  const input = {
    siteOrigin: API_ORIGIN,
    content,
    generation: props.generation,
    imageDataUris: images,
    resume: resolveResume(
      props.resume?.postId === content.postId ? props.resume : null,
      props.previousDocument,
      content.document,
    ),
    annotations: matchingAnnotations(props.annotations, content),
    theme: props.theme ?? "light",
    fontScale: Math.max(0.8, Math.min(1.8, Number.isFinite(props.fontScale) ? props.fontScale! : 1)),
  };
  const data = inlineJson(input);
  if (utf8Bytes(data) > READER_ENVELOPE_BYTES) throw new Error("Reader envelope exceeds bounded base64 budget");
  const before = [
    runtime.trimRange,
    runtime.textRange,
    runtime.annotationDocument,
    "window.Prism={manual:true};",
    runtime.prism,
    runtime.mermaid,
    runtime.reader,
  ]
    .map(inlineScript)
    .join("\n");
  const mounted = inlineScript(`window.MobileReader.mount(${data});`);
  const after = [runtime.codeBlocks, runtime.postMermaid, "window.MobileReader.start();"].map(inlineScript).join("\n");
  return runtime.template
    .replace("__READER_STYLE__", () => [runtime.theme, runtime.prismCss, runtime.codeCss, runtime.css].join("\n"))
    .replace("__READER_SCRIPTS__", () => `${before}\n${mounted}\n${after}`);
}

export function ReaderWebView(props: ReaderWebViewProps) {
  const {
    content,
    generation,
    imageDataUris,
    resume,
    previousDocument,
    annotations,
    theme = "light",
    fontScale = 1,
  } = props;
  const webview = useRef<WebView<object>>(null);
  const html = useMemo(
    () => buildReaderHtml({ content, generation, imageDataUris: {}, resume, previousDocument }),
    [content, generation, resume, previousDocument],
  );
  const source = useMemo(() => ({ html }), [html]);
  const updateLayers = useCallback(() => {
    const images = verifiedImages(content, imageDataUris);
    const context = { postId: content.postId, contentVersion: content.contentVersion, generation };
    const envelope = matchingAnnotations(annotations, content);
    const settings = { theme, fontScale: Math.max(0.8, Math.min(1.8, Number.isFinite(fontScale) ? fontScale : 1)) };
    webview.current?.injectJavaScript(
      `window.MobileReader.setImages(${inlineJson(images)},${inlineJson(context)});window.MobileReader.setAnnotations(${inlineJson(envelope)});window.MobileReader.configure(${inlineJson(settings)});true;`,
    );
  }, [annotations, content, theme, fontScale, imageDataUris, generation]);
  const jumpTo = props.jumpTo;
  const jump = useCallback(() => {
    if (jumpTo)
      webview.current?.injectJavaScript(
        `window.MobileReader.jumpToSegment(${inlineJson(jumpTo.segmentId)},${inlineJson({ postId: content.postId, contentVersion: content.contentVersion, generation })});true;`,
      );
  }, [jumpTo, content.postId, content.contentVersion, generation]);
  useEffect(updateLayers, [updateLayers]);
  useEffect(jump, [jump]);
  return (
    <WebView<object>
      ref={webview}
      key={`${props.content.postId}:${props.content.contentVersion}:${props.generation}`}
      source={source}
      originWhitelist={["*"]}
      javaScriptEnabled
      domStorageEnabled={false}
      cacheEnabled={false}
      incognito
      sharedCookiesEnabled={false}
      thirdPartyCookiesEnabled={false}
      mixedContentMode="never"
      allowFileAccess={false}
      allowFileAccessFromFileURLs={false}
      allowUniversalAccessFromFileURLs={false}
      javaScriptCanOpenWindowsAutomatically={false}
      setSupportMultipleWindows={false}
      webviewDebuggingEnabled={false}
      allowsInlineMediaPlayback={false}
      mediaPlaybackRequiresUserAction
      onShouldStartLoadWithRequest={(request) => request.url === "about:blank" && request.isTopFrame !== false}
      onOpenWindow={() => {}}
      onMessage={(event) => {
        const message = validateBridgeMessage(event.nativeEvent.data, {
          ...props.content,
          generation: props.generation,
        });
        if (message) {
          if (message.type === "ready") {
            updateLayers();
            jump();
          }
          props.onMessage(message);
        }
      }}
      onError={() =>
        props.onMessage({
          type: "renderError",
          postId: props.content.postId,
          contentVersion: props.content.contentVersion,
          generation: props.generation,
          payload: { message: "READER_LOAD_FAILED" },
        })
      }
    />
  );
}
