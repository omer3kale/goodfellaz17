@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
java -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
