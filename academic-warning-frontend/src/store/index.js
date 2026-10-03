import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const user = ref(null)
  const token = ref(localStorage.getItem('token'))

  const setUser = (userData) => {
    user.value = userData
    localStorage.setItem('user', JSON.stringify(userData))
  }

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  const logout = () => {
    user.value = null
    token.value = null
    localStorage.removeItem('user')
    localStorage.removeItem('token')
    // 2026-10-03（W8.2）：`teacherId` 原先三条登出路径**一条都不清**，而它曾被
    // TeacherScores.vue 写成 '9' ⇒ 换个人登录后仍读 9 号用户的数据。现在没有写入方了，
    // 这里再清一遍，用来收拾**已经中毒的浏览器**（值已落盘，只能靠登出/覆盖)。
    localStorage.removeItem('teacherId')
  }

  return {
    user,
    token,
    setUser,
    setToken,
    logout
  }
})
