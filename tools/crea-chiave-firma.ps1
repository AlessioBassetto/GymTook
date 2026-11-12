<#
Crea la chiave di firma dell'app e il file keystore.properties. Da lanciare UNA sola volta:

    powershell -ExecutionPolicy Bypass -File tools\crea-chiave-firma.ps1

La chiave finisce fuori dal progetto (cartella utente). Tutte le versioni dell'app vanno firmate
con questa chiave: senza il file .jks e la sua password non si possono piu' pubblicare
aggiornamenti installabili sopra la versione precedente. Vedi docs/rilascio.md.

Testo volutamente senza lettere accentate: Windows PowerShell 5.1 legge gli script come ANSI.
#>
param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE "sport-chiave-firma.jks"),
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$alias = "upload"
$propertiesPath = Join-Path $ProjectRoot "keystore.properties"

if (Test-Path $propertiesPath) {
    Write-Host "Esiste gia' ${propertiesPath}: la chiave e' gia' configurata, non tocco nulla."
    exit 1
}
if (Test-Path $KeystorePath) {
    Write-Host "Esiste gia' ${KeystorePath}: non lo sovrascrivo. Indica un altro percorso con -KeystorePath."
    exit 1
}

$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe") }
$candidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
$keytool = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $keytool) {
    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($command) { $keytool = $command.Source }
}
if (-not $keytool) {
    Write-Host "Non trovo keytool: installa Android Studio oppure imposta JAVA_HOME."
    exit 1
}

function Read-Secret([string]$prompt) {
    # Con l'input rediretto (script, prove automatiche) la password arriva da una riga dello standard input.
    if ([Console]::IsInputRedirected) { return [Console]::In.ReadLine() }
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

Write-Host "Scegli la password della chiave di firma (almeno 6 caratteri; lettere non accentate,"
Write-Host "numeri e simboli, senza spazi). Conservala: non si puo' recuperare."
$password = Read-Secret "Password"
if ($password.Length -lt 6 -or $password -notmatch '^[\x21-\x7E]+$') {
    Write-Host "Password non valida: servono almeno 6 caratteri tra lettere non accentate, numeri e simboli."
    exit 1
}
if ((Read-Secret "Ripeti la password") -cne $password) {
    Write-Host "Le due password non coincidono."
    exit 1
}

$env:SPORT_KEYSTORE_PASSWORD = $password
try {
    & $keytool -genkeypair -keystore $KeystorePath -storetype PKCS12 -alias $alias `
        -keyalg RSA -keysize 4096 -validity 9125 -dname "CN=Sport" `
        -storepass:env SPORT_KEYSTORE_PASSWORD -keypass:env SPORT_KEYSTORE_PASSWORD
    if ($LASTEXITCODE -ne 0) {
        Write-Host "keytool non e' riuscito a creare la chiave."
        exit 1
    }
}
finally {
    Remove-Item Env:SPORT_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
}

# Formato .properties: barre in avanti nel percorso, barra rovesciata raddoppiata nella password.
$escapedPassword = $password.Replace("\", "\\")
$lines = @(
    "# Firma di rilascio: creato da tools/crea-chiave-firma.ps1. Mai nel controllo di versione.",
    "storeFile=$((Resolve-Path $KeystorePath).Path.Replace('\', '/'))",
    "storePassword=$escapedPassword",
    "keyAlias=$alias",
    "keyPassword=$escapedPassword"
)
[IO.File]::WriteAllLines($propertiesPath, $lines, [Text.Encoding]::ASCII)

Write-Host ""
Write-Host "Fatto."
Write-Host "  Chiave:         $KeystorePath"
Write-Host "  Configurazione: $propertiesPath"
Write-Host "Fai subito una copia di sicurezza del file .jks e della password, fuori da questo PC."
