/**
 * 把 src/config/env.js 的 MP_HOST 换成本机当前局域网 IPv4。
 *
 * 真机调试时手机访问不到电脑的 localhost，只能用局域网地址；
 * 换 WiFi / 换热点后这个地址会变，手改容易漏掉编译产物。
 *
 * 用法：npm run host:auto     （写回 env.js 并探测 8076）
 * 退出码：0 = 已写入且验证码接口可达；1 = 找不到网卡或接口不通
 */
import fs from 'node:fs'
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const envPath = path.join(here, '..', 'src', 'config', 'env.js')

// 虚拟网卡（VMware / WSL / Hyper-V）也有 IPv4，但手机连不到，必须排除
const VIRTUAL = /vmware|virtualbox|vethernet|hyper-v|wsl|docker|loopback|bluetooth|tap|tun/i
const WIRELESS = /wlan|wi-?fi|无线/i

const candidates = []
for (const [name, addrs] of Object.entries(os.networkInterfaces())) {
  if (VIRTUAL.test(name)) continue
  for (const a of addrs || []) {
    if (a.family !== 'IPv4' || a.internal) continue
    if (!/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(a.address)) continue
    candidates.push({ name, address: a.address })
  }
}

const target = candidates.find((x) => WIRELESS.test(x.name)) || candidates[0]
if (!target) {
  console.error('找不到局域网 IPv4，请先连上 WiFi 或手机热点')
  process.exit(1)
}

const host = `${target.address}:8076`
const before = fs.readFileSync(envPath, 'utf8')
const after = before.replace(/export const MP_HOST = '[^']*'/, `export const MP_HOST = '${host}'`)
if (after === before && !before.includes(host)) {
  console.error('env.js 里没有 MP_HOST 那一行，未改动')
  process.exit(1)
}
fs.writeFileSync(envPath, after)
console.log(`[${target.name}] MP_HOST → ${host}`)

const probe = (address) =>
  new Promise((resolve) => {
    const req = http.get(
      { host: address, port: 8076, path: '/api/auth/captcha', agent: false, timeout: 3000 },
      (res) => {
        let buf = ''
        res.setEncoding('utf8')
        res.on('data', (c) => { buf += c })
        res.on('end', () => {
          try {
            const body = JSON.parse(buf)
            resolve({ ok: res.statusCode === 200 && !!body?.data?.captchaId, detail: `HTTP ${res.statusCode}` })
          } catch {
            resolve({ ok: false, detail: `HTTP ${res.statusCode}，非 JSON` })
          }
        })
      }
    )
    req.on('timeout', () => req.destroy(new Error('超时')))
    req.on('error', (e) => resolve({ ok: false, detail: e.message }))
  })

const result = await probe(target.address)
if (result.ok) {
  console.log('验证码接口可达，重新编译小程序即可')
} else {
  console.error(`接口不通（${result.detail}）：确认网关 8076 已启动、手机与电脑在同一网络`)
  process.exitCode = 1
}
