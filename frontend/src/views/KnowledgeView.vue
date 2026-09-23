<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createKnowledge,
  deleteKnowledge,
  listKnowledge,
  searchKnowledge,
  updateKnowledge,
} from '@/api/kb'

const authStore = useAuthStore()
const canWrite = computed(() => authStore.hasPermission('kb:write'))

const loading = ref(false)
const articles = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const previewQuery = ref('')
const previewHits = ref([])
const previewing = ref(false)

const form = reactive({
  title: '',
  tags: '',
  content: '',
  status: 'ENABLED',
})

onMounted(() => {
  load()
})

async function load() {
  loading.value = true
  try {
    articles.value = (await listKnowledge(keyword.value.trim() || undefined)) || []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.title = ''
  form.tags = ''
  form.content = ''
  form.status = 'ENABLED'
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.title = row.title
  form.tags = row.tags || ''
  form.content = row.content || ''
  form.status = row.status || 'ENABLED'
  dialogVisible.value = true
}

async function submit() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写标题和正文')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      tags: form.tags.trim(),
      content: form.content.trim(),
      status: form.status,
    }
    if (editingId.value) {
      await updateKnowledge(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createKnowledge(payload)
      ElMessage.success('已写入知识库')
    }
    dialogVisible.value = false
    resetForm()
    await load()
  } finally {
    submitting.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？Agent 之后将检索不到这条。`, '删除知识', { type: 'warning' })
  await deleteKnowledge(row.id)
  ElMessage.success('已删除')
  await load()
}

async function preview() {
  const q = previewQuery.value.trim()
  if (!q) {
    ElMessage.warning('请输入检索语句')
    return
  }
  previewing.value = true
  try {
    previewHits.value = (await searchKnowledge(q)) || []
  } finally {
    previewing.value = false
  }
}

function clip(text) {
  const value = (text || '').replaceAll(/\s+/g, ' ').trim()
  return value.length > 72 ? `${value.slice(0, 72)}…` : value
}
</script>

<template>
  <div class="page kb-page">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">Knowledge Base</p>
        <h2>知识库</h2>
        <p class="hero-desc">这里维护的启用文档会进入 RAG。Agent 知识官检索的就是这些条目，不是写死在代码里的说明。</p>
      </div>
    </section>

    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-head">
          <span>文档 {{ articles.length }} 篇</span>
          <div class="panel-actions">
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索标题 / 正文 / 标签"
              style="width: 240px"
              @keyup.enter="load"
              @clear="load"
            />
            <el-button @click="load">查询</el-button>
            <el-button v-if="canWrite" class="cmd-btn" type="primary" @click="openCreate">新建文档</el-button>
          </div>
        </div>
      </template>
      <div class="table-scroll">
        <el-table :data="articles" v-loading="loading" empty-text="还没有知识文档">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="摘要" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ clip(row.content) }}</template>
          </el-table-column>
          <el-table-column prop="tags" label="标签" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.tags || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</template>
          </el-table-column>
          <el-table-column v-if="canWrite" label="操作" width="148" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" text @click="openEdit(row)">编辑</el-button>
                <el-button type="danger" text @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-head">
          <span>试检索</span>
          <div class="panel-actions">
            <el-input
              v-model="previewQuery"
              clearable
              placeholder="例如：JWT 退出怎么做"
              style="width: 280px"
              @keyup.enter="preview"
            />
            <el-button class="cmd-btn" type="primary" :loading="previewing" @click="preview">检索</el-button>
          </div>
        </div>
      </template>
      <p v-if="!previewHits.length" class="muted">用 Agent 同一套 RAG 试一下，确认知识官能命中哪些文档。</p>
      <article v-for="hit in previewHits" :key="hit.title" class="hit">
        <strong>{{ hit.title }}</strong>
        <em>相关度 {{ Number(hit.score).toFixed(1) }}</em>
        <p>{{ hit.content }}</p>
      </article>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑文档' : '新建文档'"
      width="680px"
      append-to-body
      align-center
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="128" placeholder="例如：值班应急手册" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" maxlength="255" placeholder="可选，逗号分隔，例如：jwt,权限" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            maxlength="8000"
            show-word-limit
            placeholder="写给 Agent 看的说明。尽量写事实，避免含糊数字。"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="cmd-btn-ghost" @click="dialogVisible = false">取消</el-button>
        <el-button class="cmd-btn" type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.hit {
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
}

.hit:last-child {
  border-bottom: 0;
}

.hit strong {
  margin-right: 8px;
}

.hit em {
  font-style: normal;
  color: #0e7490;
  font-size: 12px;
}

.hit p {
  margin: 8px 0 0;
  color: #475569;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
