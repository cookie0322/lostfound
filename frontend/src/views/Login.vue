<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { state, refreshMe, refreshNotices } from '../store'

const router = useRouter()
const loginName = ref('')
const loading = ref(false)

const appealNo = ref('')
const appealReason = ref('')

async function doLogin() {
  if (!loginName.value) return ElMessage.warning('请输入账号名')
  loading.value = true
  try {
    await api.login(loginName.value)
    const me = await refreshMe()
    await refreshNotices()
    if (me && me.authState === 1) {
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      // 未认证就留在这个页面，右侧就是认证表单，不用再去找入口
      ElMessage.warning('登录成功，请先完成统一身份认证')
    }
  } finally {
    loading.value = false
  }
}

/**
 * 走真实形态的 SSO：跳转到认证页 → 用户授权 → 带票据回跳 → 回调页换票。
 * 不再用本地表单直接调接口（那等于把"跳转"这一步偷掉了，与 IF-01 不符）。
 */
function goSso() {
  router.push('/sso')
}

async function doUnbind() {
  // FR-13：解绑属不可逆操作，必须二次确认（班级公约第五条同理）
  try {
    await ElMessageBox.confirm(
      '解绑后本账号回到未认证状态，需要重新通过统一身份认证才能再次绑定，且期间不能参与认领。确定解绑吗？',
      '二次确认',
      { type: 'warning', confirmButtonText: '确定解绑', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  await api.unbind()
  await refreshMe()
  ElMessage.success('已解绑')
}

async function doAppeal() {
  if (!appealNo.value || !appealReason.value) return ElMessage.warning('学号与理由都要填')
  await api.appeal(appealNo.value, appealReason.value)
  ElMessage.success('申诉已提交，等待管理员核实')
  appealNo.value = ''
  appealReason.value = ''
}
</script>

<template>
  <div>
    <el-alert
      type="info"
      :closable="false"
      title="开发期使用本地模拟登录（学校 SSO 接入后只需替换后端一个实现类）"
      show-icon
      style="margin-bottom: 16px"
    />

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="1. 登录（模拟既有登录模块）">
          <el-form label-width="80px">
            <el-form-item label="账号名">
              <el-input v-model="loginName" placeholder="admin / zhang / li / wang / zhao / zhao2" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="doLogin">登录</el-button>
            </el-form-item>
          </el-form>
          <div class="hint">
            种子账号：<br />
            zhang、li、zhao 已认证；wang、zhao2 未认证；admin 是管理员
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card header="2. 统一身份认证与绑定">
          <template v-if="!state.user">
            <el-empty description="请先登录" :image-size="60" />
          </template>
          <template v-else>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="账号">{{ state.user.loginName }}</el-descriptions-item>
              <el-descriptions-item label="姓名">{{ state.user.realNameMasked || '—' }}</el-descriptions-item>
              <el-descriptions-item label="认证状态">
                <el-tag :type="state.user.authState === 1 ? 'success' : 'info'">
                  {{ state.user.authState === 1 ? '已认证' : '未认证' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="角色">{{ state.user.role }}</el-descriptions-item>
            </el-descriptions>

            <el-divider />

            <template v-if="state.user.authState !== 1">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="点击下方按钮会跳转到学校统一身份认证页，登录授权后自动带票据返回"
                style="margin-bottom: 12px"
              />
              <el-button type="primary" @click="goSso">通过统一身份认证绑定</el-button>

              <el-divider>学号已被他人占用？</el-divider>
              <el-form label-width="80px">
                <el-form-item label="学号">
                  <el-input v-model="appealNo" placeholder="被占用的学号" />
                </el-form-item>
                <el-form-item label="理由">
                  <el-input v-model="appealReason" type="textarea" :rows="2" placeholder="说明情况，便于管理员核实" />
                </el-form-item>
                <el-form-item>
                  <el-button type="warning" @click="doAppeal">提交申诉</el-button>
                </el-form-item>
              </el-form>
            </template>

            <el-button v-else type="danger" plain @click="doUnbind">解绑（二次确认）</el-button>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.hint {
  color: #666;
  font-size: 0.85rem;
  line-height: 1.7;
}
</style>
