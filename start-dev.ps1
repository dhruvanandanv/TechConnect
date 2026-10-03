# TechConnect Enterprise PowerShell Launcher
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  TechConnect Enterprise IT Service Management" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Launching services in dedicated terminal windows..." -ForegroundColor Yellow
Write-Host ""

$rootDir = $PSScriptRoot

# 1. Start Python FastAPI AI Microservice (Port 8000)
Write-Host "[1/3] Starting Python FastAPI AI Microservice (Port 8000)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$rootDir\ai_services\ticket_intelligence'; py -3.12 -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload"

# 2. Start Spring Boot Backend (Port 8080)
Write-Host "[2/3] Starting Spring Boot REST Backend (Port 8080)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$rootDir\backend'; .\mvnw.cmd spring-boot:run"

# 3. Start React Frontend (Port 5173)
Write-Host "[3/3] Starting React Vite Frontend (Port 5173)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$rootDir\frontend'; npm run dev"

Write-Host ""
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  All 3 services are launching!" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontend:  http://localhost:5173" -ForegroundColor White
Write-Host "  Backend:   http://localhost:8080" -ForegroundColor White
Write-Host "  AI Engine: http://localhost:8000" -ForegroundColor White
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Wait ~15-20 seconds for the backend to finish booting, then open http://localhost:5173 in your browser." -ForegroundColor Yellow
