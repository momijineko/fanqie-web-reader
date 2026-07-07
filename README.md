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
| 字体解析 | fontTools (ttLib) |
| 前端 | 原生 HTML / CSS / JavaScript（无框架） |
| 图标 | Lucide Icons (CDN) |
| 样式 | CSS 自定义属性、Grid/Flexbox、View Transitions API |
| 存储 | localStorage (书架/设置/历史/缓存) + IndexedDB (内容/进度) |
| PWA | Service Worker (cache-first + 图片缓存) |
| 热重载 | WebSocket + 文件轮询 |

## 🚀 快速开始

### 本地启动

```bash
# 1. 克隆项目
git clone https://github.com/momijineko/fanqie-web-reader.git
cd fanqie-web-reader

# 2. 创建虚拟环境（推荐）
python -m venv .venv
.venv\Scripts\activate      # Windows
# source .venv/bin/activate  # Linux/Mac

# 3. 安装依赖
pip install -r requirements.txt
pip install websockets watchfiles  # HMR 热重载（可选）

# 4. 启动服务
python server.py
```

启动后访问 [http://localhost:8080](http://localhost:8080)

### Cookie 登录

1. 在浏览器打开 [fanqienovel.com](https://fanqienovel.com) 并登录账号
2. 按 F12 → Network → 刷新页面
3. 点击路径以 `/api` 开头的请求 → Headers → Request Headers → 复制 `Cookie:` 行值
4. 在应用"我的"页面粘贴 Cookie 完成登录

### 段评功能

段评默认使用 Mock 数据。如需真实数据，需部署 [fqnovel-unidbg](https://github.com/mtongle/fqnovel-unidbg)：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `PARA_COMMENT_MOCK` | `true` | 设为 `false` 启用真实段评 |
| `UNIDBG_API` | `http://127.0.0.1:8099` | unidbg 签名代理地址 |

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
| 端口 | `8080` | 服务监听端口 |
| `COOKIE_FILE` | `user_cookie.json` | Cookie 存储文件（已 gitignore） |
| `COMMUNITY_API` | `http://101.35.133.34:5000` | 社区 API 地址 |

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
- 番茄小说社区 API — 上游接口服务
- [fqnovel-unidbg](https://github.com/mtongle/fqnovel-unidbg) — 段评接口签名代理
- [FQToolBox](https://github.com/jackwd387/FQToolBox) — 作者信息接口参考
- [fontTools](https://github.com/fonttools/fonttools) — 字体文件解析
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
