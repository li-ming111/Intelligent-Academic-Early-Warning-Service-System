/**
 * 登录验证码的取用helper（W1.12，2026-10-02）。
 *
 * 改之前 `AuthController.login` 只收 username/password，所有探针脚本都是
 * 「一次 POST 拿 token」。加了服务端校验之后，**每一个**登录调用都必须先
 * `GET /api/auth/captcha` 把 `captchaId` + `captchaText` 一起带上，
 * 否则一律回 `code:500 验证码错误或已过期` —— 脚本会表现成"登录失败"，
 * 看起来像账号或服务坏了，其实只是少带两个字段。
 *
 * 所以统一从这里取，别在各脚本里各写一份（那种"同一件事多处实现"正是本仓
 * 反复出问题的形状）。
 *
 * ⚠️ 签发失败**直接抛**，不返回空字段 —— 否则会退化成"登录失败"这种误导性的
 * 症状，把"验证码接口挂了"藏起来。
 *
 * 用法：
 *   import { captchaFields } from './_captcha.mjs'
 *   body: JSON.stringify({ username, password, ...(await captchaFields(BASE)) })
 */

/**
 * @param {string} base 网关或服务基址，例如 `http://localhost:8076`
 * @returns {Promise<{captchaId: string, captcha: string}>}
 */
export async function captchaFields(base) {
  const r = await fetch(`${base}/api/auth/captcha`)
  const j = await r.json().catch(() => null)
  const d = j && j.data
  if (!d || !d.captchaId || !d.captchaText) {
    throw new Error(
      `签发验证码失败 (${base}/api/auth/captcha): HTTP=${r.status} ` +
      JSON.stringify(j).slice(0, 200))
  }
  return { captchaId: d.captchaId, captcha: d.captchaText }
}
