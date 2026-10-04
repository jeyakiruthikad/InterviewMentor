@echo off
setlocal
cd /d "%~dp0"
echo.
echo ================================================
echo   InterviewMentor - Guided Demo
echo ================================================
echo.
if not exist "target\interviewmentor.jar" (
  echo Building InterviewMentor first...
  where mvn >nul 2>nul
  if errorlevel 1 (
    echo Maven was not found. Please install Maven 3.9+ and run this file again.
    pause
    exit /b 1
  )
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo Build failed. Please run ^"mvn clean package^" to see the full error.
    pause
    exit /b 1
  )
)
java -jar "target\interviewmentor.jar" --demo
pause
