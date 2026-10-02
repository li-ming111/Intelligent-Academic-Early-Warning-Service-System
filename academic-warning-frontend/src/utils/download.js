/**
 * 后端导出文件的下载工具（2026-10-02，W1.8）。
 *
 * <h3>为什么需要它</h3>
 * 本仓的导出接口都用 `responseType: 'blob'` 调（见 `api/client.js`：blob 响应**不做**
 * `code === 200` 检查，直接返回 `response.data`）。于是有两种失败会**静默变成"下载成功"**：
 *
 * 1. **后端回了 JSON 错误体但 HTTP 是 2xx** —— blob 里其实是一段 JSON，
 *    存成 `.xlsx` 后打不开；
 * 2. **后端回了空内容** —— 存下一个 0 字节的文件。
 *
 * 这两种都必须抛错，页面才会显示"导出失败"。判据是 blob 的 `type`：真正的 xlsx 是
 * `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，错误体是
 * `application/json`。**不看大小、不看文件名** —— 那两样都可能碰巧对上。
 */

/**
 * 把 blob 存成本地文件。
 *
 * @param {Blob} blob     后端返回的 Blob（axios 已解包）
 * @param {string} filename 保存的文件名（含扩展名）
 * @throws {Error} 当 blob 其实是错误 JSON 或内容为空时
 */
export async function saveBlobAsFile(blob, filename) {
  if (!(blob instanceof Blob)) {
    throw new Error('导出失败：响应不是文件')
  }
  const type = (blob.type || '').toLowerCase()
  if (type.includes('json') || type.includes('text/plain')) {
    throw new Error(await readErrorMessage(blob))
  }
  if (blob.size === 0) {
    throw new Error('导出失败：文件内容为空')
  }
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * 从 axios 的 reject 对象里取一句人话。
 *
 * <p>`responseType: 'blob'` 下**连非 2xx 的响应体也是 Blob**（403/500 都不例外），
 * 所以 `error.response.data.message` 是取不到的，得先把它读成文本再解析。
 */
export async function describeDownloadError(error) {
  if (!error) return '导出失败'
  const data = error.response && error.response.data
  if (data instanceof Blob) {
    return await readErrorMessage(data)
  }
  if (data && typeof data === 'object' && data.message) {
    return data.message
  }
  return error.message || '导出失败'
}

async function readErrorMessage(blob) {
  let text = ''
  try {
    text = await blob.text()
  } catch (e) {
    return '导出失败'
  }
  try {
    const body = JSON.parse(text)
    return body.message || body.msg || '导出失败'
  } catch (e) {
    // 不是 JSON（例如网关直接回的一段纯文本），截断后原样给出，别把整页 HTML 塞进提示
    return text ? text.substring(0, 100) : '导出失败'
  }
}
