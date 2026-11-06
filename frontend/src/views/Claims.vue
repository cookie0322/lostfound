<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { state, refreshMe } from '../store'

const router = useRouter()
const applied = ref([])
const received = ref([])
const loading = ref(false)

const reviewDialog = ref(false)
const reviewRow = ref(null)
const reviewAction = ref('ACCEPT')
const reviewReason = ref('')
const voucherFile = ref(null)

const desc = { 0: '待处理', 1: '待交接', 2: '仲裁中', 3: '已完成', 4: '已结束' }
const type = { 0: 'warning', 1: 'primary', 2: 'danger', 3: 'success', 4: 'info' }
const reasonLabel = { 1: '被拒绝', 2: '超时', 3: '撤回', 4: '裁决驳回' }

async function load() {
  if (!state.user) {
    ElMessage.warning('请先登录')
    return router.push('/login')
  }
  loading.value = true
  try {
    const data = await api.myClaims()
    applied.value = data.applied
    received.value = data.received
  } finally {
    loading.value = false
  }
}

function openReview(row, action) {
  reviewRow.value = row
  reviewAction.value = action
  reviewReason.value = ''
  reviewDialog.value = true
}

async function submitReview() {
  if (!reviewReason.value.trim()) return ElMessage.warning('请填写理由')
  await api.review(reviewRow.value.claimId, reviewAction.value, reviewReason.value.trim())
  reviewDialog.value = false
  ElMessage.success('已提交')
  await load()
}

async function confirmHandover(row) {
  await api.confirm(row.claimId)
  ElMessage.success('已确认交接')
  await load()
}

async function withdraw(row) {
  await ElMessageBox.confirm('撤销后本次认领将结束，确定吗？', '二次确认', { type: 'warning' })
  await api.withdraw(row.claimId)
  ElMessage.success('已撤销')
  await load()
}

async function arbitrate(row) {
  const { value } = await ElMessageBox.prompt('转仲裁后不可撤回，请填写理由', '转仲裁', {
    inputPlaceholder: '例如：线下已交接但对方不确认'
  })
  await api.arbitrate(row.claimId, value)
  ElMessage.success('已转入仲裁')
  await load()
}

function pickVoucher(e) {
  voucherFile.value = e.target.files[0] || null
}

async function upload(row) {
  if (!voucherFile.value) return ElMessage.warning('请先选择图片')
  await api.uploadVoucher(row.claimId, voucherFile.value)
  ElMessage.success('凭证已上传')
  voucherFile.value = null
  await load()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="认领状态：待处理 → 待交接 → 已完成；任一方可转仲裁，超时自动结束"
      style="margin-bottom: 16px"
    />

    <el-card header="我收到的申请（我是拾得者）" style="margin-bottom: 16px">
      <el-empty v-if="!received.length" description="暂无" :image-size="60" />
      <el-table v-else :data="received" stripe>
        <el-table-column prop="claimId" label="#" width="60" />
        <el-table-column prop="itemId" label="物品ID" width="80" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="type[row.state]">{{ desc[row.state] }}</el-tag>
            <span v-if="row.state === 4 && row.endReason" class="sub">（{{ reasonLabel[row.endReason] }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="特征" min-width="200">
          <template #default="{ row }">{{ (row.features || []).join(' / ') }}</template>
        </el-table-column>
        <el-table-column label="凭证" width="100">
          <template #default="{ row }">
            <a v-if="row.voucherUrl" :href="`/api/claims/${row.claimId}/voucher`" target="_blank">查看</a>
            <span v-else class="sub">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <template v-if="row.state === 0">
              <el-button size="small" type="success" @click="openReview(row, 'ACCEPT')">通过</el-button>
              <el-button size="small" type="danger" @click="openReview(row, 'REJECT')">拒绝</el-button>
              <el-button size="small" @click="openReview(row, 'ARBITRATE')">转仲裁</el-button>
            </template>
            <template v-else-if="row.state === 1">
              <el-button size="small" type="success" @click="confirmHandover(row)">确认交接</el-button>
              <el-button size="small" @click="arbitrate(row)">转仲裁</el-button>
            </template>
            <span v-else class="sub">已结束</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card header="我发起的申请（我是失主）">
      <el-empty v-if="!applied.length" description="暂无" :image-size="60" />
      <el-table v-else :data="applied" stripe>
        <el-table-column prop="claimId" label="#" width="60" />
        <el-table-column prop="itemId" label="物品ID" width="80" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="type[row.state]">{{ desc[row.state] }}</el-tag>
            <span v-if="row.state === 4 && row.endReason" class="sub">（{{ reasonLabel[row.endReason] }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="特征" min-width="200">
          <template #default="{ row }">{{ (row.features || []).join(' / ') }}</template>
        </el-table-column>
        <el-table-column prop="expireAt" label="超时时间" width="170">
          <template #default="{ row }">{{ (row.expireAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <template v-if="row.state === 0 || row.state === 1">
              <el-button v-if="row.state === 1" size="small" type="success" @click="confirmHandover(row)">确认交接</el-button>
              <el-button size="small" @click="arbitrate(row)">转仲裁</el-button>
              <el-button size="small" type="danger" plain @click="withdraw(row)">撤销</el-button>
            </template>
            <template v-else-if="row.state === 2">
              <span class="sub">等待管理员裁决</span>
            </template>
            <span v-else class="sub">已结束</span>

            <div v-if="row.state === 0" class="voucher-upload">
              <input type="file" accept="image/png,image/jpeg,image/webp" @change="pickVoucher" />
              <el-button size="small" @click="upload(row)">上传凭证</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="reviewDialog" title="核验处置" width="480px">
      <el-form label-width="70px">
        <el-form-item label="处置">
          <el-radio-group v-model="reviewAction">
            <el-radio value="ACCEPT">通过</el-radio>
            <el-radio value="REJECT">拒绝</el-radio>
            <el-radio value="ARBITRATE">转仲裁</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="理由">
          <el-input v-model="reviewReason" type="textarea" :rows="3" placeholder="必填，拒绝理由会对申请人可见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sub {
  color: #888;
  font-size: 0.85rem;
}
.voucher-upload {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  align-items: center;
}
</style>
