export const verificationHeaders = { "X-Bytedepth-Traffic": "verification" };

export function newVerificationContext(browser, options = {}) {
  return browser.newContext({
    ...options,
    extraHTTPHeaders: { ...options.extraHTTPHeaders, ...verificationHeaders },
  });
}
