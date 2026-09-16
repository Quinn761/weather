<script setup>
import { onMounted, reactive, ref } from 'vue' // 导入 vue 的组合式 API 供本文件使用
import { ElMessage, ElMessageBox } from 'element-plus' // 导入 element-plus 的消息组件供本文件使用
import { useUserStore } from '@/stores/user' // 导入用户状态仓库供本文件使用
import { listRoles } from '@/api/role' // 导入角色列表接口供分配角色使用

const userStore = useUserStore() // 计算并保存 userStore 的值
const dialogVisible = ref(false) // 计算并保存 dialogVisible 的值
const submitting = ref(false) // 计算并保存 submitting 的值
const editingId = ref(null) // 计算并保存 editingId 的值
const roles = ref([]) // 保存可供勾选的角色列表

const form = reactive({ // 计算并保存 form 的值
  username: '', // 声明枚举值或多行参数的一项
  nickname: '', // 声明枚举值或多行参数的一项
  email: '', // 声明枚举值或多行参数的一项
  phone: '', // 声明枚举值或多行参数的一项
  password: '', // 新建必填，编辑时留空表示不改密码
  status: 'ENABLED', // 声明枚举值或多行参数的一项
  roleIds: [], // 当前用户要绑定的角色主键
}) // 结束当前多行语句

onMounted(async () => { // 定义组件挂载后的回调入口
  userStore.fetchPage() // 执行 fetchPage 语句完成当前步骤
  try { // 开始当前声明或控制结构的代码块
    roles.value = (await listRoles()) || [] // 拉取角色供下拉选择
  } catch { // 开始当前声明或控制结构的代码块
    roles.value = [] // 没有角色读权限时让下拉为空
  } // 
}) // 

function resetForm() { // 定义 resetForm 方法的入口
  editingId.value = null // 执行赋值语句完成当前步骤
  form.username = '' // 执行赋值语句完成当前步骤
  form.nickname = '' // 执行赋值语句完成当前步骤
  form.email = '' // 执行赋值语句完成当前步骤
  form.phone = '' // 执行赋值语句完成当前步骤
  form.password = '' // 清空密码输入，避免把旧值带到下一次编辑
  form.status = 'ENABLED' // 执行赋值语句完成当前步骤
  form.roleIds = [] // 清空已选角色
} // 

function openCreate() { // 定义 openCreate 方法的入口
  resetForm() // 执行 resetForm 语句完成当前步骤
  dialogVisible.value = true // 执行赋值语句完成当前步骤
} // 

function openEdit(row) { // 定义 openEdit 方法的入口
  editingId.value = row.id // 执行赋值语句完成当前步骤
  form.username = row.username // 执行赋值语句完成当前步骤
  form.nickname = row.nickname // 执行赋值语句完成当前步骤
  form.email = row.email || '' // 执行赋值语句完成当前步骤
  form.phone = row.phone || '' // 执行赋值语句完成当前步骤
  form.status = row.status || 'ENABLED' // 执行赋值语句完成当前步骤
  form.roleIds = [...(row.roleIds || [])] // 回填已绑定角色
  form.password = '' // 编辑时不带回密码
  dialogVisible.value = true // 执行赋值语句完成当前步骤
} // 

async function submit() { // 定义 submit 方法的入口
  if (!form.nickname.trim()) { // 判断条件是否成立以决定是否进入分支
    ElMessage.warning('请填写显示名') // 执行 warning 语句完成当前步骤
    return // 返回当前方法的处理结果
  } // 
  if (!editingId.value && !form.username.trim()) { // 判断条件是否成立以决定是否进入分支
    ElMessage.warning('请填写用户名') // 执行 warning 语句完成当前步骤
    return // 返回当前方法的处理结果
  } // 
  if (!editingId.value && form.password.length < 6) { // 新建用户必须设置密码
    ElMessage.warning('请设置至少 6 位密码') // 提示密码长度不够
    return // 返回当前方法的处理结果
  } // 
  if (editingId.value && form.password && form.password.length < 6) { // 编辑时若填写了密码也要校验长度
    ElMessage.warning('新密码至少 6 位') // 提示密码长度不够
    return // 返回当前方法的处理结果
  } // 
  submitting.value = true // 执行赋值语句完成当前步骤
  try { // 开始当前声明或控制结构的代码块
    if (editingId.value) { // 判断条件是否成立以决定是否进入分支
      await userStore.update(editingId.value, { // 执行 update 语句完成当前步骤
        nickname: form.nickname.trim(), // 声明枚举值或多行参数的一项
        email: form.email.trim() || null, // 声明枚举值或多行参数的一项
        phone: form.phone.trim() || null, // 声明枚举值或多行参数的一项
        status: form.status, // 声明枚举值或多行参数的一项
        password: form.password || null, // 留空则不修改密码
        roleIds: form.roleIds, // 覆盖用户角色
      }) // 结束当前多行语句
      ElMessage.success('已保存') // 执行 success 语句完成当前步骤
    } else { // 开始当前声明或控制结构的代码块
      await userStore.create({ // 执行 create 语句完成当前步骤
        username: form.username.trim(), // 声明枚举值或多行参数的一项
        nickname: form.nickname.trim(), // 声明枚举值或多行参数的一项
        email: form.email.trim() || null, // 声明枚举值或多行参数的一项
        phone: form.phone.trim() || null, // 声明枚举值或多行参数的一项
        password: form.password, // 新建用户的初始密码
        status: form.status, // 声明枚举值或多行参数的一项
        roleIds: form.roleIds, // 指定角色，空则默认 USER
      }) // 结束当前多行语句
      ElMessage.success('已创建用户') // 执行 success 语句完成当前步骤
    } // 
    dialogVisible.value = false // 执行赋值语句完成当前步骤
    resetForm() // 执行 resetForm 语句完成当前步骤
  } finally { // 开始当前声明或控制结构的代码块
    submitting.value = false // 执行赋值语句完成当前步骤
  } // 
} // 

async function remove(row) { // 定义 remove 方法的入口
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '删除确认', { // 执行 confirm 语句完成当前步骤
    type: 'warning', // 声明枚举值或多行参数的一项
  }) // 结束当前多行语句
  await userStore.remove(row.id) // 执行 remove 语句完成当前步骤
  ElMessage.success('已删除') // 执行 success 语句完成当前步骤
} // 

function onSearch() { // 定义 onSearch 方法的入口
  userStore.current = 1 // 执行赋值语句完成当前步骤
  userStore.fetchPage() // 执行 fetchPage 语句完成当前步骤
} // 

function onPageChange(page) { // 定义 onPageChange 方法的入口
  userStore.current = page // 执行赋值语句完成当前步骤
  userStore.fetchPage() // 执行 fetchPage 语句完成当前步骤
} // 

function onSizeChange(size) { // 定义 onSizeChange 方法的入口
  userStore.size = size // 执行赋值语句完成当前步骤
  userStore.current = 1 // 执行赋值语句完成当前步骤
  userStore.fetchPage() // 执行 fetchPage 语句完成当前步骤
} // 
</script>

<template>
  <div class="page">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-head">
          <span>系统用户</span>
          <div class="panel-actions">
            <el-input
              v-model="userStore.keyword"
              clearable
              placeholder="搜索用户名 / 显示名 / 邮箱"
              style="width: 240px"
              @clear="onSearch"
              @keyup.enter="onSearch"
            />
            <el-button @click="onSearch">查询</el-button>
            <el-button type="primary" @click="openCreate">新建用户</el-button>
          </div>
        </div>
      </template>
      <div class="table-scroll">
        <el-table :data="userStore.list" v-loading="userStore.loading" empty-text="暂无用户，请先新建">
          <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="nickname" label="显示名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.email || '-' }}</template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.phone || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.roles || []).join(' / ') || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
                {{ row.status === 'ENABLED' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
          <el-table-column label="操作" width="148" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" text @click="openEdit(row)">编辑</el-button>
                <el-button type="danger" text @click="remove(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="userStore.total"
          :current-page="userStore.current"
          :page-size="userStore.size"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑用户' : '新建用户'"
      width="520px"
      @closed="resetForm"
    >
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" maxlength="64" :disabled="!!editingId" placeholder="登录标识，创建后不可改" />
        </el-form-item>
        <el-form-item label="显示名" required>
          <el-input v-model="form.nickname" maxlength="64" placeholder="例如：张三" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="128" placeholder="可选" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="32" placeholder="可选" />
        </el-form-item>
        <el-form-item :label="editingId ? '重置密码' : '密码'" :required="!editingId">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="64"
            :placeholder="editingId ? '不修改请留空' : '至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple collapse-tags placeholder="不选则默认普通用户" style="width: 100%">
            <el-option v-for="item in roles" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
