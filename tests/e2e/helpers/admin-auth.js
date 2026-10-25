// 管理上下文之间仅共享会话 Cookie；阅读身份仍限于
// 各自上下文，包括同时以管理员身份认证的上下文。
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
