# Orth

分布式任务调度框架，集中调度、分布式执行。

[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-green.svg)](https://spring.io/projects/spring-boot)

[English](README.md)

## 简介

Orth 是 [xuxueli](https://github.com/xuxueli) 的 [XXL-JOB](https://github.com/xuxueli/xxl-job) 的 fork，作为 **Abyss** 项目的一部分进行重构，针对高性能 Python 批量数据采集和低延迟流式任务调度进行了优化。

与上游 XXL-JOB 的主要差异：

- 全新 React 19 + TypeScript + Ant Design 5 管理后台（替换原 JSP 服务端渲染）
- JWT 认证 + 基于角色的访问控制
- SuperTask 模式，支持批量任务管理
- 带逻辑调度时间的批量触发功能
- 任务导入/导出（JSON 格式）
- 并发执行阻塞策略
- OpenAPI 优先设计
- 全英文代码库和文档

## 核心特性

### 调度引擎

- **时间轮算法** — 秒级精度调度，60 槽位环形缓冲区，5 秒预读窗口
- **调度类型** — CRON 表达式、固定频率、仅 API 触发
- **自适应线程池** — 快线程池（200 线程）和慢线程池（100 线程），根据超时历史自动迁移
- **分布式锁** — `SELECT ... FOR UPDATE` 确保集群中单调度器一致性
- **失火策略** — 忽略（`DO_NOTHING`）或立即执行（`FIRE_ONCE_NOW`）

### 执行

- **9+ 路由策略** — FIRST、LAST、ROUND、RANDOM、CONSISTENT_HASH、LFU、LRU、FAILOVER、BUSYOVER
- **分片广播** — 在所有执行器上执行，传递分片索引和总数参数
- **4 种阻塞策略** — 串行等待、丢弃后续、覆盖之前、并发执行
- **任务类型** — Bean、GLUE (Groovy)、脚本 (Shell/Python/Node.js/PHP/PowerShell)、HTTP、Command
- **超时和重试** — 按任务配置，带执行日志追踪

### 运维

- **SuperTask 模式** — 定义模板任务，批量复制子任务并设置不同参数
- **批量触发** — 指定调度时间范围触发任务，用于数据回补
- **导入/导出** — 基于 JSON 的任务配置迁移
- **滚动日志** — 通过 Netty 从执行器实时流式传输日志
- **GLUE Web IDE** — 在管理后台直接编辑 Groovy 任务源码

### 管理后台

- **React 19 + TypeScript + Ant Design 5** — 现代 SPA，Vite 构建
- **JWT 认证** — 无状态认证，基于角色的访问控制（管理员/普通用户）
- **暗色模式** — 跟随系统的主题切换
- **国际化** — 中英文双语，基于 i18next
- **移动端适配** — 自适应布局
- **仪表盘** — 任务执行图表和调度器状态概览

### 基础设施

- **心跳发现** — 执行器每 30 秒注册一次，90 秒超时判定失效
- **Docker Compose** — 全栈部署，包含 Admin、UI (Nginx) 和 Worker 容器
- **OpenAPI** — RESTful 管理 API

## 架构

详细架构文档参见 [`arch/`](arch/) 目录：

| 文档 | 主题 |
|------|------|
| [01 — 注册与发现](arch/01-registry-and-discovery.md) | 心跳协议、自动注册、故障检测 |
| [02 — API 与 RPC](arch/02-api-and-rpc.md) | REST API 设计、Netty RPC、序列化 |
| [03 — 调度与失火](arch/03-scheduling-and-misfire.md) | 时间轮算法、预读、失火处理 |
| [04 — 执行器实现](arch/04-executor-implementation.md) | 任务线程生命周期、处理器类型、阻塞策略 |
| [05 — 日志管理](arch/05-log-management.md) | 滚动日志、日志流、保留策略 |
| [06 — 数据库层](arch/06-database-layer.md) | Schema 设计、分布式锁、查询模式 |
| [07 — 关键分析](arch/07-critical-analysis.md) | 瓶颈、故障模式、改进方向 |
| [08 — SuperTask 模式](arch/08-supertask-pattern.md) | 模板任务、批量复制、参数变体 |
| [09 — 批量触发](arch/09-batch-trigger-schedule-time.md) | 调度时间触发、数据回补 |
| [10 — 导入/导出](arch/10-import-export-configuration.md) | JSON 任务配置迁移 |
| [11 — 前端架构](arch/11-frontend-architecture.md) | React UI 设计、状态管理、国际化 |

## 快速开始

### 前置条件

- Docker 和 Docker Compose
- MySQL 8.0+（或使用已有实例）
- Java 17+ 和 Maven 3.8+（从源码构建时需要）

### Docker Compose 部署

1. 克隆仓库：

   ```bash
   git clone https://github.com/zombie12138/Orth.git
   cd Orth
   ```

2. 配置 `.env`（按需修改 MySQL 连接和 access token）：

   ```bash
   cp .env.example .env   # 或直接编辑 .env
   ```

3. 构建并启动：

   ```bash
   mvn clean package -DskipTests
   cd orth-ui && pnpm install && pnpm build && cd ..
   docker-compose up -d
   ```

4. 访问管理后台 `http://localhost:18081/orth-admin`
   - 默认账号：`admin` / `123456`

### 从源码构建

```bash
# 构建所有模块
mvn clean install

# 单独运行 Admin（需要 MySQL）
cd orth-admin && mvn spring-boot:run

# 运行 UI 开发服务器
cd orth-ui && pnpm install && pnpm dev
```

## 配置说明

关键环境变量（在 `.env` 中配置）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_HOST` | `172.17.0.1` | MySQL 主机地址 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DB` | `orth_job` | 数据库名 |
| `MYSQL_USER` | `orth` | 数据库用户 |
| `ORTH_JOB_ACCESS_TOKEN` | `orth-secret-token` | Admin 与执行器之间的共享令牌 |
| `ADMIN_HTTP_PORT` | `18080` | Admin 服务器端口（宿主机） |
| `UI_HTTP_PORT` | `18081` | UI Nginx 端口（宿主机） |
| `ADMIN_DEBUG_PORT` | `15005` | Admin JVM 调试端口 |

## 模块结构

```
orth/
├── orth-core/                     # 核心库（执行器框架、OpenAPI、处理器）
├── orth-admin/                    # 调度中心（Spring Boot）
├── orth-ui/                       # 管理后台（React 19 + TypeScript + Ant Design 5）
├── orth-executor-samples/
│   ├── orth-executor-sample-springboot/       # 标准 Spring Boot 执行器
│   ├── orth-executor-sample-springboot-ai/    # AI 集成示例（Ollama、Dify）
│   └── orth-executor-sample-frameless/        # 独立执行器（无框架依赖）
├── arch/                          # 架构文档（11 篇）
├── docker-compose.yml             # 全栈部署配置
└── .env                           # 环境变量配置
```

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端 | Java | 17 |
| 框架 | Spring Boot | 3.5.8 |
| ORM | MyBatis | 3.0.5 |
| RPC | Netty | 4.2.7 |
| 脚本 | Groovy | 5.0.2 |
| 前端 | React + TypeScript | 19.0 |
| UI 组件库 | Ant Design | 5.23 |
| 构建工具 | Vite | 6.1 |
| 数据库 | MySQL | 8.0+ |
| 序列化 | Gson | 2.13 |

## 参与贡献

欢迎贡献代码。提交 Pull Request 前，请先开 Issue 讨论重大变更。

- [Issue Tracker](https://github.com/zombie12138/Orth/issues)

## 许可证与致谢

Orth 基于 [GNU 通用公共许可证 v3.0](LICENSE) 发布。

Orth 是 [xuxueli](https://github.com/xuxueli) 的 [XXL-JOB](https://github.com/xuxueli/xxl-job) 的 fork，原项目同样基于 GPLv3 许可。详见 [NOTICE](NOTICE) 了解完整的归属声明和修改说明。
