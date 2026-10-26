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
function Enc($s) { [uri]::EscapeDataString($s) }

$admin = CurlReq 'POST' '/auth/login' @{ username='admin'; password='123456' } $null
$stu = CurlReq 'POST' '/auth/login' @{ username='student01'; password='123456' } $null
Write-Host "[PASS] 登录 admin=$($admin.userId) stu=$($stu.userId)"

# 1. 新增科目
$subjectId = CurlReq 'POST' "/admin/subjects?name=$(Enc '物理实验')&sort=9" $null $admin.token
Write-Host "[PASS] 新增科目 id=$subjectId"

# 2. 重复名称 -> 拦截
try { CurlReq 'POST' "/admin/subjects?name=$(Enc '数学')" $null $admin.token | Out-Null; Write-Host "[FAIL] 重复科目未拦截" }
catch { Write-Host "[PASS] 重复科目被拦截" }

# 3. 空白名称 -> 拦截
try { CurlReq 'POST' '/admin/subjects?name=' $null $admin.token | Out-Null; Write-Host "[FAIL] 空名称未拦截" }
catch { Write-Host "[PASS] 空名称被拦截" }

# 4. 修改科目（重命名+排序）
CurlReq 'PUT' "/admin/subjects/${subjectId}?name=$(Enc '物理实验（拔高）')&sort=10" $null $admin.token | Out-Null
$subjects = CurlReq 'GET' '/subjects' $null $null
$upd = $subjects | Where-Object { $_.id -eq $subjectId }
Write-Host "$(if($upd.name -eq '物理实验（拔高）' -and $upd.sort -eq 10){'[PASS]'}else{'[FAIL]'}) 修改科目: $($upd.name) sort=$($upd.sort)"

# 5. 重命名与已有冲突 -> 拦截
try { CurlReq 'PUT' "/admin/subjects/${subjectId}?name=$(Enc '数学')" $null $admin.token | Out-Null; Write-Host "[FAIL] 重名未拦截" }
catch { Write-Host "[PASS] 重命名冲突被拦截" }

# 6. 删除被引用科目 -> 拦截
try { CurlReq 'DELETE' '/admin/subjects/2' $null $admin.token | Out-Null; Write-Host "[FAIL] 被引用科目可删除" }
catch { Write-Host "[PASS] 被引用科目删除被拦截" }

# 7. 删除无引用科目
CurlReq 'DELETE' "/admin/subjects/$subjectId" $null $admin.token | Out-Null
$after = CurlReq 'GET' '/subjects' $null $null
Write-Host "$(if(($after | Where-Object { $_.id -eq $subjectId }).Count -eq 0){'[PASS]'}else{'[FAIL]'}) 删除科目成功"

# 8. 学生越权
try { CurlReq 'POST' "/admin/subjects?name=$(Enc '越权')" $null $stu.token | Out-Null; Write-Host "[FAIL] 学生可管理科目" }
catch { Write-Host "[PASS] 学生越权被拦截" }

# 9. 删除不存在科目 -> 404
try { CurlReq 'DELETE' '/admin/subjects/99999' $null $admin.token | Out-Null; Write-Host "[FAIL] 不存在科目未报错" }
catch { Write-Host "[PASS] 删除不存在科目被拦截" }

# 10. 操作日志
$logs = CurlReq 'GET' '/admin/logs?page=1&size=10' $null $admin.token
$subjectLogs = @($logs.records | Where-Object { $_.targetType -eq 'subject' })
Write-Host "[INFO] 科目操作日志 $($subjectLogs.Count) 条: $(($subjectLogs | ForEach-Object { $_.action }) -join ',')"
