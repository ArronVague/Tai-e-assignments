@echo off
setlocal

:: 创建文件夹
mkdir A1

:: 移动文件到文件夹
copy .\tai-e\src\main\java\pascal\taie\analysis\dataflow\analysis\LiveVariableAnalysis.java A1
copy .\tai-e\src\main\java\pascal\taie\analysis\dataflow\solver\IterativeSolver.java A1
copy .\tai-e\src\main\java\pascal\taie\analysis\dataflow\solver\Solver.java A1

:: 切换到目标文件夹
cd A1

:: 压缩文件夹
powershell -command "Compress-Archive -Path .\* -DestinationPath ..\A1.zip"

:: 切换回原目录
cd ..

endlocal
