# 📖 番茄小说阅读器 (Fanqie Web Reader)

一个基于 Web 的番茄小说在线阅读器，核心解决了番茄小说网站**字体混淆反爬**问题，通过逆向解析 PUA 编码字体文件还原真实文本，提供完整的搜索、书架、阅读体验。

## ✨ 功能特性

- **🔍 小说搜索** — 按书名/作者搜索，展示封面、评分、字数、章节数、连载状态
- **📚 书籍详情** — 完整元信息、可展开简介、作者主页、相关推荐
- **📑 章节导航** — 按卷分组、章节搜索/筛选、已读/未读标记、续读入口
- **📖 三种阅读模式** — 滚动模式（无限自动翻页）、翻页模式（滑动/点击）、无动画模式
- **🎨 四套阅读主题** — 默认（暖纸）、护眼黄、护眼绿、暗黑，支持跟随系统自动切换
- **🔤 排版调节** — 字号 14–28px、行高 1.4–2.4、三种字体（黑体/宋体/楷体）
- **👆 滑动手势** — 触屏左右滑动翻页，实时拖拽预览
- **📋 书架管理** — 本地存储书架，圆形进度环展示阅读进度
- **💬 评论系统** — 书评与段评，支持嵌套回复
- **📲 PWA 支持** — Service Worker 缓存优先策略，离线可用
- **💀 骨架屏** — 全局 Shimmer 骨架屏加载状态

## 🔧 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Python 3.12 + FastAPI + Uvicorn |
| HTTP 客户端 | httpx (async) + beautifulsoup4 + lxml |
| 字体解析 | fontTools (ttLib) |
| 前端 | 原生 HTML / CSS / JavaScript（无框架） |
| 图标 | Lucide Icons (CDN) |
| 样式 | CSS 自定义属性、Grid/Flexbox、View Transitions API |
| 存储 | IndexedDB (内容缓存) + localStorage (书架/设置/历史) |
| PWA | Service Worker (cache-first) |

## 🚀 快速开始

### 本地启动

```bash
# 1. 克隆项目
git clone https://github.com/your-username/fanqie-web-reader.git
cd fanqie-web-reader

# 2. 创建虚拟环境（推荐）
python -m venv .venv
.venv\Scripts\activate      # Windows
# source .venv/bin/activate  # Linux/Mac

# 3. 安装依赖
pip install -r requirements.txt

# 4. 启动服务
python server.py
```

启动后访问 [http://localhost:8080](http://localhost:8080)

### Docker 启动

```bash
docker compose up -d
```

启动后访问 [http://localhost:8199](http://localhost:8199)

### 段评功能说明

段评（段落评论）默认使用 **Mock 数据**，无需额外配置即可体验界面。

如需获取真实段评数据，需自行部署 [fqnovel-unidbg](https://github.com/mtongle/fqnovel-unidbg) 后端服务。部署后通过环境变量配置：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `PARA_COMMENT_MOCK` | `true` | 设为 `false` 启用真实段评接口 |
| `UNIDBG_API` | `http://127.0.0.1:8099` | unidbg 签名代理地址 |

本地启动示例：

```bash
PARA_COMMENT_MOCK=false UNIDBG_API=http://127.0.0.1:8099 python server.py
```

Docker Compose 已预配置 Redis 和环境变量，只需将 `PARA_COMMENT_MOCK` 改为 `false` 并确保 unidbg 服务可达即可。

## 📁 项目结构

```
fanqie-web-reader/
├── server.py                 # FastAPI 后端（所有 API 路由）
├── font_decoder.py           # 字体 PUA→汉字 解码器
├── charset.json              # 预置字符映射表（2 组，共 743 字）
├── requirements.txt          # Python 依赖
│
└── static/
    ├── index.html            # SPA 入口
    ├── sw.js                 # Service Worker
    ├── manifest.json         # PWA 清单
    ├── js/
    │   ├── app.js            # 核心工具：IndexedDB、主题、字体、设置、骨架屏
    │   ├── views.js          # UI 渲染：首页、搜索、书架、详情、作者、评论
    │   ├── reader.js         # 阅读器引擎：滚动/翻页/手势/章节覆盖层
    │   ├── cache.js          # LRU + IndexedDB 缓存
    │   └── main.js           # Hash 路由、搜索逻辑、全局事件
    └── css/
        ├── base.css          # 重置、主题变量（4 套）、骨架屏动画
        ├── layout.css        # 头部、搜索栏、底部导航
        ├── home.css          # 书卡、续读环、书架网格、发现区
        ├── detail.css        # 详情、封面翻转、章节列表、卷分组
        ├── reader.css        # 阅读器主题、设置面板、点击区域
        └── components.css    # 评论、图片查看器、动画
```

## 🔠 字体解码

番茄小说使用自定义字体混淆文本内容，本项目通过解析字体文件中的字符映射关系还原真实文本。

## 🌐 API 接口

本项目依赖社区维护的番茄小说 API 服务，本地服务提供 `/api/` 前缀的代理接口，支持搜索、详情、章节、正文、评论等功能。

> ⚠️ 社区 API 为第三方非官方服务，可能随时变更或下线，不保证可用性和稳定性。

## ⚙️ 配置

### 后端（server.py）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 端口 | `8080` | 服务监听端口 |
| CORS | 全部允许 | 跨域策略 |

### 前端设置（localStorage 持久化）

| 键名 | 默认值 | 说明 |
|---|---|---|
| `readerTheme` | `auto` | 阅读主题：default / sepia / green / dark / auto |
| `readerFont` | `sans` | 字体：sans（黑体）/ serif（宋体）/ kai（楷体） |
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
- 番茄小说社区 API — 上游接口服务
- [fqnovel-unidbg](https://github.com/mtongle/fqnovel-unidbg) — 段评接口 X-Argus 签名代理（unidbg 补环境方案）
- [FQToolBox](https://github.com/jackwd387/FQToolBox) — 作者信息接口参考
- [fontTools](https://github.com/fonttools/fonttools) — 字体文件解析
- [Lucide](https://lucide.dev/) — 图标库
- [FastAPI](https://fastapi.tiangolo.com/) — 后端框架

## ⚠️ 免责声明

本项目仅供个人学习与技术研究使用。所有小说内容的版权归番茄小说及原作者所有。

- 本项目通过解析字体映射还原文本，涉及对平台反爬机制的研究，可能违反番茄小说用户协议，使用者需自行承担相关风险
- 不得将本项目用于批量抓取、转载、分发或任何侵犯原作者版权的行为
- 本项目不向用户收取任何费用，也不提供任何付费服务
- 如番茄小说官方认为本项目存在侵权，请联系作者删除
- 使用者应遵守相关平台规则与当地法律法规，因不当使用造成的后果由使用者自行承担

## 📄 License

[AGPL-3.0](LICENSE)
