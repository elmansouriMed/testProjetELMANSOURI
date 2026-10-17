param([switch]$AcceptMqLicense)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) { throw '.env existe déjà. Conservez vos identifiants ou modifiez ce fichier manuellement.' }
function New-LocalPassword {
    $buffer = [byte[]]::new(24)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return [Convert]::ToBase64String($buffer).Replace('+','a').Replace('/','b').TrimEnd('=')
}
$dbPassword = New-LocalPassword
$mqPassword = New-LocalPassword
$secretDirectory = Join-Path $projectRoot '.secrets'
New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $secretDirectory 'mq-app-password.txt'), $mqPassword)
$mqLicense = if ($AcceptMqLicense) { 'accept' } else { '' }
[IO.File]::WriteAllText($envFile, "DB_PASSWORD=$dbPassword`nMQ_PASSWORD=$mqPassword`nMQ_LICENSE=$mqLicense`nMQ_CONSUMERS=4`nDB_POOL_SIZE=16`n")
Write-Host 'Configuration locale créée. Les mots de passe ne sont pas affichés et sont exclus de Git.'
if (-not $AcceptMqLicense) { Write-Host 'Après lecture de la licence IBM, renseignez MQ_LICENSE=accept dans .env.' }
