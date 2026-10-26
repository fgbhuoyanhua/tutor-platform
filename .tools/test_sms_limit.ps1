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

# 用未注册手机号连续错验证码 5 次
$phone = '13900009999'
for ($i = 1; $i -le 5; $i++) {
    $r = CurlRaw 'POST' '/auth/register' @{ username="codetest$i"; phone=$phone; code='000000'; password='123456'; role=1; realName='验证码测试' } $null
    Write-Host "[INFO] 第${i}次错误验证码: code=$($r.code) msg=$($r.message)"
}
$r6 = CurlRaw 'POST' '/auth/register' @{ username='codetest6'; phone=$phone; code='000000'; password='123456'; role=1; realName='验证码测试' } $null
Write-Host "[INFO] 第6次(应429): code=$($r6.code) msg=$($r6.message)"
if ($r6.code -eq 429) { Write-Host "[PASS] 验证码错误次数限制生效" } else { Write-Host "[FAIL] 验证码限次未生效" }

# 正确验证码也无法注册（已锁定）
$ok = CurlRaw 'POST' '/auth/register' @{ username='codetest7'; phone=$phone; code='123456'; password='123456'; role=1; realName='验证码测试' } $null
Write-Host "[INFO] 锁定期正确验证码: code=$($ok.code) msg=$($ok.message)"
if ($ok.code -ne 200) { Write-Host "[PASS] 锁定期正确验证码也无法注册" } else { Write-Host "[FAIL] 锁定期可注册" }

# 其他手机号不受影响（用新手机号+正确验证码注册成功）
$newPhone = '13900008888'
$reg = CurlRaw 'POST' '/auth/register' @{ username='codetest_ok'; phone=$newPhone; code='123456'; password='123456'; role=1; realName='正常注册' } $null
Write-Host "[INFO] 新手机号正常注册: code=$($reg.code)"
if ($reg.code -eq 200) { Write-Host "[PASS] 其他手机号不受影响" } else { Write-Host "[FAIL] 其他手机号受影响" }
