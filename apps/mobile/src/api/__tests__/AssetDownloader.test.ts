import { AssetDownloader, readResponseBytes, type AssetResponse } from "../AssetDownloader";
import type { TransportOptions } from "../MobileApi";
import type { PublicResource } from "../../contracts/mobile";
const resource: PublicResource = {
  key: "image-1",
  url: "https://cdn.example/image.png",
  role: "image",
  required: true,
  mediaType: null,
  sha256: null,
};
const png = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1]);
function response(bytes: Uint8Array, headers: Record<string, string> = {}, status = 200): AssetResponse {
  let done = false;
  return {
    status,
    headers: { get: (name) => headers[name.toLowerCase()] ?? null },
    text: async () => "",
    body: {
      getReader: () => ({
        read: async () => (done ? { done: true } : { done: false, value: ((done = true), bytes) }),
        cancel: async () => {
          done = true;
        },
      }),
    },
  };
}
function bytes(text: string): Uint8Array {
  return new Uint8Array([...text].map((char) => char.charCodeAt(0)));
}
it("downloads a verified public image without business credentials", async () => {
  const requests: { url: string; options: TransportOptions }[] = [];
  const downloader = new AssetDownloader(
    async (url, options) => {
      requests.push({ url, options });
      return response(png, { "content-type": "image/png" });
    },
    async () => "a".repeat(64),
  );
  expect(await downloader.download(resource)).toEqual({
    key: "image-1",
    bytes: png,
    mediaType: "image/png",
    sha256: "a".repeat(64),
  });
  expect(requests).toEqual([
    {
      url: "https://cdn.example/image.png",
      options: {
        method: "GET",
        headers: { Accept: "image/*" },
        credentials: "omit",
        redirect: "manual",
        signal: undefined,
      },
    },
  ]);
});
it.each(["http://cdn.example/a", "file:///private/a", "https://name:pass@cdn.example/a"])(
  "refuses insecure image %s before requesting",
  async (url) => {
    const downloader = new AssetDownloader(
      async () => {
        throw new Error("must not request");
      },
      async () => "",
    );
    await expect(downloader.download({ ...resource, url })).rejects.toMatchObject({ code: "UNSAFE_RESOURCE" });
  },
);
it("follows bounded HTTPS redirects and rejects downgrade without leaking headers", async () => {
  const urls: string[] = [];
  let n = 0;
  const downloader = new AssetDownloader(
    async (url) => {
      urls.push(url);
      n++;
      return response(
        png,
        n === 1 ? { location: "https://other.example/image.png" } : { location: "http://other.example/unsafe.png" },
        302,
      );
    },
    async () => "",
  );
  await expect(downloader.download(resource)).rejects.toMatchObject({ code: "UNSAFE_RESOURCE" });
  expect(urls).toEqual(["https://cdn.example/image.png", "https://other.example/image.png"]);
});
it("does not loop through more than three redirects", async () => {
  let requests = 0;
  const downloader = new AssetDownloader(
    async () => {
      requests++;
      return response(png, { location: "/again" }, 302);
    },
    async () => "",
  );
  await expect(downloader.download(resource)).rejects.toMatchObject({ code: "RESOURCE_REDIRECT" });
  expect(requests).toBe(4);
});
it("rejects lying media types and digest mismatch instead of marking offline complete", async () => {
  const wrong = new AssetDownloader(
    async () => response(bytes("<html>login</html>"), { "content-type": "image/png" }),
    async () => "a".repeat(64),
  );
  await expect(wrong.download(resource)).rejects.toMatchObject({ code: "INVALID_RESOURCE" });
  const mismatch = new AssetDownloader(
    async () => response(png, { "content-type": "image/png" }),
    async () => "a".repeat(64),
  );
  await expect(mismatch.download({ ...resource, sha256: "b".repeat(64) })).rejects.toMatchObject({
    code: "RESOURCE_DIGEST",
  });
});
it.each([
  "<!DOCTYPE svg><svg/>",
  '<svg><image href="https://evil.invalid/\nx"/></svg>',
  '<svg><foreignObject><img srcset="https://evil.invalid/a 1x"/></foreignObject></svg>',
  "<svg><script>alert(1)</script></svg>",
  '<svg onload="alert(1)"/>',
  '<svg><image href="https://evil.invalid/x"/></svg>',
  '<svg><style>@import "https://evil.invalid"</style></svg>',
  '<svg><foreignObject><img src="https://evil.invalid/x"/></foreignObject></svg>',
  '<svg><a href="&#106;avascript:alert(1)">x</a></svg>',
])("rejects active or externally loading SVG %s", async (svg) => {
  const downloader = new AssetDownloader(
    async () => response(bytes(svg), { "content-type": "image/svg+xml" }),
    async () => "a".repeat(64),
  );
  await expect(downloader.download({ ...resource, url: "https://cdn.example/a.svg" })).rejects.toMatchObject({
    code: "UNSAFE_RESOURCE",
  });
});
it("keeps passive SVG labels only for image-context rendering", async () => {
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg"><foreignObject><div xmlns="http://www.w3.org/1999/xhtml">label</div></foreignObject></svg>';
  const downloader = new AssetDownloader(
    async () => response(bytes(svg), { "content-type": "image/svg+xml" }),
    async () => "a".repeat(64),
  );
  expect((await downloader.download(resource)).mediaType).toBe("image/svg+xml");
});
it("bounds actual bytes even without trustworthy Content-Length", async () => {
  let cancelled = false;
  let reads = 0;
  const input: AssetResponse = {
    status: 200,
    headers: { get: () => null },
    text: async () => "",
    body: {
      getReader: () => ({
        read: async () => {
          reads++;
          return { done: false, value: new Uint8Array(4) };
        },
        cancel: async () => {
          cancelled = true;
        },
      }),
    },
  };
  await expect(readResponseBytes(input, 7)).rejects.toMatchObject({ code: "RESOURCE_TOO_LARGE" });
  expect(reads).toBe(2);
  expect(cancelled).toBe(true);
});
it.each([
  ["image/jpeg", [255, 216, 255, 192, 0, 8, 8, 0, 1, 0, 1, 1]],
  ["image/gif", [71, 73, 70, 56, 57, 97, 1, 0, 1, 0]],
  [
    "image/webp",
    [82, 73, 70, 70, 0, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 88, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  ],
])("recognizes supported %s byte signatures", async (type, data) => {
  const downloader = new AssetDownloader(
    async () => response(new Uint8Array(data as number[]), { "content-type": type as string }),
    async () => "a".repeat(64),
  );
  expect(
    (await downloader.download({ ...resource, mediaType: type as string, sha256: "A".repeat(64) })).mediaType,
  ).toBe(type);
});
it.each([
  ["image/jpeg", [1, 2, 3]],
  ["image/jpeg", [255, 1, 3]],
  ["image/jpeg", [255, 216, 1]],
  ["image/gif", [1, 2, 3]],
  ["image/webp", [1, 2, 3]],
  ["image/webp", [82, 73, 70, 70, 0, 0, 0, 0, 1, 2, 3]],
  ["image/svg+xml", [60, 104, 116, 109, 108, 47, 62]],
])("rejects inconsistent %s image bytes %j", async (type, data) => {
  const downloader = new AssetDownloader(
    async () => response(new Uint8Array(data as number[]), { "content-type": type as string }),
    async () => "a".repeat(64),
  );
  await expect(downloader.download(resource)).rejects.toMatchObject({ code: "INVALID_RESOURCE" });
});
it.each(['<svg><use href="#local"/></svg>', '<svg><path style="fill:url(#gradient)"/></svg>'])(
  "allows passive local SVG references %s",
  async (svg) => {
    const downloader = new AssetDownloader(
      async () => response(bytes(svg), { "content-type": "image/svg+xml" }),
      async () => "a".repeat(64),
    );
    expect((await downloader.download(resource)).mediaType).toBe("image/svg+xml");
  },
);
it.each([
  "<svg><image href=https://evil.invalid/x/></svg>",
  '<svg><rect style="fill:url(https://evil.invalid)"/></svg>',
])("rejects nonlocal SVG loading %s", async (svg) => {
  const downloader = new AssetDownloader(
    async () => response(bytes(svg), { "content-type": "image/svg+xml" }),
    async () => "a".repeat(64),
  );
  await expect(downloader.download(resource)).rejects.toMatchObject({ code: "UNSAFE_RESOURCE" });
});
it.each(["not-a-url", "https://:password@cdn.example/image.png"])(
  "rejects malformed or credential URL %s",
  async (url) => {
    await expect(
      new AssetDownloader(
        async () => response(png),
        async () => "",
      ).download({ ...resource, url }),
    ).rejects.toMatchObject({ code: "UNSAFE_RESOURCE" });
  },
);
it("rejects unavailable resources, redirect without target, missing types and mismatched manifest type", async () => {
  for (const [input, expected] of [
    [response(png, {}, 503), "RESOURCE_UNAVAILABLE"],
    [response(png, {}, 302), "RESOURCE_REDIRECT"],
    [response(png), "INVALID_RESOURCE"],
  ] as const) {
    await expect(
      new AssetDownloader(
        async () => input,
        async () => "",
      ).download(resource),
    ).rejects.toMatchObject({ code: expected });
  }
  const downloader = new AssetDownloader(
    async () => response(png, { "content-type": "image/png" }),
    async () => "invalid-digest",
  );
  await expect(downloader.download(resource)).rejects.toMatchObject({ code: "RESOURCE_DIGEST" });
  await expect(downloader.download({ ...resource, mediaType: "image/jpeg" })).rejects.toMatchObject({
    code: "INVALID_RESOURCE",
  });
});
it("rejects oversized declared streams and absent or invalid native byte readers", async () => {
  await expect(readResponseBytes(response(png, { "content-length": "100" }), 99)).rejects.toMatchObject({
    code: "RESOURCE_TOO_LARGE",
  });
  await expect(readResponseBytes({ ...response(png), body: null }, 100)).rejects.toMatchObject({
    code: "INVALID_RESOURCE",
  });
  const invalid = {
    ...response(png),
    body: {
      getReader: () => ({
        read: async () => ({ done: false }),
        cancel: async () => {
          throw new Error("already closed");
        },
      }),
    },
  };
  await expect(readResponseBytes(invalid, 100)).rejects.toMatchObject({ code: "INVALID_RESOURCE" });
});
