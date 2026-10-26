param($PayloadFile, $Token, $OutFile)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$raw = & curl.exe -s -X POST "http://localhost:8080/api/appointments" -H "Authorization: Bearer $Token" -H "Content-Type: application/json" --data-binary "@$PayloadFile" 2>$null
$j = $raw -join '' | ConvertFrom-Json
[System.IO.File]::WriteAllText($OutFile, "$($j.code)|$($j.message)", (New-Object System.Text.UTF8Encoding($false)))
