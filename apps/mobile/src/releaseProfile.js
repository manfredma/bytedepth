/** 固定构建身份，不接受环境中任意指定的 API 地址。 */
function mobileRelease(channel) {
  if (channel === undefined || channel === "preview")
    return {
      channel: "preview",
      name: "ByteDepth 试用",
      applicationId: "cn.bytedepth.reader.preview",
      origin: "https://staging-bytedepth.bytedepth.cn",
      versionCode: 3,
    };
  if (channel === "production")
    return {
      channel: "production",
      name: "ByteDepth",
      applicationId: "cn.bytedepth.reader",
      origin: "https://bytedepth.cn",
      versionCode: 1,
    };
  throw new Error("Unknown mobile release channel");
}
module.exports = { mobileRelease };
