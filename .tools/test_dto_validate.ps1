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
$stu = CurlRaw 'POST' '/auth/login' @{ username='student01'; password='123456' } $null
$tut = CurlRaw 'POST' '/auth/login' @{ username='tutor02'; password='123456' } $null
$tutors = CurlRaw 'GET' '/tutors?page=1&size=50' $null $null
$t1 = $tutors.records | Where-Object { $_.userId -eq 2 } | Select-Object -First 1

# 1. timeSlot 格式错误 -> 400
$r1 = CurlRaw 'POST' '/appointments' @{ tutorId=$t1.id; subjectId=$t1.subjectId; appointDate='2026-09-20'; timeSlot='0900-1100' } $stu.data.token
Write-Host "[INFO] timeSlot格式: code=$($r1.code) msg=$($r1.message)"
Write-Host "$(if($r1.code -eq 400 -and $r1.message -match '时间段'){'[PASS]'}else{'[FAIL]'}) 时间段格式校验"

# 2. 过去日期 -> 400
$r2 = CurlRaw 'POST' '/appointments' @{ tutorId=$t1.id; subjectId=$t1.subjectId; appointDate='2026-01-01'; timeSlot='09:00-11:00' } $stu.data.token
Write-Host "[INFO] 过去日期: code=$($r2.code) msg=$($r2.message)"
Write-Host "$(if($r2.code -eq 400 -and $r2.message -match '早于'){'[PASS]'}else{'[FAIL]'}) 过去日期校验"

# 3. 发布家教价格<=0 -> 400
$r3 = CurlRaw 'POST' '/tutors' @{ subjectId=1; price=-1; grade='高三'; introduce='测试' } $tut.data.token
Write-Host "[INFO] 负价格: code=$($r3.code) msg=$($r3.message)"
Write-Host "$(if($r3.code -eq 400 -and $r3.message -match '资费'){'[PASS]'}else{'[FAIL]'}) 价格必须大于0"

# 4. 简介超长 -> 400
$long = '测' * 2100
$r4 = CurlRaw 'POST' '/tutors' @{ subjectId=1; price=100; grade='高三'; introduce=$long } $tut.data.token
Write-Host "[INFO] 超长简介: code=$($r4.code) msg=$($r4.message)"
Write-Host "$(if($r4.code -eq 400 -and $r4.message -match '2000'){'[PASS]'}else{'[FAIL]'}) 简介长度限制"

# 5. 正常发布 -> 200
$r5 = CurlRaw 'POST' '/tutors' @{ subjectId=1; price=150; grade='高二'; introduce='正常发布测试' } $tut.data.token
Write-Host "[INFO] 正常发布: code=$($r5.code) id=$($r5.data)"
Write-Host "$(if($r5.code -eq 200){'[PASS]'}else{'[FAIL]'}) 正常参数放行"
