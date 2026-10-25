import type { AssetTransport } from "./AssetDownloader";
import { readResponseBytes } from "./AssetDownloader";
import { fetch } from "expo/fetch";
/** Expo 原生传输强制使用 redirect:error；所有 API 和图片请求均不携带浏览器 Cookie。 */
export const nativeTransport: AssetTransport = async (url, options) => {
  const response = await fetch(url, options);
  return {
    status: response.status,
    headers: response.headers,
    body: response.body,
    text: async () =>
      new TextDecoder("utf-8", { fatal: true }).decode(await readResponseBytes(response, 16 * 1024 * 1024)),
  };
};
