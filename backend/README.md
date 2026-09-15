# 大学生家教服务预约平台 - 后端（SpringBoot）

## 环境要求
- JDK 17+
- Maven 3.8+
- MySQL 8.0（本地默认 root/123456，可用环境变量覆盖）
- Redis 6.0（默认无密码）

## 快速启动

```bash
# 1. 建库建表（执行一次）
mysql -uroot -p < sql/init.sql

# 2. 启动后端（默认 dev 环境，连 localhost:3306 / localhost:6379）
mvn spring-boot:run
# 或打包运行：mvn clean package -DskipTests && java -jar target/tutor-platform-backend-1.0.0.jar
```

## 演示账号（应用首次启动自动创建，密码统一 123456）
| 账号 | 角色 | 说明 |
|------|------|------|
| admin | 管理员 | 审核、用户管理、统计 |
| tutor01 | 老师 | 王老师-数学 |
| tutor02 | 老师 | 李老师-编程 |
| student01 | 学生 | 张同学 |

## 接口文档（Swagger）
启动后访问：http://localhost:8080/swagger-ui.html

## 核心接口
| 方法 | 路径 | 权限 |
|------|------|------|
| POST | /api/auth/register | 公开（手机号+验证码，错误限 5 次） |
| POST | /api/auth/login | 公开（JWT + 角色；连续失败 5 次锁 15 分钟） |
| GET | /api/auth/code?phone= | 公开（验证码打印到后端日志） |
| PUT | /api/users/profile | 登录用户（修改个人资料） |
| PUT | /api/users/password | 登录用户（修改密码，校验原密码） |
| GET | /api/tutors | 公开（分页+多条件筛选） |
| GET | /api/tutors/{id} | 公开（详情含评分/评价数/完成单数） |
| POST/PUT | /api/tutors | 老师（发布/编辑，参数校验后待审核） |
| GET | /api/tutors/income?month=yyyy-MM | 老师（按月收入统计） |
| POST | /api/appointments | 学生（提交预约；日期≥今天、时段格式校验） |
| PUT | /api/appointments/{id}/confirm | 老师（接单） |
| PUT | /api/appointments/{id}/reject | 老师（拒绝） |
| PUT | /api/appointments/{id}/start | 老师（开始授课，已预约→授课中） |
| PUT | /api/appointments/{id}/cancel | 学生（取消，仅待确认） |
| PUT | /api/appointments/{id}/finish | 学生（确认完成，仅授课中） |
| POST | /api/evaluations | 学生（仅可评已完成订单，1-5 分，自动重算评分） |
| GET | /api/evaluations/tutor/{userId} | 公开（老师评价分页） |
| GET | /api/admin/orders?status= | 管理员（订单监管/筛选） |
| GET | /api/admin/users、/stats、/logs、/tutors/pending | 管理员 |
| PUT | /api/admin/tutors/{id}/audit?pass= | 管理员（审核家教） |
| POST/PUT/DELETE | /api/admin/subjects | 管理员（科目分类管理） |
| GET/POST/PUT/DELETE | /api/messages | 登录用户（站内消息，部分） |

## 分层说明
- controller：参数校验 + 调 service + 返回 VO
- service/impl：业务编排、事务；register/下单含唯一键冲突精确提示
- **domain**：核心业务规则（OrderDomain 订单状态机，脱离框架可单测）
- mapper：MyBatis-Plus 数据访问（含并发防重复接单的 updateStatusIf）
- security：JWT 签发/校验 + @RequireRole 角色拦截
- common：全局异常处理（参数/类型/JSON/唯一键/JWT 统一转友好提示）、FailLimiter 登录限流（Redis 优先、本地降级）
- config：DataInitializer 演示数据（幂等 + 重复清理）、StartupSecurityCheck 生产配置自检
- 统一返回 {code, message, data}

## 测试
- `mvn test`：订单状态机（合法流转/越权/乱序/文案，7 例）+ FailLimiter 本地降级（4 例）
- 接口链路验证：见项目根 `.tools/test_*.ps1`

## 生产环境注意
- 通过环境变量覆盖敏感配置：JWT_SECRET、MYSQL_HOST、MYSQL_USER、MYSQL_PASSWORD、REDIS_HOST、REDIS_PASSWORD
- 生产环境（`--spring.profiles.active=prod`）强制校验：`app.sms-mock` 必须为 false、JWT 密钥必须 ≥32 位随机值，否则启动自检直接拒绝启动
- 移动端与 Web 端共用同一套接口，登录返回 role 字段，客户端按角色分发页面（全角色双端登录）
