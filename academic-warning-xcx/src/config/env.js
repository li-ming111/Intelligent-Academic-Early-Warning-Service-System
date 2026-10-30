/**
 * 小程序 / App 端直连网关的地址。
 *
 * 真机调试时手机上的 `localhost` 指手机自己，必须写电脑的局域网 IP（ipconfig 里的 IPv4）。
 * 换网络（不同 WiFi / 热点）后这个地址会变，只需要改这一行。
 * H5 端不走这里：开发期由 vite.config.js 的 proxy 转发 /api。
 */
export const MP_HOST = '172.20.10.2:8076'
