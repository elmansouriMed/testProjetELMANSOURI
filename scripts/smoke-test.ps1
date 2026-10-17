param([string]$BaseUrl = 'http://localhost:8080', [string]$UiUrl = 'http://localhost:4200')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
function Assert-True($Value, $Message) { if (-not $Value) { throw $Message } }
function Count-Messages($Status) {
    $sql = if ($Status) { "SELECT count(*) FROM payment_message WHERE status='$Status'" } else { 'SELECT count(*) FROM payment_message' }
    $value = docker compose exec -T postgres psql -U payments -d payments -Atc $sql
    if ($LASTEXITCODE -ne 0) { throw 'Impossible de lire PostgreSQL' }
    return [long]($value | Select-Object -Last 1)
}
function Publish-Messages([int]$Count, [switch]$Invalid) {
    $publisherArgs = @('compose','exec','-T','backend','java','-jar','/app/app.jar','--spring.main.web-application-type=none','--app.mq.enabled=false','--app.publisher.enabled=true',"--app.publisher.count=$Count")
    if ($Invalid) { $publisherArgs += '--app.publisher.invalid=true' }
    & docker @publisherArgs | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Échec du producteur MQ' }
}
function Wait-Count([long]$Expected, [string]$Status) {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        if ((Count-Messages $Status) -ge $Expected) { return }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Délai dépassé pendant l'archivage MQ -> PostgreSQL ($Status)"
}
$before = Count-Messages ''
Publish-Messages 10
Wait-Count ($before + 10) ''
$page = Invoke-RestMethod "$BaseUrl/api/messages?limit=5"
Assert-True ($page.items.Count -eq 5) 'Pagination invalide'
Assert-True $page.hasMore 'Le curseur suivant manque'
$detail = Invoke-RestMethod "$BaseUrl/api/messages/$($page.items[0].id)"
Assert-True ($detail.textPayload -like '*pacs.008*') 'Le corps publié dans MQ est absent du détail'
$invalid = Invoke-WebRequest "$BaseUrl/api/messages?limit=101" -SkipHttpErrorCheck
Assert-True ($invalid.StatusCode -eq 400) 'La borne de pagination devrait renvoyer 400'
$missing = Invoke-WebRequest "$BaseUrl/api/messages/9223372036854775807" -SkipHttpErrorCheck
Assert-True ($missing.StatusCode -eq 404) 'Un identifiant absent devrait renvoyer 404'
$quarantinedBefore = Count-Messages 'QUARANTINED'
Publish-Messages 1 -Invalid
Wait-Count ($quarantinedBefore + 1) 'QUARANTINED'
$queue = 'DISPLAY QLOCAL(DEV.PAYMENTS.QUARANTINE) CURDEPTH' | docker compose exec -T mq runmqsc QM1
Assert-True (($queue -join "`n") -match 'CURDEPTH\([1-9][0-9]*\)') 'La file de quarantaine est vide'
$ui = Invoke-WebRequest $UiUrl
Assert-True ($ui.StatusCode -eq 200 -and $ui.Content -like '*app-root*') 'Interface indisponible'
Write-Host 'OK : MQ -> PostgreSQL -> REST, validation HTTP, quarantaine MQ et interface.'
