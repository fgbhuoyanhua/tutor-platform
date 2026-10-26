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

# 快速连下 10 单（不同日期），模拟密集下单
$orderNos = @()
$fail = 0
for ($i = 1; $i -le 10; $i++) {
    $d = (Get-Date).AddDays(20 + $i).ToString('yyyy-MM-dd')
    $slot = if ($i % 2 -eq 0) { '09:00-11:00' } else { '14:00-16:00' }
    try {
        $oid = CurlReq 'POST' '/appointments' @{ tutorId=$myTutor.id; subjectId=$myTutor.subjectId; appointDate=$d; timeSlot=$slot } $stu.token
        $orderNos += $oid
        Write-Host "[INFO] 第${i}单 ok id=$oid"
    } catch { $fail++; Write-Host "[FAIL] 第${i}单失败: $($_.Exception.Message)" }
}
Write-Host "$(if($fail -eq 0){'[PASS]'}else{'[FAIL]'}) 10单全部下单成功(失败$fail)"

# 订单号唯一性（数据库层验证）
$orders = CurlReq 'GET' '/appointments?page=1&size=50' $null $stu.token
$myNos = @($orders.records | Where-Object { $_.id -in $orderNos } | ForEach-Object { $_.orderNo })
$distinct = @($myNos | Sort-Object -Unique)
Write-Host "[INFO] 订单号: $($myNos.Count)个 去重后: $($distinct.Count)个"
Write-Host "$(if($distinct.Count -eq $myNos.Count -and $myNos.Count -eq 10){'[PASS]'}else{'[FAIL]'}) 订单号全部唯一且为10位"
