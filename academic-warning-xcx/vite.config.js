import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// 注意：这里**不要**再加 @vitejs/plugin-vue —— uni 插件内部已自带
// (@dcloudio/vite-plugin-uni 的 dependencies 里有 @vitejs/plugin-vue@5.2.4)，
// 重复注册会让 SFC 被编译两次，报错信息很难定位。
export default defineConfig({
  plugins: [uni()],
  css: {
    preprocessorOptions: {
      scss: {
        // uni-app 把 uni.scss 以 @import 的形式注入每个 SFC，所以工程里
        // 只能用 @import —— 换成 @use 会有作用域隔离，各文件看不到注入的变量。
        // 这两条弃用警告因此无法通过改代码消除，静默掉以免淹没真实报错。
        silenceDeprecations: ['import', 'legacy-js-api']
      }
    }
  },
  server: {
    // 端口选择说明（踩过坑，别改回 5173）：
    // - 5173 是 PC 端 academic-warning-frontend 的 vite 默认端口，
    //   而 testsprite/local-check 下的核查脚本是按 5173 硬编码打 PC 端的；
    // - 两边一旦都占 5173，后启动的会**静默换端口**（vite 不报错），
    //   表现为"脚本验的是另一个应用"，排查成本极高；
    // - 另外 vite 监听到本文件变化会自行重启并重新绑端口，
    //   重叠启动时容易留下孤儿进程占着端口。
    // 所以小程序端固定在 5180 这个与任何默认值都不冲突的号上。
    port: 5180,
    // 端口被占时必须报错退出，不能静默换端口（否则又回到上面那个坑）
    strictPort: true,
    // 必须显式绑所有网卡。只写 port 时 Node 会按 `localhost` 解析成 ::1，
    // 服务变成**仅 IPv6**：curl 能通（会回退到 ::1），但 Chrome 解析 localhost
    // 时若先试 127.0.0.1 就直接 ERR_CONNECTION_REFUSED，表现为
    // "页面时好时坏 / 自动化脚本连不上"，极难排查。PC 端配置里本来就有这行。
    host: true,
    // H5 开发时走 vite 代理，绕开跨域与"合法域名"校验；
    // 小程序端没有 proxy，由 services/request.js 直接指向 8076。
    proxy: {
      '/api': {
        target: 'http://localhost:8076',
        changeOrigin: true
      }
    }
  }
})
