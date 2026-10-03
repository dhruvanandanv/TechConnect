@echo off
title TechConnect Enterprise Launcher
echo ===================================================
echo   TechConnect Enterprise IT Service Management
echo ===================================================
echo.
echo Launching services in dedicated terminal windows...
echo.

:: 1. Launch Python AI Microservice (Port 8000)
echo [1/3] Starting Python FastAPI AI Microservice (Port 8000)...
start "TechConnect AI Service (Port 8000)" cmd /k "cd /d %~dp0ai_services\ticket_intelligence && py -3.12 -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload"

:: 2. Launch Spring Boot Backend (Port 8080)
echo [2/3] Starting Spring Boot REST Backend (Port 8080)...
start "TechConnect Backend API (Port 8080)" cmd /k "cd /d %~dp0backend && mvnw.cmd spring-boot:run"

:: 3. Launch React Frontend (Port 5173)
echo [3/3] Starting React Vite Frontend (Port 5173)...
start "TechConnect React Frontend (Port 5173)" cmd /k "cd /d %~dp0frontend && npm run dev"

echo.
echo ===================================================
echo   All 3 services are starting!
echo.
echo   Frontend:  http://localhost:5173
echo   Backend:   http://localhost:8080
echo   AI Engine: http://localhost:8000
echo ===================================================
echo.
echo Once the windows finish booting, open http://localhost:5173 in your browser.
pause
