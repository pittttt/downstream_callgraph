# Downstream Call Graph Visualizer

An IntelliJ IDEA plugin that visualizes **downstream (callee)** relationships for any Java method — showing what methods are called recursively — with an interactive graph and Markdown export for LLM consumption.

![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-2021.1%2B-blue)
![Java](https://img.shields.io/badge/Java-8%2B-orange)

## Features

- **Downstream call graph**: Given a method, recursively find all methods it calls (the opposite of "find usages")
- **Hierarchical visualization**: Interactive graph powered by [vis-network](https://visjs.github.io/vis-network/docs/network/), with a top-down layout where the root method is at the top
- **Configurable depth**: Control max recursion depth (1–15, default 5)
- **Smart filtering**: Automatically filters out JDK/library methods; optionally includes constructors (`new Foo()`) and method references (`Foo::bar`)
- **Source navigation**: Click any node or edge to jump directly to the corresponding source code
- **Export as HTML**: Save the graph as a standalone interactive HTML file
- **Export as Markdown**: Export the call tree with source code in a format optimized for LLM consumption

## Usage

### Generate a call graph

Three ways to trigger:

1. **Right-click** on a method → *Generate Downstream Call Graph*
2. **Keyboard shortcut**: `Alt+Shift+D` (`Option+Shift+D` on macOS)
3. **Tool Window**: Open *View → Tool Windows → DownstreamCallGraph*, place caret on a method, click **GENERATE**

### Interact with the graph

- **Click a node** → navigate to method definition
- **Click an edge** → navigate to the exact call site
- **HIDE NODE** → hide a selected node to declutter the view
- **SHOW ALL** → restore hidden nodes
- **FIT** → fit the entire graph in view

### Export

- **SAVE AS HTML** → standalone HTML file with the interactive graph
- **EXPORT MD** → Markdown file containing:
  - Metadata table (method, direction, depth, total methods)
  - ASCII call tree with box-drawing characters
  - Method details grouped by level, with full source code

### Settings

Click **OPTIONS** to configure:

| Setting | Default | Description |
|---------|---------|-------------|
| Max Depth | 5 | Maximum recursion depth (1–15) |
| Filter library methods | true | Exclude JDK/third-party library methods |
| Include constructors | true | Include `new Foo()` calls |
| Include method references | true | Include `Foo::bar` references |
| Background color | Custom (#000000) | Use custom color or IDE editor background |
| Include source in Markdown | true | Embed source code in Markdown export |

## Markdown Export Example

```markdown
# Call Graph: OrderService.processOrder

| Property | Value |
|---|---|
| Method | `void OrderService.processOrder(Order order)` |
| Direction | DOWNSTREAM |
| Max Depth | 5 |
| Total Methods | 8 |

## Call Tree

\-- OrderService.processOrder()
    |-- OrderValidator.validate()
    |   \-- RuleEngine.evaluate()
    |-- PaymentService.charge()
    |   |-- PaymentGateway.submit()
    |   \-- TransactionLog.record()
    \-- NotificationService.send()
        \-- EmailClient.deliver()

## Method Details

### Level 0

#### `void OrderService.processOrder(Order order)`

- **Class**: `com.example.OrderService`
- **File**: `src/main/java/com/example/OrderService.java:42`

​```java
public void processOrder(Order order) {
    validator.validate(order);
    paymentService.charge(order);
    notificationService.send(order);
}
​```
```

## Building from Source

### Prerequisites

- JDK 11+ (for Gradle build)
- Node.js & npm (for frontend)

### Build

```bash
# Build frontend
cd src/main/frontend
npm install
npm run build:prod
cd ../../..

# Build plugin
./gradlew buildPlugin
```

The plugin zip will be at `build/distributions/downstream-callgraph-*.zip`.

Install it in IntelliJ IDEA via *Settings → Plugins → ⚙️ → Install Plugin from Disk*.

### Run in development

```bash
./gradlew runIde
```

## Architecture

| Layer | Technology | Key Files |
|-------|-----------|-----------|
| Build | Gradle + Webpack | `build.gradle.kts`, `webpack.config.js` |
| Core | Java, IntelliJ PSI API | `DownstreamCallGraphGenerator.java` |
| Frontend | vis-network 9.1.6, FontAwesome | `callgraph.js`, `vis-options.js` |
| Communication | JCEF (JBCefBrowser + JBCefJSQuery) | `BrowserManager.java`, `JSQueryHandler.java` |
| Export | HTML + Markdown | `SaveAsHtmlHandler.java`, `MarkdownExporter.java` |

## Inspired By

[yunusemregul/callgraph](https://github.com/yunusemregul/callgraph) — an IntelliJ plugin for **upstream** (caller) call graph visualization. This plugin takes the opposite direction: **downstream** (callee) traversal.

## License

MIT
