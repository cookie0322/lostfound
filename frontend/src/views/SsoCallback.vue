<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { refreshMe } from '../store'

const route = useRoute()
const router = useRouter()
const msg = ref('正在用票据换取学号与姓名…')
const failed = ref(false)

/**
 * 认证平台回跳的落地页：对应图附 2-3 的第 5、6 步，
 * 前端把一次性 ticket 交给后端，由后端调用认证接口换取身份。
 */
onMounted(async () => {
  const ticket = route.query.ticket
  if (!ticket) {
    failed.value = true
    msg.value = '没有收到票据，认证未完成'
    return
  }
  // 这是整页跳转回来的，App 的 onMounted 可能还没跑完，先自己拉一次会话
  const me = await refreshMe()
  if (!me) {
    // 票据绑定需要先有本地账号（既有登录模块负责），否则不知道绑给谁
    failed.value = true
    msg.value = '请先登录本地账号，再进行统一身份认证'
    return
  }
  try {
    await api.bind(ticket)
    await refreshMe()
    ElMessage.success('认证完成')
    router.replace('/')
  } catch (e) {
    failed.value = true
    msg.value = '票据无效或已过期，请重新认证'
  }
})

function back() {
  router.replace('/login')
}
</script>

<template>
  <div class="cb-wrap">
    <div class="cb-card">
      <el-result :icon="failed ? 'warning' : 'info'" :title="failed ? '认证未完成' : '正在认证'">
        <template #sub-title>
          <p>{{ msg }}</p>
        </template>
        <template #extra>
          <el-button v-if="failed" type="primary" @click="back">返回登录页</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<style scoped>
.cb-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f9fc;
}
.cb-card {
  width: 460px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(31, 111, 235, 0.1);
}
</style>
