---
name: skill-atlas-dev
description: 开发 Skill Atlas（技能树模组，MC 1.20.1 Forge）时的项目约定，涉及技能/分类/点数池的文本字段、编辑器、快捷键、帮助界面、语言文件或配置读写时必须先读。
---

# Skill Atlas 开发约定

## 1. 唯一 ID 与自动翻译键（必须兼容）

技能（`SkillNode`）、分类（`Category`）、点数池（`PointPool`）都有全局唯一的 `id`：
- 创建时由 `EditActions.freshId` 或 `uniqueId` 自动分配，之后不可修改。
- 翻译键都基于这个 ID 生成。

玩家可见的文本字段**不允许开发者手填语言键**，由服务端在保存时自动生成：

- 键格式：`skill_atlas.atlas.<kind>.<id>.<field>`，由 `AtlasTexts.key(kind, id, field)` 生成。
- 现有字段：
  - node：`name`、`aliases`、`tooltip`
  - category：`name`
  - pool：`name`、`description`
- 翻译文本存放在 `config/skill_atlas/lang/<lang>.json`，`atlas.json` 中只保存键。
- 运行时数据位于 `AtlasDefinition.texts`（`AtlasTexts`，结构为 lang → key → text）。

### 数据流

1. 编辑框显示文本时调用 `Texts.display(raw)`，显示解析后的文本，**不要显示键**。
2. 用户没有改动的字段保持原键，原样发送；改过的字段发送纯文本。
3. 服务端在 `EditOps.apply(d, ops, lang)` 中调用 `localize`，`lang` 取自 `ServerPlayer.getLanguage()`：
   - 纯文本：写入 `texts[lang][自身键]`，字段值替换为自身键。
   - 纯文本恰好等于原键在当前语言（含回退语言）下的文本：保留原键，不写入。
   - 其他对象的 atlas 键（批量编辑、复制粘贴时出现）：把该键所有语言的文本复制到自身键。
   - 形如 `a.b.c` 的外部语言键：原样保留。
4. `EditOps.prune` 在每次 apply 结束和加载时清理未被引用的键。变化的键记入 `Changes.texts`，通过 PATCH 的 `texts` 增量同步。
5. FULL 包把 `texts` 放进 `definition` 对象下发。客户端在 `ClientEvents.onSetup` 中设置 `Texts.lookup`，按当前语言解析。
6. 解析回退顺序：当前语言 → en_us → zh_cn → 任意语言（`AtlasTexts.resolve`）。
7. 服务端发给玩家的文本不能依赖客户端语言表，必须在服务端解析：
   - 用 `AtlasTexts.display(raw, player.getLanguage())`，或 `AtlasServer.text(raw, source)`；
   - 再交给 `Texts.component(...)`。
8. 复制时，剪贴板 JSON 附带 `texts`。粘贴时，本地不存在的键会替换为剪贴板里的文本（`EditActions.pastedText`）。
9. 加载时由 `AtlasServer.migrate` 把旧的 `skill_atlas.xxx` 模组语言键（如示例数据 `skill_atlas.sample.*`）迁移为自动键：
   - 文本从 jar 内的 `en_us` 和 `zh_cn` 语言文件读取；
   - 迁移后立即写盘。

### 新增文本字段时必须同时完成

- `AtlasTexts.collect` 中加入该字段，否则它的键会被 prune 清掉。
- `EditOps.apply` 对应的 op 分支中调用 `localize`。
- `AtlasServer.migrate` 中加入迁移。
- 编辑框初值使用 `Texts.display(...)`；显示时走 `Texts.component(...)` 或 `Names.*`。
- 搜索或拼音索引里先 `Texts.display` 再处理。
- 节点文本还要在 `EditActions.paste` 中加上 `pastedText`。
- 不要把翻译文本写进 `atlas.json`，也不要让用户在界面上填写 `skill_atlas.atlas.*` 键。

## 2. 快捷键必须写进帮助界面

每新增或修改一个编辑或浏览快捷键，都要同步以下内容：

- 帮助界面：`client/screen/MessageDialog.help()` 中的 `keys` 分区，即 `{"keys", "<条数>"}`。条数加 1，并在 zh_cn、en_us 两个语言文件中新增 `skill_atlas.help.keys.<n>`。
- 如果属于浏览、编辑等已有分区，同时更新对应的 `help.browse.*` 或 `help.edit.*` 文案。
- `README.md` 的快捷键说明。
- 语言键检查脚本 `D:/AI临时文件/check_lang.py` 的 dyn 表中，把 `keys` 的数量范围同步改掉。

## 3. 通用约定

- 玩家可见的固定文本使用语言键，zh_cn 与 en_us 同步维护。界面只显示当前语言的名称，不拼接 ID。
- Java 代码不写注释，不用三元运算符，调试日志用英文。
- 构建、语言键检查、harness 测试、搜索盘点等工作交给 Haiku 子代理执行。
- 不改动另一个会话尚未提交的 `ClientAtlas.java`、`Json.java` 和 `src/test/`。
