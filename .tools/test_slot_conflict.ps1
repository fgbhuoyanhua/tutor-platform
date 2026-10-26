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
    return ($raw -join '' | ConvertFrom-Json)
}

$stu = CurlRaw 'POST' '/auth/login' @{ username='student01'; password='123456' } $null
$tutors = CurlRaw 'GET' '/tutors?page=1&size=50' $null $null
$t1 = $tutors.data.records | Where-Object { $_.userId -eq 2 } | Select-Object -First 1
Write-Host "[INFO] 目标家教 id=$($t1.id)"

$date = (Get-Date).AddDays(32).ToString('yyyy-MM-dd')
$payload = @{ tutorId=$t1.id; subjectId=$t1.subjectId; appointDate=$date; timeSlot='14:00-16:00' } | ConvertTo-Json -Compress -Depth 5

$worker = "E:\毕业设计\project\.tools\worker_slot.ps1"
$procs = @()
for ($i = 0; $i -lt 5; $i++) {
    $pf = "E:\毕业设计\project\.tools\_slot_body_$i.json"
    $of = "E:\毕业设计\project\.tools\_slot_out_$i.txt"
    [System.IO.File]::WriteAllText($pf, $payload, (New-Object System.Text.UTF8Encoding($false)))
    $procs += Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile","-ExecutionPolicy","Bypass","-File",$worker,$pf,$stu.data.token,$of -WindowStyle Hidden -PassThru
}
$procs | Wait-Process

$ok = 0; $conflict = 0; $err = 0
for ($i = 0; $i -lt 5; $i++) {
    $of = "E:\毕业设计\project\.tools\_slot_out_$i.txt"
    $line = [System.IO.File]::ReadAllText($of, [System.Text.Encoding]::UTF8).Trim()
    Write-Host "[INFO] 请求${i}: $line"
    $parts = $line -split '\|'
    if ($parts[0] -eq '200') { $ok++ } elseif ($parts[1] -match '已被预约|繁忙') { $conflict++ } else { $err++ }
}
Write-Host "[INFO] 并发5单: 成功=$ok 冲突拒绝=$conflict 其他异常=$err"
if ($ok -eq 1 -and $conflict -eq 4) { Write-Host "[PASS] 并发兜底正确：仅1单成功，其余4单提示时段已被预约" }
elseif ($ok -eq 1 -and $conflict -ge 3 -and $err -eq 0) { Write-Host "[PASS] 并发兜底生效" }
else { Write-Host "[FAIL] 并发结果异常" }
