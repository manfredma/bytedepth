// Only a session cookie crosses admin contexts. Reader identities remain local to
// each context, including contexts that also authenticate as the administrator.
export function authenticationCookies(state) {
  const cookies = state.cookies.filter((cookie) => /^(SESSION|JSESSIONID)$/.test(cookie.name));
  if (cookies.length !== 1 || !cookies[0].value) throw new Error("E2E admin session cookie missing or ambiguous");
  return cookies;
}

export async function loginAsAdmin(page) {
  const serialized = process.env.E2E_ADMIN_COOKIES;
  if (!serialized)
    throw new Error("E2E admin session missing: global setup must authenticate once before workers start");
  await page.context().addCookies(authenticationCookies({ cookies: JSON.parse(serialized) }));
}
