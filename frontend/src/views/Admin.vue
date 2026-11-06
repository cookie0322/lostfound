<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { state } from '../store'

const router = useRouter()
const appeals = ref([])
const arbClaims = ref([])
const loading = ref(false)

async function load() {
  if (!state.user || state.user.role !== 'ADMIN') {
    ElMessage.warning('需要管理员权限')
    return router.push('/login')
  }
  loading.value = true
  try {
    appeals.value = await api.appeals()
    arbClaims.value = await api.adminClaims(2)
  } finally {
    loading.value = false
  }
}

async function resolveAppeal(row, action) {
  const { value } = await ElMessageBox.prompt(
    action === 'UNBIND' ? '确认解除原绑定？请填写核实说明' : '请填写驳回理由',
    action === 'UNBIND' ? '解除原绑定' : '驳回申诉',
    { inputPlaceholder: '处理说明' }
  )
  await api.resolveAppeal(row.appealId, action, value)
  ElMessage.success('已处理')
  await load()
}

async function verdict(row, result) {
  const { value } = await ElMessageBox.prompt(
    result === 'RETURN' ? '判定归还，请填写裁决理由' : '判定驳回，请填写裁决理由',
    '管理员裁决',
    { inputPlaceholder: '裁决理由' }
  )
  await api.verdict(row.claimId, result, value)
  ElMessage.success('已裁决')
  await load()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-card header="学号占用申诉（FR-05）" style="margin-bottom: 16px">
      <el-empty v-if="!appeals.length" description="没有待处理的申诉" :image-size="60" />
      <el-table v-else :data="appeals" stripe>
        <el-table-column prop="appealId" label="#" width="60" />
        <el-table-column prop="userId" label="申诉人ID" width="100" />
        <el-table-column prop="reason" label="理由" min-width="220" />
        <el-table-column prop="createdAt" label="提交时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="resolveAppeal(row, 'UNBIND')">解除原绑定</el-button>
            <el-button size="small" @click="resolveAppeal(row, 'REJECT')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card header="仲裁中的认领（FR-12）">
      <el-empty v-if="!arbClaims.length" description="没有待裁决的认领" :image-size="60" />
      <el-table v-else :data="arbClaims" stripe>
        <el-table-column prop="claimId" label="#" width="60" />
        <el-table-column label="双方" width="150">
          <template #default="{ row }">
            {{ row.applicantId }} → {{ row.respondentId }}
          </template>
        </el-table-column>
        <el-table-column label="特征与凭证" min-width="240">
          <template #default="{ row }">
            <div>{{ (row.features || []).join(' / ') }}</div>
            <a v-if="row.voucherUrl" :href="`/api/claims/${row.claimId}/voucher`" target="_blank">查看凭证</a>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="verdict(row, 'RETURN')">判归还</el-button>
            <el-button size="small" type="danger" @click="verdict(row, 'REJECT')">判驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
