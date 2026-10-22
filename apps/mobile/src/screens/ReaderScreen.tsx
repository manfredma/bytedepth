import React, { useEffect, useMemo, useRef, useState } from "react";
import { Linking, useColorScheme, useWindowDimensions, View } from "react-native";
import { ArticleRow, Button, Label, Loading, Notice, Page, Row, Sheet } from "../components/ui";
import type {
  ArticleDocument,
  PublicContent,
  ReaderBridgeMessage,
  ReaderSnapshot,
  ResumePosition,
  TocEntry,
} from "../contracts/mobile";
import { API_ORIGIN } from "../config";
import type { Navigation } from "../navigation/types";
import { useOnline, usePreferences, useRuntime, useUser } from "../runtime/Runtime";
import { errorMessage } from "../runtime/useQuery";
import { ReaderWebView } from "../reader/ReaderWebView";
import { matchingAnnotations } from "../reader/bridge";
import { resolveResume } from "../reader/resume";
import { InteractionStore } from "../interaction/InteractionStore";
import { AnnotationsPanel, CommentsPanel, RatingPanel, useInteraction } from "../interaction/panels";
import { ReadingSettings } from "./AccountScreen";
let pageGeneration = 0;
interface OpenState {
  snapshot: ReaderSnapshot;
  resume: ResumePosition | null;
  previousDocument: ArticleDocument | null;
}
export function ReaderScreen(props: { navigation: Navigation; route: { params: { postId: string } } }) {
  return <ReaderSession key={props.route.params.postId} {...props} />;
}
function ReaderSession({ navigation, route }: { navigation: Navigation; route: { params: { postId: string } } }) {
  const runtime = useRuntime();
  const postId = route.params.postId;
  const [state, setState] = useState<OpenState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [position, setPosition] = useState<ResumePosition | null>(null);
  const latest = useRef<ResumePosition | null>(null);
  const currentContent = useRef<PublicContent | null>(null);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    latest.current = null;
    currentContent.current = null;
    void (async () => {
      try {
        const recent = await runtime.reader.listRecent();
        if (!active) return;
        latest.current = recent.find((entry) => entry.postId === postId)?.position ?? null;
        setPosition(latest.current);
        for await (const snapshot of runtime.reader.open(postId)) {
          if (!active) return;
          const old = currentContent.current;
          setState((previous) => {
            if (snapshot.content && old?.representationHash !== snapshot.content.representationHash)
              return {
                snapshot,
                resume: latest.current,
                previousDocument:
                  old?.document ??
                  (latest.current?.contentVersion === snapshot.content.contentVersion
                    ? snapshot.content.document
                    : null),
              };
            return { snapshot, resume: previous?.resume ?? null, previousDocument: previous?.previousDocument ?? null };
          });
          currentContent.current = snapshot.content;
        }
      } catch (failure) {
        if (active) setError(errorMessage(failure));
      }
    })();
    return () => {
      active = false;
      runtime.reader.release(postId);
    };
  }, [runtime, postId, retry]);
  const save = (next: ResumePosition) => {
    latest.current = next;
    setPosition(next);
    void runtime.reader.savePosition(next).catch((failure) => setError(errorMessage(failure)));
  };
  if (!state)
    return (
      <Page>
        {error ? (
          <>
            <Notice message={error} />
            <Button
              title="重新打开"
              onPress={() => {
                setState(null);
                setError(null);
                setRetry((n) => n + 1);
              }}
            />
          </>
        ) : (
          <Loading />
        )}
      </Page>
    );
  const { snapshot } = state;
  if (snapshot.copyState === "UNAVAILABLE")
    return (
      <Page>
        <Label heading>文章已不可访问</Label>
        <Label muted>本机历史仍保留。</Label>
      </Page>
    );
  if (!snapshot.content)
    return (
      <Page>
        {snapshot.checkState === "CHECKING" ? (
          <Loading />
        ) : (
          <>
            <Label>此文尚未保存，需要联网后打开。</Label>
            <Button
              title="重新打开"
              onPress={() => {
                setState(null);
                setError(null);
                setRetry((n) => n + 1);
              }}
            />
          </>
        )}
      </Page>
    );
  return (
    <ArticleReader
      key={`${postId}:${snapshot.content.representationHash}`}
      state={state}
      navigation={navigation}
      onPosition={save}
      position={position}
      error={error}
    />
  );
}
function ArticleReader({
  state,
  navigation,
  onPosition,
  position,
  error,
}: {
  state: OpenState;
  navigation: Navigation;
  onPosition: (position: ResumePosition) => void;
  position: ResumePosition | null;
  error: string | null;
}) {
  const runtime = useRuntime();
  const online = useOnline();
  const user = useUser();
  const prefs = usePreferences();
  const dimensions = useWindowDimensions();
  const system = useColorScheme();
  const content = state.snapshot.content!;
  const [generation] = useState(() => ++pageGeneration);
  const [images, setImages] = useState<Record<string, string>>({});
  const [toc, setToc] = useState<TocEntry[]>([]);
  const [panel, setPanel] = useState<"toc" | "annotations" | "comments" | "rating" | "settings" | null>(null);
  const [jumpTo, setJumpTo] = useState<{ segmentId: string; request: number } | undefined>();
  const [message, setMessage] = useState<string | null>(null);
  const [store] = useState(() => new InteractionStore(runtime.api, runtime.auth, runtime.uuid, content));
  const lifetime = useMemo(() => ({ active: true, generation: 0 }), []);
  const interaction = useInteraction(store);
  const current = state.snapshot.checkState === "CURRENT" && online;
  const canReadAnnotations = online;
  useEffect(() => {
    const unsubscribe = runtime.auth.onPrivateClear(() => {
      lifetime.generation++;
      setPanel(null);
      setMessage(null);
    });
    return () => {
      lifetime.active = false;
      lifetime.generation++;
      unsubscribe();
    };
  }, [runtime, lifetime]);
  useEffect(() => {
    store.setOnline(online);
    if (canReadAnnotations) void store.loadAnnotations().catch(() => {});
  }, [store, online, canReadAnnotations, user]);
  useEffect(() => () => store.dispose(), [store]);
  useEffect(() => {
    let active = true;
    void runtime.reader.assetDataUris(content).then(
      (value) => {
        if (active) setImages(value);
      },
      () => {
        if (active) setMessage("正文可阅读，图片暂未完整保存。");
      },
    );
    return () => {
      active = false;
    };
  }, [runtime, content, state.snapshot.copyState]);
  const resume = resolveResume(state.resume, state.previousDocument, content.document);
  const resumeMessage =
    state.resume && state.resume.contentVersion !== content.contentVersion
      ? resume.kind === "heading"
        ? "正文已更新，已返回相邻章节"
        : resume.kind === "start"
          ? "正文已更新，无法确认原位置，已返回篇首。"
          : null
      : null;
  const refresh = () => {
    setMessage(null);
    const expected = lifetime.generation;
    void runtime.reader.refresh(content.postId).catch((failure) => {
      if (lifetime.active && expected === lifetime.generation) setMessage(errorMessage(failure));
    });
  };
  const handle = (event: ReaderBridgeMessage) => {
    if (
      !lifetime.active ||
      event.postId !== content.postId ||
      event.contentVersion !== content.contentVersion ||
      event.generation !== generation
    )
      return;
    const expected = lifetime.generation;
    switch (event.type) {
      case "position":
        onPosition(event.payload);
        break;
      case "toc":
        setToc(event.payload.entries);
        break;
      case "selection":
        try {
          store.select(event.payload, current);
          setPanel("annotations");
        } catch (failure) {
          setMessage(errorMessage(failure));
        }
        break;
      case "link":
        void (async () => {
          try {
            const url = new URL(event.payload.href, API_ORIGIN);
            if (url.protocol !== "https:" || url.username || url.password) throw new Error("此链接无法打开");
            if (url.origin === API_ORIGIN) {
              const match = /^\/posts\/([^/]+)$/.exec(url.pathname);
              if (match) {
                const result = await runtime.api.request<{ postId: string }>(
                  `/posts/resolve?slug=${encodeURIComponent(decodeURIComponent(match[1]))}`,
                  { method: "GET" },
                );
                if (lifetime.active && expected === lifetime.generation)
                  navigation.push("Reader", { postId: result.postId });
                return;
              }
            }
            await Linking.openURL(url.href);
          } catch (failure) {
            if (lifetime.active && expected === lifetime.generation) setMessage(errorMessage(failure));
          }
        })();
        break;
      case "renderError": {
        const match = /^(?:IMAGE_DECODE_FAILED|IMAGE_UNAVAILABLE):(.+)$/.exec(event.payload.message);
        if (match) {
          if (state.snapshot.copyState === "OFFLINE_READY")
            void runtime.reader.reportImageFailure(content.postId, match[1], content.representationHash).catch(() => {
              if (lifetime.active && expected === lifetime.generation) setMessage("图片未完整保存，请联网后刷新。");
            });
        } else setMessage("正文显示遇到问题，可尝试刷新。");
        break;
      }
      case "ready":
        break;
    }
  };
  const status =
    state.snapshot.checkState === "UPDATE_AVAILABLE"
      ? "有新版本，刷新"
      : state.snapshot.checkState === "CHECKING"
        ? "正在确认更新"
        : state.snapshot.checkState === "FAILED"
          ? "暂未确认更新"
          : state.snapshot.copyState === "OFFLINE_READY"
            ? "离线可读"
            : "正文可读，正在自动保存";
  return (
    <Page scroll={false}>
      <View style={{ paddingHorizontal: 16, paddingTop: 8 }}>
        <Label>{content.title}</Label>
        <Row>
          <Label muted>{status}</Label>
          <Button
            title={state.snapshot.checkState === "UPDATE_AVAILABLE" ? "有新版本，刷新" : "刷新"}
            disabled={!online || state.snapshot.checkState === "CHECKING"}
            onPress={refresh}
          />
        </Row>
        {state.snapshot.lastConfirmedAt && (
          <Label muted>最近确认：{state.snapshot.lastConfirmedAt.slice(0, 16).replace("T", " ")}</Label>
        )}
        <Notice message={message ?? error ?? resumeMessage} />
        {content.series && (
          <Row>
            <Button
              title={`${content.series.name} · ${content.series.position}/${content.series.total}`}
              onPress={() => navigation.navigate("Column", { columnId: content.series!.id })}
            />
            {content.series.previous && (
              <Button
                title="上一篇"
                onPress={() => navigation.replace("Reader", { postId: content.series!.previous!.id })}
              />
            )}
            {content.series.next && (
              <Button
                title="下一篇"
                onPress={() => navigation.replace("Reader", { postId: content.series!.next!.id })}
              />
            )}
          </Row>
        )}
      </View>
      <View style={{ flex: 1 }}>
        <ReaderWebView
          content={content}
          generation={generation}
          imageDataUris={images}
          resume={state.resume}
          previousDocument={state.previousDocument}
          annotations={online ? interaction.annotations : null}
          theme={prefs.theme === "dark" || (prefs.theme === "system" && system === "dark") ? "dark" : "light"}
          fontScale={prefs.fontScale * dimensions.fontScale}
          jumpTo={jumpTo}
          onMessage={handle}
        />
      </View>
      <Row style={{ justifyContent: "space-evenly" }}>
        <Button title="目录" onPress={() => setPanel("toc")} />
        <Button title="评注" onPress={() => setPanel("annotations")} />
        <Button title="评论" onPress={() => setPanel("comments")} />
        <Button title="评分" onPress={() => setPanel("rating")} />
        <Button title="设置" onPress={() => setPanel("settings")} />
      </Row>
      {position && <View accessibilityLabel={`阅读进度 ${Math.round(position.progress * 100)}%`} />}
      {panel && (
        <Sheet
          title={{ toc: "目录", annotations: "评注", comments: "评论", rating: "评分", settings: "阅读设置" }[panel]}
          onClose={() => setPanel(null)}
        >
          {panel === "toc" &&
            (toc.length ? (
              toc.map((entry) => (
                <ArticleRow
                  key={entry.segmentId}
                  title={entry.title}
                  onPress={() => {
                    setJumpTo({ segmentId: entry.segmentId, request: (jumpTo?.request ?? 0) + 1 });
                    setPanel(null);
                  }}
                />
              ))
            ) : (
              <Label muted>本文没有目录</Label>
            ))}
          {panel === "annotations" && (
            <AnnotationsPanel
              store={store}
              current={current}
              matches={!!matchingAnnotations(interaction.annotations, content)}
            />
          )}
          {panel === "comments" && <CommentsPanel store={store} />}
          {panel === "rating" && <RatingPanel store={store} />}
          {panel === "settings" && <ReadingSettings />}
        </Sheet>
      )}
    </Page>
  );
}
