<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { state, refreshMe } from '../store'

const router = useRouter()
const items = ref([])
const loading = ref(false)

const dialog = ref(false)
const current = ref(null)
const f1 = ref('')
const f2 = ref('')
const f3 = ref('')
const file = ref(null)

async function load() {
  loading.value = true
  try {
    items.value = await api.items()
  } finally {
    loading.value = false
  }
}

function openApply(item) {
  if (!state.user) {
    ElMessage.warning('请先登录')
    return router.push('/login')
  }
  if (state.user.authState !== 1) {
    ElMessage.warning('发起认领需要先完成统一身份认证')
    return router.push('/login')
  }
  current.value = item
  f1.value = f2.value = f3.value = ''
  file.value = null
  dialog.value = true
}

function onFile(e) {
  file.value = e.target.files[0] || null
}

async function submit() {
  const features = [f1.value, f2.value, f3.value].map((s) => s.trim()).filter(Boolean)
  if (features.length < 3) return ElMessage.warning('请填写 3 项特征描述')
  const claim = await api.apply(current.value.itemId, features)

  if (file.value) {
    await api.uploadVoucher(claim.claimId, file.value)
  }
  await refreshMe()
  dialog.value = false
  ElMessage.success('认领申请已提交，等待拾得者核验')
  router.push('/claims')
}
</script>

<template>
  <el-card>
    <template #header>
      <div class="head">
        <span>招领信息（只读台账，由 M2 提供）</span>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </template>

    <el-table :data="items" v-loading="loading" stripe>
      <template #empty>
        <el-empty description="没有可认领的招领信息">
          <el-button @click="load">刷新</el-button>
        </el-empty>
      </template>
      <el-table-column prop="itemId" label="#" width="60" />
      <el-table-column prop="title" label="物品" min-width="160" />
      <el-table-column prop="category" label="类别" width="100" />
      <el-table-column prop="place" label="拾得地点" min-width="140" />
      <el-table-column prop="foundAt" label="拾得时间" width="170">
        <template #default="{ row }">{{ (row.foundAt || '').replace('T', ' ').slice(0, 16) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="openApply(row)">发起认领</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="发起认领" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        title="只受理低价值日常物品；请填写至少 3 项只有失主才知道的特征"
        show-icon
        style="margin-bottom: 12px"
      />
      <p><strong>{{ current?.title }}</strong> · {{ current?.place }}</p>
      <el-input v-model="f1" placeholder="特征 1（例如：伞柄上有一道划痕）" style="margin-bottom: 8px" />
      <el-input v-model="f2" placeholder="特征 2" style="margin-bottom: 8px" />
      <el-input v-model="f3" placeholder="特征 3" style="margin-bottom: 12px" />
      <div class="upload">
        <span>凭证（可选，1 张图片）：</span>
        <input type="file" accept="image/png,image/jpeg,image/webp" @change="onFile" />
      </div>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.upload {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #555;
}
</style>
