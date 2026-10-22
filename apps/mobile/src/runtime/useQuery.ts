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
  const retryAt = useRef(0);
  const key = JSON.stringify([path, authenticated, identity, online, revision]);
  const [state, set] = useState<{ key: string; data: T | null; error: string | null }>({
    key: "",
    data: null,
    error: null,
  });
  useEffect(() => {
    if (!path || !online) return;
    let current = true;
    const controller = new AbortController();
    runtime.api.request<T>(path, { method: "GET", authenticated, signal: controller.signal }).then(
      (data) => {
        if (current) set({ key, data, error: null });
      },
      (error) => {
        if (current) {
          if (error instanceof MobileRequestError && error.status === 429 && error.retryAfterMs !== null)
            retryAt.current = Date.now() + error.retryAfterMs;
          set({ key, data: null, error: errorMessage(error) });
        }
      },
    );
    return () => {
      current = false;
      controller.abort();
    };
  }, [runtime, path, authenticated, online, key]);
  const latest = state.key === key;
  return {
    data: latest ? state.data : null,
    error: latest ? state.error : null,
    loading: !!path && online && !latest,
    reload: useCallback(() => {
      if (Date.now() >= retryAt.current) reload((n) => n + 1);
    }, []),
  };
}
