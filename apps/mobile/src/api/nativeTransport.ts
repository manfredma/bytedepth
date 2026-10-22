import type { AssetTransport } from "./AssetDownloader";
import { readResponseBytes } from "./AssetDownloader";
import { fetch } from "expo/fetch";
/** Expo's native transport enforces redirect:error; all API and image requests omit browser cookies. */
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
