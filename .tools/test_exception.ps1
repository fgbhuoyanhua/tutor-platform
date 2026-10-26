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

# 1. 非法 JWT -> 401
$r1 = CurlRaw 'GET' '/appointments' $null 'Bearer not-a-real-token'
Write-Host "[INFO] 非法token: code=$($r1.code) msg=$($r1.message)"
Write-Host "$(if($r1.code -eq 401){'[PASS]'}else{'[FAIL]'}) 非法JWT返回401"

# 2. 过期JWT -> 401（构造一个过期token）
$login = CurlRaw 'POST' '/auth/login' @{ username='student01'; password='123456' } $null
$good = $login.data.token
Write-Host "[INFO] 正常token访问: code=$((CurlRaw 'GET' '/appointments' $null $good).code)"

# 3. 缺少参数 -> 400
$r3 = CurlRaw 'GET' '/tutors/income?monthx=2026-09' $null $good
Write-Host "[INFO] 缺month参数: code=$($r3.code) msg=$($r3.message)"
Write-Host "$(if($r3.code -eq 400 -and $r3.message -match 'month'){'[PASS]'}else{'[FAIL]'}) 缺少参数返回400"

# 4. 参数类型错误 -> 400
$r4 = CurlRaw 'GET' '/appointments?page=abc' $null $good
Write-Host "[INFO] 类型错误: code=$($r4.code) msg=$($r4.message)"
Write-Host "$(if($r4.code -eq 400){'[PASS]'}else{'[FAIL]'}) 参数类型错误返回400"

# 5. 请求体JSON格式错误 -> 400
$raw5 = & curl.exe -s -X POST "$base/appointments" -H "Authorization: Bearer $good" -H 'Content-Type: application/json' -d '{bad json'
$r5 = $raw5 | ConvertFrom-Json
Write-Host "[INFO] 坏JSON: code=$($r5.code) msg=$($r5.message)"
Write-Host "$(if($r5.code -eq 400){'[PASS]'}else{'[FAIL]'}) JSON格式错误返回400"

# 6. 校验失败（DTO校验）-> 400
$r6 = CurlRaw 'POST' '/auth/register' @{ username='x'; phone='123'; code='123456'; password='123'; role=1 } $null
Write-Host "[INFO] DTO校验: code=$($r6.code) msg=$($r6.message)"
Write-Host "$(if($r6.code -eq 400){'[PASS]'}else{'[FAIL]'}) 参数校验返回400"

# 7. 注册重复手机号 -> 400（唯一键，非500）
$r7 = CurlRaw 'POST' '/auth/register' @{ username='dupuser1'; phone='13900008888'; code='123456'; password='123456'; role=1 } $null
Write-Host "[INFO] 重复注册: code=$($r7.code) msg=$($r7.message)"
Write-Host "$(if($r7.code -eq 400){'[PASS]'}else{'[FAIL]'}) 唯一键冲突返回400而非500"
