Jetpack Compose 的架构是一个极其优雅的 **“自底向上、逐层解耦”** 的设计。你可以把它想象成盖房子：

- **Runtime** 是物理定律（重力、时间）。
- **UI Core** 是砖块、水泥和卷尺。
- **Foundation** 是建好的标准毛坯房（有门有窗，但没有装修）。
- **Material 3** 是精装房（宜家风格，拎包入住）。

如果你不用 Material，意味着你拒绝“精装房”，但你可以（也应该）继续使用“毛坯房”和“砖块”。

下面我们从底向上，逐层拆解并附带代码示例：

------

### 第 1 层：Runtime（运行时层）—— “引擎与物理定律”

**职责**：它根本不关心 UI 长什么样。它只负责管理 `@Composable` 函数的执行、状态（State）的保存、以及触发重组（Recomposition）。 **核心 API**：`@Composable`, `remember`, `mutableStateOf`, `LaunchedEffect`。

```kotlin
// 这一层甚至不需要引入任何 UI 包
@Composable
fun CounterLogic() {
    // 状态管理，完全脱离 UI
    var count by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        // 副作用管理
        delay(1000)
        count++
    }
}
```

*💡 **Spring 类比**：类似于 Spring 的 IoC 容器和 Bean 生命周期管理。它管理对象的存活和依赖，但不关心对象是 Controller 还是 Service。*

------

### 第 2 层：UI Core（UI 核心层）—— “画布与基本法则”

**职责**：定义了屏幕上的“物理法则”：如何测量（Measure）、如何布局（Layout）、如何绘制（Draw）、如何接收触摸事件。 **核心 API**：`Modifier`, `Layout` (自定义布局), `Canvas`, `pointerInput`, `Semantics` (无障碍)。

**例子：用底层 API 实现一个“流式布局”（类似 HTML 的 FlexWrap）** Material 和 Foundation 都没有提供原生的 FlowRow（注：Foundation 后来加了，但这里展示底层原理），你需要用 `Layout` 自己算坐标：

```kotlin
@Composable
fun CustomFlowRow(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        // 1. 测量所有子组件
        val placeables = measurables.map { it.measure(constraints) }
        
        // 2. 计算坐标（简化的流式逻辑）
        var y = 0
        var x = 0
        val maxY = placeables.maxOfOrNull { it.height } ?: 0
        
        layout(constraints.maxWidth, maxY * 2) { // 假设固定两行
            placeables.forEach { placeable ->
                if (x + placeable.width > constraints.maxWidth) {
                    x = 0; y += maxY // 换行
                }
                placeable.placeRelative(x, y)
                x += placeable.width
            }
        }
    }
}
```

------

### 第 3 层：Foundation（基础层）—— “毛坯房与标准件”

**职责**：基于 UI Core 封装的**跨设计系统**的基础交互和布局组件。它**没有任何视觉风格（没有默认颜色、没有默认圆角）**，但提供了完备的交互逻辑（滚动、点击水波纹、文本输入光标）。 **核心 API**：`Box`, `Row`, `Column`, `LazyColumn`, `TextField` (基础版), `clickable`, `Scrollable`。

**例子：不用 Material，用 Foundation 搭建一个“按钮”** 注意看，这里没有 `Button` 这个词，我们用 `Box` + `Modifier` 拼装：

```kotlin
@Composable
fun FoundationButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = Modifier
            // 1. 交互与无障碍 (Foundation/UI 层)
            .clickable(
                onClick = onClick,
                indication = rememberRipple(), // 自带水波纹效果
                interactionSource = remember { MutableInteractionSource() }
            )
            .semantics { role = Role.Button } // 告诉屏幕阅读器这是按钮
            // 2. 尺寸与形状 (UI 层)
            .height(48.dp)
            .padding(horizontal = 16.dp)
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
    ) {
        // 3. 内部布局 (Foundation 层)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}
```

*💡 **关键点**：这个按钮已经有了完美的点击反馈、按压态、无障碍支持，但它**不是** Material Button。它是你自己的设计系统组件。*

------

### 第 4 层：Material / Material3（设计系统层）—— “精装房”

**职责**：Google 官方基于 Foundation 层实现的一套**特定视觉规范**（MD3）。它预设了颜色系统、字体排版、组件的具体长相。 **核心 API**：`Button`, `Card`, `TopAppBar`, `MaterialTheme`, `Text` (带 MD 样式的)。

**对比：Material 是怎么写 Button 的？** 如果你去看 Material3 的源码，它的 `Button` 本质上就是调用了 Foundation 层的 `Surface` 和 `Row`，然后强行塞入了 MD3 的颜色和形状：

```kotlin
// Material3 源码的极度简化版
@Composable
fun Button(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    // 强制使用 MaterialTheme 里的 Token (颜色、形状)
    val containerColor = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.full 
    
    Surface( // Surface 也是 Foundation 层的
        onClick = onClick,
        color = containerColor,
        shape = shape
    ) {
        Row(...) { content() }
    }
}
```

------

### 实战演示：如何优雅地“去 Material 化”？

假设你的公司有一套名为 "Cyberpunk 2077" 的独特设计规范（全是直角、霓虹色、没有水波纹只有闪烁）。

**❌ 错误做法（与框架作对）：** 强行使用 `MaterialTheme`，然后在每个 `Button` 上用 `Modifier` 覆盖颜色，或者用 `CompositionLocal`  hack 内部变量。

**✅ 正确做法（利用分层架构）：** 抛弃 Material 层，在 Foundation 层之上，建立你自己的 **Layer 4 (Cyberpunk Design System)**。

#### 步骤 1：定义你的 Design Tokens (替代 MaterialTheme)

```kotlin
// 你的主题对象
object CyberpunkTheme {
    val colors = CyberpunkColors(neonPink = Color(0xFFFF00FF), darkBg = Color.Black)
    val typography = CyberpunkTypography(...)
}

// 通过 CompositionLocal 注入全局
val LocalCyberpunkTheme = staticCompositionLocalOf { CyberpunkTheme }
```

#### 步骤 2：用 Foundation 实现你的基础组件

```kotlin
@Composable
fun CyberButton(onClick: () -> Unit, text: String) {
    val theme = LocalCyberpunkTheme.current
    
    // 使用 Foundation 的 Box，不使用 Material 的 Surface
    Box(
        modifier = Modifier
            .border(2.dp, theme.colors.neonPink) // 直角边框
            .background(theme.colors.darkBg)
            .clickable(
                onClick = onClick,
                indication = null // 关掉水波纹！
            )
            .padding(16.dp)
    ) {
        // 使用基础 Text，不带 Material 样式
        BasicText(
            text = text, 
            style = theme.typography.buttonText.copy(color = theme.colors.neonPink)
        )
    }
}
```

#### 步骤 3：在 App 中应用

```kotlin
@Composable
fun MyApp() {
    // 你的主题包裹
    CompositionLocalProvider(LocalCyberpunkTheme provides CyberpunkTheme) {
        CyberButton(onClick = { /*...*/ }, text = "JACK IN")
    }
}
```

### 总结：分层框架带来的好处

1. **极致的灵活性**：你可以只用 Layer 1~3，完全自己画 UI（比如写一个游戏引擎的 UI）。
2. **避免“样式战争”**：当你不需要 MD3 的圆角和阴影时，你不需要去覆盖 Material 的默认值（这通常很痛苦且容易出 Bug），而是直接降级使用 Foundation 层，自己写几行代码。
3. **包体积控制**：如果你完全不用 `androidx.compose.material3`，你的 APK 体积会显著减小，因为你没有打包那套庞大的设计系统代码和图标资源。

**记住一句话**：Material 只是 Compose 的一个“皮肤”。Compose 的真正威力在于底层的 **UI Core** 和 **Foundation**，掌握了它们，你可以写出任何设计系统。