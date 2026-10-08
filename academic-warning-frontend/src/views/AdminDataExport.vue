<template>
  <div class="admin-data-export" style="background-color: #f8f9fa !important; min-height: 100vh;">
    <div class="page-header">
      <h1>数据导出与备份</h1>
      <p>Excel导入导出、报表生成、数据备份恢复</p>
    </div>

    <!-- Tab页签 -->
    <el-tabs type="card">
      <!-- 数据导出 -->
      <el-tab-pane label="数据导出">
        <div class="tab-content">
          <el-row :gutter="20" style="margin-bottom: 20px;">
            <el-col :xs="24" :sm="12" :md="6">
              <el-card class="export-card" shadow="hover">
                <template #header>
                  <div class="card-header">学生数据</div>
                </template>
                <p style="text-align: center; color: #666; margin-bottom: 15px;">
                  导出所有学生信息
                </p>
                <el-button type="primary" @click="exportStudents" style="width: 100%;">
                  导出Excel
                </el-button>
              </el-card>
            </el-col>

            <el-col :xs="24" :sm="12" :md="6">
              <el-card class="export-card" shadow="hover">
                <template #header>
                  <div class="card-header">成绩数据</div>
                </template>
                <p style="text-align: center; color: #666; margin-bottom: 15px;">
                  导出全部课程成绩
                </p>
                <el-button type="primary" @click="exportScores" style="width: 100%;">
                  导出Excel
                </el-button>
              </el-card>
            </el-col>

            <el-col :xs="24" :sm="12" :md="6">
              <el-card class="export-card" shadow="hover">
                <template #header>
                  <div class="card-header">预警数据</div>
                </template>
                <p style="text-align: center; color: #666; margin-bottom: 15px;">
                  导出全部预警记录
                </p>
                <el-button type="primary" @click="exportWarnings" style="width: 100%;">
                  导出Excel
                </el-button>
              </el-card>
            </el-col>

            <el-col :xs="24" :sm="12" :md="6">
              <el-card class="export-card" shadow="hover">
                <template #header>
                  <div class="card-header">用户数据</div>
                </template>
                <p style="text-align: center; color: #666; margin-bottom: 15px;">
                  导出管理人员数据
                </p>
                <el-button type="primary" @click="exportUsers" style="width: 100%;">
                  导出Excel
                </el-button>
              </el-card>
            </el-col>

            <!-- W26 新增：挂科学生清单。后端一直是有的（GET /admin/export/insufficient-students），
                 封装 adminAPI.exportInsufficientStudents 也早就写好了，只是**没有任何页面调用**。 -->
            <el-col :xs="24" :sm="12" :md="6">
              <el-card class="export-card" shadow="hover">
                <template #header>
                  <div class="card-header">挂科学生</div>
                </template>
                <p style="text-align: center; color: #666; margin-bottom: 15px;">
                  导出有不及格科目的学生清单
                </p>
                <el-button
                  type="primary"
                  @click="exportInsufficientStudents"
                  :loading="exportingInsufficient"
                  style="width: 100%;"
                >
                  导出Excel
                </el-button>
              </el-card>
            </el-col>
          </el-row>

          <!-- 导出历史 -->
          <el-card>
            <template #header>
              <div class="card-header">
                <span>导出历史</span>
                <span class="history-count">共 {{ exportHistoryTotal }} 条记录</span>
              </div>
            </template>
            
            <div v-if="exportHistory.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无导出记录
            </div>
            <el-table v-else :data="exportHistory" stripe @selection-change="handleHistorySelectionChange">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="ID" width="80" sortable></el-table-column>
              <el-table-column prop="file_name" label="文件名" min-width="180" show-overflow-tooltip></el-table-column>
              <el-table-column prop="data_type_name" label="文件名称" width="120"></el-table-column>
              <el-table-column prop="record_count" label="记录数" width="100" sortable></el-table-column>
              <el-table-column prop="file_size" label="文件大小" width="100"></el-table-column>
              <el-table-column prop="created_at" label="导出时间" width="180" sortable></el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button type="primary" size="small" @click="downloadFile(row)">
                      <el-icon><Download /></el-icon> 下载
                    </el-button>
                    <el-button type="danger" size="small" @click="deleteExport(row.id)">
                      <el-icon><Delete /></el-icon> 删除
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-footer" v-if="exportHistoryTotal > 0">
              <div class="selection-info" v-if="selectedHistory.length > 0">
                已选择 {{ selectedHistory.length }} 项
                <el-button type="primary" size="small" @click="batchDownloadHistory">批量下载</el-button>
                <el-button type="danger" size="small" @click="batchDeleteHistory">批量删除</el-button>
              </div>
              <div class="pagination-wrapper">
                <el-pagination
                  :current-page="exportHistoryPage"
                  :page-size="exportHistoryPageSize"
                  :total="exportHistoryTotal"
                  :page-sizes="[10, 20, 50, 100]"
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="handleExportHistorySizeChange"
                  @current-change="handleExportHistoryPageChange"
                />
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 数据导入 -->
      <el-tab-pane label="数据导入">
        <div class="tab-content">
          <el-row :gutter="20">
            <el-col :xs="24" :md="12">
              <el-card>
                <template #header>
                  <div class="card-header">导入学生数据</div>
                </template>

                <el-upload
                  ref="uploadStudents"
                  action="/api/admin/import/students"
                  :headers="{ Authorization: 'Bearer ' + token }"
                  :auto-upload="false"
                  accept=".xlsx,.xls"
                  @change="handleStudentFileChange"
                  style="margin-bottom: 20px;"
                >
                  <template #default>
                    <el-button>选择Excel文件</el-button>
                  </template>
                </el-upload>

                <div style="margin-bottom: 15px; padding: 10px; background: #f5f7fa; border-radius: 4px;">
                  <p style="color: #666; margin: 0;">
                    <strong>导入说明：</strong>
                  </p>
                  <ul style="margin: 5px 0; padding-left: 20px; color: #666;">
                    <li>支持Excel格式（.xlsx、.xls）</li>
                    <li>需包含：学号、姓名、班级等字段</li>
                    <li>系统将自动验证数据完整性</li>
                  </ul>
                </div>

                <el-button type="success" @click="importStudents">上传导入</el-button>
                <el-button @click="downloadTemplate('students')">下载模板</el-button>
              </el-card>
            </el-col>

            <el-col :xs="24" :md="12">
              <el-card>
                <template #header>
                  <div class="card-header">导入成绩数据</div>
                </template>

                <el-upload
                  ref="uploadScores"
                  action="/api/admin/import/scores"
                  :headers="{ Authorization: 'Bearer ' + token }"
                  :auto-upload="false"
                  accept=".xlsx,.xls"
                  @change="handleScoreFileChange"
                  style="margin-bottom: 20px;"
                >
                  <template #default>
                    <el-button>选择Excel文件</el-button>
                  </template>
                </el-upload>

                <div style="margin-bottom: 15px; padding: 10px; background: #f5f7fa; border-radius: 4px;">
                  <p style="color: #666; margin: 0;">
                    <strong>导入说明：</strong>
                  </p>
                  <ul style="margin: 5px 0; padding-left: 20px; color: #666;">
                    <li>支持Excel格式（.xlsx、.xls）</li>
                    <li>需包含：学号、课程、成绩等字段</li>
                    <li>支持批量导入（最多5000行）</li>
                  </ul>
                </div>

                <el-button type="success" @click="importScores">上传导入</el-button>
                <el-button @click="downloadTemplate('scores')">下载模板</el-button>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <!-- 报表生成 -->
      <el-tab-pane label="报表生成">
        <div class="tab-content">
          <el-card style="margin-bottom: 20px;">
            <template #header>
              <div class="card-header">自定义报表</div>
            </template>

            <el-form :model="reportForm" label-width="120px" style="max-width: 600px;">
              <el-form-item label="报表模板">
                <!-- 2026-10-04（W23）：下拉此前**只列系统模板**，而同一张卡片上的「上传说明」
                     白纸黑字写着"自定义模板可以在生成报表时使用" —— 诺言在与实现不符。
                     现在按来源分成两组，选中自定义模板时走 /admin/reports/generate-with-template
                     （那条路径本来就会按模板表头去匹配内置报表取真实数据，此前零调用方）。
                     value 前缀 system:/custom: 用来区分 —— 两边的 id 有可能同号。 -->
                <el-select v-model="templateChoice" placeholder="选择报表模板" style="width: 100%;">
                  <el-option-group v-if="reportTemplates.length > 0" label="系统模板">
                    <el-option
                      v-for="template in reportTemplates"
                      :key="'sys-' + template.id"
                      :label="template.name"
                      :value="'system:' + template.id"
                    ></el-option>
                  </el-option-group>
                  <el-option-group v-if="customTemplates.length > 0" label="自定义模板">
                    <el-option
                      v-for="template in customTemplates"
                      :key="'cus-' + template.id"
                      :label="template.name + '（自定义）'"
                      :value="'custom:' + template.id"
                    ></el-option>
                  </el-option-group>
                </el-select>
              </el-form-item>

              <el-form-item label="开始日期">
                <el-date-picker v-model="reportForm.start_date" type="date" :max-date="new Date()"></el-date-picker>
              </el-form-item>

              <el-form-item label="结束日期">
                <el-date-picker v-model="reportForm.end_date" type="date" :max-date="new Date()"></el-date-picker>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" @click="generateReport">生成报表</el-button>
                <el-button @click="resetReportForm">重置</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 模板上传 -->
          <el-card style="margin-bottom: 20px;">
            <template #header>
              <div class="card-header">上传自定义模板</div>
            </template>

            <!-- W23：这里原来写着一个 `:action="'/api/admin/export/reports/templates/upload'"`
                 —— 路径里多一段 `export`，**是错的**（第二十四轮已在 uploadTemplate() 里
                 订正过：真实地址是 /admin/reports/templates/upload，带 export 那条回 404）。
                 它之所以没暴露，是因为 :auto-upload="false"，真正上传走的是 uploadTemplate()
                 里的 apiClient.post。既然不由 el-upload 发请求，就不该留一个错的地址在这儿
                 —— 谁把 auto-upload 改成 true，就会立刻 404。 -->
            <el-upload
              ref="uploadTemplate"
              :headers="{ Authorization: 'Bearer ' + token }"
              :auto-upload="false"
              accept=".xlsx,.xls"
              :on-change="handleTemplateFileChange"
              style="margin-bottom: 15px;"
            >
              <template #default>
                <el-button>选择Excel模板文件</el-button>
              </template>
            </el-upload>

            <el-form :model="templateForm" label-width="100px" style="max-width: 500px;">
              <el-form-item label="模板名称">
                <el-input v-model="templateForm.name" placeholder="请输入模板名称"></el-input>
              </el-form-item>
              <el-form-item label="模板描述">
                <el-input v-model="templateForm.description" placeholder="请输入模板描述"></el-input>
              </el-form-item>
              <el-form-item>
                <el-button type="success" @click="uploadTemplate">上传模板</el-button>
                <el-button @click="resetTemplateForm">重置</el-button>
              </el-form-item>
            </el-form>

            <div style="padding: 10px; background: #f5f7fa; border-radius: 4px;">
              <p style="color: #666; margin: 0;">
                <strong>上传说明：</strong>
              </p>
              <ul style="margin: 5px 0; padding-left: 20px; color: #666; font-size: 12px;">
                <li>支持Excel格式（.xlsx、.xls）</li>
                <li>模板第一行将作为表头</li>
                <li>上传后在「报表模板」下拉的<b>自定义模板</b>分组里选它来生成报表</li>
                <li>表头与某张内置报表的列名<b>逐字一致</b>时，报表里会填入该报表的真实数据；认不出来时生成的报表只含表头，不会编造数据</li>
              </ul>
            </div>
          </el-card>

          <!-- 可用模板列表 -->
          <el-card>
            <template #header>
              <div class="card-header">
                <span>可用模板</span>
                <span class="template-count">共 {{ reportTemplates.length + customTemplates.length }} 个模板</span>
              </div>
            </template>

            <div v-if="reportTemplates.length === 0 && customTemplates.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无可用模板
            </div>
            
            <div v-if="reportTemplates.length > 0" style="margin-bottom: 20px;">
              <h4 style="color: #333; margin-bottom: 10px;">系统模板</h4>
              <el-row :gutter="20">
                <el-col v-for="template in reportTemplates" :key="'sys-' + template.id" :xs="24" :md="8">
                  <div style="padding: 15px; border: 1px solid #ddd; border-radius: 4px; margin-bottom: 10px;">
                    <h4 style="margin: 0 0 8px 0;">{{ template.name }}</h4>
                    <p style="margin: 0 0 10px 0; color: #666; font-size: 12px;">
                      {{ template.description }}
                    </p>
                    <div>
                      <el-tag v-for="field in template.fields" :key="field" size="small" style="margin: 2px;">
                        {{ field }}
                      </el-tag>
                      <el-tag size="small" type="info" style="margin: 2px;">系统</el-tag>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>

            <div v-if="customTemplates.length > 0">
              <h4 style="color: #333; margin-bottom: 10px;">自定义模板</h4>
              <el-row :gutter="20">
                <el-col v-for="template in customTemplates" :key="'custom-' + template.id" :xs="24" :md="8">
                  <div style="padding: 15px; border: 1px solid #409eff; border-radius: 4px; margin-bottom: 10px; background: #f8fafc;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                      <h4 style="margin: 0;">{{ template.name }}</h4>
                      <el-button type="text" size="small" @click="deleteCustomTemplate(template.id)" style="color: #f56c6c;">
                        删除
                      </el-button>
                    </div>
                    <p style="margin: 0 0 10px 0; color: #666; font-size: 12px;">
                      {{ template.description || '无描述' }}
                    </p>
                    <div>
                      <el-tag size="small" type="success" style="margin: 2px;">自定义</el-tag>
                      <el-tag size="small" style="margin: 2px;">{{ template.fileName }}</el-tag>
                      <el-tag size="small" style="margin: 2px;">{{ template.fileSize }}</el-tag>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 数据备份 -->
      <el-tab-pane label="数据备份">
        <div class="tab-content">
          <el-card style="margin-bottom: 20px;">
            <template #header>
              <div class="card-header">备份操作</div>
            </template>

            <!-- ⚠️ 这里原来写死「**系统自动每日备份**，备份时间为凌晨02:00」——
                 而后端 `/admin/backup` 与 `/admin/backups/list` 明说 `implemented:false`，
                 同一页的下方空态里就印着"本系统未实现运行时自动备份"。一句话把同一页否掉，
                 答辩时被问"备份在哪个目录"就答不上来。现在改成**由后端 implemented 决定**：
                 后端说没实现就如实说没实现，不再替它许诺。 -->
            <div style="margin-bottom: 15px; padding: 15px; background: #f0f9ff; border-radius: 4px; border-left: 4px solid #409eff;">
              <p style="margin: 0; color: #333;">{{ backupNotice }}</p>
            </div>

            <el-button type="success" size="large" @click="backupNow" style="margin-bottom: 20px;">
              立即备份数据
            </el-button>

            <!-- 备份列表 -->
            <div v-if="backupList.length === 0" style="padding: 20px; text-align: center; color: #999;">
              暂无备份记录
              <!-- 后端说明"运行时备份未实现"时如实透出，而不是让人以为"点了就会有的" -->
              <div v-if="backupNote" style="margin-top: 8px; font-size: 12px; color: #c0c4cc;">{{ backupNote }}</div>
            </div>
            <!-- ⚠️ 2026-10-02（W1.10）：这张表原来**没有 @selection-change** ——
                 勾选框只改 Element Plus 的内部状态，`selectedBackups` 永远是空数组，
                 于是 `v-if="selectedBackups.length > 0"` 包着的「批量删除」按钮
                 **一次都显示不出来**（按钮在，但没有任何路径能让它出现）。
                 导出历史那张表是接了的，备份这张被落下了。 -->
            <el-table v-else :data="backupList" stripe v-loading="backupLoading"
                      @selection-change="handleBackupSelectionChange">
              <el-table-column type="selection" width="55" />
              <el-table-column prop="id" label="ID" width="80" sortable></el-table-column>
              <el-table-column prop="name" label="备份名称" min-width="180" show-overflow-tooltip></el-table-column>
              <el-table-column prop="created_at" label="备份时间" width="180" sortable></el-table-column>
              <el-table-column prop="size" label="文件大小" width="120"></el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'success' ? 'success' : 'warning'">{{ row.status === 'success' ? '成功' : '进行中' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button type="primary" size="small" @click="restoreBackup(row.id)">
                      恢复
                    </el-button>
                    <el-button type="danger" size="small" @click="deleteBackup(row.id)">
                      <el-icon><Delete /></el-icon> 删除
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="table-footer" v-if="backupList.length > 0">
              <div class="selection-info" v-if="selectedBackups.length > 0">
                已选择 {{ selectedBackups.length }} 项
                <el-button type="danger" size="small" @click="batchDeleteBackups">批量删除</el-button>
              </div>
            </div>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 删除确认弹窗 -->
    <el-dialog v-model="deleteDialogVisible" title="确认删除" width="400px">
      <div class="delete-warning">
        <el-icon color="#F56C6C" size="20"><WarningFilled /></el-icon>
        <p>确定要删除选中的 <strong>{{ deleteCount }} 项</strong> 吗？</p>
        <p class="warning-text">此操作不可逆，删除后将无法恢复！</p>
      </div>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDelete" :loading="deleteLoading">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminAPI } from '@/api/index'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Delete, WarningFilled } from '@element-plus/icons-vue'
// W26：文件端点失败时的正确姿势 —— 见 utils/download.js 顶部那段注释（blob 响应的两种"静默成功"）。
import { saveBlobAsFile, describeDownloadError } from '@/utils/download'

// 数据
const token = localStorage.getItem('token')
const exportHistory = ref([])
/** 「导出挂科学生」按钮的加载态（W26）。 */
const exportingInsufficient = ref(false)
const reportTemplates = ref([])
const customTemplates = ref([])
const backupList = ref([])
const backupNote = ref('')
// null = 还没问到后端，说实话就是"还不知道"；由 /admin/backups/list 的 implemented 决定横幅写什么
const backupImplemented = ref(null)
const backupNotice = computed(() => {
  if (backupImplemented.value === false) {
    return '「立即备份数据」不会生成备份文件 —— 本系统未实现运行时自动备份。'
  }
  if (backupImplemented.value === true) {
    // 2026-10-02（W1.10）：后端真接上 mysqldump 了，但**没有**"每日凌晨02:00自动备份"
    // 这回事 —— 定时任务里没有它。原来那半句是替后端许诺，现在只说实话。
    return '「立即备份数据」会调用 mysqldump 生成一份 .sql 到 ' + (backupDir.value || '导出目录的 backups 子目录')
      + '。系统**没有**定时自动备份，需要留存请手动点。'
  }
  return '正在读取后端备份能力…'
})
const backupLoading = ref(false)
// 备份目录（后端 /admin/backups/list 回的真实路径）—— 横幅要能告诉人"文件在哪"
const backupDir = ref('')
const selectedHistory = ref([])
const selectedBackups = ref([])
const deleteDialogVisible = ref(false)
const deleteLoading = ref(false)
const deleteCount = ref(0)
const deleteType = ref('')
// 「单条」还是「批量」**不能拿 deleteCount 推** —— 只勾一行再点「批量删除」时
// 计数也是 1，会走进单条分支去读一个没被设置过的 id：备份那条会报"没有拿到名字"，
// 导出那条更糟，会跳过删除却照旧弹「成功删除 1 条」。所以按下哪个按钮就记哪个。
const deleteSource = ref('')
const deleteSingleId = ref(null)

// 分页相关
const exportHistoryTotal = ref(0)
const exportHistoryPage = ref(1)
const exportHistoryPageSize = ref(10)

const studentFile = ref(null)
const scoreFile = ref(null)
const templateFile = ref(null)

const reportForm = ref({
  start_date: null,
  end_date: null
})

// W23：报表模板选择。值形如 'system:1' / 'custom:5' —— 前缀用来区分来源，
// 因为系统模板 id 是 "1".."4"，而自定义模板 id 来自 report_template 表，两边可能同号。
const templateChoice = ref('')

const templateForm = ref({
  name: '',
  description: ''
})

onMounted(async () => {
  await loadExportHistory()
  await loadReportTemplates()
  await loadCustomTemplates()
  await loadBackupList()
})

// 加载导出历史
const loadExportHistory = async (page = exportHistoryPage.value, pageSize = exportHistoryPageSize.value) => {
  try {
    const response = await adminAPI.getExportHistory(page, pageSize)
    let dataList = []
    let total = 0
    
    if (response?.data?.items) {
      dataList = response.data.items
      total = response.data.total || 0
    } else if (response?.items) {
      dataList = response.items
      total = response.total || 0
    } else if (Array.isArray(response)) {
      dataList = response
      total = response.length
    } else if (response?.data && Array.isArray(response.data)) {
      dataList = response.data
      total = response.data.length
    }
    
    const formatted = dataList.map(item => ({
      id: item.id,
      file_name: item.fileName || item.file_name || '',
      data_type: item.dataType || item.data_type || '',
      data_type_name: item.dataTypeName || item.data_type_name || item.dataType || item.data_type || '',
      record_count: item.recordCount || item.record_count || 0,
      file_size: item.fileSize || item.file_size || '未知',
      created_at: item.createdAt ? new Date(item.createdAt).toLocaleString('zh-CN') : (item.created_at ? new Date(item.created_at).toLocaleString('zh-CN') : '')
    }))
    exportHistory.value = formatted
    exportHistoryTotal.value = total
    selectedHistory.value = []
  } catch (error) {
    console.error('加载导出历史失败:', error)
    exportHistory.value = []
    exportHistoryTotal.value = 0
  }
}

// 分页大小改变
const handleExportHistorySizeChange = (val) => {
  exportHistoryPageSize.value = val
  exportHistoryPage.value = 1
  loadExportHistory()
}

// 分页页码改变
const handleExportHistoryPageChange = (val) => {
  exportHistoryPage.value = val
  loadExportHistory()
}

// 导出挂科学生（W26 新增的入口；后端与封装本来就有，此前全仓零调用）
//
// ⚠️ 这里刻意用 utils/download.js 的 saveBlobAsFile，而不是本页那个 downloadFileFromBlob：
//    后者不看 blob.type，后端若回 2xx + JSON/纯文本，它会把错误提示**当成 .xlsx 存下来**
//    （用户看到"下载成功"、打开却打不开）。saveBlobAsFile 会按 type 拒绝并把 message 读出来。
//    —— 后端那一侧也一并修了（AdminController#writeFileEndpointError），两头都堵。
const exportInsufficientStudents = async () => {
  exportingInsufficient.value = true
  try {
    const blob = await adminAPI.exportInsufficientStudents()
    await saveBlobAsFile(blob, `挂科学生_${new Date().getTime()}.xlsx`)
    ElMessage.success('挂科学生清单已导出')
    await loadExportHistory()
  } catch (error) {
    console.error('导出挂科学生失败:', error)
    ElMessage.error(await describeDownloadError(error))
  } finally {
    exportingInsufficient.value = false
  }
}

// 导出学生数据
const exportStudents = async () => {
  try {
    const response = await adminAPI.exportStudents()
    if (!response || response.size === 0) {
      ElMessage.warning('暂无学生数据')
      return
    }
    
    downloadFileFromBlob(response, `学生数据_${new Date().getTime()}.xlsx`)
    ElMessage.success('学生数据已导出')
    await loadExportHistory()
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 导出成绩数据
const exportScores = async () => {
  try {
    const response = await adminAPI.exportScores()
    if (!response || response.size === 0) {
      ElMessage.warning('暂无成绩数据')
      return
    }
    
    downloadFileFromBlob(response, `成绩数据_${new Date().getTime()}.xlsx`)
    ElMessage.success('成绩数据已导出')
    await loadExportHistory()
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 导出预警数据
const exportWarnings = async () => {
  try {
    const response = await adminAPI.exportWarnings()
    if (!response || response.size === 0) {
      ElMessage.warning('暂无预警数据')
      return
    }
    
    downloadFileFromBlob(response, `预警数据_${new Date().getTime()}.xlsx`)
    ElMessage.success('预警数据已导出')
    await loadExportHistory()
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 导出用户数据
const exportUsers = async () => {
  try {
    const response = await adminAPI.exportUsers()
    if (!response || response.size === 0) {
      ElMessage.warning('暂无用户数据')
      return
    }
    
    downloadFileFromBlob(response, `用户数据_${new Date().getTime()}.xlsx`)
    ElMessage.success('用户数据已导出')
    await loadExportHistory()
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 从Blob下载文件（后端返回文件流时使用）
const downloadFileFromBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

// 下载报表文件（携带认证信息）
const downloadReportFile = async (downloadUrl) => {
  try {
    const response = await apiClient.get('/admin/export/reports/' + downloadUrl.split('/').pop(), {
      responseType: 'blob',
      headers: {
        'Authorization': 'Bearer ' + token
      }
    })
    if (response && response.size > 0) {
      const fileName = downloadUrl.split('/').pop()
      downloadFileFromBlob(response, fileName)
    } else {
      ElMessage.warning('报表文件为空')
    }
  } catch (error) {
    console.error('下载报表失败:', error)
    ElMessage.error('下载报表失败')
  }
}

// 使用 JSON 导出 Excel（前端方案，无需后端依赖）
const downloadExcel = (data, filename) => {
  try {
    if (!window.XLSX) {
      const script = document.createElement('script')
      script.src = 'https://cdnjs.cloudflare.com/ajax/libs/xlsx/0.18.5/xlsx.full.min.js'
      script.async = true
      script.onload = () => {
        exportToExcel(data, filename)
      }
      document.body.appendChild(script)
    } else {
      exportToExcel(data, filename)
    }
  } catch (error) {
    console.error('导出Excel失败:', error)
    ElMessage.error('导出Excel失败，请稍后重试')
  }
}

// 导出到 Excel 的具体逻辑
const exportToExcel = (data, filename) => {
  if (!data || data.length === 0) {
    ElMessage.error('没有数据可导出')
    return
  }

  const headers = Object.keys(data[0])
  
  const worksheetData = [
    headers,
    ...data.map(row => headers.map(header => row[header]))
  ]
  
  const worksheet = window.XLSX.utils.aoa_to_sheet(worksheetData)
  const workbook = window.XLSX.utils.book_new()
  window.XLSX.utils.book_append_sheet(workbook, worksheet, 'Sheet1')
  
  const colWidths = headers.map(h => Math.max(h.length * 2, 15))
  worksheet['!cols'] = colWidths.map(width => ({ wch: width }))
  
  window.XLSX.writeFile(workbook, filename)
}

// 文件选择处理
const handleStudentFileChange = (file) => {
  studentFile.value = file
}

const handleScoreFileChange = (file) => {
  scoreFile.value = file
}

// 导入学生数据
// 后端 /admin/import/students 收的是 multipart（`file` 字段），Excel 由服务端用 POI 解析。
// 原来这里把 el-upload 的 UploadFile 对象整个当 body 发出去（那不是文件内容，
// 是 {name,url,status,raw}），后端要的是 List<Map>，Jackson 绑不上 —— 这个按钮
// 从来没有成功过一次。改成 FormData 就对了，跟教师端那处（TeacherClassManagement.vue）一致。
const importStudents = async () => {
  const raw = studentFile.value?.raw
  if (!raw) {
    ElMessage.error('请选择文件')
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', raw)
    const res = await adminAPI.importStudents(formData)

    // 导入是**逐行尽力而为**：成功的行已经落库，失败的行在 errors 里。
    // 所以只要跳过了哪怕一行，就不能只报"导入成功" —— 那正是这个仓库一贯要避免的
    // "报告与事实不符"。把前几处原因一并说出来。
    const success = res?.success ?? 0
    const skipped = res?.skipped ?? 0
    const errorList = res?.errors || []
    if (skipped === 0) {
      ElMessage.success(`导入完成：成功 ${success} 条`)
    } else {
      const detail = errorList.slice(0, 5).map(e => `第${e.row}行：${e.reason}`).join('；')
      const more = errorList.length > 5 ? ` 等共 ${errorList.length} 处` : ''
      ElMessage.warning(`导入完成：成功 ${success} 条，跳过 ${skipped} 条。${detail}${more}`)
    }
    studentFile.value = null
  } catch (error) {
    ElMessage.error('导入失败：' + (error.response?.data?.message || error.message || '未知错误'))
  }
}

// 导入成绩数据
// 后端 /admin/import/scores 收的是 multipart（`file` 字段），Excel 由服务端用 POI 解析，
// 模板来自 /admin/import/template/scores。原来这里把 el-upload 的 UploadFile 对象
// 直接当 body 发出去，后端要的是 JSON 列表 —— 这个按钮从来没有成功过一次。
const importScores = async () => {
  const raw = scoreFile.value?.raw
  if (!raw) {
    ElMessage.error('请选择文件')
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', raw)
    const res = await adminAPI.importScores(formData)

    // 与「导入学生」同款：逐行尽力而为，跳过了行就必须说出来，
    // 不能无条件报"导入成功"（那正是本仓一贯要避免的"报告与事实不符"）。
    const success = res?.success ?? 0
    const skipped = res?.skipped ?? 0
    const errorList = res?.errors || []
    if (skipped === 0) {
      ElMessage.success(`导入完成：成功 ${success} 条`)
    } else {
      const detail = errorList.slice(0, 5).map(e => `第${e.row}行：${e.reason}`).join('；')
      const more = errorList.length > 5 ? ` 等共 ${errorList.length} 处` : ''
      ElMessage.warning(`导入完成：成功 ${success} 条，跳过 ${skipped} 条。${detail}${more}`)
    }
    scoreFile.value = null
  } catch (error) {
    ElMessage.error('导入失败：' + (error.response?.data?.message || error.message || '未知错误'))
  }
}

// 下载模板
const downloadTemplate = async (type) => {
  try {
    const response = await adminAPI.downloadTemplate(type)
    if (response) {
      const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = type === 'students' ? '学生导入模板.xlsx' : '成绩导入模板.xlsx'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      ElMessage.success('模板下载成功')
    }
  } catch (error) {
    console.error('下载模板失败:', error)
    ElMessage.error('下载模板失败')
  }
}

// 加载自定义模板
const loadCustomTemplates = async () => {
  try {
    const response = await adminAPI.getCustomReportTemplates()
    if (Array.isArray(response)) {
      customTemplates.value = response
    } else if (response?.data && Array.isArray(response.data)) {
      customTemplates.value = response.data
    }
  } catch (error) {
    console.error('加载自定义模板失败:', error)
    customTemplates.value = []
  }
}

// 模板文件选择处理
const handleTemplateFileChange = (file) => {
  templateFile.value = file.raw || file
}

// 上传模板
const uploadTemplate = async () => {
  if (!templateFile.value) {
    ElMessage.error('请选择要上传的模板文件')
    return
  }
  if (!templateForm.value.name) {
    ElMessage.error('请输入模板名称')
    return
  }

  try {
    const formData = new FormData()
    formData.append('file', templateFile.value)
    formData.append('name', templateForm.value.name)
    if (templateForm.value.description) {
      formData.append('description', templateForm.value.description)
    }

    // 2026-10-01 第二十四轮：原路径多了一段 export（/admin/**export**/reports/templates/upload），
    // 而后端映射是 AdminController 的 @PostMapping("/reports/templates/upload") ⇒ 真实地址
    // /admin/reports/templates/upload。实测两条路对照：带 export 那条回
    // 404「请求路径不存在」，去掉后能碰到 handler ⇒ 模板上传此前必然失败。
    const response = await apiClient.post('/admin/reports/templates/upload', formData, {
      headers: {
        'Authorization': 'Bearer ' + token,
        'Content-Type': 'multipart/form-data'
      }
    })

    if (response && response.message) {
      ElMessage.success(response.message)
      await loadCustomTemplates()
      resetTemplateForm()
    }
  } catch (error) {
    console.error('上传模板失败:', error)
    ElMessage.error('上传模板失败: ' + (error.response?.data?.message || error.message))
  }
}

// 重置模板表单
const resetTemplateForm = () => {
  templateForm.value = {
    name: '',
    description: ''
  }
  templateFile.value = null
  const uploadRef = document.querySelector('.el-upload')
  if (uploadRef && uploadRef.querySelector('.el-upload__tip')) {
    uploadRef.querySelector('.el-upload__tip')?.remove()
  }
}

// 删除自定义模板
const deleteCustomTemplate = async (templateId) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这个模板吗？',
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await adminAPI.deleteCustomTemplate(templateId)
    if (response) {
      ElMessage.success('模板删除成功')
      await loadCustomTemplates()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除模板失败:', error)
      ElMessage.error('删除模板失败')
    }
  }
}

// 加载报表模板
const loadReportTemplates = async () => {
  try {
    const response = await adminAPI.getReportTemplates()
    if (Array.isArray(response)) {
      reportTemplates.value = response
    }
  } catch (error) {
    console.error('加载报表模板失败:', error)
  }
}

// 生成报表
const generateReport = async () => {
  if (!templateChoice.value) {
    ElMessage.error('请选择报表模板')
    return
  }
  if (!reportForm.value.start_date || !reportForm.value.end_date) {
    ElMessage.error('请选择时间范围')
    return
  }

  // 日期校验
  const startDate = new Date(reportForm.value.start_date)
  const endDate = new Date(reportForm.value.end_date)
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  if (startDate > endDate) {
    ElMessage.error('开始日期不能大于结束日期')
    return
  }

  try {
    const [kind, templateId] = templateChoice.value.split(':')
    const range = {
      start_date: reportForm.value.start_date,
      end_date: reportForm.value.end_date
    }
    // W23：系统模板走 /reports/generate（白名单 1-4），自定义模板走
    // /reports/generate-with-template（按表头匹配内置报表取数）。两条路后端都会回 rows。
    const response = kind === 'custom'
      ? await adminAPI.generateReportWithCustomTemplate({ ...range, custom_template_id: templateId })
      : await adminAPI.generateReport({ ...range, template_id: templateId })
    console.log('报表生成响应:', response)

    // 按后端**真的写了几行**来提示。此前无论文件里有没有数据行，一律弹「报表已生成」——
    // 数据源为空时下载到的是一份只有表头的 xlsx，用户不会知道。
    const rows = typeof response?.rows === 'number' ? response.rows : null
    let note
    if (response?.matched === false) {
      note = '报表已生成：模板表头没有匹配上任何内置报表，文件中只含表头'
    } else if (rows === 0) {
      note = '报表已生成：该报表当前没有数据行，文件中只含表头'
    } else if (rows !== null) {
      note = `报表已生成（${rows} 行数据）`
    } else {
      note = '报表已生成'
    }

    if (response && response.downloadUrl) {
      await downloadReportFile(response.downloadUrl)
      ElMessage.success(note + '，已开始下载')
    } else if (response && response.filePath) {
      await downloadReportFile(response.filePath)
      ElMessage.success(note + '，已开始下载')
    } else {
      ElMessage.success(note)
    }
    resetReportForm()
  } catch (error) {
    ElMessage.error('生成报表失败: ' + (error.response?.data?.message || error.message))
  }
}

// 重置报表表单
const resetReportForm = () => {
  reportForm.value = {
    start_date: null,
    end_date: null
  }
  templateChoice.value = ''
}

// 备份数据（2026-10-02 W1.10：后端真的起 mysqldump 了）
const backupNow = async () => {
  backupLoading.value = true
  try {
    const result = await adminAPI.backupData()
    // 只报后端真给的文件名与大小，不编"已完成"
    if (result?.name) {
      ElMessage.success(`备份已生成：${result.name}（${result.size}）`)
    } else {
      ElMessage.success('备份已完成')
    }
    await loadBackupList()
  } catch (error) {
    // ⚠️ 别把后端原话抹平成"备份失败"：工具缺失/命令失败时后端回的是
    // "无法启动命令 …mysqldump.exe（路径不对或不在 PATH 上？…）" 这类**可操作**的原因。
    // 抹平之后管理员只会以为"网络抖了一下，再点一次"。
    ElMessage.error('备份未完成：' + (error?.message || '请求失败'))
  } finally {
    backupLoading.value = false
  }
}

// 加载备份列表
const loadBackupList = async () => {
  backupLoading.value = true
  try {
    const response = await adminAPI.getBackupList()
    // ⚠️ `/admin/backups/list` 回的是 {total, items}（不是裸数组）；原来只判 Array.isArray
    // → 备份列表恒空。后端现在如实返回空列表 + implemented:false（不再编造三条假备份）。
    const list = Array.isArray(response) ? response
      : Array.isArray(response?.items) ? response.items
      : []
    // 后端回的是 `created_at`（字符串，已经格式化过）。原来只认 `createdAt`，
    // 于是每一行的"备份时间"都是空 —— 现在两种都认。
    backupList.value = list.map(item => ({
      ...item,
      created_at: item.created_at
        || (item.createdAt ? new Date(item.createdAt).toLocaleString('zh-CN') : '')
    }))
    backupNote.value = response?.note || ''
    backupDir.value = response?.dir || ''
    if (response && response.implemented === false && !backupNote.value) {
      backupNote.value = '本系统未实现运行时自动备份'
    }
    // 横幅跟着后端走（认布尔、也认 "false" 字符串）
    if (response && response.implemented !== undefined) {
      backupImplemented.value = !(response.implemented === false || response.implemented === 'false')
    }
  } catch (error) {
    console.error('加载备份列表失败:', error)
  } finally {
    backupLoading.value = false
  }
}

// 恢复备份
/**
 * 恢复**演练**（2026-10-02 W1.10）。
 *
 * <p>原来这里写的是「数据恢复需5-10分钟，恢复期间系统将暂时不可用」+「数据恢复中，请稍候...」
 * —— 而后端那时直接回"未实现"。现在后端会**在一个临时库上真把备份灌一遍**并校验表数，
 * 然后删掉临时库：这是"这份备份可用"的证据，**当前演示库不会被覆盖**（刻意的保护）。
 * 所以提示语必须照实说，不能让人以为线上数据被回滚了。
 */
const restoreBackup = async (backupId) => {
  try {
    const result = await adminAPI.restoreBackup(backupId)
    ElMessageBox.alert(
      `已在临时库 ${result?.scratchDb || '(临时库)'} 上完整恢复并校验（${result?.tableCount ?? '?'} 张表），随后已删除该临时库。`
      + '\n\n当前数据库**没有被覆盖** —— 这是刻意的保护，避免误操作清掉演示数据。',
      '恢复演练完成',
      { confirmButtonText: '知道了', type: 'success' }
    )
  } catch (error) {
    ElMessage.error('恢复演练失败：' + (error?.message || '请求失败'))
  }
}

// 删除备份
const deleteBackup = (backupId) => {
  deleteType.value = 'backup'
  deleteCount.value = 1
  // 备份的 id 就是文件名（后端 /admin/backups/list 的约定），确认时要用它拼 URL
  deleteSource.value = 'single'
  deleteSingleId.value = backupId
  deleteDialogVisible.value = true
}

// 处理导出历史选择变化
const handleHistorySelectionChange = (val) => {
  selectedHistory.value = val
}

// 处理备份选择变化（2026-10-02 W1.10：这张表原来漏了这个绑定，见模板里的注释）
const handleBackupSelectionChange = (val) => {
  selectedBackups.value = val
}

// 删除导出记录
const deleteExport = (exportId) => {
  deleteType.value = 'export'
  deleteCount.value = 1
  deleteDialogVisible.value = true
  // 临时保存要删除的单个ID
  deleteSource.value = 'single'
  deleteSingleId.value = exportId
}

// 批量下载导出记录
const batchDownloadHistory = async () => {
  if (selectedHistory.value.length === 0) {
    ElMessage.warning('请先选择要下载的记录')
    return
  }
  
  const loading = ElLoading.service({ text: '正在下载文件...' })
  
  try {
    for (let i = 0; i < selectedHistory.value.length; i++) {
      const item = selectedHistory.value[i]
      await downloadFile(item)
      if (i < selectedHistory.value.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 500))
      }
    }
    ElMessage.success(`成功下载 ${selectedHistory.value.length} 个文件`)
  } catch (error) {
    console.error('批量下载失败:', error)
    ElMessage.error('下载失败')
  } finally {
    loading.close()
  }
}

// 批量删除导出记录
const batchDeleteHistory = () => {
  if (selectedHistory.value.length === 0) {
    ElMessage.warning('请先选择要删除的记录')
    return
  }
  deleteType.value = 'export'
  deleteCount.value = selectedHistory.value.length
  deleteSource.value = 'batch'
  deleteDialogVisible.value = true
}

// 批量删除备份
const batchDeleteBackups = () => {
  if (selectedBackups.value.length === 0) {
    ElMessage.warning('请先选择要删除的备份')
    return
  }
  deleteType.value = 'backup'
  deleteCount.value = selectedBackups.value.length
  deleteSource.value = 'batch'
  deleteDialogVisible.value = true
}

// 确认删除
const confirmDelete = async () => {
  deleteLoading.value = true
  try {
    if (deleteType.value === 'export') {
      // 判据是「按下哪个按钮」，不是「数了几条」（见 deleteSource 的注释）
      const ids = deleteSource.value === 'single'
        ? [deleteSingleId.value]
        : selectedHistory.value.map(item => item.id)
        .map(id => (typeof id === 'number' ? id : parseInt(id, 10)))
      if (!ids[0]) {
        // 拿不到 id 就说拿不到 —— **不许跳过删除再弹"成功"**（本仓踩过的"假成功"）
        ElMessage.error('没有拿到要删除的记录 id')
        deleteDialogVisible.value = false
        return
      }
      await Promise.all(ids.map(id => adminAPI.deleteExport(id)))
      deleteSingleId.value = null
      selectedHistory.value = []
      ElMessage.success(`成功删除 ${ids.length} 条导出记录`)
      await loadExportHistory()
    } else if (deleteType.value === 'backup') {
      // 2026-10-02（W1.10）：原来是 `ElMessage.info('删除备份功能开发中...')` 的空壳。
      // 现在备份是磁盘上的真文件，删除就是真删（后端拿文件名删，带路径穿越校验）。
      // 单条走行内按钮存的 id，多条走表格勾选 —— 两条路径都得通。
      const ids = deleteSource.value === 'single'
        ? [deleteSingleId.value]
        : selectedBackups.value.map(item => item.id)
      if (!ids[0]) {
        ElMessage.error('没有拿到要删除的备份名')
        deleteDialogVisible.value = false
        return
      }
      await Promise.all(ids.map(id => adminAPI.deleteBackup(id)))
      deleteSingleId.value = null
      selectedBackups.value = []
      ElMessage.success(`已删除 ${ids.length} 个备份文件`)
      await loadBackupList()
    }
    deleteDialogVisible.value = false
  } catch (error) {
    ElMessage.error('删除失败')
  } finally {
    deleteLoading.value = false
  }
}

// 下载文件
const downloadFile = async (file) => {
  try {
    let response
    const fileName = file.file_name || file.fileName
    const dataType = file.data_type || file.dataType
    
    switch(dataType) {
      case '学生数据':
      case 'students':
        response = await adminAPI.exportStudents()
        break
      case '成绩数据':
      case 'scores':
        response = await adminAPI.exportScores()
        break
      case '预警数据':
      case 'warnings':
        response = await adminAPI.exportWarnings()
        break
      case '用户数据':
      case 'users':
        response = await adminAPI.exportUsers()
        break
      case 'colleges':
        response = await adminAPI.exportColleges()
        break
      case 'teachers':
        response = await adminAPI.exportTeachers()
        break
      default:
        ElMessage.error('未知的数据类型')
        return
    }
    
    if (!response || response.size === 0) {
      ElMessage.warning('数据已删除或不存在')
      return
    }
    
    downloadFileFromBlob(response, fileName)
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败')
  }
}
</script>

<style scoped>
.admin-data-export {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #333;
}

.page-header p {
  margin: 0;
  color: #999;
  font-size: 14px;
}

.tab-content {
  padding: 20px 0;
}

.card-header {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.history-count, .template-count {
  font-size: 14px;
  color: #909399;
  font-weight: normal;
}

.export-card {
  height: 100%;
}

.table-footer {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selection-info {
  color: #606266;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.delete-warning {
  text-align: center;
  padding: 20px;
}

.delete-warning p {
  margin: 10px 0;
  color: #606266;
}

.delete-warning .warning-text {
  color: #F56C6C;
  font-size: 14px;
}

.admin-data-export :deep(.el-card) {
  border: 1px solid #e9ecef !important;
}

.admin-data-export :deep(.el-button--primary) {
  background-color: #667eea !important;
  border-color: #667eea !important;
}

.admin-data-export :deep(.el-button--primary:hover) {
  background-color: #5568d3 !important;
  border-color: #5568d3 !important;
}

.admin-data-export :deep(.el-button--danger) {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
}

.admin-data-export :deep(.el-button--danger:hover) {
  background-color: #e64141 !important;
  border-color: #e64141 !important;
}

.action-buttons {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.action-buttons .el-button {
  margin: 0;
}
</style>