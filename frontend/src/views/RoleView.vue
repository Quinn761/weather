<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMenuStore } from '@/stores/menu'
import { useRoleStore } from '@/stores/role'
import { useAuthStore } from '@/stores/auth'

const roleStore = useRoleStore()
const menuStore = useMenuStore()
const authStore = useAuthStore()
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const treeRef = ref()
const canWrite = computed(() => authStore.hasPermission('role:write'))

const form = reactive({
  code: '',
  name: '',
})

onMounted(async () => {
  await Promise.all([roleStore.fetchList(), menuStore.fetchTree()])
})

function resetForm() {
  editingId.value = null
  form.code = ''
  form.name = ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.code = row.code
  form.name = row.name
  dialogVisible.value = true
}

function onDialogOpened() {
  const current = roleStore.list.find((item) => item.id === editingId.value)
  treeRef.value?.setCheckedKeys(current?.menuIds || [])
}

async function submit() {
  if (!form.code.trim() || !form.name.trim()) {
    ElMessage.warning('请填写角色编码和名称')
    return
  }
  submitting.value = true
  try {
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      menuIds: treeRef.value?.getCheckedKeys() || [],
    }
    if (editingId.value) {
      await roleStore.update(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await roleStore.create(payload)
      ElMessage.success('已创建角色')
    }
    dialogVisible.value = false
    resetForm()
  } finally {
    submitting.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, '删除确认', { type: 'warning' })
  await roleStore.remove(row.id)
  ElMessage.success('已删除')
}
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-head">
          <span>系统角色</span>
          <el-button v-if="canWrite" class="cmd-btn" type="primary" @click="openCreate">新建角色</el-button>
        </div>
      </template>
      <div class="table-scroll">
        <el-table :data="roleStore.list" v-loading="roleStore.loading" empty-text="暂无角色">
          <el-table-column prop="code" label="编码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="菜单数" width="100">
            <template #default="{ row }">{{ (row.menuIds || []).length }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
          <el-table-column v-if="canWrite" label="操作" width="148" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" text @click="openEdit(row)">编辑</el-button>
                <el-button v-if="row.code !== 'ADMIN'" type="danger" text @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑角色' : '新建角色'"
      width="560px"
      append-to-body
      align-center
      destroy-on-close
      @opened="onDialogOpened"
      @closed="resetForm"
    >
      <el-form label-position="top">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.code" maxlength="64" :disabled="!!editingId" placeholder="例如：OPERATOR" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="例如：运营人员" />
        </el-form-item>
        <el-form-item label="授权菜单">
          <el-tree
            ref="treeRef"
            :data="menuStore.tree"
            node-key="id"
            show-checkbox
            check-strictly
            default-expand-all
            :props="{ label: 'name', children: 'children' }"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="cmd-btn-ghost" @click="dialogVisible = false">取消</el-button>
        <el-button class="cmd-btn" type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
