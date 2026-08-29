Write-Host "=== Rate Limiter E2E (free/premium, JSON) ===" -ForegroundColor Cyan
function Send-Check {
  param($ClientId, $EndPoint)
  $body = @{clientId=$ClientId; endPoint=$EndPoint} | ConvertTo-Json
  try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/v1/check" -Method Post -ContentType "application/json" -Body $body -UseBasicParsing -ErrorAction Stop
    $j = $r.Content | ConvertFrom-Json
    return @{Status=[int]$r.StatusCode; Json=$j; Headers=$r.Headers; Ok=$true}
  } catch {
    $code=0; $j=$null; $h=@{}
    if($_.Exception.Response){
      $code=[int]$_.Exception.Response.StatusCode
      try{ $s=New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $j=$s.ReadToEnd() | ConvertFrom-Json } catch {}
    }
    return @{Status=$code; Json=$j; Headers=$h; Ok=$false}
  }
}
# helper for 400 via HttpClient (more reliable headers on 400)
function Send-CheckDetailed {
  param($ClientId, $EndPoint)
  Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
  $c = New-Object System.Net.Http.HttpClient
  $json = @{clientId=$ClientId; endPoint=$EndPoint} | ConvertTo-Json
  $content = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
  $resp = $c.PostAsync("http://localhost:8080/v1/check", $content).Result
  $code=[int]$resp.StatusCode
  $body=$resp.Content.ReadAsStringAsync().Result | ConvertFrom-Json -ErrorAction SilentlyContinue
  return @{Status=$code; Json=$body; Headers=$resp.Headers}
}

docker exec rate-limiter-redis-test redis-cli FLUSHALL | Out-Null
Write-Host "`n1) premium /payments (100)" -ForegroundColor Yellow
$r=Send-Check "premium" "/payments"; Write-Host " $($r.Status) limit=$($r.Json.limit) rem=$($r.Json.remaining) allowed=$($r.Json.allowed) H:$($r.Headers['X-RateLimit-Limit'])" -ForegroundColor Green

Write-Host "2) premium /notifications (10)" -ForegroundColor Yellow
$r=Send-Check "premium" "/notifications"; Write-Host " $($r.Status) limit=$($r.Json.limit) H:$($r.Headers['X-RateLimit-Limit'])" 

Write-Host "3) premium /search -> 50" -ForegroundColor Yellow
$r=Send-Check "premium" "/search"; Write-Host " $($r.Status) limit=$($r.Json.limit) expected 50 $(if($r.Json.limit -eq 50){'PASS'}else{'FAIL'})"

Write-Host "4) free /payments (5)" -ForegroundColor Yellow
$r=Send-Check "free" "/payments"; Write-Host " $($r.Status) limit=$($r.Json.limit) expected 5 $(if($r.Json.limit -eq 5){'PASS'}else{'FAIL'})"

Write-Host "5) free /search -> global 10" -ForegroundColor Yellow
$r=Send-Check "free" "/search"; Write-Host " $($r.Status) limit=$($r.Json.limit) expected 10 $(if($r.Json.limit -eq 10){'PASS'}else{'FAIL'})"

Write-Host "6) unknown -> global 10" -ForegroundColor Yellow
$r=Send-Check "unknown" "/any"; Write-Host " $($r.Status) limit=$($r.Json.limit) expected 10"

Write-Host "`n7) validation 400" -ForegroundColor Yellow
# use HttpClient for reliable 400 body
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$emptyBody = '{}'
$content = New-Object System.Net.Http.StringContent($emptyBody, [System.Text.Encoding]::UTF8, "application/json")
$resp = $client.PostAsync("http://localhost:8080/v1/check", $content).Result
Write-Host " empty body -> $($resp.StatusCode) expected 400 $(if([int]$resp.StatusCode -eq 400){'PASS'}else{'FAIL'})"
$content2 = New-Object System.Net.Http.StringContent('{"clientId":"","endPoint":"/payments"}', [System.Text.Encoding]::UTF8, "application/json")
$resp2 = $client.PostAsync("http://localhost:8080/v1/check", $content2).Result
Write-Host " empty clientId -> $($resp2.StatusCode) expected 400"

Write-Host "`n8) exhaust free /payments 5 -> 429" -ForegroundColor Yellow
docker exec rate-limiter-redis-test redis-cli FLUSHALL | Out-Null
1..6 | ForEach-Object {
  $r=Send-Check "free" "/payments"
  $col=if($r.Status -eq 429){"Red"}else{"Green"}
  Write-Host ("  {0}: {1} rem={2} retry={3}" -f $_, $r.Status, $r.Json.remaining, $r.Json.retryAfterSeconds) -ForegroundColor $col
}

Write-Host "`n9) slash normalization" -ForegroundColor Yellow
docker exec rate-limiter-redis-test redis-cli FLUSHALL | Out-Null
$a=Send-Check "free" "/payments"; $b=Send-Check "free" "payments"
Write-Host "  /payments rem=$($a.Json.remaining) -> payments rem=$($b.Json.remaining) $(if($b.Json.remaining -eq $a.Json.remaining-1){'PASS same bucket'}else{'FAIL'})"

Write-Host "`n10) hammer 20 parallel free (5 allow)" -ForegroundColor Yellow
docker exec rate-limiter-redis-test redis-cli FLUSHALL | Out-Null
$client2 = New-Object System.Net.Http.HttpClient
$tasks = 1..20 | ForEach-Object {
  $j='{"clientId":"free","endPoint":"/payments"}'
  $ct = New-Object System.Net.Http.StringContent($j, [System.Text.Encoding]::UTF8, "application/json")
  $client2.PostAsync("http://localhost:8080/v1/check", $ct)
}
[System.Threading.Tasks.Task]::WaitAll($tasks)
$c200=($tasks | Where-Object { [int]$_.Result.StatusCode -eq 200 }).Count
$c429=($tasks | Where-Object { [int]$_.Result.StatusCode -eq 429 }).Count
Write-Host "  200=$c200 (exp 5) 429=$c429 (exp15) $(if($c200 -eq 5){'PASS atomic'}else{'FAIL race'})" -ForegroundColor $(if($c200 -eq 5){'Green'}else{'Red'})

Write-Host "`n11) fail-open (stop redis)" -ForegroundColor Yellow
docker stop rate-limiter-redis-test | Out-Null
Start-Sleep -Seconds 1
$r=Send-Check "free" "/payments"
Write-Host "  redis down -> $($r.Status) allowed=$($r.Json.allowed) limit=$($r.Json.limit) (fail-open true -> 200 allowed true)" -ForegroundColor $(if($r.Json.allowed){'Green'}else{'Red'})
docker start rate-limiter-redis-test | Out-Null
Start-Sleep -Seconds 2
Write-Host "  redis back ->"
$r=Send-Check "free" "/payments"; Write-Host "   $($r.Status) limit=$($r.Json.limit)"

Write-Host "`n=== E2E Complete ===" -ForegroundColor Cyan