<#
.SYNOPSIS
  Compila SyncPass API (con pruebas), la sube a EC2 y reinicia el servicio. Guarda la version
  anterior como app-previous.jar para poder volver atras.

.DESCRIPTION
  Primera vez: agregue -Instalar para preparar el servidor (Java, nginx, usuario de servicio,
  swap y systemd) con deploy/server/setup-server.sh.
  Despues: ejecute sin -Instalar para cada version nueva.
  -Rollback vuelve a la version anterior sin compilar.

.EXAMPLE
  .\deploy\deploy.ps1 -Servidor 3.140.10.20 -Llave C:\claves\syncpass-key.pem -Instalar
.EXAMPLE
  .\deploy\deploy.ps1 -Servidor 3.140.10.20 -Llave C:\claves\syncpass-key.pem
.EXAMPLE
  .\deploy\deploy.ps1 -Servidor 3.140.10.20 -Llave C:\claves\syncpass-key.pem -Rollback
#>
param(
    [Parameter(Mandatory = $true)] [string] $Servidor,
    [Parameter(Mandatory = $true)] [string] $Llave,
    [string] $Usuario = "ec2-user",
    [switch] $Instalar,
    [string] $Region = "us-east-1",
    [switch] $SinPruebas,
    [switch] $Rollback
)

$ErrorActionPreference = "Continue"
$raiz = Split-Path -Parent $PSScriptRoot
$destino = "$Usuario@$Servidor"
$opcionesSsh = @("-i", $Llave, "-o", "StrictHostKeyChecking=accept-new")

function Invoke-Remoto([string] $comando) {
    & ssh @opcionesSsh $destino $comando
    if ($LASTEXITCODE -ne 0) { throw "Fallo en el servidor: $comando" }
}

function Wait-Api {
    Write-Host "==> Esperando a que la API responda (hasta 90 s)..."
    Invoke-Remoto 'for i in $(seq 1 45); do if curl -fs http://127.0.0.1:8080/api/health; then echo; exit 0; fi; sleep 2; done; echo La API no respondio. Ultimas lineas del log:; sudo journalctl -u syncpass -n 60 --no-pager; exit 1'
}

if (-not (Test-Path $Llave)) { throw "No existe la llave $Llave" }

if ($Rollback) {
    Write-Host "==> Volviendo a la version anterior"
    Invoke-Remoto 'set -e; test -f /opt/syncpass/app-previous.jar; sudo cp /opt/syncpass/app-previous.jar /opt/syncpass/app.jar; sudo systemctl restart syncpass'
    Wait-Api
    Write-Host "==> Rollback OK"
    exit 0
}

# 1) JDK: usa JAVA_HOME o, si no existe, el JDK de Android Studio o el mas nuevo de ~/.jdks
if (-not $env:JAVA_HOME) {
    $candidatos = @("C:\Program Files\Android\Android Studio\jbr")
    $candidatos += Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object { $_.FullName }
    $env:JAVA_HOME = $candidatos | Where-Object { Test-Path (Join-Path $_ "bin\java.exe") } | Select-Object -First 1
    if (-not $env:JAVA_HOME) { throw "No se encontro un JDK 21 o superior; configure JAVA_HOME" }
}
Write-Host "==> JDK: $env:JAVA_HOME"

# 2) Compilar (y probar)
Push-Location $raiz
try {
    $metas = @("-B", "clean", "package")
    if ($SinPruebas) { $metas += "-DskipTests" }
    & .\mvnw.cmd @metas
    if ($LASTEXITCODE -ne 0) { throw "La compilacion o las pruebas fallaron; no se despliega" }
} finally {
    Pop-Location
}
$jar = Get-ChildItem "$raiz\target\syncpass-api-*.jar" | Where-Object { $_.Name -notlike "*plain*" } | Select-Object -First 1
if (-not $jar) { throw "No se encontro el JAR en target\" }
Write-Host "==> JAR: $($jar.Name)"

# 3) Primera instalacion del servidor
if ($Instalar) {
    Write-Host "==> Preparando el servidor (setup-server.sh)"
    Invoke-Remoto 'rm -rf /tmp/syncpass-setup && mkdir -p /tmp/syncpass-setup'
    $archivos = Get-ChildItem "$PSScriptRoot\server" -File | ForEach-Object { $_.FullName }
    & scp @opcionesSsh @archivos "${destino}:/tmp/syncpass-setup/"
    if ($LASTEXITCODE -ne 0) { throw "No se pudieron subir los scripts del servidor" }
    # Quita CRLF por si Git los convirtio en Windows
    Invoke-Remoto ('sed -i ''s/\r$//'' /tmp/syncpass-setup/* && sudo bash /tmp/syncpass-setup/setup-server.sh ' + $Region)
}

# 4) Subir el JAR y reiniciar, guardando la version anterior
Write-Host "==> Subiendo JAR"
& scp @opcionesSsh $jar.FullName "${destino}:/tmp/app.jar"
if ($LASTEXITCODE -ne 0) { throw "No se pudo subir el JAR" }
Invoke-Remoto 'set -e; if [ -f /opt/syncpass/app.jar ]; then sudo cp /opt/syncpass/app.jar /opt/syncpass/app-previous.jar; fi; sudo mv /tmp/app.jar /opt/syncpass/app.jar; sudo chown syncpass:syncpass /opt/syncpass/app.jar; sudo systemctl restart syncpass'

# 5) Verificar
Wait-Api
Write-Host "==> Despliegue OK."
if ($Instalar) {
    Write-Host "    Siguiente paso: exponer la API con HTTPS por API Gateway:"
    Write-Host "    .\deploy\apigateway-setup.ps1 -Servidor $Servidor"
}
