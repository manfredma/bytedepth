import { MobileRequestError } from "../api/errors";
import packageInfo from "../../package.json";
import React, { useEffect, useRef, useState } from "react";
import {
  ArticleRow,
  Button,
  Field,
  Label,
  Loading,
  Notice,
  Page,
  Row,
  PageHeading,
  Group,
  Sheet,
} from "../components/ui";
import type { MobileAnnotation, MobilePage, RegistrationResult } from "../contracts/mobile";
import type { Navigation } from "../navigation/types";
import { useOnline, usePreferences, useRuntime, useUser } from "../runtime/Runtime";
import { errorMessage, useQuery } from "../runtime/useQuery";
export function ReadingSettings() {
  const { preferences } = useRuntime();
  const value = usePreferences();
  const [error, setError] = useState<string | null>(null);
  const save = (next: typeof value) => {
    void preferences.set(next).then(
      () => setError(null),
      (failure) => setError(errorMessage(failure)),
    );
  };
  return (
    <>
      <Label heading>阅读偏好</Label>
      <Row>
        {(["system", "light", "dark"] as const).map((theme, index) => (
          <Button
            key={theme}
            title={["跟随系统", "浅色", "深色"][index]}
            selected={value.theme === theme}
            onPress={() => save({ ...value, theme })}
          />
        ))}
      </Row>
      <Row>
        <Button
          title="缩小字号"
          disabled={value.fontScale <= 0.8}
          onPress={() => save({ ...value, fontScale: Math.max(0.8, Math.round((value.fontScale - 0.1) * 10) / 10) })}
        />
        <Label>{Math.round(value.fontScale * 100)}%</Label>
        <Button
          title="放大字号"
          disabled={value.fontScale >= 1.8}
          onPress={() => save({ ...value, fontScale: Math.min(1.8, Math.round((value.fontScale + 0.1) * 10) / 10) })}
        />
      </Row>
      <Notice message={error} />
    </>
  );
}
export function AccountScreen({ navigation }: { navigation: Navigation }) {
  const runtime = useRuntime();
  const user = useUser();
  const online = useOnline();
  const [authOpen, setAuthOpen] = useState(false);
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showOwn, setShowOwn] = useState(false);
  const [page, setPage] = useState(1);
  const own = useQuery<MobilePage<MobileAnnotation>>(
    showOwn && user ? `/me/annotations?page=${page}` : null,
    true,
    user?.id ?? "",
  );
  const scope = useRef({ generation: 0 });
  // Rate limits survive private-state clearing and reconnects; only an explicit submit after the deadline retries.
  const deadlines = useRef(new Map<"login" | "register", { until: number; error: MobileRequestError }>());
  useEffect(() => {
    const activeScope = scope.current;
    const unsubscribe = runtime.auth.onPrivateClear(() => {
      activeScope.generation++;
      setPassword("");
      setShowOwn(false);
      setMessage(null);
      setBusy(false);
    });
    return () => {
      activeScope.generation++;
      unsubscribe();
    };
  }, [runtime, scope]);
  const submit = async () => {
    const deadline = deadlines.current.get(mode);
    if (deadline && Date.now() < deadline.until) {
      setMessage(errorMessage(deadline.error));
      return;
    }
    setMessage(null);
    const pending =
      mode === "login"
        ? runtime.auth.login(username.trim(), password)
        : runtime.api.request<RegistrationResult>("/registrations", {
            method: "POST",
            body: { username: username.trim(), password },
          });
    const generation = scope.current.generation;
    setBusy(true);
    try {
      const result = await pending;
      if (generation === scope.current.generation && mode === "login") setAuthOpen(false);
      if (generation === scope.current.generation && mode === "register")
        setMessage(
          (result as RegistrationResult).status === "PENDING"
            ? "申请已提交，等待管理员审核后才能登录。"
            : "注册结果无法确认，请稍后重试",
        );
    } catch (error) {
      if (error instanceof MobileRequestError && error.status === 429 && error.retryAfterMs !== null)
        deadlines.current.set(mode, { until: Date.now() + error.retryAfterMs, error });
      if (generation === scope.current.generation) setMessage(errorMessage(error));
    } finally {
      if (generation === scope.current.generation) {
        setPassword("");
        setBusy(false);
      }
    }
  };
  return (
    <Page>
      <PageHeading title="我的" eyebrow="YOUR READING SPACE" />
      {user ? (
        <Group>
          <Label>{user.username}</Label>
          <Button
            title="退出登录"
            onPress={() => {
              void runtime.auth.logout();
            }}
          />
          <Button
            title="本人批注"
            disabled={!online}
            onPress={() => {
              setPage(1);
              setShowOwn(true);
            }}
          />
        </Group>
      ) : (
        <>
          <Label muted>游客阅读</Label>
          <Label muted>登录后可在线查看本人批注；本机历史不会上传。</Label>
          <Group>
            <Label heading>按自己的节奏阅读</Label>
            <Label muted>游客也可以阅读、自动缓存和评分。</Label>
            <Button
              title="登录网站账号"
              primary
              onPress={() => {
                setMessage(null);
                setAuthOpen(true);
              }}
            />
          </Group>
          {authOpen && (
            <Sheet
              title={mode === "login" ? "登录网站账号" : "申请账号"}
              onClose={() => {
                scope.current.generation++;
                setPassword("");
                setMessage(null);
                setBusy(false);
                setAuthOpen(false);
              }}
            >
              <Row>
                <Button
                  title="登录"
                  selected={mode === "login"}
                  onPress={() => {
                    setMode("login");
                    setMessage(null);
                  }}
                />
                <Button
                  title="申请账号"
                  selected={mode === "register"}
                  onPress={() => {
                    setMode("register");
                    setMessage(null);
                  }}
                />
              </Row>
              <Field
                accessibilityLabel="用户名"
                placeholder="用户名"
                autoCapitalize="none"
                autoCorrect={false}
                textContentType="username"
                value={username}
                onChangeText={setUsername}
              />
              <Field
                accessibilityLabel="密码"
                placeholder="密码"
                secureTextEntry
                textContentType={mode === "login" ? "password" : "newPassword"}
                value={password}
                onChangeText={setPassword}
              />
              <Button
                title={mode === "login" ? "登录账号" : "提交申请"}
                disabled={!online || busy || !username.trim() || !password}
                onPress={() => {
                  void submit();
                }}
              />
              <Notice message={message} />
              {!online && <Label muted>账号与互动功能需要联网</Label>}
              {busy && <Loading />}
            </Sheet>
          )}
        </>
      )}
      {!online && !authOpen && <Label muted>账号与互动功能需要联网</Label>}
      {showOwn && (
        <>
          <Label heading>本人批注</Label>
          {own.loading && <Loading />}
          <Notice message={own.error} />
          {own.error && <Button title="重试批注" onPress={own.reload} />}
          {own.data?.items.length === 0 && <Label muted>还没有批注</Label>}
          {own.data?.items.map((annotation) => (
            <ArticleRow
              key={annotation.id}
              title={annotation.selectedText}
              detail={annotation.annotationText}
              onPress={() => navigation.navigate("Reader", { postId: annotation.postId })}
            />
          ))}
          <Row>
            {page > 1 && <Button title="上一页" onPress={() => setPage((p) => p - 1)} />}
            {own.data?.hasNext && <Button title="下一页" onPress={() => setPage((p) => p + 1)} />}
          </Row>
        </>
      )}
      <Group>
        <ReadingSettings />
      </Group>
      <Label muted>ByteDepth 试用版 {packageInfo.version}</Label>
    </Page>
  );
}
