# 招标现场工具箱 Windows 构建

Java 17 / Swing 离线工具箱，供 Windows 10/11 x64 使用。

## 下载和运行

在 Actions 中打开成功的 Build Windows x64，下载 TenderToolbox-Windows-x64 构建产物。
将其中的 TenderToolbox-Windows-x64.zip 完整解压，双击 TenderToolbox/TenderToolbox.exe。
运行环境已经内置，无需安装 Java 或部署网站。分发时保留完整目录。

## 构建

GitHub Actions 使用 Windows 2022 x64 和 Temurin Java 17，执行全部测试、jpackage 打包及13项内置演示验证。
也可以在装有 JDK 17 和 Maven 的 Windows 上运行 ./build-windows.ps1。

源码和内置模板用于工具箱构建；业务输入及用户输出不在本仓库中。
