<script>
import { restoreSession } from '@/composables/useUser.js'

export default {
  onLaunch() {
    // 建一次登录态快照。storage 是同步 API，各页面在 setup 里直接读即可，
    // 不依赖这个时机（历史上的"姓名闪一下"就是因为它依赖了 App 的时序）。
    restoreSession()
  }
}
</script>

<style lang="scss">
/* 这两个是**全局输出**，只在这里 import 一次。
   不要写进 uni.scss —— 那个文件会被注入到每个 SFC，放输出会被复制 N 遍。 */
@import './styles/tokens.scss';
@import './styles/common.scss';
</style>
