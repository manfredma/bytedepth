import React, { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Alert, Text, View } from "react-native";
import { Button, Field, Label, Loading, Notice, Row } from "../components/ui";
import { useOnline, useRuntime, useUser } from "../runtime/Runtime";
import { errorMessage } from "../runtime/useQuery";
import type { InteractionStore } from "./InteractionStore";
import type { AnnotationVisibility, MobileAnnotation } from "../contracts/mobile";
const annotationColors: Record<string, string> = {
  blue: "#315efb",
  yellow: "#b7791f",
  green: "#25855a",
  red: "#ca5b56",
};
const annotationNames: Record<string, string> = {
  blue: "补充说明",
  yellow: "重点摘录",
  green: "实践结论",
  red: "疑问待办",
};
export function useInteraction(store: InteractionStore) {
  return useSyncExternalStore(store.subscribe, store.snapshot);
}
function usePanelAction() {
  const { auth } = useRuntime();
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const epoch = useMemo(() => ({ generation: 0 }), []);
  useEffect(() => {
    const unsubscribe = auth.onPrivateClear(() => {
      epoch.generation++;
      setMessage(null);
      setBusy(false);
    });
    return () => {
      epoch.generation++;
      unsubscribe();
    };
  }, [auth, epoch]);
  const run = useCallback(
    async (action: () => Promise<void>) => {
      const generation = epoch.generation;
      setBusy(true);
      setMessage(null);
      try {
        await action();
      } catch (error) {
        if (generation === epoch.generation) setMessage(errorMessage(error));
      } finally {
        if (generation === epoch.generation) setBusy(false);
      }
    },
    [epoch],
  );
  return { message, busy, run };
}
export function RatingPanel({ store }: { store: InteractionStore }) {
  const state = useInteraction(store);
  const online = useOnline();
  const { run, busy, message } = usePanelAction();
  useEffect(() => {
    if (online) void run(() => store.loadRating());
  }, [store, online, run]);
  return (
    <>
      <Label heading>为这篇文章评分</Label>
      <Label muted>无需登录，同一次安装可以改分。</Label>
      {state.rating && (
        <Label>
          {state.rating.averageScore.toFixed(1)} 分 · {state.rating.ratingCount} 人评分
        </Label>
      )}
      <Row>
        {([1, 2, 3, 4, 5] as const).map((score) => (
          <Button
            key={score}
            title={`${score} 分`}
            selected={state.rating?.visitorScore === score}
            disabled={!online || busy}
            onPress={() => {
              void run(() => store.rate(score));
            }}
          />
        ))}
      </Row>
      <Notice message={message} />
      {busy && <Loading />}
    </>
  );
}
export function CommentsPanel({ store }: { store: InteractionStore }) {
  const state = useInteraction(store);
  const runtime = useRuntime();
  const user = useUser();
  const online = useOnline();
  const [text, setText] = useState("");
  const [page, setPage] = useState(1);
  const { run, busy, message } = usePanelAction();
  useEffect(() => runtime.auth.onPrivateClear(() => setText("")), [runtime]);
  useEffect(() => {
    if (online) void run(() => store.loadComments());
  }, [store, online, run]);
  const allowed = !!user?.permissions.includes("blog:comment:create");
  return (
    <>
      <Label heading>文章评论</Label>
      {state.comments.length === 0 && !busy && <Label muted>暂无评论</Label>}
      {state.comments.map((comment) => (
        <React.Fragment key={comment.id}>
          <Label>{comment.content}</Label>
          <Label muted>
            {comment.username} · {comment.createdAt.slice(0, 10)}
          </Label>
        </React.Fragment>
      ))}
      {state.hasMoreComments && (
        <Button
          title="更多评论"
          disabled={busy || !online}
          onPress={() => {
            const next = page + 1;
            void run(async () => {
              await store.loadComments(next);
              setPage(next);
            });
          }}
        />
      )}
      <Label muted>
        {!online ? "评论需要联网" : !user ? "登录后可评论" : !allowed ? "当前账号没有评论权限" : "分享你的理解"}
      </Label>
      <Field
        accessibilityLabel="评论内容"
        placeholder="评论"
        multiline
        maxLength={2000}
        editable={online && allowed}
        value={text}
        onChangeText={setText}
      />
      <Button
        title="发表评论"
        disabled={!online || !allowed || busy || !text.trim()}
        onPress={() => {
          void run(async () => {
            await store.comment(text);
            setText("");
            setPage(1);
          });
        }}
      />
      <Notice message={message} />
      {busy && <Loading />}
    </>
  );
}
export function AnnotationEditor({
  text,
  visibility,
  color,
  onChange,
  onSave,
  onCancel,
  busy,
  allowColor = true,
}: {
  text: string;
  visibility: AnnotationVisibility;
  color: string;
  onChange: (text: string, visibility: AnnotationVisibility, color: string) => void;
  onSave: () => void;
  onCancel: () => void;
  busy: boolean;
  allowColor?: boolean;
}) {
  return (
    <>
      <Field
        accessibilityLabel="评注文字"
        placeholder="写下评注（可留空，仅划线）"
        multiline
        maxLength={2000}
        value={text}
        onChangeText={(value) => onChange(value, visibility, color)}
      />
      <Row>
        {allowColor &&
          (["blue", "yellow", "green", "red"] as const).map((value, index) => (
            <Button
              key={value}
              title={["补充说明", "重点摘录", "实践结论", "疑问待办"][index]}
              selected={color === value}
              color={annotationColors[value]}
              onPress={() => onChange(text, visibility, value)}
            />
          ))}
      </Row>
      <Row>
        <Button title="仅自己" selected={visibility === "PRIVATE"} onPress={() => onChange(text, "PRIVATE", color)} />
        <Button title="公开" selected={visibility === "PUBLIC"} onPress={() => onChange(text, "PUBLIC", color)} />
      </Row>
      <Row>
        <Button title="取消编辑" onPress={onCancel} />
        <Button title="保存评注" disabled={busy} onPress={onSave} />
      </Row>
    </>
  );
}
function AnnotationRow({ annotation, store }: { annotation: MobileAnnotation; store: InteractionStore }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(annotation.annotationText);
  const [visibility, setVisibility] = useState(annotation.visibility);
  const { run, busy, message } = usePanelAction();
  return (
    <View
      style={{
        borderLeftWidth: 2,
        borderLeftColor: annotationColors[annotation.color],
        paddingLeft: 12,
        paddingVertical: 8,
        marginVertical: 8,
      }}
    >
      <View style={{ borderBottomWidth: 0.5, borderBottomColor: annotationColors[annotation.color], paddingBottom: 4 }}>
        <Label>{annotation.selectedText}</Label>
      </View>
      {editing ? (
        <AnnotationEditor
          text={text}
          visibility={visibility}
          color={annotation.color}
          allowColor={false}
          onChange={(next, scope) => {
            setText(next);
            setVisibility(scope);
          }}
          busy={busy}
          onCancel={() => {
            setText(annotation.annotationText);
            setVisibility(annotation.visibility);
            setEditing(false);
          }}
          onSave={() => {
            void run(async () => {
              await store.update(annotation, text, visibility);
              setEditing(false);
            });
          }}
        />
      ) : (
        <>
          <Label muted>{annotation.annotationText || "划线摘录"}</Label>
          <Text style={{ color: annotationColors[annotation.color], fontSize: 14, lineHeight: 22 }}>
            {annotationNames[annotation.color]} · {annotation.visibility === "PRIVATE" ? "仅自己" : "公开"} ·{" "}
            {annotation.createdAt.slice(0, 10)}
          </Text>
          {annotation.ownedByCurrentVisitor && (
            <Row>
              <Button
                title="编辑评注"
                onPress={() => {
                  setText(annotation.annotationText);
                  setVisibility(annotation.visibility);
                  setEditing(true);
                }}
              />
              <Button
                title="删除评注"
                onPress={() =>
                  Alert.alert("删除这条评注？", "删除后无法恢复。", [
                    { text: "取消", style: "cancel" },
                    {
                      text: "删除",
                      style: "destructive",
                      onPress: () => {
                        void run(() => store.remove(annotation));
                      },
                    },
                  ])
                }
              />
            </Row>
          )}
        </>
      )}
      <Notice message={message} />
    </View>
  );
}
export function AnnotationsPanel({
  store,
  matches,
  current,
}: {
  store: InteractionStore;
  matches: boolean;
  current: boolean;
}) {
  const state = useInteraction(store);
  const user = useUser();
  const online = useOnline();
  const { run, busy, message } = usePanelAction();
  useEffect(() => {
    if (online) void run(() => store.loadAnnotations());
  }, [store, online, user, run]);
  return (
    <>
      <Label heading>阅读评注</Label>
      {!matches && state.annotations && (
        <Label muted>批注对应当前新版，刷新正文后才能显示划线；以下保留原始引用。</Label>
      )}
      <Label muted>
        {!online
          ? "评注需要联网"
          : !user
            ? "登录后长按原文，选择文字可新建评注"
            : !current
              ? "请先确认更新，再新建划线或评注"
              : "长按原文，选择文字即可新建评注"}
      </Label>
      {state.annotations?.annotations.length === 0 && <Label muted>暂无评注</Label>}
      {state.annotations?.annotations.map((annotation) => (
        <AnnotationRow key={annotation.id} annotation={annotation} store={store} />
      ))}
      {state.draft && (
        <>
          <Label>{state.draft.anchor.exact}</Label>
          <AnnotationEditor
            text={state.draft.annotationText}
            visibility={state.draft.visibility}
            color={state.draft.color}
            onChange={(text, visibility, color) => store.editDraft(text, visibility, color)}
            busy={busy || !online || !current}
            onCancel={() => store.cancelDraft()}
            onSave={() => {
              void run(() => store.saveDraft());
            }}
          />
        </>
      )}
      <Notice message={message} />
      {busy && <Loading />}
    </>
  );
}
