<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const router = useRouter()
const studentNo = ref('')
const realName = ref('')
const loading = ref(false)

/**
 * 这一页模拟"学校的统一身份认证网站"。
 * 真实系统里它是另一个域名下的页面，用户在这里输账号密码，
 * 成功后认证平台带一次性 ticket 跳回我们的站点。
 * 开发期用本页代替，跳转与回跳都是真的浏览器跳转（会整页刷新）。
 */
async function submit() {
  if (!studentNo.value || !realName.value) {
    return ElMessage.warning('请输入学号与姓名')
  }
  loading.value = true
  try {
    const { ticket } = await api.mockSso(studentNo.value.trim(), realName.value.trim())
    // 模拟"认证成功后带票据回跳"，对应图附 2-3 的第 2~4 步
    window.location.href = `/sso/callback?ticket=${encodeURIComponent(ticket)}`
  } finally {
    loading.value = false
  }
}

function cancel() {
  router.push('/login')
}
</script>

<template>
  <div class="sso-wrap">
    <div class="sso-card">
      <div class="sso-head">
        <div class="sso-logo">统一身份认证</div>
        <div class="sso-sub">校园信息化门户 · 开发期模拟页</div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="这是模拟的学校认证页：真实环境由学校提供，本系统只负责接收票据"
        style="margin-bottom: 16px"
      />

      <el-form label-width="70px">
        <el-form-item label="学号">
          <el-input v-model="studentNo" placeholder="例如 2023010105" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="realName" placeholder="认证平台返回的姓名" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">登录并授权</el-button>
          <el-button @click="cancel">取消</el-button>
        </el-form-item>
      </el-form>

      <div class="sso-note">
        说明：开发期不接真实账号密码。认证平台理论上会返回学号、姓名、课表等属性，
        本系统按最小必要原则**只取学号与姓名**（DC-05 不采集超范围信息）。
      </div>
    </div>
  </div>
</template>

<style scoped>
.sso-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef4ff, #f7f9fc);
}
.sso-card {
  width: 520px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(31, 111, 235, 0.12);
  padding: 28px 32px;
}
.sso-head {
  text-align: center;
  margin-bottom: 18px;
}
.sso-logo {
  font-size: 1.3rem;
  font-weight: 700;
  color: #1f6feb;
}
.sso-sub {
  color: #888;
  font-size: 0.85rem;
  margin-top: 4px;
}
.sso-note {
  color: #777;
  font-size: 0.82rem;
  line-height: 1.7;
  border-top: 1px dashed #e5e7eb;
  padding-top: 12px;
}
</style>
