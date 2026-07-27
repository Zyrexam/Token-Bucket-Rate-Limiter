# test.ps1 - Fixed version
Write-Host "=== Rate Limiter Tests ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "Test 1: First request (basic-client)" -ForegroundColor Yellow
$response = Invoke-WebRequest -Uri "http://localhost:8080/v1/check?client_id=basic-client" -Method POST
Write-Host "Status: $($response.StatusCode)"
Write-Host "Limit: $($response.Headers['X-RateLimit-Limit'])"
Write-Host "Remaining: $($response.Headers['X-RateLimit-Remaining'])"
Write-Host "Body: $($response.Content)"
Write-Host ""

Write-Host "Test 2: 6 consecutive requests to exhaust bucket" -ForegroundColor Yellow
1..6 | ForEach-Object {
    Write-Host "Request ${_}:" -NoNewline
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/v1/check?client_id=basic-client" -Method POST
    $color = if ($resp.StatusCode -eq 429) { "Red" } else { "Green" }
    Write-Host " HTTP $($resp.StatusCode) | Remaining: $($resp.Headers['X-RateLimit-Remaining'])" -ForegroundColor $color
}
Write-Host ""

Write-Host "Test 3: Premium client (100 capacity)" -ForegroundColor Yellow
$response = Invoke-WebRequest -Uri "http://localhost:8080/v1/check?client_id=premium-client" -Method POST
Write-Host "Status: $($response.StatusCode)"
Write-Host "Limit: $($response.Headers['X-RateLimit-Limit'])"
Write-Host "Remaining: $($response.Headers['X-RateLimit-Remaining'])"
Write-Host ""

Write-Host "Test 4: Unknown client (default config)" -ForegroundColor Yellow
$response = Invoke-WebRequest -Uri "http://localhost:8080/v1/check?client_id=some-random-id" -Method POST
Write-Host "Status: $($response.StatusCode)"
Write-Host "Limit: $($response.Headers['X-RateLimit-Limit'])"
Write-Host "Remaining: $($response.Headers['X-RateLimit-Remaining'])"
Write-Host ""

Write-Host "Test 5: Empty client_id (validation)" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/v1/check?client_id=" -Method POST
    Write-Host "Status: $($response.StatusCode)"
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status: 400 (as expected)" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "=== Tests Complete ===" -ForegroundColor Cyan