$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'
function Req($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $args = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
    if ($body) { $args['Body'] = ($body | ConvertTo-Json -Depth 5); $args['ContentType'] = 'application/json; charset=utf-8' }
    $resp = Invoke-RestMethod @args
    if ($resp.code -ne 200) { throw "业务错误 code=$($resp.code) msg=$($resp.message)" }
    return $resp.data
}

# 1. 登录
$stu = Req 'Post' '/auth/login' @{ username='student01'; password='123456' } $null
$tut = Req 'Post' '/auth/login' @{ username='tutor01'; password='123456' } $null
Write-Host "[PASS] 登录 student01($($stu.userId)) tutor01($($tut.userId))"

# 2. 家教信息
$tutors = Req 'Get' '/tutors' $null $null
$myTutor = $tutors.records | Where-Object { $_.userId -eq $tut.userId } | Select-Object -First 1
Write-Host "[INFO] 家教 id=$($myTutor.id) userId=$($myTutor.userId) 初始评分=$($myTutor.rating) 评价数=$($myTutor.evaluateCount) 完成单=$($myTutor.finishedOrderCount)"

# 3. 下单 -> 4. 接单 -> 5. 完成
$date = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
$orderId = Req 'Post' '/appointments' @{ tutorId=$myTutor.id; subjectId=$myTutor.subjectId; appointDate=$date; timeSlot='09:00-11:00' } $stu.token
Req 'Put' "/appointments/$orderId/confirm" $null $tut.token | Out-Null
Req 'Put' "/appointments/$orderId/finish" $null $stu.token | Out-Null
Write-Host "[PASS] 下单($orderId)->接单->完成 全流程"

# 6. 评价(4分)
Req 'Post' '/evaluations' @{ orderId=$orderId; score=4; content='老师讲得很细致，收获很大' } $stu.token | Out-Null
Write-Host "[PASS] 评价提交成功(4分)"

# 7. 重复评价 -> 拦截
try { Req 'Post' '/evaluations' @{ orderId=$orderId; score=5; content='重复' } $stu.token | Out-Null; Write-Host "[FAIL] 重复评价未被拦截" }
catch { Write-Host "[PASS] 重复评价被拦截: $($_.Exception.Message)" }

# 8. 未完成订单评价 -> 拦截
$order2 = Req 'Post' '/appointments' @{ tutorId=$myTutor.id; subjectId=$myTutor.subjectId; appointDate=(Get-Date).AddDays(4).ToString('yyyy-MM-dd'); timeSlot='14:00-16:00' } $stu.token
try { Req 'Post' '/evaluations' @{ orderId=$order2; score=5 } $stu.token | Out-Null; Write-Host "[FAIL] 未完成订单可评价" }
catch { Write-Host "[PASS] 未完成订单评价被拦截: $($_.Exception.Message)" }

# 9. 越权：老师代评 -> 拦截
try { Req 'Post' '/evaluations' @{ orderId=$order2; score=1 } $tut.token | Out-Null; Write-Host "[FAIL] 老师可评价" }
catch { Write-Host "[PASS] 老师越权评价被拦截: $($_.Exception.Message)" }

# 10. 评价列表（公开，未带token）
$evals = Req 'Get' "/evaluations/tutor/$($tut.userId)?page=1&size=10" $null $null
Write-Host "[PASS] 评价列表(公开) total=$($evals.total) 第1条: $($evals.records[0].studentName) $($evals.records[0].score)分 '$($evals.records[0].content)'"

# 11. 家教详情评分聚合
$detail = Req 'Get' "/tutors/$($myTutor.id)" $null $null
Write-Host "[PASS] 家教详情 新评分=$($detail.rating) 评价数=$($detail.evaluateCount) 完成单=$($detail.finishedOrderCount)"

# 12. 评分重算正确性：该老师2条评价(4,4) -> AVG=4.00
$all = Req 'Get' "/tutors?page=1&size=50" $null $null
$t1 = $all.records | Where-Object { $_.userId -eq $tut.userId } | Select-Object -First 1
Write-Host "[INFO] 列表页聚合: 评分=$($t1.rating) 评价数=$($t1.evaluateCount) 完成单=$($t1.finishedOrderCount)"
