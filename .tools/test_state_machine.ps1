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
$tut = CurlReq 'POST' '/auth/login' @{ username='tutor01'; password='123456' } $null
$tutors = CurlReq 'GET' '/tutors?page=1&size=50' $null $null
$myTutor = $tutors.records | Where-Object { $_.userId -eq $tut.userId } | Select-Object -First 1

# 1. 下单（未来日期，避开已有冲突）
$date = (Get-Date).AddDays(10).ToString('yyyy-MM-dd')
$orderA = CurlReq 'POST' '/appointments' @{ tutorId=$myTutor.id; subjectId=$myTutor.subjectId; appointDate=$date; timeSlot='09:00-11:00' } $stu.token
Write-Host "[PASS] 下单 orderA=$orderA"

# 2. 老师接单
CurlReq 'PUT' "/appointments/$orderA/confirm" $null $tut.token | Out-Null
Write-Host "[PASS] 接单 -> 已预约(1)"

# 3. 接单后学生直接确认完成 -> 应拦截（仅授课中可完成）
try { CurlReq 'PUT' "/appointments/$orderA/finish" $null $stu.token | Out-Null; Write-Host "[FAIL] 未开始授课可完成" }
catch { Write-Host "[PASS] 跳过授课中直接完成被拦截" }

# 4. 老师开始授课
CurlReq 'PUT' "/appointments/$orderA/start" $null $tut.token | Out-Null
Write-Host "[PASS] 开始授课 -> 授课中(2)"

# 5. 重复开始 -> 拦截
try { CurlReq 'PUT' "/appointments/$orderA/start" $null $tut.token | Out-Null; Write-Host "[FAIL] 重复开始未拦截" }
catch { Write-Host "[PASS] 重复开始授课被拦截" }

# 6. 学生调 start -> 403
try { CurlReq 'PUT' "/appointments/$orderA/start" $null $stu.token | Out-Null; Write-Host "[FAIL] 学生可开始授课" }
catch { Write-Host "[PASS] 学生开始授课被拦截" }

# 7. 授课中 -> 学生确认完成
CurlReq 'PUT' "/appointments/$orderA/finish" $null $stu.token | Out-Null
Write-Host "[PASS] 授课中确认完成 -> 已完成(3)"

# 8. 未接单直接开始 -> 拦截
$orderB = CurlReq 'POST' '/appointments' @{ tutorId=$myTutor.id; subjectId=$myTutor.subjectId; appointDate=(Get-Date).AddDays(11).ToString('yyyy-MM-dd'); timeSlot='14:00-16:00' } $stu.token
try { CurlReq 'PUT' "/appointments/$orderB/start" $null $tut.token | Out-Null; Write-Host "[FAIL] 未接单可开始" }
catch { Write-Host "[PASS] 未接单直接开始被拦截" }

# 9. 取消订单后再开始 -> 拦截
$orderC = CurlReq 'POST' '/appointments' @{ tutorId=$myTutor.id; subjectId=$myTutor.subjectId; appointDate=(Get-Date).AddDays(12).ToString('yyyy-MM-dd'); timeSlot='09:00-11:00' } $stu.token
CurlReq 'PUT' "/appointments/$orderC/cancel" $null $stu.token | Out-Null
try { CurlReq 'PUT' "/appointments/$orderC/start" $null $tut.token | Out-Null; Write-Host "[FAIL] 已取消可开始" }
catch { Write-Host "[PASS] 已取消订单开始被拦截" }

# 10. 状态查询验证：老师订单列表含授课中/已完成
$list = CurlReq 'GET' '/appointments?page=1&size=20' $null $tut.token
$sA = ($list.records | Where-Object { $_.id -eq $orderA }).status
$sB = ($list.records | Where-Object { $_.id -eq $orderB }).status
Write-Host "[INFO] orderA状态=$sA(应3) orderB状态=$sB(应0)"
Write-Host "$(if($sA -eq 3){'[PASS]'}else{'[FAIL]'}) 状态机完整流转验证"
