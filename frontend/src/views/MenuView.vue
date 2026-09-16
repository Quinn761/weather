<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMenuStore } from '@/stores/menu'
import { useAuthStore } from '@/stores/auth'

const menuStore = useMenuStore()
const authStore = useAuthStore()
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const canWrite = computed(() => authStore.hasPermission('menu:write'))

const typeLabel = {
  DIR: '目录',
  MENU: '页面',
  BUTTON: '按钮',
}

const form = reactive({
  parentId: 0,
  name: '',
  path: '',
  icon: 'Odometer',
  sortNo: 0,
  permissionCode: '',
  type: 'MENU',
  status: 'ENABLED',
})

onMounted(() => {
  menuStore.fetchTree()
})

const parentOptions = computed(() => {
  const rows = [{ id: 0, label: '顶级菜单' }]
  function walk(nodes, prefix) {
    for (const node of nodes) {
      if (node.id === editingId.value) {
        continue
      }
      rows.push({ id: node.id, label: `${prefix}${node.name}` })
      if (node.children?.length) {
        walk(node.children, `${prefix}${node.name} / `)
      }
    }
  }
  walk(menuStore.tree, '')
  return rows
})

function resetForm() {
  editingId.value = null
  form.parentId = 0
  form.name = ''
  form.path = ''
  form.icon = 'Odometer'
  form.sortNo = 0
  form.permissionCode = ''
  form.type = 'MENU'
  form.status = 'ENABLED'
}

function openCreate(parentId = 0) {
  resetForm()
  form.parentId = parentId
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.parentId = row.parentId || 0
  form.name = row.name
  form.path = row.path || ''
  form.icon = row.icon || 'Odometer'
  form.sortNo = row.sortNo || 0
  form.permissionCode = row.permissionCode || ''
  form.type = row.type || 'MENU'
  form.status = row.status || 'ENABLED'
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写菜单名称')
    return
  }
  submitting.value = true
  try {
    const payload = {
      parentId: form.parentId || 0,
      name: form.name.trim(),
      path: form.path.trim() || null,
      icon: form.icon.trim() || null,
      sortNo: Number(form.sortNo) || 0,
      permissionCode: form.permissionCode.trim() || null,
      type: form.type,
      status: form.status,
    }
    if (editingId.value) {
      await menuStore.update(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await menuStore.create(payload)
      ElMessage.success('已创建菜单')
    }
    dialogVisible.value = false
    resetForm()
  } finally {
    submitting.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除菜单「${row.name}」？`, '删除确认', { type: 'warning' })
  await menuStore.remove(row.id)
  ElMessage.success('已删除')
}
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-head">
          <span>系统菜单</span>
          <el-button v-if="canWrite" type="primary" @click="openCreate(0)">新建菜单</el-button>
        </div>
      </template>
      <div class="table-scroll">
        <el-table
          :data="menuStore.tree"
          v-loading="menuStore.loading"
          row-key="id"
          default-expand-all
          empty-text="暂无菜单"
        >
          <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="path" label="路由" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.path || '-' }}</template>
          </el-table-column>
          <el-table-column prop="icon" label="图标" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.icon || '-' }}</template>
          </el-table-column>
          <el-table-column prop="permissionCode" label="权限编码" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.permissionCode || '-' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ typeLabel[row.type] || row.type }}</template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column v-if="canWrite" label="操作" width="228" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" text @click="openCreate(row.id)">子菜单</el-button>
                <el-button type="primary" text @click="openEdit(row)">编辑</el-button>
                <el-button type="danger" text @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑菜单' : '新建菜单'"
      width="560px"
      @closed="resetForm"
    >
      <el-form label-position="top">
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="例如：用户管理" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="DIR">目录</el-radio>
            <el-radio value="MENU">页面</el-radio>
            <el-radio value="BUTTON">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type !== 'BUTTON'" label="路由路径">
          <el-input v-model="form.path" maxlength="128" placeholder="例如：/users" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'BUTTON'" label="图标名">
          <el-input v-model="form.icon" maxlength="64" placeholder="Element Plus 图标名，例如 User" />
        </el-form-item>
        <el-form-item label="权限编码">
          <el-input v-model="form.permissionCode" maxlength="64" placeholder="例如：user:read" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
