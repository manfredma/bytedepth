const verificationHeaders = { "X-Bytedepth-Traffic": "verification" };

function newVerificationContext(browser, options = {}) {
  return browser.newContext({
    ...options,
    extraHTTPHeaders: { ...options.extraHTTPHeaders, ...verificationHeaders },
  });
}

// Playwright's .mjs configuration imports this helper directly in the package's CommonJS scope.
module.exports = { verificationHeaders, newVerificationContext };
