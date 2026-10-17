# À utiliser uniquement avec la pile Compose de développement de ce dépôt.
param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
function Read-Count {
    $result = docker compose exec -T postgres psql -U payments -d payments -Atc 'SELECT count(*) FROM payment_message'
    if ($LASTEXITCODE -ne 0) { throw 'Lecture du nombre de messages impossible' }
    return [long]($result | Select-Object -Last 1)
}
$before = Read-Count
$stopped = $false
try {
    docker compose stop postgres | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Impossible d’arrêter PostgreSQL" }
    $stopped = $true
    docker compose exec -T backend java -jar /app/app.jar --spring.main.web-application-type=none --app.mq.enabled=false --app.publisher.enabled=true --app.publisher.count=12 --spring.flyway.enabled=false --spring.datasource.hikari.minimum-idle=0 --spring.datasource.hikari.initialization-fail-timeout=-1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Publication MQ impossible pendant la panne SQL' }
    $response = Invoke-WebRequest "$BaseUrl/api/messages" -SkipHttpErrorCheck -TimeoutSec 25
    if ($response.StatusCode -ne 503) { throw "API : attendu 503, reçu $($response.StatusCode)" }
    $depth = 'DISPLAY QLOCAL(DEV.PAYMENTS.IN) CURDEPTH' | docker compose exec -T mq runmqsc QM1
    if (($depth -join "`n") -notmatch 'CURDEPTH\([1-9][0-9]*\)') { throw 'Aucun message en attente dans MQ pendant la panne SQL' }
    Write-Host 'Panne SQL : API 503 et messages conservés dans MQ.'
} finally {
    if ($stopped) {
        docker compose start postgres | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Error 'Relance de PostgreSQL impossible ; exécuter docker compose start postgres.' }
    }
}
$deadline = [DateTime]::UtcNow.AddSeconds(90)
$after = $before
do {
    try { $after = Read-Count } catch { $after = $before }
    if ($after -eq ($before + 12)) { break }
    Start-Sleep -Seconds 2
} while ([DateTime]::UtcNow -lt $deadline)
if ($after -ne ($before + 12)) { throw "Reprise incomplète : attendu $($before + 12), reçu $after" }
$ready = Invoke-RestMethod "$BaseUrl/actuator/health/readiness"
if ($ready.status -ne 'UP') { throw 'API non disponible après reprise' }
Write-Host 'OK : les 12 messages publiés pendant la panne sont archivés après reprise.'
