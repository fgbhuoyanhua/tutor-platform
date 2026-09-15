# 大学生家教服务预约平台

基于 SpringBoot 3 + uni-app x + Vue3 的全栈项目。

## 项目结构

```
project/
├── backend/           # 后端：SpringBoot 3.2 + MyBatis-Plus + JWT + Redis + Swagger
│   ├── sql/init.sql   # 建库建表脚本（MySQL 8.0）
│   └── src/main/java/com/tutor/platform/
│       ├── controller/  # 认证/家教/订单/管理/消息/科目
│       ├── service/     # 业务层（订单状态机、防并发接单、时段冲突校验）
│       ├── entity/ mapper/ dto/ vo/ security/ config/ common/
├── frontend-common/   # 共享层：TypeScript 类型 + 常量 + API 封装 + 工具
├── web-app/           # Web 管理端：Vue3 + Vite + Pinia + Element Plus
│   ├── src/views/     # 登录注册 / 工作台 / 找家教 / 我的家教 / 订单 / 管理后台
│   └── vite.config.ts # 开发代理 /api → localhost:8080
└── mobile-app/        # 移动端：uni-app x（.uvue + UTS）
    ├── pages/         # 登录注册 / 家教列表 / 预约下单 / 我的订单
    ├── api/           # uni.request 封装（Token 注入、code 校验）
    ├── components/    # 家教卡片 / 空态组件
    └── utils/         # 存储、请求工具
```

## 快速启动

### 1. 后端（IDEA 打开 backend 目录）

1. 准备 MySQL 8.0，执行建库脚本：`mysql -uroot -proot < backend/sql/init.sql`
   - 连接信息在 `backend/src/main/resources/application-dev.yml`（默认 root/root，可用环境变量覆盖）
2. 启动 Redis（可选）：仅短信验证码功能需要；未安装时演示模式自动兜底
3. 运行 `TutorPlatformApplication`，端口 8080
4. 接口文档：http://localhost:8080/swagger-ui.html

首次启动会自动创建演示账号（密码均 123456）：学生 student01 / 老师 tutor01、tutor02 / 管理员 admin。

### 2. Web 管理端

```bash
cd web-app
npm install
npm run dev        # http://localhost:5173
```

学生/老师/管理员共用一套 Web，登录后按角色展示不同功能；管理员可审核家教、管理用户、查看统计。

### 3. 移动端（uni-app x）

用 **HBuilderX（4.x 及以上，实测 5.24 可用）** 打开 `mobile-app` 目录（标准 HBuilderX 工程结构，manifest.json/pages.json 在根目录）：
1. 菜单「运行 → 运行到浏览器」可先看 H5 效果（H5 入口为根目录 `index.html`，脚本指向 `/main`）；「运行到手机或模拟器」跑 App
2. **跑微信小程序**：「运行 → 运行到小程序模拟器 → 微信开发者工具」，HBuilderX 自动编译并在微信开发者工具中打开（需本机已装微信开发者工具并开启服务端口）
3. 真机调试时把 `utils/request.uts` 中 `BASE_URL` 改为电脑局域网 IP（如 `http://192.168.x.x:8080/api`）

> 提示：短信验证码在演示模式（`app.sms-mock: true`）下固定为 `123456`，无需安装 Redis。

## 演示账号与验证码

| 账号 | 密码 | 角色 |
|------|------|------|
| student01 | 123456 | 学生 |
| tutor01 / tutor02 | 123456 | 老师 |
| admin | 123456 | 管理员 |

注册时验证码固定为 **123456**（演示模式 `app.sms-mock: true`，不依赖 Redis；接入真实短信后置为 false）。

## 核心业务规则

- 订单状态机：0待确认 →(老师接单) 1已预约 →(老师开课) 2授课中 →(学生确认完成) 3已完成；4已取消（学生）、5已拒绝（老师）
- 防并发接单：SQL 条件更新 `updateStatusIf`，仅当状态仍为 0 才可接单
- 时段冲突校验：同一老师同一日期同一时间段仅允许一个有效预约（状态 0/1/2）；极端并发下由唯一索引 `uk_slot` + 显式异常兜底转为友好提示
- 订单号防碰撞：时间戳 + 6 位随机，插入唯一键冲突时自动换号重试
- 角色权限：JWT 拦截器统一鉴权，`@RequireRole` 标注方法级角色控制；Web 端路由另加角色守卫（meta.roles），未授权访问重定向工作台
- 登录安全：登录失败连续 5 次锁定 15 分钟（`app.login.max-fail` / `app.login.lock-minutes` 可调）；注册验证码错误连续 5 次需重新获取（`app.sms.max-fail`）；Redis 不可用时自动降级为进程内限流
- 生产自检：启动时校验 prod 环境禁止 `app.sms-mock=true`、JWT 密钥必须 ≥32 位且非默认值，否则拒绝启动
- 评价闭环：学生仅可对已完成订单评价（1-5 分），提交后自动重算老师评分与评价数

## 主要接口一览

| 模块 | 接口 | 说明 |
|------|------|------|
| 认证 | POST /api/auth/register、/login、GET /code | 注册（手机号+验证码）、登录（JWT）、验证码 |
| 用户 | PUT /api/users/profile、/password | 修改个人资料、修改密码（需原密码） |
| 家教 | GET /api/tutors、GET /api/tutors/{id}、POST/PUT /api/tutors | 检索、详情、发布/编辑（发布后待审核） |
| 家教 | GET /api/tutors/income?month=yyyy-MM | 老师按月收入统计 |
| 订单 | POST /api/appointments、PUT /{id}/confirm\|reject\|start\|cancel\|finish | 预约下单与状态流转 |
| 评价 | POST /api/evaluations、GET /api/evaluations/tutor/{userId} | 学生评价、老师评价列表（公开） |
| 管理 | GET /api/admin/orders?status=、/users、/stats、/logs、/tutors/pending | 订单监管、用户管理、统计、日志、家教审核 |
| 管理 | POST/PUT/DELETE /api/admin/subjects | 科目分类管理（被引用/有子类禁止删除） |
| 消息 | GET/POST/PUT/DELETE /api/messages | 站内消息（部分） |

统一返回 `{code, message, data}`；参数错误 400、未登录/登录过期 401、无权限 403、系统异常 500。

## 测试

- 后端单元测试：`mvn test`（订单状态机全流转 + 登录限流降级，11 个用例）
- 接口链路验证脚本：`project/.tools/test_*.ps1`（评价/收入/订单监管/科目管理/状态机/用户/限流/异常/生产自检等，均含硬断言）
