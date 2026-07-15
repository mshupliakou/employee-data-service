@echo off
chcp 65001 >nul
echo [1/1] Starting application with PostgreSQL...
call .\mvnw.cmd spring-boot:run
