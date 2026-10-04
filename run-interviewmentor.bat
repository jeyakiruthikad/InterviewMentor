@echo off
setlocal
cd /d "%~dp0"
echo.
echo ================================================
echo   InterviewMentor - Full Application
echo ================================================
echo.
if not exist ".env" (
  echo No .env file was found.
  echo Creating it from .env.example...
  copy /Y ".env.example" ".env" >nul
  echo.
  echo Please open .env and enter your MySQL password and optional OpenAI API key.
  echo Then run this file again.
  echo.
  notepad ".env"
  pause
  exit /b 0
)
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
java -jar "target\interviewmentor.jar"
pause
