import { createSSRApp } from 'vue'
import App from './App.vue'

// 原来这里还有一整段 `#ifndef VUE3` 的 Vue2 启动代码（new Vue / $mount /
// uni.promisify.adaptor），在 Vue3 工程里永远不执行，已删除。
//
// 4 个自定义 tab-bar 也不在这里注册了：uni-app 的 easycom 会自动解析
// components/<name>/<name>.vue，用到哪个由页面决定，不必全局注册。
export function createApp() {
  const app = createSSRApp(App)
  return { app }
}
