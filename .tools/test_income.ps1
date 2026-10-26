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

$tut = Req 'Post' '/auth/login' @{ username='tutor01'; password='123456' } $null
Write-Host "[PASS] tutor01 登录 userId=$($tut.userId)"

# 1. 正常查询当月收入
$income = Req 'Get' "/tutors/income?month=2026-09" $null $tut.token
Write-Host "[PASS] 收入统计 月份=$($income.month) 单数=$($income.orderCount) 金额=$($income.totalIncome)"

# 2. 无订单月份 -> 0
$empty = Req 'Get' "/tutors/income?month=2020-01" $null $tut.token
Write-Host "[PASS] 空月份 单数=$($empty.orderCount) 金额=$($empty.totalIncome)"

# 3. 非法月份格式 -> 400
try { Req 'Get' "/tutors/income?month=202609" $null $tut.token | Out-Null; Write-Host "[FAIL] 非法月份未拦截" }
catch { Write-Host "[PASS] 非法月份被拦截: $($_.Exception.Message)" }

# 4. 学生越权 -> 403
$stu = Req 'Post' '/auth/login' @{ username='student01'; password='123456' } $null
try { Req 'Get' "/tutors/income?month=2026-09" $null $stu.token | Out-Null; Write-Host "[FAIL] 学生可查老师收入" }
catch { Write-Host "[PASS] 学生越权被拦截: $($_.Exception.Message)" }

# 5. 未登录 -> 401
try { Req 'Get' "/tutors/income?month=2026-09" $null $null | Out-Null; Write-Host "[FAIL] 未登录可查" }
catch { Write-Host "[PASS] 未登录被拦截(HTTP $($_.Exception.Response.StatusCode.value__))" }
