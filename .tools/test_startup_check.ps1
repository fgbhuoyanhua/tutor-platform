$ErrorActionPreference = 'Continue'
$jar = "E:\毕业设计\project\backend\target\tutor-platform-backend-1.0.0.jar"
$env:MYSQL_HOST = 'localhost'; $env:MYSQL_USER = 'root'; $env:MYSQL_PASSWORD = 'root'
$env:REDIS_HOST = 'localhost'; $env:REDIS_PORT = '6379'

function RunCheck($name, $extraArgs, $expectAlive) {
    $log = "E:\毕业设计\project\.tools\startup_$name.log"
    $argList = @("-jar", "`"$jar`"", "--server.port=8081")
    $argList += $extraArgs
    $p = Start-Process -FilePath "java" -ArgumentList $argList -WorkingDirectory "E:\毕业设计\project\backend" -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds 14
    $alive = -not $p.HasExited
    if ($expectAlive) {
        if ($alive) { Write-Host "[PASS] $name 启动成功(进程存活)" } else { Write-Host "[FAIL] $name 意外退出 code=$($p.ExitCode)" }
    } else {
        if (-not $alive) { Write-Host "[PASS] $name 被拒绝启动(exit=$($p.ExitCode))" } else { Write-Host "[FAIL] $name 未被拦截(仍在运行)" }
    }
    $lines = Get-Content $log -Encoding UTF8 -ErrorAction SilentlyContinue | Select-String "配置自检|ERROR|Exception" | Select-Object -Last 3
    $lines | ForEach-Object { Write-Host "  LOG: $($_.Line.Trim())" }
    if ($alive) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}

# 场景A: prod + sms-mock=true -> 拒绝
RunCheck 'A_prod_mock' @('--spring.profiles.active=prod', '--app.sms-mock=true') $false

# 场景B: prod + 默认JWT密钥 -> 拒绝
RunCheck 'B_prod_weakkey' @('--spring.profiles.active=prod') $false

# 场景C: prod + 强JWT密钥 -> 通过
RunCheck 'C_prod_ok' @('--spring.profiles.active=prod', '--jwt.secret=ThisIsAStrongProdSecretKey-0123456789ABCDEF') $true
