# Task.md —— 编码任务执行计划（纵向切片 · 可见性驱动）

> **地位**：本文件是唯一**编码执行计划**。它把 README §9 / doc/00 §8 的「Phase 1–8」从**横切分层顺序**重组为「**纵向切片里程碑**」（每条功能主线立即端到端跑通）；Phase 表保留为模块覆盖参考，**执行顺序以本文件为准**。
> 设计细节一律以各 doc 为准，本文件只负责**拆步、排序、验收**与**风险早期暴露**。编码中发现设计假设被推翻时，回写对应 doc 勘误位（见 §4 工作规则）。
> 当前工程起点：官方 Wear Compose Starter（`MainActivity.kt` + `Theme.kt`），业务代码为零；`assets/sprite/` 1339 张素材已就位。

---

## 1. 为什么要纵向切片（方法论，用户决策）

**不做「一次性把整层写完」**：按层横切（先写完全部 domain → 再写 data → 再写 UI）会让项目长时间没有可运行画面——大量代码写完才发现**一开始的设计假设错了**（朝向映射、圆屏裁切、数值手感、启动时序……），返工成本随层数放大。

**改为「一条主线从头穿到尾」**，每条功能主线都按用户定的三拍节奏推进：

| 拍 | 内容 | 可见性 |
|---|---|---|
| ① 架子 | 该功能线所需的**最小 UI 壳**，先用占位/假数据把整条链路「壳」跑起来 | 能跑能看 |
| ② 基建 | 对应的 domain/data 纯逻辑 + 单测（本拍通常没有画面增量） | 能编译 + 单测绿 |
| ③ 上层使用 | 把真实数据接入 ① 的架子，形成该线的**端到端闭环演示** | 再次能跑能玩 |

**里程碑纪律（≤2 步）**：相邻两个里程碑之间最多 2 个 Step，且**每个里程碑都以「能运行看到效果」（或可见演示）收盘**。基建步（②）允许画面不推进，但**紧邻的下一步必须接入可见**——全项目不存在「连做 3 步以上看不见任何东西」的区间。这样可以最快发现设计错误，且每次发现都发生在小步内、可低成本修正。

**风险前置纪律**：所有 doc 标注「待实测 / 风险项」（素材朝向与帧序、首帧黑闪、圆屏裁切、64×64 放大观感、打开频率数值手感……）全部**压到最早能验证它的 Step**（见 §3 各里程碑「风险检查点」，及 §6 汇总）。

---

## 2. 验收分级与全局工程规范

### 2.1 验收分级（每 Step 必须满足其一才可收盘）

| 档 | 含义 | 判定方式 |
|---|---|---|
| P0 能跑 | 安装到真机/模拟器，按「演示路径」可复现可见结果 | 手测 + 录屏 |
| P1 能测 | 基建步：工程编译绿 + 本步新增 domain 单测全绿 | `./gradlew testDebugUnitTest` |
| P2 能走查 | 性能/清单类（08 §5） | 工具走查记录 |

> 所有 Step 结束时**工程必须可编译运行**——基建步（P1）只是画面不推进，不是留红。

### 2.2 全局技术约束（每步都遵守，违规即返工）

1. `domain/` 零 Android 依赖，纯 Kotlin + 可单测（README §6.9）。
2. 唯一持久化 = `PetStore` SP（schema v2，01 §9）：M1–M10 为**单键当前宠快照**；M15 起扩展为「当前档 + 墓碑档（换宠归档，仍只存快照、不落日志）」，见 §3 详节 M15；**SessionLog 只存内存**，不落盘（04 §3.1）；`stats` 里程碑计数随档落盘。
3. **无** AlarmManager / 后台 Service / 定时通知；状态推进只发生在「打开即结算」（08 §3）。
4. 一切 UI 走 RoundSafe 圆屏组件 + 胶囊两级（实心=可点 / 空心=不可点，06 §8.2/§8.3）。
5. 配色一律 `ColorToken`（06 §4.1）；语义色莫奈低饱和（06 §4）；页面主题背景 = RadialGradient、最外圈恒为 `#000000`（06 §4.2）。
6. **范围纪律**：M1–M10 只做 doc/09 §3 主链功能集内功能；扩展位（清洁/学习/玩具/便条/档案/小游戏）一律不提前混入主链，按 §3 详节 M11–M16 逐个收盘。
7. **技术主选型（用户决策，doc/00 T-19/T-20、ADR-17）**：功能实现以 **Compose 为主**（UI / 动画 / 渲染均在 Compose 内，doc/07 §8）；UI / 组件优先**官方与系统组件**（Wear Compose Material3 / AndroidX），屏幕兼容遵循官方 Wear UI 建议；**任何 Compose 之外技术或非官方 UI 组件，须先更新本文件并经用户确认后才可引入**——**唯一既定必要例外：启动首帧 Logo 原生 View**（<200ms 硬指标，doc/08 §1）。系统能力调用（插件拉起 Intent / 系统设置等，doc/05）属系统交互而非 UI 技术，不受「Compose 为主」约束，不计入例外。

### 2.3 运行与验证工具（贯穿全程）

- **真机**（必需）：Wear OS 圆形表盘真机用于：首帧观感、渐变外圈与黑边、朝向/放大观感、插件真实探测、功耗。方屏模拟器用于逻辑路径与截图回归。
- **Debug 时间旅行工具**（debug-only，M5.S2 起）：主屏预留 debug 入口，把 `lastSettledAt` 拨回 1h/6h/24h/72h（或直接注入 now），配合重启模拟任意离线窗，是 M5/M6/M7 验收的基石，**不要做成正式 UI**。
- **日志标记**：五阶启动（08 §1）与 settle 全程按阶打点，便于 `am start -W` + logcat 对时序。
- 单测依赖：M3.S1 需新增 `testImplementation(junit)`（当前 `gradle/libs.versions.toml` 未含）。

---

## 3. 里程碑计划（M1–M17：主链 20 步 + 扩展 14 步，每里程碑 ≤ 2 步）

### 3.1 总览与依赖

```
M1 壳(可跑) ─┬→ M2 真宠上屏(可跑) ─→ M3 建档与数据闭环(可跑)
             │                                │
             │                     M4 宠物活起来(可跑) ─┐
             │                                │        │
             │                     M5 时间流逝·打开即结算(可跑) ──→ M6 照顾闭环(可玩)
             │                                                       │
             │                                 M7 离线叙事·回放开场(可跑) ←┘
             │
             └→ M8 右滑插件清单(可跑，壳层独立、可随时并行)
             
M9 在线惊喜(随机事件+会话回顾，可跑) ─ M10 表现质感 + 性能走查调参
（M9 依赖 M6 的 SessionLog 骨架与 M4 渲染循环；M10 依赖 M4/M6/M7 表现位）

── 扩展段（M11–M17，doc/09 §5 扩展位连续落地，不叫 V2）──
M11 清洁 → M12 学习(智力) → M13 玩具变体 → M14 便条 → M15 收藏档案(墓碑+切换) → M16 休闲小游戏入右滑 → M17 偏好设置(功能清单入口)
（各步都踩在主链对应基建上：M6 动作框架 / M9 回顾 / M10 表现 / M8 插件位；M16 仅依赖 M8，可随时并行；M17 偏好设置依赖 M8 功能清单面板 + M15 墓碑/切换）
```

| 里程碑 | 主题 | 步数 | 收盘演示 | 吸收的原 Phase |
|---|---|---|---|---|
| M1 | 圆屏深色壳 + 手势路由 | 2 | 五阶首帧、三手势切换三 overlay、深色渐变观感 | 1 |
| M2 | 素材事实 + 静态渲染 | 2 | 真宠正确朝向静态上屏 | 1/4 前置 |
| M3 | 建档 + 数据闭环 | 2 | 选宠建档 → 主屏真实快照 → 重开保持 | 2(局部)/3 |
| M4 | FSM + 行走作息 | 2 | 宠物自主活动（走/停/睡） | 2/4 |
| M5 | settle v1 + 五阶 apply | 2 | 时间旅行拨回 → 数值按时间推进、状态推进 | 2/1 收尾 |
| M6 | 照顾动作闭环 | 2 | 喂/玩/抚/治面板可玩、数值落库冷却生效 | 5 |
| M7 | 长离线回放叙事 | 2 | 26h 离线 → 迎接语气 + 回放时间线 | 2 收尾/7 |
| M8 | 右滑插件清单 | 2 | 胶囊列表可点/空心、容错、编辑持久 | 6 |
| M9 | 在线随机事件 + 会话回顾 | 2 | 在线惊喜事件 + 「本次陪伴小结」 | 7 |
| M10 | 表现质感 + 走查调参 | 2 | 情绪形变/短脚本 + 08 §5 走查通过 | 8 |

> 里程碑编号是**建议顺序**。M8 与 M9/M10 相互解耦（M8 只依赖 M1 壳 + RoundSafe），可按团队并行或调前调后；M9/M10 内部部分子任务可互换。

**扩展里程碑（M11–M17）**：对应 doc/09 §5 六个扩展位 + 偏好设置（§5.7），全部续接在主链 M10 之后（**编号连续、不区分版本**，非「V2 阶段」），每块 ≤2 步收盘：

| 里程碑 | 扩展位（doc/09 §5） | 收盘演示 | 依赖主链 |
|---|---|---|---|
| M11 | 清洁/洗澡（§5.3） | 可洗：泡泡/抖毛表现、脏图标清除、冷却 3h | M6 动作框架 / M10 表现 |
| M12 | 学习/智力养成（§5.2） | 可学：智力只增、解锁提示、learner 加权 | M6 / M10 / M11 stats |
| M13 | 玩具变体（§5.1） | 多种玩具有差异表现与收益、来源可解锁 | M6 / M9 事件 / M10 / M12 解锁表 |
| M14 | 便条/邮件（§5.4） | 长按/回顾可见宠物便条（非推送） | M7/M9 会话回顾 + 迎接 |
| M15 | 收藏档案：电子墓碑 + 快照切换（§5.5） | 换宠归档旧宠、可切回、原样恢复 | M3 建档 / M10 stats / M14 |
| M16 | 休闲小游戏入右滑（§5.6） | 右滑清单内可玩一局小游戏 | M8 插件位（可并行） |
| M17 | 偏好设置（§5.7） | 右滑清单「偏好设置」→ 独立设置页；重开宠物 / 墓碑相关设置可用 | M8 功能清单 / M15 墓碑 |

---

### M1 圆屏深色壳 + 手势路由（可跑）

> 目的：把「启动首帧、深色基底 + 同心圆渐变观感、圆屏裁切、三手势可行性」这批**环境级假设**放到第一个可运行里程碑验证。**全程假数据，不碰 domain/data。**

#### M1.S1 工程基座 + 深色壳 + Logo 首帧（P0）

**任务**
- [ ] 按 doc/00 §7 建立四层包目录（`core/ domain/ data/ presentation/`，空模块占位）；`presentation/theme/Color.kt` 起步。
- [ ] `ColorToken`：`bg` 纯黑、`Accent` 近白、`OnAccent`、`Text2`、§4 莫奈低饱和语义色全表、异常预警色（doc/06 §4/§4.1）。
- [ ] `TamagotchiTheme` 落地深色 Material3 scheme（替换 Starter 空主题，删掉演示 Button 列表）。
- [ ] RadialGradient 页面主题背景 helper（doc/06 §4.2）：参数 = 中心色（低饱和深色调）→ 半径扩散 → **最外圈硬性 `#000000`**。
- [ ] `BaseActivity`（doc/08 §2）：`showShell()` = 纯黑 + 居中静态 Logo（原生 View、无 Compose）；`injectContent()` 抽象（Compose addView + 淡入、壳层移除）。
- [ ] 五阶 reveal 骨架：以空实现/占位数据跑通「壳 → 快捷 → 宠物 → 状态环 → 底部 + 结算」的时序调度与打点（doc/08 §1 伪代码）；`bootSettle` 先留空实现接口。
- [ ] RoundSafe 起步：内切半径 + `EdgeFade`（doc/06 §8.2）。
- [ ] `./gradlew` 编译绿、可安装。

**产出**：`presentation/base/BaseActivity.kt`、`presentation/boot/ShellBridge.kt`、`presentation/theme/`（Color/Theme）、组件库骨架、启动打点日志。

**验收（P0）**：冷启动点图标 → <200ms Logo 首帧 → 五阶按序揭示（占位内容）无卡顿黑屏；主屏背景 RadialGradient 外圈与设备黑边**零色差**；删除 Starter 演示列表后编译无残留引用。
**风险检查点**：首帧黑闪 / 注入切换观感（doc/08 §6）——**本里程碑提前暴露**。

#### M1.S2 主屏骨架 + 三 overlay 路由（P0）

**任务**
- [ ] 主屏四区占位（doc/06 §1/§2）：中央活动区占位块 + 主环 `RingProgressBar`（假值三段）+ 顶部状态图标区占位 + 底部入口条占位。
- [ ] 手势骨架（doc/06 §5）：上缘下拉→顶部状态面板 / 下缘上滑→操作面板 / 右缘左滑→右滑功能清单；**不做从左往右**（避开系统返回）；一次一种（互斥）。
- [ ] RoundSafe 组件三件套：`RoundSheet` / `RoundList` / `PillItem`（doc/06 §8.2/§8.3），三个 overlay 用它渲染空内容占位。
- [ ] 返回键 / 外点收起 overlay 兜底。
- [ ] `PetActivity` 挂主屏（先只承载主屏 + 三 overlay 路由）。

**产出**：`presentation/ui/RingProgressBar.kt`、`presentation/component/RoundSafe 三件套`、手势与 overlay 路由。

**验收（P0）**：真机手测——主屏四区可见；上滑/下拉/左滑分别弹对应面板且互斥；外点/返回收起；圆形屏缘文字无裁切（内切安全区生效）；主屏四区与三个 overlay 均按深色渐变规范呈现。
**风险检查点**：圆屏裁切、手势方向冲突——**本里程碑提前暴露**。

---

### M2 真宠上屏：素材事实 + 静态渲染（可跑）

> 目的：doc/02 §2.3 / doc/07 §1 明确「行=朝向 或 列=朝向」**待实测**——这是全局最大的事实性风险，必须在写 FSM/行走前钉死；同时让第一个真宠上屏，建立激励反馈。

#### M2.S1 素材实测 + 切片解码器 + SpriteRepository（P0 debug 屏）

**任务**
- [x] 以 `BULBASAUR.png` 实测 256×256 / 4×4 / 单帧 64×64 的真实布局（doc/07 §1）。
- [ ] Debug-only 朝向核对屏（或日志 dump）：把 16 格子图按网格绘制 + 行列/帧号标签，人工核对「行=朝向、列=帧」还是反置、行走帧序 0→3 是否正确（doc/02 §2.3）。（屏已就绪：Debug 安装长按主屏中央活动区进入，左右翻看；**待真机人工核对**）
- [x] 实测结论写入代码常量表：`dir → row/col`、帧序已落定 `SpriteSheetDecoder`；放大过滤策略 **M2.S2 初定 FilterQuality.None**（近邻复古像素），观感复核留 M10.S2（doc/07 §9）。
- [ ] **若事实与文档描述冲突 → 当日回写 doc/07 §1 / doc/02 §2.3 勘误**。
- [x] `SpriteRepository`（data/sprite）：`AssetManager.list("sprite")` 一次取列表 + 过滤数字/形态后缀（如 `000.png`、`ALCREMIE_12`）；单 Bitmap 池（池=1，doc/07 §6）。帧切取由渲染侧以 `SpriteSheetDecoder.frameRect` srcRect 完成，避免额外分配子 Bitmap。

**产出**：`presentation/render/SpriteSheetDecoder.kt`、`data/sprite/SpriteRepository.kt`、Debug 核对屏。

**验收（P0）**：debug 屏可逐格翻看，实测朝向映射人工确认并在常量落定；扫描 1339 条文件名列表稳定不卡（无预解码）。
**风险检查点**：朝向/帧序事实、文件名过滤规则——**本里程碑钉死**。

#### M2.S2 静态帧上屏（P0）

**任务**
- [x] `PetRenderer` v0：中心化 `dstRect` + 单帧 `drawBitmap` + 圆形活动区裁切遮罩（doc/07 §4/§9，防后续形变露边）。
- [x] IDLE 呼吸最简动效：纵向 ±2px、2s 周期（doc/07 §4.4 层1）。
- [x] 把 M1 主屏中央占位替换为实际宠物静态帧（先用 `BULBASAUR` 走通管线；活动区尺寸与 06 §1 对齐）。

**产出**：`presentation/render/PetRenderer.kt`、主屏中央真宠静态呈现。

**验收（P0）**：主屏中央出现正确朝向的宠物静态立姿、圆屏内无破边；切换任意帧不越界。（双变体编译绿；**目测观感留 M4.S2 行走接入反向复核 + M10.S2 定稿**，见 §6 记录）
**风险检查点**：64×64 放大模糊观感——M2.S2 初定 `FilterQuality.None` 并回写 doc/07 §9，M10.S2 真机定稿。

---

### M3 建档与数据闭环：值对象 → trait → SP → 建档 UI（可跑）

> 目的：建立 domain 数据底座 + 唯一持久化点，并完成**第一条端到端闭环**：建档 → 落盘 → 重开恢复。此后所有里程碑都基于「真实快照」。

#### M3.S1 值对象 + AttributeRegistry + PersonalityGenerator（P1）

**任务**
- [x] 新增单测依赖：`testImplementation(junit)`（libs.versions.toml + app/build.gradle.kts）；建立 `src/test/` 源集与测试基类约定。
- [x] core 值对象：`AttributeId/Meta/AttributeMap`、三类属性注册表 + 默认值 + 只增不减标记（doc/01 §1/§3/§4）；`FoodType` 与口味标签（doc/01 §7）；`PetState`、归一化坐标/方向值对象（doc/02 §1.1/§2.1）。
- [x] `PersonalityGenerator`（domain/generator，doc/02 §4.2/§4.3）：`seed → 6 维 traits(0~1)` + `flavor` 派生 + 描述词映射（仅文案）。
- [x] 单测：同 seed 派生全等且可重复；traits ∈ [0,1]；flavor 命中合法表；描述词主副顺序稳定。

**产出**：`core/` 值对象族、`domain/generator/PersonalityGenerator.kt`、首组 domain 单测。

**验收（P1）**：`testDebugUnitTest` 全绿。
> 本步为「基建」拍：工程编译绿 + 单测绿即收盘，无画面增量可接受（纪律见 §1）。

#### M3.S2 PetStore(SP) + 建档选宠 UI（P0）

**任务**
- [x] `PetStore`（data/store，doc/01 §9）：单键 JSON `pet_profile`、`schemaVersion=2` 全字段读写、缺省字段补默认、覆盖写、`stats`/`cooldowns`/`personality`/`plugins` 完整承载。
- [x] 建档流程 UI（无档首启进入）：Asset 扫描列表按**主名聚合**（`_数字` 形态变体先取默认形态，doc/07 §6）；缩略 Bitmap 池 LRU ~12 张（**绝不全量解码 1339**）；胶囊列表（建档屏为 boot 整屏页，自行留内边距；overlay 侧复用 RoundSafe 语义）。
- [x] 建档动作：随机 seed → 展示派生性格（描述词 + 六维微条）→ 确认 → `PetStore.save` → 回主屏。
- [x] 主屏读档呈现：主环一圈状态 + 中央宠物静态帧（真实快照值，建档宠 + 主环三主属性 + 状态面板全属性）。
- [x] 重开档开发入口（debug-only：清档重来，验证换宠覆盖写）。

**产出**：`data/store/PetStore.kt`、建档屏（`presentation/screen/setup/`）、开档闭环。

**验收（P0）**：手测——首启建档选宠（浏览列表缩略流畅）→ 见描述词与 trait → 确认 → 主屏显示**所选宠 + 真实属性快照** → 杀进程重开 → 原样恢复；换宠建档覆盖写正确。SP 单测：写→读 round-trip 相等、缺省补默认。
**风险检查点**：1339 资产扫描/缩略不卡顿、`stats`/冷却缺省语义——**本里程碑验证**。

---

### M4 宠物活起来：FSM + 切片行走 + 作息（可跑）

> 目的：让宠物「自己活」——纯 domain 的 FSM 与防穿模行走接入渲染循环。这是「AI 决策 → 视觉表现」主链路的首次真实打通。

#### M4.S1 BehaviorFSM 纯函数 + 单测（P1）

**任务**
- [x] `BehaviorFSM.step(now, snapshot): FSMResult`（domain/engine，doc/02 §1/§2/§3，纯函数不碰 UI）。
- [x] 状态集与转移优先级：SICK > 睡眠 > SAD > IDLE/WALKING/EATING/EXCITED（doc/02 §1.2）。
- [x] 随机决策：IDLE 2~4s → WALKING 3~5s（时长按 trait.activity 修正）→ 换向；方向 0..3（doc/02 §2）。
- [x] 防穿模：归一化圆内坐标、触边随机转向、速度 = base × activity（doc/02 §2.1/§2.2）。
- [x] 作息：22:00~07:00 倾向 SLEEPING、本地时区作息时钟（doc/02 §3；深夜互动收益=0 归 M6 动作层判定）。
- [x] 行走帧 0→3 序号推进。
- [x] 单测：状态合法性、pos 永不越圆、作息时段切换、SICK 最高优先、帧序号 ∈ 0..3、同输入可复现。

**产出**：`domain/engine/BehaviorFSM.kt`、`FSMResult` 契约、行走/作息单测。

**验收（P1）**：单测全绿。本步不接 UI（基建拍）。
> 注意：EATING/EXCITED 等**动作触发**状态在 M6 才接，本步先保证 IDLE/WALKING/SLEEPING 决策正确。

#### M4.S2 主循环接线 + PetRenderer v1（P0）

**任务**
- [x] 主循环（doc/07 §5）：`LaunchedEffect + delay(250ms)` → FSM.step → FSMResult 驱动绘制；呼吸/Zzz 相位随 tick 低帧推进（动态 4FPS）；pose 无变化不触发 UI 树重组（Canvas draw 相位读渲染状态，仅重绘）。
- [x] `PetRenderer` v1：按 `state` 选帧用法——IDLE 呼吸、WALKING 方向帧循环 0→3、SLEEPING 暗罩（近似闭眼，素材无睡姿帧）+ Zzz 最简（doc/07 §4，风险已回写 doc/07 §9）。
- [x] 坐标映射：FSM 归一化 pos → Canvas 活动区 dstRect（中心化，02 §2.1；模型=活动区直径×0.5≈0.30 屏直径，不随活动区放大）。
- [x] `onPause`/`onStop` 停循环 / `onResume`/`onStart` 恢复（doc/08 §4）——0 后台 CPU；面板/overlay 打开亦停（不必要重绘）。
- [x] 主环读**真实快照**三主属性并随快照刷新（M5 前数值无时间推进属预期）。
- [x] Bubble 层最小骨架（doc/06 §7）：睡眠 Zzz 即渲染脚本层首个内容；气泡带挂点注释就位，文案事件 M6/M9 接入。

**产出**：`presentation/render/PetRenderer.kt` v1（`PetLivingSprite` + 主循环）、真实快照驱动的状态环。

**验收（P0）**：宠物自主循环（发呆 → 走动 → 触边转向 → 发呆）；帧序流畅无错位（反向验证 M2 朝向常量正确）；22:00 自动入睡 + Zzz；退回后台动画全停；真机功耗待 M10 走查。**代码收盘，编译/单测绿；真机手测与 M2/M3 一并批量补验。**
**风险检查点**：切片行走帧序伪影（doc/07 §9）、作息与真实时钟、睡眠近似观感（doc/07 §9 新增行）——**本里程碑验证（真机）**。

---

### M5 时间流逝成立：打开即结算 settle v1 + 五阶 apply（可跑）

> 目的：把核心玩法另一半「时间推进」接到启动主流程——短离线先走快速积分路径，长离线事件化留 M7。**验证 settle 幂等、五阶 apply 不阻塞、数值手感**，全项目最重要的架构前提在此落地。

#### M5.S1 SettleEngine v1（短窗快速积分）+ 单测（P1）

**任务**
- [x] `Clock` 抽象（data/time：真实实现 + 可注入 debug 假时钟）。
- [x] `SettleEngine`（domain/engine，doc/01 §6/§8.1/§8.2 **只先实现 <20min 快速积分分支**）：幂等（`now <= lastSettledAt` → noOp）；速率表（饱腹 -2/h 白天、作息档、清洁 -0.6/h、智力不衰减）× trait 系数；状态推进（health 触底 → SICK、mood 持续低 → SAD、sat<20 → hungry）；统一 clamp 地板归零不致死；结算后 `lastSettledAt = now` 并落盘。
- [x] `SettlementSummary` 结构（doc/01 §8.3）：elapsedMs / totalDelta / timeline=null / endingMood 先占位。
- [x] 单测：0 区间 noOp；1h 扣减 = 速率×系数；clamp 到 0；SICK/SAD/hungry 推进；**同窗口重复结算幂等**。

**产出**：`domain/engine/SettleEngine.kt`、`data/time/Clock`、`SettlementSummary`。

**验收（P1）**：单测全绿（含幂等断言）。
> 基建拍。注意：长离线（≥20min）时间线在 M7 才实现——M5 先用快速积分统一兜住，保证主链路完整。

#### M5.S2 五阶 ⑤ 接入 + 时间旅行 Debug（P0）

**任务**
- [x] PetActivity 第⑤阶：后台 settle(now) → 主线程一次 apply = `PetStore.save` + 数值层/状态环刷新 + 动作可用性（doc/08 §1/§3；SessionLog 开场段 M7 接）。
- [x] `onResume` 距上次 settle ≥ 5s 再 settle（doc/08 §3 热启动表）。
- [x] **Debug 时间旅行工具**（debug-only）：入口（长按主屏或 debug 浮钮）拨回 `lastSettledAt` 1h/6h/24h/72h；状态图标/主环/动作冷却随 apply 刷新。
- [x] settle 全程 logcat 打点，验证结算不阻塞首帧。

**产出**：结算编排在 `PetActivity.EntryFlow`（持档案事实源的组合层）：冷启动五阶 reveal 完成后 force 结算、热恢复 observer（≥5s 节流）、时间旅行回拨后立即结算；settle+save 放 `Dispatchers.Default`，apply = 主线程 profile state 一次更新（原 BaseActivity.bootSettle 职责移交并移除，doc/08 §2/§3 已回写）。

**验收（P0）**：时间旅行拨回 24h → 重启 → 主环/状态图标按扣减刷新（health 触底则宠物 SICK 表现）；SLEEPING 窗口耗率显著低；首帧 Logo → 状态环先现快照 → settle apply 后数值平滑跳变、全程 UI 可交互。
**风险检查点**：settle 后台时长/不阻塞（08 §6）、主线程 SP 读——**本里程碑验证**。

---

### M6 照顾闭环：操作面板 + ActionRule + 喂玩抚治（可玩）

> 目的：玩家真正能「照顾」——面板动作 → domain 动作规则 → FSM 表现 → 数值落库/冷却生效的完整闭环。自此进入可玩状态。

#### M6.S1 ActionRule + 动作 domain + SessionLog 骨架（P1）

**任务**
- [x] `ActionRule`（doc/01 §10）：可用条件（`now >= 冷却until` 且 属性 < 上限）表 + 边际收益递减（doc/01 §6.3）。
- [x] domain 动作：`onFeed(food)/onPlay/onPet/onHeal`（doc/02 §5 预留位，v1 只实现这四个）：返回新快照 + 动作 FSM 提示（EATING/EXCITED/亲昵）+ delta + `stats` 命中 +1 + 冷却写 next-until（doc/01 §9）。
- [x] 食物类型效果 + 口味契合：`trait.flavor` 对食物权重加成（doc/01 §7 / 02 §4）。
- [x] `SessionLog` 骨架（domain/log，doc/04 §7）：`append` / `liveCount` / `entries`（先只存 list；`openWith`/ReplayEntry M7 扩展）；动作成功即 `append(ACTION_*)`。
- [x] 单测：冷却阻挡、效果边界 clamp、边际收益递减、口味契合命中、**治疗仅 SICK 可点**（health +40）、stats 计数。

**产出**：`domain/engine/Actions.kt`（ActionRule 条件/数值规则 + PetActions 四动作）、`domain/log/SessionLog.kt` 骨架、动作单测。

**验收（P1）**：单测全绿（Actions 18 例 + SessionLog 4 例，全套 58 例通过）。

#### M6.S2 操作/状态面板闭环（P0）

**任务**
- [x] 操作面板 v1（doc/06 §6 / 09 §3）：状态 / 投喂（→食物子页，类别色覆写胶囊）/ 玩耍 / 抚摸；可执行 = **实心胶囊**，冷却中 / 属性满 / 锁定 = **空心胶囊**（只读信息，显示剩余冷却）。
- [x] 状态面板（doc/06 §3.2）：属性「列表 + 进度条 + 颜色 + 图标」；三入口合一（下拉 / 屏顶图标 / 下面板「状态」= 收起下、展开上）；三主属性 + 隐藏智力/清洁只读行；低值预警 < 30 呼吸。
- [x] 状态图标区（doc/06 §3.1）：饥饿 / 不开心 / 脏 / 病按阈值亮位。
- [x] 执行链：点胶囊 → domain 动作 → FSM 状态切换（EATING 咀嚼 / EXCITED 蹦跳最简 / 亲昵歪头）→ 属性浮字 + 气泡 → `PetStore.save`。
- [x] 治疗仅 SICK 出现；SICK 由 M5 状态推进触发（可经时间旅行制造）。

**产出**：`presentation/screen/` 操作面板 + 状态面板 v1、动作执行链。

**验收（P0）**：喂食（选食物类型）→ 转向进食 + 数值按口味/契合 ↑；玩耍 → EXCITED 蹦跳 + mood ↑；抚摸 → 亲昵气泡 + 轻 mood；冷却中空心显示倒计时、到点恢复实心；时间旅行 24h 制造低状态 → 状态面板/图标联动、治疗随 SICK 出现并治愈；杀进程重开冷却与数值保持、`stats` 累积。（**代码收盘：编译绿 + 全套单测绿；真机手测与 M1–M5 一并批量补验**）

---

### M7 离线叙事：长离线事件化 + 回放开场 + 迎接（可跑）

> 目的：把「离线 20min+」从「一次扣减」升级为「可阅读的事件时间线」——doc/01 §8 / 03 §7 / 04 §5/§7 的长线叙事在此落地，是陪伴感核心。

#### M7.S1 OfflineTimelineBuilder + 事件库 + settle 长窗分支（P1）

**任务**
- [ ] 事件初版库（doc/03 §4）：生理骨架事件（饿/渴→sat、困/倦→mood/作息、玩耍好奇等正向）+ 突发扰动池（雷声/惊吓等，正负都可有）。
- [ ] `OfflineTimelineBuilder.build(window, startAttrs, traits)`（doc/03 §7）：时间片分段 + 间隔波动 + trait 命中/节奏 + 两类事件 + **条数封顶 48** + **净值守恒**（骨架消耗 ≈ 速率×trait×时长，突发 |ΣΔ|/24h ≤ 3 且正向略多）+ **同 seed 确定性**。
- [ ] `SettleEngine` 补长窗分支（doc/01 §8.2）：`elapsed ≥ MIN_TIMELINE_MS` 走 timeline → 逐事件应用 → clamp 聚合 → 状态推进 → commit；`SettlementSummary` 携带 timeline + `EndingMood`（doc/03 §7.6）。
- [ ] 单测：确定性（同 seed 同终态）；守恒 |ΣΔ|；48 封顶；clamp 不死；SICK/SAD 推进正确；重复结算同 now 幂等（终态与开场段一致）。

**产出**：`OfflineTimelineBuilder`、事件库初版、settle 长窗分支、时间线单测。

**验收（P1）**：单测全绿。

#### M7.S2 会话开场 + 迎接气泡 + 回放剧 UI（P0）

**任务**
- [ ] `SessionLog.openWith(summary)`（doc/04 §3/§7）：长离线 ReplayEntry 逐条入列开场段 + 一律追加 SETTLE 聚合锚点；与 M6 append 的在线条目同一条时间线。
- [ ] 迎接反馈气泡（doc/04 §5）：离线时长档 × 结尾基调（EndingMood）修正，优先于普通在线事件；不改写状态。
- [ ] 回放剧 UI「在你离开期间发生了…」：RoundSafe 折叠/滚动时间线（ReplayEntry 时间轴样式、highlight 强调、SETTLE 锚点摘要），可从迎接气泡展开（doc/06 §7 气泡 + 折叠面板）。
- [ ] kill 重启验证：日志清空、开场段由新 settle 重铺（04 §3.1 内存语义）；`stats` 跨会话不受影响。

**产出**：`SessionLog.openWith`、迎接气泡、回放剧折叠面板。

**验收（P0）**：时间旅行 26h → 重启 → 迎接语气随 EndingMood（病恹恹 / 委屈 / 睡梦醒）→ 展开看到「离开期间发生了…」时间线（饿着等了 / 被雷吓醒 / 睡了好久）→ 数值守恒呈现；杀进程重开列表清空仅 stats 累计。

---

### M8 右滑功能清单：插件子系统（可跑）

> 目的：壳层「并行第二屏」（05 §7）独立跑通——与宠物玩法完全解耦，因此可随时插入/并行。模拟器大多空心、真机探测实心，天然验证胶囊两级语义。

#### M8.S1 插件模型 + 注册表 + 执行器（P1 + 手测）

**任务**
- [x] `PluginSpec/PluginTriggerType`（LAUNCH_APP / OPEN_SETTINGS / LOCAL_ACTION）+ `PluginRegistry` 默认表（微信/支付宝/健康/电话 / Wi-Fi/蓝牙/电量 / 手电筒(相机权限默认关)/勿扰/计时器）（doc/05 §2/§3）。
- [x] 用户偏好接入：`PluginPrefs`(order/hidden 纯数据) + `PluginPrefsStore`(SP `plugin_prefs` 读写) + `applyPrefs` 纯函数（01 §9）。
- [x] `PluginExecutor`（domain 纯函数，05 §5）：expectedPackages 依序 `resolveLaunch` 探测 → LAUNCH_APP/OPEN_SETTINGS/LOCAL_ACTION 分发 → try-catch `notFound` → `PluginNotifier` 提示不 crash；权限类前置（05 §6 手电筒）留 M8.S2。解析/本地动作/通知经 `PluginResolver`/`LocalActionRunner`/`PluginNotifier` 端口注入（domain 零 Android 依赖，故执行器可单测）。
- [x] 注册表纯逻辑单测（Registry/Prefs/Executor 三套，含 LAUNCH 命中/未命中、OPEN_SETTINGS/LocalAction 成败、null target 不崩）+ 真实设备手测容错（待真机走查）。

**产出**：`domain/plugin/`、`data/plugin/PluginExecutor.kt`、默认插件表。

**验收（P1）**：单测绿；真机手测——触发失败给出 Bubble 不崩溃。

#### M8.S2 右滑清单 UI + 编辑（P0，已重构：动态 App 网格，去预设）

**任务**
- [x] 右缘 EdgeSwipe 进双列图标网格（doc/06 §5 / §8）：**不预设任何快捷方式**，App 经由 `AppLister`（PackageManager `queryIntentActivities(MAIN+LAUNCHER)` + Manifest `<queries>`）动态发现；图标直接加载系统 App 图标（`getApplicationIcon` → `ImageBitmap`），名称 11sp 不透明（doc/06 §8）。
- [x] 普通态：点击经 `PluginExecutor`→`PluginResolver.launch` **真正拉起** App（修 M8.S2 仅解析不启动的 Bug）；长按进入编辑；触发失败面板内 Bubble「未找到：X」。
- [x] 编辑态（长按 / 顶栏「编辑」）：↑↓ 排序 / × 移除 / ＋ 从「系统能读到的全部 App」中添加（`AppAddOverlay`）/ 恢复默认（显示全部）→ `PluginPrefsStore`（`selected` 有序包名序列）SP 写回（05 §4 / 01 §9）。
- [x] 非 App 功能（Wi-Fi/手电筒/勿扰…）**仅保留代码结构接口**：`PluginSpec`/`PluginExecutor`/`PluginResolver`/`LocalActionRunner`/`PluginNotifier`/`PluginRegistry`（当前 `defaults()` 空）已就绪，后续登记接入，面板无需改动。
- [x] 右缘小箭头按「菜单数据就绪」显示（08 §1 ②，壳内先行不依赖宠物）：沿用 M1.S2 既有 EdgeHint。

**产出**：`QuickPanelBody` 重写为双列 App 图标网格 + 编辑/添加覆盖层；`AppLister`(data)；`PluginPrefs(selected)` + `applySelection`。

**验收（P0）**：右滑弹出双列 App 图标网格；点击拉起真实 App；长按进编辑、↑↓ 排序 / × 移除 / ＋添加全量系统 App；选择持久化（杀进程重开保持）；无 App 时 Bubble 不崩。真机手测（图标清晰度/包可见性/拉起/持久化）待走查。

---

### M9 在线惊喜：随机事件 + 会话回顾（可跑）

> 目的：宠物在线时的「小惊喜」与「本次陪伴小结」——让在线时段也有随机性叙事，并用内存日志做出低成本高陪伴的回顾（04 §4）。

#### M9.S1 EventEngine 候选 / 冷却 / 节奏（P1）

**任务**
- [ ] `EventEngine`（domain/engine，doc/03 §1/§2）：三类触发时机（FSM tick / 动作后 / 冷却到点）；候选池按 `trait.curiosity`、时段、当前状态/属性过滤；在线节奏上限防刷（doc/03 §2.3）。
- [ ] 事件 → 结果：气泡 key / 小动作 / 属性微扰（受控），命中即 `append(RANDOM_EVENT)`（doc/03 §6 / 04 §7）。
- [ ] 事件在线库补齐（doc/03 §4 在线部分）。
- [ ] 单测：冷却不连刷、trait 权重生效、结果 delta 受控 clamp、日志 ts 升序。

**产出**：`domain/engine/EventEngine.kt`、在线事件库、单测。

**验收（P1）**：单测全绿。

#### M9.S2 事件表现 + 会话回顾 UI（P0）

**任务**
- [ ] 在线事件表现：复用/预引 Emotion 最简（CURIOUS 歪头 / STARTLED 受惊，doc/07 §3）+ Bubble 文案。
- [ ] 会话回顾入口与视图（doc/04 §4）：本次陪伴小结——开场段（长离线）折叠 + 本会话 `liveCount` 统计（喂/玩/抚次数）+ 时间线；只读内存、不回写状态。
- [ ] 入口位置不冲突：状态面板一栏（或主屏边缘提示），不与抚摸（点击宠物）抢手势（doc/06 §9）。

**产出**：`presentation/screen/review/` 会话回顾视图、在线事件接线。

**验收（P0）**：持续在线数分钟 → 偶发随机气泡/好奇表现、冷却内不连刷；打开「本次陪伴小结」→ 见开场段（长离线）+ 本会话统计 + 时间线；kill 重启后回顾内容清空（内存语义正确）。

---

### M10 表现质感 + 性能走查调参（打磨）

> 目的：补齐「表情/情绪」与一次性动作演出的质感（07 §2/§3），并对齐 08 §5 性能红线 + 01 §11 数值手感。

#### M10.S1 情绪修饰层 + 动作短脚本（P0）

**任务**
- [ ] Emotion 参数化脚本全量（doc/07 §3）：HAPPY 蹦跳 / SAD 压扁 / ANGRY 抽动 / TIRED 点头 / SICK 战栗+灰 tint / CURIOUS 歪头 / SHY 躲闪——`FSMResult.emotionHint` → 渲染层播放（02 §5）。
- [ ] 形变预留 padding + 圆形裁切遮罩，防拉伸露边（doc/07 §9）。
- [ ] 层3 短脚本（doc/07 §4 / 09 §3）：EATING 低头咀嚼帧复用、喂食食物包、治疗针剂/药丸、Zzz 粒子、亲昵泡泡。
- [ ] SICK 基底 tint + 浮字色与低饱和规范（06 §4）校正。

**产出**：`presentation/render/Emotion` 脚本库、短脚本层。

**验收（P0）**：喂食/玩耍/抚摸/SICK 各有对应形变与脚本；形变过程无破边；情绪可整体停用而不破坏状态。

#### M10.S2 性能走查 + 真机复核 + 调参（P2）

**任务**
- [ ] 08 §5 清单走查：首帧 <200ms（`am start -W`）、五阶揭示正确、settle 后台不阻塞、后台 0 CPU、内存峰值 <30MB（Profiler）、重复冷启 10 次稳定。
- [ ] 真机复核（07 §9 / 08 §6）：朝向实测结论推广到常用宠抽检；64×64 放大观感定案；形变遮罩；ROM 黑闪对策（addView 淡入）真机验证。
- [ ] 数值手感调参（doc/01 §11）：以「一天 2~3 次打开」校准 satiation 速率、冷却与作息配合、SICK 阈值温和度；`trait` 系数观感。
- [ ] 里程碑统计文案首版（doc/04 §3.3，`stats` 解锁简单文案）。

**产出**：性能走查记录表、调参结果（**回写 doc/01 §11 注记**）、里程碑文案。

**验收（P2）**：走查项全部通过或有明确例外记录；数值手感真机确认；调参结论回写设计文档，Task.md 状态收尾。

---

### M11 清洁/洗澡：脏了能洗（可玩）

> 扩展位 doc/09 §5.3。动作接口已预留（02 §5），属性 `hygiene` 已在 v1 建（01 §4.2），本里程碑把它接到面板与表现。

#### M11.S1 清洁 domain：onClean + 冷却（P1）

**任务**
- [x] `PetActions.onClean`（沿用 M6 动作模式，非 BehaviorFSM）实现：效果 `hygiene +35`（clamp 100）、冷却 **3h**、低 hygiene 仅轻度表现（doc/01 §4.2 已在 v1 建）。
- [x] `ActionRule.cleanDenied` + 面板仅在「脏了」(hygiene < LOW_VALUE_WARN=30) 上下文出现清洁胶囊（06 §8.3）。
- [x] 单测：冷却 3h、+35 clamp、可用判定、`SessionLog` 追加 `ACTION_CLEAN`（04 §2 枚举启用）；`ActionsTest` 4 例全绿。

**产出**：onClean 纯逻辑 + 单测绿。

**验收（P1）**：单测覆盖冷却 / clamp / 可用性 / 日志条目。

#### M11.S2 清洁胶囊 + 泡泡表现（P0）

**任务**
- [x] 操作面板「脏了」时出现「清洁」胶囊（实心可点 / 冷却空心倒计时；06 §8.3），`ActionType.CLEAN` 接入 `PetActivity.performAction`。
- [x] 表现：`CLEANING` hint/FxKind 管线接通 + 泡泡装饰占位（真机定稿），`fx_clean` 气泡文案三语齐；状态图标脏(hygiene<30)→净随数值自动显隐。
- [ ] 手测路径（真机）：让 hygiene 降（或 debug 注入）→ 点清洁 → 数值上升 + 表现（留真机走查）。

**产出**：可玩的清洁闭环。

**验收（P0）**：真机可见「脏 → 点清洁 → 泡泡 → 净」，冷却生效。

---

### M12 学习/教育：智力增长与解锁（可玩）

> 扩展位 doc/09 §5.2。`knowledge` 属性 v1 已建（01 §4.1，原 `intelligence` 重命名为知识；`learner` 固定特质 = 智力/学习速度），本里程碑接 onStudy 并落地「增长即解锁」正向内容。

#### M12.S1 学习 domain：onStudy + 解锁表（P1）

**任务**
- [x] `BehaviorFSM.onStudy(now, s)`：`knowledge +5`（受 `learner` 加权）、`mood -5`、`sat -3`、冷却 **5s**（与喂/玩/抚/清洁统一）；玩耍消耗 `knowledge`（−2 固定），形成「学↔玩」拉锯（01 §4.1 / 02 §4）。
- [x] 智力解锁表（01 §4.1）：阈值 → 解锁新互动气泡 / 玩具类型（M13 消费）/ 事件池条目扩容（03 §7）；纯正向、无惩罚。
- [x] 单测：冷却 5s、trait 加权、阈值解锁判定、SessionLog `ACTION_STUDY`。

**产出**：onStudy 纯逻辑 + 解锁表 + 单测绿。

**验收（P1）**：单测覆盖加权 / clamp / 解锁阈值。

#### M12.S2 学习入口 + 成长反馈（P0）

**任务**
- [x] 状态面板上下文出现「学习」胶囊（可与 M11 清洁同入口组，06 §8）。
- [x] 表现：专注/思考短脚本 + 智力上升浮字 + 解锁时气泡提示「学会新招」（07 §7）。
- [x] 手测路径：连续学习至阈值 → 解锁气泡/玩具条目出现。

**产出**：可玩的学习闭环 + 解锁可见。

**验收（P0）**：真机可学、智力只增不减、解锁有可见反馈。

---

### M13 玩具变体：玩耍有差异（可玩）

> 扩展位 doc/09 §5.1。`onPlay(now, s, toy?)` 已预留（02 §5）；玩具来源 = 随机事件掉落 / 智力或里程碑解锁（无货币，doc/09 §5.1）。

#### M13.S1 ToyType 注册表 + 差异化收益（P1）

**任务**
- [x] `ToyType` 注册表：每玩具配置心情收益档 / 情绪表现类别（活泼、温顺、专注）/ 稀有度（doc/09 §5.1）。
- [x] 来源规则：随机事件掉落（03 事件库）+ 智力解锁（M12 解锁表）+ 里程碑计数（doc/04 §3.3 stats）三者任一，无购买。
- [x] 单测：各玩具 delta、来源判定、会话日志带 toy 信息。

**产出**：ToyType 模型 + 来源逻辑 + 单测绿。

**验收（P1）**：单测覆盖不同玩具差异化结果。

#### M13.S2 玩耍选玩具 + 差异表现（P0）

**任务**
- [ ] 玩耍入口支持选玩具（切换胶囊/清单，已解锁实心、未解锁空心带来源，06 §8）。
- [ ] 表现：不同玩具 → 不同情绪形态/强度（复用 M10 情绪修饰层参数化，07 §7），不给每种玩具造新素材。
- [ ] 手测路径：收集/解锁 ≥2 玩具 → 对比表现差异。

**产出**：差异化的玩耍闭环。

**验收（P0）**：真机两种玩具表现可区分、解锁路径可见。

---

### M14 宠物便条：打开见问候（可跑）

> 扩展位 doc/09 §5.4。无推送；便条挂在「会话回顾」或长按宠物触发（doc/04 §4）；来源 = 本会话日志开场段 + 在线互动，非持久化（04 §3.1 不改）。

#### M14.S1 Note 生成：从会话日志到便条（P1）

**任务**
- [x] `NoteGenerator`：输入 `SettlementSummary` + `SessionLog` 开场段/在线摘要 → 输出结构化 `PetNote`（离线开场 + 在线陪伴），由 presentation 经 `PetNote.toText` 模板化为 1~2 句多语言文案（doc/09 §5.4 / 04 §4）。
- [x] 语气方向：离线时长（分档与 M7 `greetingRes` 同档）、endingMood、动作/事件计数决定语气（欢迎回来 / 惦记 / 撒娇），不引入新状态。
- [x] 单测：`NoteGeneratorTest` 覆盖 NoteTone 分档边界 + 离线/在线拼装（9 例全绿）。

**产出**：便条纯函数 + 模板 + 单测绿。

**验收（P1）**：单测覆盖若干会话情境的便条文本。

#### M14.S2 便条呈现（P0）

**任务**
- [x] 入口：状态（回顾）面板顶部便条卡（在属性列表之后、离线回放/本次动态之前）。
- [x] 样式：走 RoundSafe 滚动流 + 圆角胶囊文本（低 alpha 底色，文字不透明 ≥11sp，06 §8），不越安全区。
- [ ] 手测路径：长离线 → 打开 → 便条与回顾剧语义一致（真机走查待做）。

**产出**：可见的宠物便条。

**验收（P0）**：真机长按宠物/回顾页可见与本次会话贴合的便条。

---

### M15 收藏档案：电子墓碑 + 快照切换（可跑）

> 扩展位 doc/09 §5.5 + doc/04 §3.3。**语义（用户决策）**：档案 ≠ 日志——SessionLog 仍只在内存（04 §3.1 不变）；档案 = 换宠时把**被换下宠物的最终快照**序列化存为墓碑，并提供**快照切换**（把墓碑宠再切回当前）。本质是「多份快照的存取」，不新增日志持久化面，故不推翻 T-09「SP 为唯一持久化点」（快照仍是唯一持久化，只是从 1 份变多份）。

#### M15.S1 PetStore 多档化 + 墓碑/切换纯逻辑（P1）

**任务**
- [x] `PetStore` 从单键升级多档（SP 键：当前档 `pet_profile` + 墓碑集合 `pet_tombs_v3`；含 **旧 v2 单键迁移**——旧 v2 档即当前档、墓碑集合为空，doc/01 §9）：当前档 = 正在养；墓碑 = `{petId, finalSnapshot, stats 终值, retiredAt, 共处时长}` 只读。
- [x] `archiveCurrent()`：换宠前把当前宠最终快照写入墓碑（电子墓碑素材，不删当前档）。
- [x] `switchTo(petId)`：把墓碑宠反序列化为当前档（快照切换），从墓碑集合移除；`lastSettledAt` 语义由切回即视为打开该宠（08 §3「打开即结算」）在调用方复用。
- [x] 单测：`PetArchiveTest` + `PetTombCodecTest` 覆盖 v2→v3 迁移、存档/取档 roundtrip、墓碑只读不丢、切回状态一致（共 13 例全绿）。

**产出**：多档 PetStore + 迁移 + 单测绿。

**验收（P1）**：单测覆盖迁移与存取往返；M3 既有建档单测不回退（仍只读 `pet_profile` 单键）。

#### M15.S2 换宠流程 + 档案页 UI（P0）

**任务**
- [x] 统一入口（用户决策，收敛外部感知）：首页→右侧功能面板→设置（SettingsActivity，`settings` 图标已就位）→「重新开始选择宠物」/「我的档案 / 电子墓碑」，不再单开网格单元或状态面板入口。
- [x] 换宠 = `archiveCurrent(当前宠)` 写墓碑（含 stats 终值/共处时长）→ `PetStore.delete()` 清当前档 → 重启 PetActivity 走建档（M3 复用）；原档只读不丢、可在「档案」切回。
- [x] 墓碑页（TombActivity）：列出墓碑（宠物名/共处时长，复用 `formatNoteDuration`）+「切回」按钮（`switchTo` 还原为当前档并重启主屏，原样恢复、不重抽性格/不重跑日志）。
- [ ] 手测路径：养 A → 设置换 B（A 成墓碑）→ 档案切回 A → 状态/数值一致（真机走查待做）。

**产出**：换宠归档 + 切回的完整闭环。

**验收（P0）**：真机 A→B→切回 A 全链路状态一致；墓碑信息可回看。

---

### M16 休闲小游戏：右滑清单里的游戏（可玩）

> 扩展位 doc/09 §5.6。挂在 doc/05 的 LOCAL_ACTION 插件位（M8 已建壳），与宠物陪伴解耦；**仅依赖 M8**，可与 M11–M15 并行。

#### M16.S1 小游戏骨架 + 插件注册（P1）

**任务**
- [ ] 选一款超轻量小游戏（如 30s 点泡泡计分），domain 纯函数（计时/计分/判定）可单测；全部 Compose 绘制、零新素材、零 Compose 外技术（00 T-19 / 07 §8）。
- [ ] 注册进 LOCAL_ACTION 插件位（05 注册表，沿用 M8 的 实心/空心 胶囊）。
- [ ] 单测：计分、限时结束、异常输入容错。

**产出**：小游戏 domain + 插件注册 + 单测绿。

**验收（P1）**：单测绿；右滑清单出现游戏项。

#### M16.S2 游戏页 + 一局闭环（P0）

**任务**
- [ ] 点击游戏项 → `FeatureActivity : BaseActivity` 打开（00 T-16 / ADR-12，不占主屏常驻）。
- [ ] 玩一局（限时）→ 结算提示（仅会话级，不入 stats / 不动宠物状态，doc/09 §5.6 解耦）。
- [ ] 手测路径：右滑 → 游戏 → 一局结束 → 返回主屏。

**产出**：一局可玩的完整闭环。

**验收（P0）**：真机右滑可进、一局有始有终、返回后主屏无扰动。

---

### M17 偏好设置（右滑功能清单入口 → 独立设置 Activity）

> 扩展位 doc/09 §5.7。入口在右滑功能清单面板（`QuickPanelBody` / `AppGridContent`），位于现有「编辑」(EditGridCell, tune 图标) **之前**；点击打开独立 `SettingsActivity : BaseActivity`（沿用 00 T-16 / ADR-12 同款全屏 Activity 范式，与 M16 小游戏页一致），承载游戏内偏好调整。设置项除基础「重新开始选择宠物」外，还联动 M15 电子墓碑/切换；其余项按场景增删（标注「可后续按场景添加」）。

#### M17.S1 设置数据 + 清单入口（P1）

**任务**
- [ ] 新增 `SettingsStore`（SP，独立 schema，与宠物快照 `PetStore` 解耦；T-09 唯一持久化点仍成立——只是新增一份偏好 SP）：承载偏好项键值（如 `restartPet`、`tombstone*` 相关开关/选择）。
- [ ] 功能清单面板在 `AppGridContent` 的「编辑」单元**之前**插入「偏好设置」网格单元（图标待定，见未决项）；点击 → `startActivity(SettingsActivity)` 并 `onDismiss()`。
- [ ] `SettingsActivity : BaseActivity`（新建，遵循 M16 同款 Activity 范式）：标准圆屏滚动页（`RoundList` + `PillItem`，doc/06 §8.3/§8）；先有壳 + 入口闭环，暂不绑定具体控件。
- [ ] 单测：`SettingsStore` 读写 roundtrip。

**产出**：设置 SP + 功能清单「偏好设置」入口 + 设置 Activity 壳。

**验收（P1）**：右滑清单出现「偏好设置」（编辑之前）；点击进入设置页；返回主屏无扰动；单测绿。

#### M17.S2 设置项落地（P0）

**任务**
- [ ] **重新开始选择宠物**：触发「换宠/重开」流程（承接 M15 换宠归档——归档当前宠为墓碑 → 开新档；若未启用墓碑则回退覆盖写重开，与 M3 debug 重开档口径一致）。需二次确认弹窗（防误触）。
- [ ] **电子墓碑相关设置**：与 M15 墓碑/切换联动的项（如：是否启用墓碑归档、墓碑列表入口可见性、切回确认强度等；具体按场景增删）。
- [ ] 预留「按场景添加更多设置项」扩展位（动作间隔开关/微调、衰减速率、音效/震动等可后续接入）。
- [ ] 手测：右滑 → 偏好设置 → 调整 → 返回主屏无扰动；重开宠物闭环正确。

**产出**：偏好设置基础项闭环。

**验收（P0）**：「偏好设置」入口可用；重新开始选择宠物 / 墓碑相关设置可用；返回主屏无扰动。

**未决项（实现前需补）**：「偏好设置」网格单元图标（drawable）待用户提供；图标到位后提醒添加到项目并接入 `AppGridContent`（沿用 `EditGridCell` 同款样式）。

---

## 4. 工作规则（编码期，AI 与人共守）

1. **范围纪律**：一个里程碑内只做该里程碑范围（主链各线 v1 内容 / M11–M16 扩展）；发现想法不在当前范围时记录到后续里程碑或 doc/09 §5 扩展位，不顺手实现、不提前混入。
2. **不留红**：每个 Step 结束编译绿；基建步单测绿；禁止「明天再补」的悬挂红。
3. **假设推翻即回写 doc**：朝向/帧序、FilterQuality、黑闪对策、数值手感等事实类结论，改动当日同步 doc/02 §2.3、07 §1/§9、08 §6、01 §11 等对应勘误位，不许只在代码里改。
4. **里程碑收盘留痕**：每里程碑收盘在 §6 跟踪表记录「验证结果 + 例外/风险项」；主链例外与调参在 M10.S2 汇总，扩展里程碑例外回写对应 doc。
5. **domain 测试驱动**：主链 M3/M4/M5/M6/M7/M9 与扩展 M11–M15 的 S1（基建拍）都以「先写断言后实现」推进，作为「跑不起来阶段」的质量兜底。
6. **日志与存储纪律**：SessionLog 永不落盘；`stats` 只增不减（随档）；快照是唯一持久化（01 §9）；换宠墓碑 = 快照归档（M15），非日志。
7. **技术确认纪律**：引入 Compose 外技术或非官方 UI 组件前，先在本文件与相关 doc 更新方案，并经用户确认后再动工——不得以「顺手引入」绕过（例外清单与确认制见 §2.2-7）。

---

## 5. 风险 / 待实测项 → 里程碑映射（全部前置）

| 待实测 / 风险项 | 来源 | 提前到 | 收盘判定 |
|---|---|---|---|
| 素材朝向顺序、帧序、行/列映射 | 02 §2.3 / 07 §1/§9 | M2.S1 | Debug 核对屏人工确认 + 常量表 |
| 1339 资产扫描 / 缩略内存 | 07 §6 | M3.S2 | 列表流畅不预解码 |
| 首帧黑闪 / 注入切换 | 08 §6 | M1.S1 | 真机五阶无黑闪 |
| 圆屏裁切 / 手势方向 | 06 §5/§8 | M1.S2 | 三手势 + 内切安全区手测 |
| 64×64 放大观感 | 07 §9 | M2.S2 / M10.S2 | 真机定 FilterQuality |
| 切片行走帧序伪影 | 07 §9 | M4.S2 | 行走帧序流畅 |
| settle 幂等 / 后台不阻塞 | 08 §6 / 01 §8 | M5.S1/.S2 | 单测幂等 + 时间旅行手测 |
| 打开频率 / 数值手感 | 01 §11 | M5/M6 手测 + M10.S2 | 一天 2~3 次调参并回写 |
| 离线时间线守恒 / 封顶 / 确定性 | 03 §7 | M7.S1 | 单测断言 |
| 插件 ROM 启动 Flags | 05 §5 | M8.S2 | 真机触发手测 |
| 形变露边 | 07 §9 | M10.S1 | 遮罩后无破边 |
| 内存 <30MB / 后台 0 CPU | 07/08 §5 | M10.S2 | Profiler 走查 |

---

## 6. 里程碑状态跟踪表（收盘时更新）

| 里程碑 | 收盘演示 | 状态 | 验证记录 / 例外 |
|---|---|---|---|
| M1 圆屏深色壳 + 手势路由 | 五阶首帧 + 三手势三 overlay | ☐ | 代码收官（作为 M2 前置编译绿）；真机五阶/手势演示未执行（本轮跳过 UI 验收），与 M2 一并补验 |
| M2 真宠上屏 | 正确朝向静态上屏 | ☑ | M2.S1 朝向常量落定 + Debug 核对屏就绪；M2.S2 BULBASAUR 静态 IDLE（呼吸 ±2px/2s、圆遮罩）上屏，双变体编译绿；真机目测（朝向/FilterQuality/呼吸幅度）留 M4.S2 反向复核 + M10.S2 定稿 |
| M3 建档与数据闭环 | 建档 → 快照 → 重开保持 | ☑ | S1 收盘：值对象族 + 注册表（建档初值 sat/mood 80、health 100、int 0、hyg 100，回写 doc/01 §11）+ PersonalityGenerator，`testDebugUnitTest` 5 例全绿。S2 收盘：PetProfile v2 值对象族（pos/sleep/cooldowns/stats/personality/plugins）+ PetProfileCodec（org.json 单键、容错补默认，JVM 依赖 org.json:json）+ PetStore（SP 薄壳）；建档屏（主名聚合列表 LRU≤12 缩略 + 性格预览六维条 + 确认）+ PetActivity 有档/无档路由 + 主屏真实快照（建档宠 / 主环 / 状态面板全属性）+ debug 重开档入口；codec round-trip 6 例全绿，双变体编译绿。真机手测（浏览流畅、杀进程重开恢复、换宠覆盖写）留后续批量补验 |
| M4 宠物活起来 | 自主走/停/睡 | ☑ | S1 收盘：BehaviorFSM 纯函数 + FSMResult 契约（SICK>睡眠>SAD>IDLE/WALKING、22:00~07:00 入睡、防穿模 clamp+随机换向、行走帧 0→3、seed 可复现），单测 10 例全绿。S2 收盘：PetLivingSprite 主循环（250ms tick、生命周期闸 onPause/onStop 停=0 后台 CPU、面板覆盖亦停）+ PetRenderer v1（WALKING dir×帧循环 / IDLE 呼吸 tick 相位 / SLEEPING 暗罩+Zzz / 归一化坐标→活动区映射），主环真实快照；doc/07 §9 增「睡眠帧缺失」风险行。编译与 testDebugUnitTest 全绿；真机手测（走/停/睡观感、帧序反向复核 M2 朝向、22:00 入睡、后台 0 CPU）与 M1–M3 一并批量补验。**M4.S2 布局修订：宠物活动范围=全屏叠层（环形把手为 overlay、可重叠、恒不出屏），doc/00 T-21 记录** |
| M5 时间流逝 settle v1 | 时间旅行拨回数值推进 | ☑ | S1 收盘：SettleEngine 快速积分兜底全程（幂等 noOp、白昼/夜间睡眠/SICK 三速率档 × trait 系数、饥饿/脏心情修正、SAD 累计 4h 推进与健康下滑、clamp 归零、sickTotal 只增一次、状态推进只写 SICK/SAD/IDLE），SettlementSummary + 时间线占位类型 + AttributeDelta + Clock(SystemClock) + settle 作息/系数注记回写 doc/01 §11。S2 收盘：结算编排入 PetActivity.EntryFlow（冷启动五阶 reveal 完成 force 结算 → SettleEngine+save @Default → 主线程一次 apply=profile state 更新；热恢复 Lifecycle observer 距上次 ≥5s 节流；时间旅行 Debug——长按主屏精灵核对屏内置「结算 Debug」拨回 1h/6h/24h/72h 后立即结算，幂等可反复点）；PetLivingSprite 档案刷新软合并（同宠保留跑动坐标，避免结算瞬移）；BaseActivity.bootSettle 职责移交 EntryFlow 并移除，doc/08 §2/§3 口径回写。单测 15 例全绿 + 编译绿。真机手测（时间旅行拨 24h→重启/立即结算按扣减刷新、health 触底 SICK 表现、夜间回血耗率低、首帧 Logo→状态环先现快照→apply 后平滑跳变且全程可交互、settle 不阻塞）与 M1–M4 一并批量补验 |
| M6 照顾闭环 | 喂/玩/抚/治面板闭环 | ☑ | S1 收盘：`domain/engine/Actions.kt`——ActionRule（doc/01 §6.3/§7/§10：冷却 2h/1h/10min 写 next-until、上限喂 sat<95/玩 mood<90、边际收益递减 sat/mood/int、health 不走递减、口味契合 food.flavor==personality.flavor 该餐 sat/mood ×1.1）+ PetActions.onFeed/onPlay/onPet/onHeal 纯函数（heal 仅 SICK +40 治愈、SAD 动作后 mood≥40 即时解除、EATING/EXCITED 短态不落持久快照）；`domain/log/SessionLog.kt` 骨架（append ts 升序 / liveCount / entries / liveLogsSince，动作成功即 append ACTION_*，openWith M7）；M6.S1 落地注记回写 doc/01 §11 + doc/02 §5；单测 22 例全绿（Actions 18 / SessionLog 4，全套 58 例 testDebugUnitTest 通过）。S2 收盘（面板闭环）：操作面板真实动作列表（「状态」首项 + 投喂/玩耍/抚摸 + SICK 治疗；冷却/属性满 → 空心胶囊行内倒计时，面板开启每秒刷新、到点自动恢复实心；SLEEPING 熟睡动作收为空心提示）；投喂 → 食物子页（返回 chevron 行内首元素 + 口味类别色覆写实心胶囊 + 契合标注）；状态面板环形进度 + 主环配色 + 行尾环低值预警 `<30`（告警色 2Hz 呼吸、行首圆点转告警色）；三入口合一（顶缘下拉 / 屏顶状态图标 / 下面板「状态」= 收起下展开上）；屏顶状态图标区（饿碗/闷云/脏滴/病十字，异常才亮、随语义色、点击即状态面板）；执行链 = EntryFlow.performAction（domain 纯函数 + Default 存档 → 主线程 apply profile → 上抛 ActionEvent → PetScreen 收面板 + PetFx 短演出：EATING 咀嚼 / EXCITED 蹦跳 / AFFECTION 亲昵摇摆 / TREATED 治愈 + 气泡 + 属性浮字；`ui/MiniProgressRing` 增 warn 预警呼吸，`ColorToken` 增 FoodBalanced…FoodNovel 口味类别色）；doc/06 §3.1/§3.2/§4.1/§6 落地注记回写。编译绿 + 全套单测绿（58 例）。真机手测（喂/玩/抚/治表现、冷却实时、低值图标联动、SICK 治疗闭环、杀进程冷却/stats 保持）与 M1–M5 一并批量补验 |
| M7 离线叙事回放 | 迎接语气 + 回放时间线 | ☑ | S1+S2 收盘：共享速率模型 `SettleModel`(DecayModel+simulateSegments) 防漂移；`DefaultOfflineTimelineBuilder`——physio 份额切分严格守恒(=快速积分 totalDelta)、突发受控(|ΣΔ|≤3/24h、正向略多)、封顶 48(>72h→24)、种子=lastSettledAt 确定性、EndingMood 推导；`SettleEngine.settle` 长窗(≥20min)挂 `SettlementSummary.offlineTimeline+endingMood`。S2：`SessionLog.openWith` 铺开场段(ReplayEntry+SettleEntry) + 单测；i18n 扩展函数（`EndingMood.labelRes` / `offlineEventBubbleRes` / `greetingRes`，core 不引 R，沿用用户确认的扩展函数方案）+ 三语言字符串；`EntryFlow` 调 `openWith` 并透出 `settleSummary`；`PetScreen` 加迎接气泡(GreetingBubble，4.5s/点击消失) + 状态面板内回放时间线列表。全量单测绿。 |
| M8 右滑插件清单 | 动态 App 网格 + 编辑添加 + 预留接口 | ☑ | S1+S2 收盘（重构）：面板**去预设**——`AppLister`(PackageManager 动态发现全量可启动 App，Manifest `<queries>` 保包可见性) + 双列真实 App 图标网格；点击经 `PluginExecutor`→`PluginResolver.launch` 真正拉起（修 M8.S2 仅解析不启动）；长按/顶栏「编辑」→ ↑↓ 排序 / × 移除 / ＋ 从系统可读到的全部 App 添加 / 恢复默认，选择写回 `PluginPrefsStore`(`selected` 有序包名)；失败 Bubble 不崩。非 App 功能仅留接口（`PluginSpec`/`PluginExecutor`/`PluginRegistry` 当前空表）。Registry/Prefs/Executor 单测全绿（24 套），编译绿。真机手测（图标清晰度/包可见性/拉起/持久化）待走查 |
| M9 在线惊喜 + 回顾 | 随机事件 + 本次小结 | ☑ | S1 收盘：`domain/engine/EventEngine.kt`——在线事件库（doc/03 §4 子集：found_food/sneeze/curious_walk/yawn/beg_food/dream/treat_hunt）、候选过滤（状态/属性/时段/性格）+ 全局节奏(5min)/单事件冷却/日上限(3) + 权重抽签（trait 加权）；`trigger`=应用数值微扰(clamp)+写 RANDOM_EVENT 日志，瞬态(EXCITED)不落盘、持久态(SLEEPING/WALKING)写回 fsmState。`EventEngineTest` 17 例全绿。S2 收盘：EntryFlow 前台每 30s 探一次 `EventEngine.rollEvent`，命中→应用 delta+存档+写日志+上抛 `OnlineEvent`，PetScreen 复用 `PetFx` 气泡/浮字短演出；状态面板新增「本次动态」回放本会话 RANDOM_EVENT（7 条事件气泡三语齐全）。main 编译绿、单测全绿 |
| M10 质感 + 走查调参 | 情绪/短脚本 + 08§5 走查 | ◑ | S1 收盘（代码）：PetRenderer 三层表现落地——层2 Emotion 参数化脚本全量（HAPPY/SAD/ANGRY/TIRED/SICK/CURIOUS/SHY/STARTLED，scale/rotate/translate 由 tick 相位驱动）+ 层3 短脚本装饰（EATING 食物包 / TREATED 药丸 / AFFECTION 爱心，简单矢量无外部素材）+ SICK 灰 tint（BlendMode.Multiply，仅作用于不透明像素）+ 形变垫层（绕模型中心 scale/rotate/translate + 屏圆 clip，不裁源图）；`EmotionLayer.enabled` 可整体停用情绪而不破坏状态/演出。编译绿。真机观感（形变幅度手感/睡姿/64×64 放大 FilterQuality）留 M10.S2 走查定稿。 |
| M11 清洁/洗澡 | 脏→清洁→净 + 泡泡表现 | ☑ | S1+S2 代码收盘：onClean+3h 冷却+hygiene+35+clean 里程碑+ACTION_CLEAN 日志；面板脏时清洁胶囊+泡泡占位+fx_clean 三语；ActionsTest 4 例绿、全量单测绿。真机手测（脏→清洁→净、冷却实时）留走查 |
| M12 学习/教育 | 知识增长 + 阈值解锁可见 | ☑ | S1+S2 代码收盘：onStudy(knowledge +5×(0.5+learner)加权 / mood −5 / sat −3，冷却 5s，知识可经玩耍主动减) + `Milestones.study` + `ACTION_STUDY` 日志；操作面板「学习」胶囊（冷却 5s 空心只读、可用实心）+ 解锁档位 30/60/90 弹「学会新招」气泡（阈值首版待真机校准）；`ActionsTest` 学习 7 例全绿、全量单测绿。真机手测（学习闭环/解锁气泡观感/思考 pose 细化）留走查 |
| M13 玩具变体 | ≥2 玩具表现/收益可区分 | ☑ | S1 收盘（domain）：`ToyType` 注册表（7 款，差异化 moodDelta / 情绪类别 ToyVibe / 稀有度）+ 解锁来源规则 `ToyRules`（默认 / 智力阈值 / 里程碑 / 事件掉落）+ `onPlay` 接入可选 `toy`（玩具覆盖数值档、日志带 toy 名）；`ActionsTest` 新增玩具专项 9 例全绿、全量单测绿。S2 收盘（差异表现已接）：玩具选择子页（镜像 FeedPage，已解锁实心可选 / 未解锁空心带来源）+ `onAction/performAction` 透传 `toy` 已接通；`ToyVibe→M10 情绪层` 已接入（presentation `ToyVibe.toEmotion()` 映射 LIVELY→HAPPY 蹦跳 / GENTLE→SHY 侧头 / FOCUSED→CURIOUS 歪头，经 `ActionResult.toy`→`PetFx.emotion` 透传）；真机手测待走查 |
| M14 宠物便条 | 长按/回顾见贴合便条 | ☑ | S1 收盘（domain）：`NoteGenerator` 纯函数（输入 `SettlementSummary`+`SessionLog` → 结构化 `PetNote`：离线开场档位 + 在线陪伴计数）+ `NoteGeneratorTest` 9 例全绿。S2 收盘（presentation）：`PetNote.toText`（映射 `NoteTone`→`greet_*` 五档 + 在线计数模板）、状态面板顶部便条卡（圆角胶囊、低 alpha 底、文字不透明）、`sessionLog` 由 PetActivity 经 PetScreen→OverlayLayer→StatusPanelBody 透传；真机手测走查待做 ||
| M15 收藏档案 | A→换B(墓碑)→切回A 一致 | ☑ | S1 收盘（domain）：`PetStore` 升级多档（当前档 + 墓碑集合 `pet_tombs_v3`，含 v2 单键迁移）+ `archiveCurrent/switchTo/listTombs/deleteTomb` 纯逻辑（`PetArchive` 经 `KVStore` 抽象、PetStore 用 SharedPreferences 实现，JVM 单测用内存假实现）+ `PetTombCodec`；`PetArchiveTest`/`PetTombCodecTest` 共 13 例全绿、编译绿。S2 收盘（presentation）：`SettingsActivity`（设置统一入口：换宠归档 + 我的档案/电子墓碑，`settings` 图标已就位）、`TombActivity`（墓碑列表 + 切回），右滑网格新增「设置」单元；编译绿。真机 A→B→切回 A 手测走查待做 ||
| M16 休闲小游戏 | 右滑进游戏一局有始有终 | ☐ | |
| M17 偏好设置 | 右滑「偏好设置」→设置页；重开宠物/墓碑设置可用 | ☐ | |

> 里程碑建议顺序：主链 M1→…→M10（M8 可与 M9/M10 并行，仅依赖 M1）；扩展 M11→…→M17（编号连续、不叫 V2，M16 仅依赖 M8、可与 M11–M15 并行；M17 依赖 M8 功能清单 + M15 墓碑）。每里程碑收盘把「验证记录 / 例外」填入上表并同步更新 README §9 阶段指引。

---

## 7. 多语言（i18n）基建（M7 起贯穿）

> 现状：此前 UI / 枚举中文全部硬编码。M6.S2 收盘前补齐「配置支持 + 文件」，后续里程碑文案一律走资源。

- **资源文件**：`app/src/main/res/values/strings.xml`（默认 = 中文）、`values-zh/strings.xml`（显式中文）、`values-en/strings.xml`（英文）三套，键名一致；新增文案 = 三处各加一条。
- **枚举 → 资源映射**：`presentation/screen/I18n.kt` 承载 `AttributeId / FoodType / FoodFlavor.labelRes` 与 `TRAIT_NAMES_RES`，**放在 presentation 层**（core 枚举保持纯值对象、不引 Android 资源，见 core/attribute/Attribute.kt 注释）。
- **取词**：Composable 内用 `stringResource(R.string.x)`；非 Composable（拒绝原因 `reasonText`、动作短演出 `toPetFx`、枚举遍历）用 `Context.getString(R.string.x)`；`semantics { }` lambda 非 Composable，contentDescription 须提前在 Composable 上下文求值。
- **范围**：已接 `PetScreen` + `SetupProfileScreen` + 枚举 / trait 名；`PetActivity` 启动日志、`DebugSpeciesGridScreen` 仍硬编码（诊断 / 开发用途，暂不在 i18n 范围）。
- 新增语言 = 复制 `values-en/` 改目录名 `values-xx/`。
