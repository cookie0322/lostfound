# 校园失物招领系统

课程项目实现部分：**F1 统一身份认证与账号绑定** + **F2 失物认领全流程管理**。

> 范围与非目标见《需求分析专项》3.2：不做信息发布与浏览、不做聊天、不做智能匹配、
> 不做完整管理后台；`item` 招领台账由 M2 以只读数据形式提供（本项目用种子数据模拟）。

---

## 一、技术栈

| 层 | 选型 | 依据 |
|---|---|---|
| 后端 | Java 17 + Spring Boot 3.5.16 + MyBatis-Plus 3.5.18 | DC-02 |
| 数据库 | MySQL 8（utf8mb4） | DC-02 |
| 前端 | Vue 3.5 + Vite 5 + Element Plus 2.8 | DC-02 |
| 部署 | 单机：前端 5173 / 后端 8080 / MySQL 3306 同机 | DC-03 |

---

## 二、目录结构

```
code/
├─ backend/                 Spring Boot 后端
│  └─ src/main/java/edu/campus/lostfound/
│     ├─ auth/              F1 认证模块（绑定、解绑、申诉、两态状态机）
│     ├─ claim/             F2 认领模块（申请、核验、确认、超时、仲裁、裁决、凭证）
│     ├─ notice/            通知模块（站内信，五类节点）
│     ├─ audit/             审计模块（状态变更统一留痕）
│     ├─ data/              数据访问（实体、Mapper、凭证存取）
│     └─ web/               表示层适配（Controller、统一返回、异常、越权拦截）
├─ frontend/                Vue 3 前端
│  └─ src/views/            Login / Items / Claims / Admin / Sso / SsoCallback
├─ db/                      建库脚本（按编号顺序执行）
└─ docs/                    追溯矩阵等
```

**依赖方向（架构 3.3.3 三条红线）**：`web → auth/claim`，`auth/claim → data/audit/notice`，
F2 对 F1 只读（`AuthStatusQuery`），任何模块都不反向依赖 web。

---

## 三、环境要求

- JDK 17 或 21（`pom.xml` 用 `java.version=17`，用 21 的 JDK 编 17 字节码没问题）
- MySQL 8（本项目在 8.0.45 上验证）
- Node.js 18+（本项目在 Node 24 上验证）
- 不需要单独装 Maven，仓库自带 `mvnw`

---

## 四、首次运行

### 1. 建库（以 root 执行，只需一次）

```powershell
cd "路径\code"
mysql -u root -p < db\00-init.sql        # 建库 + 建应用账号 lf_app
mysql -u root -p < db\01-schema.sql      # 建表 + 表级授权
mysql -u root -p < db\02-seed.sql        # 种子数据（账号、招领信息、状态转移表）
mysql -u root -p < db\03-migrate-20261105.sql   # 删手机号字段 + 补转移行
mysql -u root -p < db\04-appeal-20261106.sql    # 申诉表
```

验证：`mysql -u lf_app -plf_dev_2026 -D lostfound -e "SHOW TABLES;"` 应有 8 张表。

> 口令仅用于本机开发。要换：改 `backend/src/main/resources/application.yml` 里的
> `DB_USER` / `DB_PASSWORD`，或设置同名环境变量。

### 2. 启动后端

```powershell
cd "路径\code\backend"
.\mvnw.cmd spring-boot:run
```

看到 `Started LostfoundApplication` 即成功。健康检查：<http://localhost:8080/actuator/health>。

### 3. 启动前端

```powershell
cd "路径\code\frontend"
npm install        # 首次
npm run dev
```

打开 <http://localhost:5173>。Vite 已把 `/api` 代理到 8080，不需要处理跨域。

---

## 五、演示账号

`login_name` 是"既有登录模块"的账号名（模拟），认证状态由统一身份认证绑定决定。

| 账号 | 姓名 | 认证状态 | 用途 |
|---|---|---|---|
| `admin` | 管理员 | — | 管理端：申诉处理、仲裁裁决 |
| `zhang` | 张同学 | 未认证 | 演示"未认证不能认领" |
| `li` | 李同学 | 已认证 | 拾得者（发布了 3 条信息） |
| `wang` | 王同学 | 未认证 | 演示未认证拦截 |
| `zhao` | 赵同学 | 已认证 | 失主 |
| `zhao2` | 赵同学 | 未认证 | 演示"学号被占用→申诉→重新绑定" |

**统一身份认证**：点「通过统一身份认证绑定」会跳到 `/sso`（模拟学校认证页，整页跳转），
输入学号与姓名后带票据回跳，由后端用票据换取学号姓名。票据一次性、60 秒过期。

---

## 六、验收路径（建议按顺序点一遍）

1. **认证**：`wang` 登录 → 停在登录页 → 点统一认证 → 学号 `2023010105` → 自动回首页，状态变已认证
2. **申诉**：`zhao2` 登录 → 认证页填 `2023010104`（已被 zhao 占用）→ 提示可申诉 → 提交申诉
3. **管理端**：`admin` 登录 → 申诉列表 → 解除原绑定 → `zhao2` 可重新绑定该学号
4. **认领**：`zhao` 登录 → 首页对某条信息发起认领（3 项特征 + 可选凭证）→ 提交
5. **核验**：`li` 登录 → 我的认领 →「我收到的」→ 通过 / 拒绝 / 转仲裁（必填理由）
6. **交接**：双方各点一次「确认交接」→ 状态变已完成，该招领信息从首页消失
7. **凭证**：拾得者能看凭证，第三方看不到（403）
8. **通知**：每个关键节点后，顶栏通知角标 +1，内容不含对方姓名与联系方式
9. **解绑**：任意已认证账号点「解绑」→ 弹出二次确认

---

## 七、测试

```powershell
cd code\backend
.\mvnw.cmd test
```

包含：

- `ClaimConcurrencyTest`：50 线程并发申请同一条信息，断言 **成功 1 / 冲突 49 / 占用表 1 行**（QR-09、FR-07）
- `LostfoundApplicationTests`：上下文可启动

> 并发测试自己造账号与物品、跑完清理，不依赖种子数据的当前状态。

---

## 八、常见问题

| 现象 | 原因与处理 |
|---|---|
| 建表报 `ERROR 1046 No database selected` | 脚本已加 `USE lostfound;`；若仍报，改用 `mysql -u root -p lostfound < db\xx.sql` |
| 首页空、但顶部能显示 | 后端没起或端口不对；浏览器直接打开 <http://localhost:8080/api/items> 看是否有 JSON |
| 启动报 `Port 8080 was already in use` | 上一次进程没退：`Get-NetTCPConnection -LocalPort 8080` 找 PID 后 `Stop-Process -Id <PID> -Force` |
| `npm run dev` 报 esbuild 相关错误 | 依赖装得不完整，在 `frontend` 目录重跑 `npm install` |
| 改了 `claim_transition` 不生效 | 状态机每 60 秒刷新一次，等一分钟或重启后端 |

**关闭服务**：在对应终端窗口按 `Ctrl + C`。关浏览器标签页不会停服务。

---

## 九、设计文档索引

| 文档 | 位置 |
|---|---|
| 需求分析专项 | `交付物/需求分析专项作业-校园失物招领系统.docx` |
| 软件架构设计专项 | `交付物/软件架构设计专项作业-校园失物招领系统.docx` |
| 需求—代码追溯矩阵 | `code/docs/追溯矩阵.md` |
| 文档回改清单 | `文档回改清单.md` |
