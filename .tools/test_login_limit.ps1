$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$bodyFile = "E:\毕业设计\project\.tools\_body.json"
function CurlRaw($method, $path, $body, $token) {
    $args = @('-s', '-X', $method, "$base$path")
    if ($token) { $args += @('-H', "Authorization: Bearer $token") }
    if ($body) {
        [System.IO.File]::WriteAllText($bodyFile, ($body | ConvertTo-Json -Depth 5 -Compress), (New-Object System.Text.UTF8Encoding($false)))
        $args += @('-H', 'Content-Type: application/json; charset=utf-8', '--data-binary', "@$bodyFile")
    }
    $raw = & curl.exe @args 2>$null
    return ($raw | ConvertFrom-Json)
}

# 0. 注册测试账号（若未注册）
$reg = CurlRaw 'POST' '/auth/register' @{ username='locktest01'; phone='13800000001'; code='123456'; password='123456'; role=1; realName='限流测试' } $null
if ($reg.code -ne 200) { Write-Host "[INFO] 注册跳过: code=$($reg.code)" } else { Write-Host "[PASS] 注册 locktest01" }

# 1. 连续错误密码 5 次
for ($i = 1; $i -le 5; $i++) {
    $r = CurlRaw 'POST' '/auth/login' @{ username='locktest01'; password='wrongpass' } $null
    Write-Host "[INFO] 第${i}次错误: code=$($r.code) msg=$($r.message)"
}
$r5 = CurlRaw 'POST' '/auth/login' @{ username='locktest01'; password='wrongpass' } $null
Write-Host "[INFO] 第6次错误(应锁定): code=$($r5.code) msg=$($r5.message)"
if ($r5.code -eq 429 -or $r5.message -match '锁定') { Write-Host "[PASS] 第5次失败后账号锁定" } else { Write-Host "[FAIL] 锁定未生效" }

# 2. 锁定后即使密码正确也登录失败
$ok = CurlRaw 'POST' '/auth/login' @{ username='locktest01'; password='123456' } $null
Write-Host "[INFO] 锁定后正确密码: code=$($ok.code) msg=$($ok.message)"
if ($ok.code -ne 200) { Write-Host "[PASS] 锁定期间正确密码也无法登录" } else { Write-Host "[FAIL] 锁定期间可登录" }

# 3. 正常账号不受影响
$normal = CurlRaw 'POST' '/auth/login' @{ username='student01'; password='123456' } $null
Write-Host "[INFO] 正常账号登录: code=$($normal.code)"
if ($normal.code -eq 200) { Write-Host "[PASS] 正常账号不受限流影响" } else { Write-Host "[FAIL] 正常账号被误锁" }
