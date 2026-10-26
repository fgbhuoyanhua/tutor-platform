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

$admin = Req 'Post' '/auth/login' @{ username='admin'; password='123456' } $null
$stu = Req 'Post' '/auth/login' @{ username='student01'; password='123456' } $null
Write-Host "[PASS] admin 登录 userId=$($admin.userId)"

# 1. 全量订单
$all = Req 'Get' '/admin/orders?page=1&size=20' $null $admin.token
Write-Host "[PASS] 全量订单 total=$($all.total) 第1条: $($all.records[0].orderNo) 学生=$($all.records[0].studentName) 老师=$($all.records[0].tutorName) 状态=$($all.records[0].status)"

# 2. 按状态筛选 已完成(3)
$done = Req 'Get' '/admin/orders?status=3&page=1&size=20' $null $admin.token
$ok = ($done.records | Where-Object { $_.status -ne 3 }).Count -eq 0
Write-Host "$(if($ok){'[PASS]'}else{'[FAIL]'}) 按状态筛选(已完成) total=$($done.total) 全部为已完成: $ok"

# 3. 待确认(0)
$pending = Req 'Get' '/admin/orders?status=0&page=1&size=20' $null $admin.token
Write-Host "[PASS] 待确认筛选 total=$($pending.total)"

# 4. 学生越权
try { Req 'Get' '/admin/orders' $null $stu.token | Out-Null; Write-Host "[FAIL] 学生可访问管理接口" }
catch { Write-Host "[PASS] 学生越权被拦截: $($_.Exception.Message)" }
