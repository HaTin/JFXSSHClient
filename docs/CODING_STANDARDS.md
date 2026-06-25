# 编码规范

Java版本：

21

## 必须

使用：

- SLF4J
- JavaDoc
- Constructor Injection

## 禁止

System.out.println()

空catch

硬编码密码

硬编码路径

硬编码界面文案（必须用 I18n，见 I18N.md）

## 命名规范

接口：

SSHService

实现：

SSHServiceImpl

常量：

DEFAULT_PORT

MAX_RETRY_COUNT

## UI规范

禁止：

绝对布局

必须：

BorderPane

VBox

HBox

GridPane
