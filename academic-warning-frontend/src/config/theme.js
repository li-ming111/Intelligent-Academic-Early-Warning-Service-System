// 统一主题配置文件
// 角色主题色定义

// 应用深色模式 - 使用 Element Plus 原生深色模式
export const applyDarkMode = () => {
  document.documentElement.classList.add('dark')
  document.body.classList.add('dark-mode')
}

// 应用浅色模式
export const applyLightMode = () => {
  document.documentElement.classList.remove('dark')
  document.body.classList.remove('dark-mode')
}

// 根据设置应用主题模式
export const applyThemeMode = (mode) => {
  if (mode === 'dark') {
    applyDarkMode()
  } else {
    applyLightMode()
  }
}

export const themes = {
  // 学生端主题 - 绿色系
  student: {
    primary: '#4CAF50',
    primaryLight: '#81C784',
    primaryDark: '#388E3C',
    gradient: 'linear-gradient(135deg, #4CAF50 0%, #81C784 100%)',
    gradientHeader: 'linear-gradient(90deg, #4CAF50 0%, #66BB6A 50%, #4CAF50 100%)',
    text: '#2E7D32',
    background: '#E8F5E9',
    border: '#C8E6C9',
  },

  // 教师端主题 - 蓝色系
  teacher: {
    primary: '#2196F3',
    primaryLight: '#64B5F6',
    primaryDark: '#1976D2',
    gradient: 'linear-gradient(135deg, #2196F3 0%, #64B5F6 100%)',
    gradientHeader: 'linear-gradient(90deg, #2196F3 0%, #42A5F5 50%, #2196F3 100%)',
    text: '#1565C0',
    background: '#E3F2FD',
    border: '#BBDEFB',
  },

  // 辅导员端主题 - 蓝色系
  counselor: {
    primary: '#2563eb',
    primaryLight: '#60a5fa',
    primaryDark: '#1d4ed8',
    gradient: 'linear-gradient(135deg, #2563eb 0%, #60a5fa 100%)',
    gradientHeader: 'linear-gradient(90deg, #2563eb 0%, #3b82f6 50%, #2563eb 100%)',
    text: '#1e40af',
    background: '#eff6ff',
    border: '#bfdbfe',
  },

  // 管理员端主题 - 紫蓝色系
  admin: {
    primary: '#667eea',
    primaryLight: '#869af5',
    primaryDark: '#5568d3',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    gradientHeader: 'linear-gradient(90deg, #667eea 0%, #66b1ff 50%, #667eea 100%)',
    text: '#4a52b8',
    background: '#f0f2f8',
    border: '#dce0ed',
  },

  // 学院管理员端主题 - 天蓝色系
  //
  // 此前 5 号角色在两个 roleMap 里都缺席，一路兜底到 default / admin，
  // 结果学院管理员和管理员完全是同一个紫色。小程序端给的是 #0EA5E9，
  // 这里取同一组值，两端同色。
  collegeAdmin: {
    primary: '#0EA5E9',
    primaryLight: '#38BDF8',
    primaryDark: '#0284C7',
    gradient: 'linear-gradient(135deg, #0EA5E9 0%, #38BDF8 100%)',
    gradientHeader: 'linear-gradient(90deg, #0EA5E9 0%, #38BDF8 50%, #0EA5E9 100%)',
    text: '#0369A1',
    background: '#F0F9FF',
    border: '#BAE6FD',
  },

  // 默认主题
  default: {
    primary: '#667eea',
    primaryLight: '#869af5',
    primaryDark: '#5568d3',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    gradientHeader: 'linear-gradient(90deg, #667eea 0%, #66b1ff 50%, #667eea 100%)',
    text: '#4a52b8',
    background: '#f8f9fa',
    border: '#e9ecef',
  }
}

// 获取角色对应主题
export const getTheme = (role) => {
  const roleMap = {
    '1': 'student',
    '2': 'teacher',
    '3': 'counselor',
    '4': 'admin',
    '5': 'collegeAdmin',
    'student': 'student',
    'teacher': 'teacher',
    'counselor': 'counselor',
    'admin': 'admin',
    // 路由与后端用的都是 college_admin，这里一并收下，免得再有人踩同一个坑
    'collegeAdmin': 'collegeAdmin',
    'college_admin': 'collegeAdmin',
    'college-admin': 'collegeAdmin'
  }
  const themeKey = roleMap[role] || 'default'
  return themes[themeKey] || themes.default
}

// 获取当前用户角色对应的主题
export const getCurrentTheme = () => {
  const role = localStorage.getItem('role') || '1'
  return getTheme(role)
}

// Element Plus 主题色配置（CSS变量）
export const elementPlusTheme = {
  student: {
    '--el-color-primary': '#4CAF50',
    '--el-color-primary-light-3': '#66BB6A',
    '--el-color-primary-light-5': '#81C784',
    '--el-color-primary-light-7': '#A5D6A7',
    '--el-color-primary-light-8': '#C8E6C9',
    '--el-color-primary-light-9': '#E8F5E9',
    '--el-color-primary-dark-2': '#388E3C',
  },
  teacher: {
    '--el-color-primary': '#2196F3',
    '--el-color-primary-light-3': '#42A5F5',
    '--el-color-primary-light-5': '#64B5F6',
    '--el-color-primary-light-7': '#90CAF9',
    '--el-color-primary-light-8': '#BBDEFB',
    '--el-color-primary-light-9': '#E3F2FD',
    '--el-color-primary-dark-2': '#1976D2',
  },
  counselor: {
    '--el-color-primary': '#2563eb',
    '--el-color-primary-light-3': '#3b82f6',
    '--el-color-primary-light-5': '#60a5fa',
    '--el-color-primary-light-7': '#93c5fd',
    '--el-color-primary-light-8': '#bfdbfe',
    '--el-color-primary-light-9': '#eff6ff',
    '--el-color-primary-dark-2': '#1d4ed8',
  },
  admin: {
    '--el-color-primary': '#667eea',
    '--el-color-primary-light-3': '#869af5',
    '--el-color-primary-light-5': '#9eb0f7',
    '--el-color-primary-light-7': '#c5ccf9',
    '--el-color-primary-light-8': '#d8dcfb',
    '--el-color-primary-light-9': '#eef0fd',
    '--el-color-primary-dark-2': '#5568d3',
  },
  collegeAdmin: {
    '--el-color-primary': '#0EA5E9',
    '--el-color-primary-light-3': '#38BDF8',
    '--el-color-primary-light-5': '#7DD3FC',
    '--el-color-primary-light-7': '#BAE6FD',
    '--el-color-primary-light-8': '#CFEFFE',
    '--el-color-primary-light-9': '#F0F9FF',
    '--el-color-primary-dark-2': '#0284C7',
  },
}

// 获取当前角色的 Element Plus 主题变量
export const getElementPlusTheme = () => {
  const role = localStorage.getItem('role') || '1'
  const roleMap = {
    '1': 'student',
    '2': 'teacher',
    '3': 'counselor',
    '4': 'admin',
    '5': 'collegeAdmin',
    'college_admin': 'collegeAdmin',
    'collegeAdmin': 'collegeAdmin'
  }
  const themeKey = roleMap[role] || 'admin'
  return elementPlusTheme[themeKey] || elementPlusTheme.admin
}
