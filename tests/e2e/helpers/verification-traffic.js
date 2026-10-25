const verificationHeaders = { "X-Bytedepth-Traffic": "verification" };

function newVerificationContext(browser, options = {}) {
  return browser.newContext({
    ...options,
    extraHTTPHeaders: { ...options.extraHTTPHeaders, ...verificationHeaders },
  });
}

// Playwright 的 .mjs 配置直接在包的 CommonJS 范围中导入此辅助模块。
module.exports = { verificationHeaders, newVerificationContext };
