@echo off
set MYSQL_PWD=root
"D:\JAVA\mysql\mysql-8.0.44-winx64\mysql-8.0.44-winx64\bin\mysql.exe" --default-character-set=utf8mb4 -u root academic_warning_system -N -B < "D:\ABS\testsprite\local-check\_fk1.sql"
