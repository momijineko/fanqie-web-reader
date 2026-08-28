# 📖 番茄小说阅读器 (Fanqie Web Reader)

一个基于 Web 的番茄小说在线阅读器，支持 Cookie 登录同步云端书架、分组管理、阅读进度同步、四种阅读主题及墨水屏模式。

## ✨ 功能特性

### 书架与云端
- **🔐 Cookie 登录** — 通过番茄网站 Cookie 登录，同步在线书架数据
- **📂 分组管理** — 云端书架支持文件夹分组，长按书籍可移动/新建分组/移出书架
- **🔄 进度同步** — 阅读时自动上报进度到番茄服务器，云端进度同步回本地
- **📥 收藏同步** — 登录后收藏同时加入云端书架

### 阅读体验
- **📖 三种阅读模式** — 滚动模式（无限自动翻页）、翻页模式（滑动/点击）、无动画模式
- **🎨 五套阅读主题** — 默认（暖纸）、羊皮纸、护眼绿、暗黑、墨水屏
- **🖤 墨水屏模式** — 高对比黑白、全局禁止动画、整页翻动、工具栏透明
- **🔤 排版调节** — 字号 14–28px、行高 1.4–2.4、三种字体（黑体/宋体/楷体）
- **💬 段评系统** — 段落评论阅读，支持嵌套回复

### 浏览与发现
- **🔍 小说搜索** — 按书名/作者搜索，展示封面、评分、字数
- **📚 书籍详情** — 封面、简介、章节目录、续读定位、已读进度
- **🏷️ 封面标签** — 连载中/已完结/有更新/已断更/X章未读
- **🔎 书架筛选** — 本地/云端书架实时搜索，CSS 动画无重载
- **📱 PWA 支持** — Service Worker 缓存，离线可用

## 🔧 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Python 3.12 + FastAPI + Uvicorn |
| HTTP 客户端 | httpx (async) |
| 前端 | 原生 HTML / CSS / JavaScript（无框架） |
| 图标 | Lucide Icons (CDN) |
| 样式 | CSS 自定义属性、Grid/Flexbox、View Transitions API |
| 存储 | localStorage (书架/设置/历史/缓存) + IndexedDB (内容/进度) |
| PWA | Service Worker (cache-first + 图片缓存) |
| 热重载 | WebSocket + 文件轮询 |

## 🚀 快速开始

### 本地启动

一键脚本（推荐）：

- **Windows**：双击 `start.bat`（或在 cmd 中运行）
- **macOS / Linux**：`bash start.sh`

脚本会自动完成：创建 `.venv` 并安装依赖 → 检测 Java（缺失或版本 21+ 时**自动下载 Temurin 17 JRE 到 `jre/` 目录，约 45MB，不污染系统**）→ 下载并校验 unidbg jar（v0.0.6，**约 351MB**，存到 `unidbg/`，国内走 gh-proxy 加速）→ 后台启动 unidbg 并等待就绪 → 启动 Web 服务并打开浏览器。所有下载仅首次需要，之后直接复用。

可用环境变量：`SKIP_UNIDBG=1` 跳过 unidbg（降级社区 API 数据源）、`SKIP_JRE=1` 跳过 JRE 自动下载（改用手动安装）。脚本会自动加载项目根目录的 `.env`（与 Docker 共用同一份配置，系统已设的变量优先）。

> **Java 运行时（JRE 即可，无需 JDK）**：脚本会自动下载 Temurin 17 到项目 `jre/` 目录；也可手动安装（11 或 17，勿用 21+）。Redis 可选，仅全本下载类功能需要。重复运行脚本时若 8080 端口已被占用会直接提示，不会重复启动。

手动方式（等效）：

```bash
# 1. 克隆项目
git clone https://github.com/momijineko/fanqie-web-reader.git
cd fanqie-web-reader

# 2. 创建虚拟环境（推荐）
python -m venv .venv
.venv\Scripts\activate      # Windows
# source .venv/bin/activate  # Linux/Mac

# 3. 安装依赖
pip install -r requirements.txt   # 已包含 fonttools / watchfiles / websockets

# 4. 启动服务
python server.py
```

启动后访问 [http://localhost:8080](http://localhost:8080)

> 端口说明：本地 `python server.py` 监听 **8080**；Docker 镜像（`Dockerfile`/`docker-compose.yml`）监听 **8199**。两套端口刻意分开，避免本地开发与容器实例冲突。生产部署见 `.github/workflows/deploy.yml`。

### Cookie 登录

1. 在浏览器打开 [fanqienovel.com](https://fanqienovel.com) 并登录账号
2. 按 F12 → Network → 刷新页面
3. 点击路径以 `/api` 开头的请求 → Headers → Request Headers → 复制 `Cookie:` 行值
4. 在应用"我的"页面粘贴 Cookie 完成登录

### 数据源与段评

搜索 / 详情 / 章节目录 / 正文的优先数据源是 [fqnovel-unidbg](https://github.com/mtongle/fqnovel-unidbg) 签名代理（番茄 App 协议，自带风控签名，不再依赖第三方社区 API 存活）；unidbg 不可用时自动回退到社区 API。段评同样依赖 unidbg：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `UNIDBG_API` | `http://127.0.0.1:8099` | unidbg 签名代理地址（搜索/详情/正文/段评） |
| `PARA_COMMENT_MOCK` | `false` | **仅供开发调试**：没启动 unidbg 时预览段评格式/样式（假数据），日常使用与生产环境保持 `false` |
| `COMMUNITY_API` | `http://101.35.133.34:5000,https://tt.sjmyzq.cn` | 备用社区 API，逗号分隔多实例按序轮询（unidbg 不可用时兜底） |
| `UNIDBG_ADMIN_PASSWORD` | 无 | Docker 部署必填；可用 `openssl rand -base64 32` 生成 |

## 📁 项目结构

```
fanqie-web-reader/
├── server.py                 # FastAPI 入口（生命周期、路由挂载、HMR）
├── shared.py                 # 共享状态：HTTP 客户端、常量、Cookie 工具
├── routers/
│   ├── books.py              # 搜索、章节、正文、详情
│   ├── comments.py           # 书评、段评、Mock 数据
│   └── user.py               # Cookie 管理、用户信息、书架同步、进度上报
├── requirements.txt          # Python 依赖
│
└── static/
    ├── index.html            # SPA 入口
    ├── sw.js                 # Service Worker（含图片缓存）
    ├── manifest.json         # PWA 清单
    ├── js/
    │   ├── app.js            # 全局工具、主题、字体、墨水屏、封面缓存
    │   ├── views.js          # UI 渲染：书架、详情、作者、评论、分组操作
    │   ├── reader.js         # 阅读器引擎：滚动/翻页/手势/段评/工具栏
    │   ├── cache.js          # LRU + IndexedDB 缓存 + 图片缓存
    │   └── main.js           # Hash 路由、搜索、全局事件
    └── css/
        ├── base.css          # 重置、6 套主题、骨架屏、墨水屏
        ├── layout.css        # 头部、搜索栏、底部导航、回到顶部
        ├── home.css          # 书卡、书架网格、分组文件夹、设置、弹窗
        ├── detail.css        # 详情、封面翻转、章节列表
        ├── reader.css        # 阅读器、设置面板、段评面板
        └── components.css    # 评论、图片查看器
```

## ⚙️ 配置

### 后端

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 端口 | `8080`（本地）/ `8199`（Docker） | 服务监听端口 |
| `CORS_ORIGINS` | 本地常用端口 | 逗号分隔的允许来源；设为 `*` 则关闭凭证模式 |
| `COMMUNITY_API` | `http://101.35.133.34:5000,https://tt.sjmyzq.cn` | 备用社区 API，逗号分隔多实例按序轮询（unidbg 不可用时兜底） |
| `REDIS_PASSWORD` | `fanqie_unidbg_2026` | unidbg 使用的 Redis 密码（Python 端不直接使用） |
| `UNIDBG_ADMIN_PASSWORD` | 无 | unidbg 管理后台密码，禁止使用上游默认值 `admin123` |
| `LOG_LEVEL` | `INFO` | 日志级别 |
| `COOKIE_FILE` | `user_cookie.json` | Cookie 存储文件（已 gitignore） |

### 前端设置（localStorage）

| 键名 | 默认值 | 说明 |
|---|---|---|
| `readerTheme` | `auto` | 阅读主题：default / sepia / green / dark / auto |
| `einkMode` | `off` | 墨水屏模式：on / off |
| `readerFont` | `sans` | 字体：sans / serif / kai |
| `fontSize` | `17` | 字号 14–28px |
| `lineHeight` | `1.85` | 行高 1.4–2.4 |
| `readMode` | `page` | 阅读模式：page / scroll / no-anim |

## 🔗 推荐

💡 [OpenCode](https://opencode.ai) — 本项目辅助开发工具，[使用邀请链接注册](https://opencode.ai/go?ref=RZ04W6NJYV) 双方各获 $5 额度

🚀 [方舟 Coding Plan](https://volcengine.com/L/3H9VZa1bq1s/) — 支持 GLM-5.2、Kimi-K2.7、MiniMax-M3、DeepSeek-V4、Doubao-Seed-2.0 等模型，订阅叠加 9.5 折低至 9.4 元，邀请码：`EMXDHE8B`

🧩 [智谱 Coding Plan](https://www.bigmodel.cn/glm-coding?ic=DPYG6NTSNI) — 国内顶流编程大模型，20+ 主流工具全适配，性价比拉满（笑死，根本抢不到）

🌐 [Nube.sh](https://nube.sh/invite/660603280ZQ7QF) — 高性价比且强劲的弹性云服务器，基于 Zen 3 EPYC，1 vCPU + 1 GB DDR4 每月仅 $1.09 起

## 🙏 致谢

- [番茄小说](https://fanqienovel.com/) — 内容来源平台
- [fqnovel-unidbg](https://github.com/mtongle/fqnovel-unidbg) — 主数据源与段评签名代理
- 番茄小说社区 API — 备用上游接口服务
- [FQToolBox](https://github.com/jackwd387/FQToolBox) — 作者信息接口参考
- [Lucide](https://lucide.dev/) — 图标库
- [FastAPI](https://fastapi.tiangolo.com/) — 后端框架

## ⚠️ 免责声明

本项目仅供个人学习与技术研究使用。所有小说内容的版权归番茄小说及原作者所有。

- 本项目涉及对平台反爬机制的研究，可能违反番茄小说用户协议
- 不得用于批量抓取、转载、分发或任何侵犯原作者版权的行为
- 本项目不向用户收取任何费用
- 如番茄小说官方认为存在侵权，请联系作者删除
- 使用者应遵守相关平台规则与当地法律法规

## 📄 License

[AGPL-3.0](LICENSE)
