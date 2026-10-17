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
      return { ok: response.ok, status: response.status };
    },
    { path, fields },
  );
  if (!result.ok) throw new Error(`Owned article operation failed: ${result.status}`);
}
