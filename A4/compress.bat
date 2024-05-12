@echo off
setlocal

:: 检查并删除已存在的文件夹
if exist test rd /s /q test

:: 检查并删除已存在的zip文件
if exist test.zip del /f test.zip

:: 创建文件夹
mkdir test

:: 移动文件到文件夹
copy .\tai-e\src\main\java\pascal\taie\analysis\graph\callgraph\CHABuilder.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\dataflow\inter\InterConstantPropagation.java test
copy .\tai-e\src\main\java\pascal\taie\analysis\dataflow\inter\InterSolver.java test

:: 切换到目标文件夹
cd test

:: 压缩文件夹
powershell -command "Compress-Archive -Path .\* -DestinationPath ..\test.zip"

:: 切换回原目录
cd ..

endlocal
