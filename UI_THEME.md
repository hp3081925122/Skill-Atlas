# Skill Atlas 贴图主题

界面面板、侧栏、工具栏、分类、按钮各状态、单行/多行输入框底板、搜索结果、节点状态、连线、框选、滚动条和节点悬浮提示均从资源纹理绘制。文字、物品图标和原生输入的光标/文本选区继续使用 Minecraft 的字体和输入机制。

默认素材为 Kenney 的 **UI Pack - Pixel Adventure**，许可证为 **CC0 1.0**。官方来源：https://kenney.nl/assets/ui-pack-pixel-adventure 。模组内保留了素材包原始许可证 `META-INF/LICENSE-Kenney-UI.txt`、NOTICE，以及 `assets/skill_atlas/ui/sources.json` 中的逐文件来源。选用的 16 张 PNG 未修改，裁切区域、透明度和九宫格缩放在渲染时处理。本模组不商用；素材许可本身也允许随项目分发。

## 更换主题

1. 安装随附的“Skill Atlas UI 资源包模板.zip”，放进客户端 `resourcepacks` 并启用；服务器不需要安装这个资源包。
2. 修改 `assets/skill_atlas/ui/theme.json`，或直接替换 `assets/skill_atlas/textures/gui/kenney/` 下的对应 PNG。替换 PNG 后保留尺寸；尺寸变化时同步修改 theme.json。
3. 游戏中使用 F3+T 重载资源，已打开的技能树、编辑表单与搜索页使用新主题。无需重新编译 JAR。

模组的完整默认定义位于 `assets/skill_atlas/ui/default_theme.json`。实际换肤入口为 `theme.json`：允许只写需要修改的字段，其他字段继承模组内置默认值。资源包中直接覆盖 default_theme.json 不是换肤入口。

例如，只替换普通按钮，并改变标题颜色：

```json
{
  "textures": {
    "button": {
      "texture": "my_pack:textures/gui/button.png",
      "width": 64,
      "height": 32,
      "u": 0,
      "v": 0,
      "regionWidth": 64,
      "regionHeight": 32,
      "border": 4,
      "alpha": 1.0
    }
  },
  "colors": {
    "title": "#FFF4DBA4"
  }
}
```

自定义资源路径应位于对应命名空间。例如上面的文件放在 `assets/my_pack/textures/gui/button.png`。所有路径用小写英文字母、数字、下划线、短横线和斜杠。

## 纹理参数

| 字段 | 含义 |
| --- | --- |
| texture | PNG 资源路径，包含命名空间 |
| width / height | 整张 PNG 的实际像素尺寸，1～4096 |
| u / v | 图集区域左上角像素坐标 |
| regionWidth / regionHeight | 图集内要绘制的区域尺寸 |
| border | 九宫格固定边角大小；0 为整块拉伸，必须小于区域最短边的一半 |
| alpha | 渲染透明度，0～1 |
| tint | 可选的运行时染色，使用 #AARRGGBB；省略为白色，源 PNG 保持原样 |

面板中心和四边分别拉伸，不平铺成大量小块；一次面板绘制最多 9 个四边形。原尺寸节点直接绘制 1 个四边形。纹理路径、尺寸和切片参数均由 JSON 决定，不在 Java 中逐个固定。

## 主题槽位

| 用途 | 槽位 |
| --- | --- |
| 背景与布局 | background、panel、sidebar、toolbar、canvas、footer |
| 按钮 | button、button_hover、button_pressed、button_disabled |
| 输入底板 | input、input_focus |
| 分类与搜索 | category、category_selected、result、result_hover |
| 节点 | node_locked、node_available、node_learned、node_mastered、node_selected |
| 其他 | tooltip、scroll_track、scroll_thumb、selection、grid、line_locked、line_learned |
| 预留输入装饰 | cursor、text_selection；原生文字输入当前不使用这两个预留槽位 |

颜色使用 `#AARRGGBB` 格式。`colors` 的现有键可在完整默认定义中查看。`metrics` 可调网格间距（gridSpacing，8～256）、网格点尺寸（gridSize，1～8）、连线宽度（lineWidth，1～12）、悬浮提示边距（tooltipPadding，2～16）、悬浮提示最大宽度（tooltipMaxWidth，64～1024）。布局和点击区域仍由界面管理，换肤不会更改学习或编辑逻辑。

主题重载只读取一次 JSON 并检查 PNG 头与尺寸，渲染期间不反复读取磁盘或解析 JSON。错误主题会输出明确英文日志并回退到默认配置；缺失或尺寸不符的自定义纹理按槽位回退。若资源包直接损坏同路径的内置 PNG，应撤销该文件覆盖，避免 Minecraft 自身显示缺失贴图。

## 实机验收

- [ ] 不同 GUI 缩放与窗口尺寸下，面板边角、输入框、分类、表单、Tooltip 和确认弹窗没有拉伸异常或遮挡。
- [ ] 按钮普通、悬浮、按下、禁用，节点未满足、可学习、已学习、满级、选中状态显示正确。
- [ ] 单行输入点击与光标定位、多行输入/选择/滚动、循环选项鼠标/键盘/Shift 反向操作正常。
- [ ] 资源包只换一张按钮图或一项颜色后，F3+T 立即生效；无效 JSON、非法切片和缺图能够回退且有日志。
- [ ] 千节点场景中的缩放、拖动、连线裁剪与帧率可接受，学习和编辑功能保持正常。

编译、资源检查与自动测试不能替代以上游戏内验收；此次没有自动启动客户端。
