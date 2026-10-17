export async function submitOwnedForm(page, path, fields) {
  const result = await page.evaluate(
    async ({ path, fields }) => {
      const matchingForm = [...document.forms].find((form) => new URL(form.action, location.href).pathname === path);
      const csrf =
        matchingForm?.querySelector('input[name="_csrf"]')?.value ||
        document.querySelector('meta[name="_csrf"]')?.content;
      if (!csrf) throw new Error(`Missing CSRF token for owned article operation: ${path}`);
      const response = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ ...fields, _csrf: csrf }),
      });
      return { ok: response.ok, status: response.status, url: response.url, redirected: response.redirected };
    },
    { path, fields },
  );
  if (!result.ok) throw new Error(`Owned article operation failed: ${result.status}`);
  if (!result.redirected || new URL(result.url).pathname !== "/admin/posts")
    throw new Error(`Owned article operation has an unexpected redirect: ${new URL(result.url).pathname}`);
}

export async function updateOwnedArticle(page, article, content) {
  await page.goto(article.editPath, { waitUntil: "domcontentloaded" });
  const categoryId = await page.locator('select[name="categoryId"] option:checked').getAttribute("value");
  const path = await page.locator("form.post-editor-form").getAttribute("action");
  await submitOwnedForm(page, path, { title: article.title, content, categoryId });
  // A 200 after following a redirect alone is not evidence of a successful write.
  await page.goto(article.editPath, { waitUntil: "domcontentloaded" });
  const persisted = await page.locator("#content-editor").textContent();
  if (persisted !== content)
    throw new Error(
      `Owned article persisted content mismatch: expected ${content.length} chars, got ${persisted?.length ?? "missing"}`,
    );
}
