import { useCallback, useEffect, useRef, useState } from "react";
import { useOnline, useRuntime } from "./Runtime";
import { MobileRequestError } from "../api/errors";
export function errorMessage(error: unknown): string {
  if (error instanceof MobileRequestError) {
    if (error.status === 409) return "正文或选区已变化，请刷新后重新选择。草稿仍保留在本页。";
    if (error.status === 422) return "这段原文无法定位，请重新选择。草稿仍保留在本页。";
    if (error.status === 429)
      return `操作较频繁，请稍后重试${error.retryAfterMs === null ? "" : `（约 ${Math.ceil(error.retryAfterMs / 1000)} 秒）`}`;
    return error.message;
  }
  return error instanceof Error ? error.message : "暂时无法完成，请联网后重试";
}
export function useQuery<T>(path: string | null, authenticated = false, identity = "") {
  const runtime = useRuntime();
  const online = useOnline();
  const [revision, reload] = useState(0);
  const [cooldown, setCooldown] = useState<{ until: number; message: string } | null>(null);
  const scope = useRef({ generation: 0, controller: null as AbortController | null });
  const [privateGeneration, setPrivateGeneration] = useState(0);
  const key = JSON.stringify([path, authenticated, identity, online, revision, privateGeneration]);
  const [state, set] = useState<{ key: string; data: T | null; error: string | null }>({
    key: "",
    data: null,
    error: null,
  });
  // 仅用户 ID 不能标识私有缓存代次；清理后同一账号可能再次登录。
  useEffect(() => {
    if (!authenticated) return;
    return runtime.auth.onPrivateClear(() => {
      scope.current.generation++;
      setPrivateGeneration((value) => value + 1);
      scope.current.controller?.abort();
      set({ key: "", data: null, error: null });
    });
  }, [runtime, authenticated, scope]);
  useEffect(() => {
    // 重连或切换账号不得跳过服务端规定的等待，也不得触发其重试。
    if (!path || !online || cooldown) return;
    let current = true;
    const controller = new AbortController();
    scope.current.controller = controller;
    const generation = scope.current.generation;
    runtime.api.request<T>(path, { method: "GET", authenticated, signal: controller.signal }).then(
      (data) => {
        if (current && generation === scope.current.generation) set({ key, data, error: null });
      },
      (error) => {
        if (current && generation === scope.current.generation) {
          if (error instanceof MobileRequestError && error.status === 429 && error.retryAfterMs !== null)
            setCooldown({ until: Date.now() + error.retryAfterMs, message: errorMessage(error) });
          set({ key, data: null, error: errorMessage(error) });
        }
      },
    );
    return () => {
      current = false;
      controller.abort();
    };
  }, [runtime, path, authenticated, online, key, scope, cooldown]);
  const latest = state.key === key;
  return {
    data: cooldown ? null : latest ? state.data : null,
    error: cooldown ? cooldown.message : latest ? state.error : null,
    loading: !!path && online && !cooldown && !latest,
    reload: useCallback(() => {
      if (!cooldown || Date.now() >= cooldown.until) {
        setCooldown(null);
        reload((n) => n + 1);
      }
    }, [cooldown]),
  };
}
