# KubeJS 集成迁移到 26.1.2 NeoForge

## 基线与边界

- 源代码：官方附属 `Skill Atlas KubeJS-1.21.1-neoforge` 1.2.0，已先合并进本体 `1211-neoforge-skill-atlas` 的 `com.hp.skillatlas.kubejs` 包。
- 目标：本工作树 `26.1.2-neoforge-skill-atlas`，Minecraft 26.1.2、NeoForge 26.1.2.95、Java 25、ModDevGradle 2.0.147。
- KubeJS：`dev.latvian.mods:kubejs-neoforge:26.1.2-8.0.7`，Rhino `2101.2.8-build.91`，均为可选依赖，只用 `compileOnly`/`testImplementation`，不写入 `neoforge.mods.toml`。
- 不删减任何功能：事件、查询、写入、定义覆盖、自定义条件/奖励/行为类型、动态费用与报价、客户端 tooltip 与导航、互斥组 `choiceGroup` 全部保留。

## 功能逐项保留清单

| 功能 | 源文件 | 迁移处理 |
| --- | --- | --- |
| 服务端事件组 `SkillAtlasEvents`（学习前/后、重置、点数、行为计点、等级、定义重载） | `AtlasEvents`、`Hooks`、`AtlasServerMixin` 等 | KubeJS 8 事件 API 签名与 2101 一致，原样保留 |
| 客户端事件组 `SkillAtlasClientEvents.tooltip` | `AtlasClientEvents`、`ClientTooltips` | 原样保留 |
| `SkillAtlas` / `SkillAtlasClient` 绑定与类白名单 | `AtlasPlugin` | `FMLEnvironment.dist` 改为 `FMLEnvironment.getDist()` |
| 定义覆盖（节点/分类/点数池/等级/费用/奖励/前置/布局/图标/原生 JSON/互斥组） | `DefinitionsEvent`、`NodeBuilder`、`RankBuilder` 等 | 纯数据逻辑，原样保留 |
| 报价与动态费用网络 | `network/*` | `ResourceLocation` 改为 `Identifier`；客户端处理器改为在 `RegisterClientPayloadHandlersEvent` 注册；客户端发包改用 `ClientPacketDistributor` |
| 导航请求 | `ClientNavigation`、`NavigationPacket` | `displayClientMessage(msg, false)` 改为 `sendSystemMessage(msg)`（聊天栏显示，语义一致） |
| 持续奖励键 | `RewardContext` | `modifierKey` 类型改为 `Identifier` |
| 客户端脚本重载清理 | `ClientScriptsMixin` | KubeJS 8 `ScriptManager.reload()` 无参，与 1.21.1 一致 |

## 合并方式

- 所有 mixin 放在独立配置 `skill_atlas.kubejs.mixins.json`，由 `KubeJSMixinPlugin` 控制：只有加载了 KubeJS 且没有安装旧独立附属 `skill_atlas_kubejs` 时才应用。
- `KubeJSSupport` 只依赖 FML 的 `LoadingModList`，不引用任何 KubeJS 类；本体入口只有在启用时才加载 `KubeJSCompat`。
- 网络 payload 注册为 `optional()`，服务端发包前检查对端是否有该频道，单端安装 KubeJS 不会导致无法连接。

## 验证计划

1. `compileJava` 与 `test`（含 `com.hp.skillatlas.kubejs` 全部集成测试和 `ChoiceGroupTest`）。
2. 正式 JAR 内容检查：`kubejs.plugins.txt`、`skill_atlas.kubejs.mixins.json`、集成类与语言文件。
3. 服务端测试（交给子代理）：未安装 KubeJS 时本体正常启动；安装 KubeJS 时脚本绑定、事件与 `choiceGroup` 生效。

## 验证记录

2026年10月25日：

- 三个版本（1.20.1 Forge、1.21.1 NeoForge、26.1.2 NeoForge）`build` 均成功：17 个测试类、77 个用例，0 失败、0 错误。
- 26.1.2 正式 JAR 包含 63 个 `com/hp/skillatlas/kubejs` 类、`kubejs.plugins.txt`、`skill_atlas.kubejs.mixins.json`，`neoforge.mods.toml` 有两个 `[[mixins]]`；`choice_excluded` 中英文案已打包。
- 26.1.2 适配点：`Identifier`、`player.level().getServer()`、`sendSystemMessage`、`FMLEnvironment.getDist()`、`FMLLoader.getCurrentOrNull().getLoadingModList()`、`RegisterClientPayloadHandlersEvent` 与 `ClientPacketDistributor`。
- 服务端实机测试：尚未执行。
