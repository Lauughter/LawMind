@echo off
echo ============================================================
echo Sentinel 限流配置检查脚本
echo ============================================================
echo.

echo [1/5] 检查 Java 进程...
tasklist | findstr java.exe
if %errorlevel% == 0 (
    echo ✅ Java 进程正在运行
) else (
    echo ❌ Java 进程未运行，请先启动服务！
    pause
    exit /b 1
)
echo.

echo [2/5] 检查 Maven 依赖...
cd /d D:\develop\Code\LawMind
call mvn dependency:tree 2^>nul | findstr sentinel-annotation-aspectj
if %errorlevel% == 0 (
    echo ✅ sentinel-annotation-aspectj 依赖已添加
) else (
    echo ❌ 缺少 AOP 依赖，请检查 pom.xml
    pause
    exit /b 1
)
echo.

echo [3/5] 检查配置文件...
if exist "src\main\java\com\lhs\lawmind\config\SentinelAutoConfig.java" (
    echo ✅ SentinelAutoConfig.java 存在
    findstr "@EnableAspectJAutoProxy" src\main\java\com\lhs\lawmind\config\SentinelAutoConfig.java >nul
    if %errorlevel% == 0 (
        echo ✅ @EnableAspectJAutoProxy 注解已添加
    ) else (
        echo ❌ 缺少@EnableAspectJAutoProxy 注解
    )
) else (
    echo ❌ SentinelAutoConfig.java 不存在
)
echo.

if exist "src\main\java\com\lhs\lawmind\config\SentinelConfig.java" (
    echo ✅ SentinelConfig.java 存在
) else (
    echo ❌ SentinelConfig.java 不存在
)
echo.

if exist "src\main\java\com\lhs\lawmind\controller\AiChatController.java" (
    findstr "@SentinelResource" src\main\java\com\lhs\lawmind\controller\AiChatController.java >nul
    if %errorlevel% == 0 (
        echo ✅ AiChatController.java 有@SentinelResource 注解
    ) else (
        echo ❌ AiChatController.java 缺少@SentinelResource 注解
    )
) else (
    echo ❌ AiChatController.java 不存在
)
echo.

echo [4/5] 编译项目...
call mvn clean compile -DskipTests
if %errorlevel% == 0 (
    echo ✅ 编译成功
) else (
    echo ❌ 编译失败，请检查错误信息
    pause
    exit /b 1
)
echo.

echo [5/5] 检查 Python 环境...
python --version >nul 2>&1
if %errorlevel% == 0 (
    echo ✅ Python 已安装
    python -c "import requests" 2>nul
    if %errorlevel% == 0 (
        echo ✅ requests 库已安装
    ) else (
        echo ⚠️  requests 库未安装，正在安装...
        pip install requests
    )
) else (
    echo ❌ Python 未安装
)
echo.

echo ============================================================
echo 检查完成！
echo ============================================================
echo.
echo 下一步操作:
echo 1. 在 IDEA 中完全停止当前服务（点击红色停止按钮）
echo 2. 重新启动 AiChatApplication
echo 3. 观察启动日志，应该看到:
echo    - "Sentinel 限流规则加载完成!"
echo    - "Sentinel 配置验证开始..."
echo    - "当前限流规则数量：1"
echo 4. 服务启动后，运行测试脚本:
echo    python test-rate-limit.py
echo    选择选项 2 (并发测试)
echo.
echo 预期结果:
echo    - 成功：5 个请求
echo    - 被限流：15 个请求
echo    - 限流率：75%%
echo.
pause
