---
title: Java 与 Kotlin Bridge
status: draft
---

# Java 与 Kotlin Bridge

全局 `Java` 提供 QuickJS 到 Android JVM 的动态反射代理。它不是 JavaScript 的 `import`，也不自动把 JVM API 类型加入 TypeScript；包代码应只引用目标 Operit 运行时中存在且可访问的类。`Java` 与 `Kotlin` 的全局声明见 `examples/types/index.d.ts`。

当前 `java-bridge.d.ts` 没有 ToolPkg API `@since` 标记；以下接口按当前基线记录。外部 DEX/JAR 加载和类代理实现分别位于 `JsExternalJavaCodeLoader`、`JsJavaBridge` 与 `JsJavaBridgeDelegates`。

## 类与包解析

| API | 签名 | 行为 |
| --- | --- | --- |
| `Java.type` | `(className: string): JavaBridgeClass` | 去除首尾空白；空类名抛错。返回动态类代理，不保证此时类已存在；访问成员或构造时才可能报 `class not found`。 |
| `Java.use` | `(className: string): JavaBridgeClass` | `Java.type` 的别名。 |
| `Java.importClass` | `(className: string): JavaBridgeClass` | `Java.type` 的别名；不会把类写入当前 JS 词法作用域。 |
| `Java.package` | `(packageName: string): JavaBridgePackage` | 要求非空包名，返回可继续点访问的动态包代理。 |
| `Java.classExists` | `(className: string): boolean` | 空名称或类加载失败返回 `false`；此检查不会抛出类不存在异常。 |

也可以使用 `Java.java.lang.StringBuilder` 这样的属性链。代理在每一级尝试解析类；未命中时继续作为子包。包代理不可作为空路径构造或调用。

## 创建对象与调用

### `Java.newInstance(className, ...args)`

```ts
newInstance<T extends JavaBridgeInstance = JavaBridgeInstance>(
  className: string,
  ...args: JavaBridgeArg[]
): T
```

通过反射选择可匹配的构造函数并创建 JVM 对象。无匹配构造函数、参数不能转换、类无法解析或构造器本身抛错时，bridge 结果会变成 JS `Error`。返回的是带 native handle 的动态代理，不是复制到 JS 的普通对象。

### `Java.callStatic(className, methodName, ...args)`

同步调用静态方法，返回经 bridge 转换的值。`className`、`methodName` 会转成字符串；方法名为空时原生端拒绝调用。重载根据参数类型进行匹配。Kotlin companion 方法有额外回退逻辑，因此 Java 风格静态调用也可能解析到 companion 实例方法。

### `Java.callSuspend(className, methodName, ...args)`

```ts
callSuspend(className: string, methodName: string, ...args: JavaBridgeArg[]): Promise<JavaBridgeValue>
```

用于 JVM suspend 方法。JS 端分配 callback ID，native 完成后 resolve 值或以 `Error` reject；此包装没有独立 timeout、AbortSignal 或取消句柄。不要把普通同步 Java 方法传给该入口。

### `Java.getApplicationContext()` / `Java.getContext()`

两者都返回宿主 Application Context 的 Java 代理；`getContext()` 直接委托给 `getApplicationContext()`。

### `Java.getCurrentActivity()` / `Java.getActivity()`

取得当前 Activity 的 Java 代理；`getActivity()` 是 `getCurrentActivity()` 的别名。当前没有可用 Activity 时，以 native bridge 返回的空值/错误为准，不要把 Activity 生命周期跨越页面切换保存为稳定引用。

## 动态代理对象

### `JavaBridgeInstance`

`Java.type("...").newInstance(...)`、`new Java.type("...")(...)` 或类代理的 `new` 调用都会得到实例代理。

| 成员 | 行为 |
| --- | --- |
| `className`、`handle` | 只读类名和 native 对象句柄；句柄是不透明标识。 |
| `call(methodName, ...args)` | 明确调用实例方法，适合属性名冲突或调试。 |
| `callSuspend(methodName, ...args)` | 异步调用实例 suspend 方法，返回 Promise。 |
| `get()` | 通过实例的 `get` 方法读取值。 |
| `get(fieldName)` | 反射读取实例字段/property。 |
| `set(value)` | 通过实例的 `set` 方法写入值。 |
| `set(fieldName, value)` | 反射写入实例字段/property。 |
| `toJSON()` | 序列化为 `{ __javaHandle, __javaClass }` 句柄标记；不序列化实例内部字段。 |
| `toString()` | 返回代理的字符串表示。 |

未知属性读取优先生成实例方法调用代理，失败时尝试字段/property 读取；未知属性赋值执行字段/property 写入。通常使用 `obj.methodName(...)`；只有存在歧义时才调用 `obj.call("methodName", ...)`。

### `JavaBridgeClass`

类代理支持直接函数调用、`new` 和 `newInstance(...)` 三种构造方式。`exists()` 查询类是否可解析；`callStatic()`、`callSuspend()`、`getStatic()`、`setStatic()` 分别执行静态方法、suspend 静态方法、静态字段读取和写入。未知属性优先读取静态字段，其次解析嵌套类，最后生成静态方法调用函数；未知属性写入会设置静态字段。

### `JavaBridgePackage`

包代理支持 `.path`、`toString()`、继续读取子包/类成员以及直接构造已解析的类。属性访问区分不了编译期包与类，最终以运行时类加载结果为准。

## Java 接口回调

```ts
Java.implement(interfaceName, implementation);
Java.implement(interfaceNames, implementation);
Java.implement(implementation);
Java.proxy(interfaceName, implementation);
```

`implementation` 必须是函数或对象。函数用于单方法/SAM 接口；对象按接口方法名提供回调。接口引用可用全限定类名字符串或 `Java.type(...)` 的类代理。`Java.proxy` 是 `Java.implement` 的别名。返回值是 bridge marker，供后续 Java 构造器或方法参数接收，不是可直接调用的 Java 实例。

回调在 QuickJS runtime 线程执行。回调参数和返回值通过 bridge value 转换；JS 对象 ID 与 native proxy 有生命周期跟踪，代理被回收时会释放相应注册。不要在接口回调中假设自己处于 Android UI 线程。

## 值传递与异常

bridge 支持 primitive、数组、普通记录、Java 句柄和接口 marker。Java 返回对象会包装成代理句柄；普通结构化值按可传递字段转换。重载解析和参数转换失败会作为 bridge 错误抛出。

同步调用要求 native 返回 JSON `{ "success": true, "data": ... }`；缺少 native 方法、返回 JSON 无效或 `success` 不为 `true` 时抛出 JS `Error`。suspend 调用则由 callback 的 error/value 参数分别 reject/resolve。Bridge 本身不承诺调用耗时上限。

## 加载外部 DEX/JAR

```ts
interface JavaBridgeExternalCodeLoadOptions {
  nativeLibraryDir?: string;
  childFirstPrefixes?: string[];
}

Java.loadDex(path, options?): JavaBridgeLoadedCodePath;
Java.loadJar(path, options?): JavaBridgeLoadedCodePath;
Java.listLoadedCodePaths(): JavaBridgeLoadedCodePath[];
```

- `path` 必须是可读文件；`loadDex` 只接受 `.dex`，`loadJar` 只接受 `.jar` 且归档中必须有 `classes.dex`，普通 JVM bytecode JAR 不支持。
- `options` 可省略、传 `JavaBridgeExternalCodeLoadOptions`，或传字符串。字符串兼容形式表示 `nativeLibraryDir`。
- `nativeLibraryDir` 必须指向存在的目录。`childFirstPrefixes` 会去空白、移除空项并去重；匹配此前缀的类优先从该 DEX/JAR 加载，未命中时回退父加载器。
- 加载源会复制到应用管理的只读缓存目录。相同文件类型、canonical path 和加载选项再次调用时复用已注册项，返回 `alreadyLoaded: true`。
- 返回记录包含 `index`、`type`、`path`、`nativeLibraryDir`、`childFirstPrefixes` 和 `alreadyLoaded`。`listLoadedCodePaths()` 返回当前 loader 链的快照，尚无加载项时为空数组。
- 路径、扩展名、归档内容、native 库目录或 class loader 初始化失败时同步抛错；此 API 不返回 Promise。

加载入口不会验证 DEX/JAR 中每个类的可用性；实际解析仍受 Android class loader、依赖项和 ABI/native 库条件影响。

## 示例

```js
const StringBuilder = Java.type("java.lang.StringBuilder");
const builder = StringBuilder.newInstance();
builder.append("ToolPkg");
const text = builder.toString();

const Runnable = Java.implement("java.lang.Runnable", {
  run() {
    console.log("callback");
  }
});

complete({ text, hasRunnable: Runnable !== null });
```

## 声明与实现

- 声明：`examples/types/java-bridge.d.ts`
- JS facade：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsJavaBridge.kt`
- 类型转换、反射与接口代理：`JsJavaBridgeDelegates.kt`
- DEX/JAR loader：`JsExternalJavaCodeLoader.kt`