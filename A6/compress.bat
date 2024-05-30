@echo off
setlocal

:: 检查并删除已存在的文件夹
if exist test rd /s /q test

:: 检查并删除已存在的zip文件
if exist test.zip del /f test.zip

:: 创建文件夹
mkdir test

:: 移动文件到文件夹
@REM Solver.java
@REM _1CallSelector.java
@REM _1ObjSelector.java
@REM _1TypeSelector.java
@REM _2CallSelector.java
@REM _2ObjSelector.java
@REM _2TypeSelector.java
@REM copy .\tai-e\src\main\java\pascal\taie\analysis\pta\ci\Solver.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\pta\cs\Solver.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\pta\core\cs\selector\_1CallSelector.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\pta\core\cs\selector\_1ObjSelector.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\pta\core\cs\selector\_1TypeSelector.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\pta\core\cs\selector\_2CallSelector.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\pta\core\cs\selector\_2ObjSelector.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\pta\core\cs\selector\_2TypeSelector.java test


:: 切换到目标文件夹
cd test

:: 压缩文件夹
powershell -command "Compress-Archive -Path .\* -DestinationPath ..\test.zip"

:: 切换回原目录
cd ..

endlocal
