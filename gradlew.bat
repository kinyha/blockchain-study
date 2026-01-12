@rem Gradle startup script for Windows

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem  Gradle startup script for Windows
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto checkWrapper
goto noJava

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

:checkWrapper
@rem Download wrapper JAR if not present
if exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" goto execute

echo Downloading Gradle Wrapper...
mkdir "%APP_HOME%\gradle\wrapper" 2>NUL
powershell -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%APP_HOME%\gradle\wrapper\gradle-wrapper.jar'"

:execute
@rem Setup the command line
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:noJava
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
exit /b 1
