$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'
$bodyFile = "E:\毕业设计\project\.tools\_body.json"
function CurlReq($method, $path, $body, $token) {
    $args = @('-s', '-X', $method, "$base$path")
    if ($token) { $args += @('-H', "Authorization: Bearer $token") }
    if ($body) {
        [System.IO.File]::WriteAllText($bodyFile, ($body | ConvertTo-Json -Depth 5 -Compress), (New-Object System.Text.UTF8Encoding($false)))
        $args += @('-H', 'Content-Type: application/json; charset=utf-8', '--data-binary', "@$bodyFile")
    }
    $raw = & curl.exe @args 2>$null
    $resp = $raw | ConvertFrom-Json
    if ($resp.code -ne 200) { throw "业务错误 code=$($resp.code) msg=$($resp.message)" }
    return $resp.data
}

$stu = CurlReq 'POST' '/auth/login' @{ username='student01'; password='123456' } $null

# 1. 修改资料（改名）
CurlReq 'PUT' '/users/profile' @{ realName='张同学(测试)' } $stu.token | Out-Null
$tutors = CurlReq 'GET' '/tutors?page=1&size=50' $null $null
$mine = $tutors.records | Where-Object { $_.userId -eq $stu.userId } | Select-Object -First 1
Write-Host "[PASS] 修改资料成功"

# 2. 未登录修改 -> 401
try { CurlReq 'PUT' '/users/profile' @{ realName='x' } $null | Out-Null; Write-Host "[FAIL] 未登录可改资料" }
catch { Write-Host "[PASS] 未登录被拦截" }

# 3. 原密码错误 -> 400
try { CurlReq 'PUT' '/users/password' @{ oldPassword='wrong'; newPassword='abcdef' } $stu.token | Out-Null; Write-Host "[FAIL] 错误原密码未拦截" }
catch { Write-Host "[PASS] 原密码错误被拦截" }

# 4. 新密码与原密码相同 -> 400
try { CurlReq 'PUT' '/users/password' @{ oldPassword='123456'; newPassword='123456' } $stu.token | Out-Null; Write-Host "[FAIL] 新旧密码相同未拦截" }
catch { Write-Host "[PASS] 新旧密码相同被拦截" }

# 5. 新密码过短 -> 400
try { CurlReq 'PUT' '/users/password' @{ oldPassword='123456'; newPassword='123' } $stu.token | Out-Null; Write-Host "[FAIL] 短密码未拦截" }
catch { Write-Host "[PASS] 短密码被拦截" }

# 6. 正确修改密码
CurlReq 'PUT' '/users/password' @{ oldPassword='123456'; newPassword='newpass888' } $stu.token | Out-Null
Write-Host "[PASS] 修改密码成功"

# 7. 旧密码登录失败 / 新密码登录成功
try { CurlReq 'POST' '/auth/login' @{ username='student01'; password='123456' } $null | Out-Null; Write-Host "[FAIL] 旧密码仍可登录" }
catch { Write-Host "[PASS] 旧密码登录被拒" }
$re = CurlReq 'POST' '/auth/login' @{ username='student01'; password='newpass888' } $null
Write-Host "$(if($re.userId -eq $stu.userId){'[PASS]'}else{'[FAIL]'}) 新密码登录成功"

# 8. 改回原密码（恢复数据）
CurlReq 'PUT' '/users/password' @{ oldPassword='newpass888'; newPassword='123456' } $re.token | Out-Null
$back = CurlReq 'POST' '/auth/login' @{ username='student01'; password='123456' } $null
Write-Host "$(if($back.userId -eq $stu.userId){'[PASS]'}else{'[FAIL]'}) 恢复原密码成功"
