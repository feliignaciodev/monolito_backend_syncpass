<#
.SYNOPSIS
  Guarda los 6 secretos de SyncPass en AWS Systems Manager Parameter Store (/syncpass/prod/*)
  como SecureString. La contrasena de la base se pide de forma oculta.

.DESCRIPTION
  Requiere AWS CLI v2 (en v1, un valor que empieza por https:// se descargaria en vez de guardarse).
  Se puede ejecutar de nuevo para cambiar valores (--overwrite).

.EXAMPLE
  .\deploy\parameter-store.ps1 `
     -DbUrl 'jdbc:postgresql://aws-0-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require' `
     -DbUser 'postgres.abcdefghijklmnop' `
     -CognitoUserPoolId 'us-east-1_AbCdEf123' -CognitoClientId '1a2b3c4d5e6f7g8h9i0j'
#>
param(
    [string] $Region = "us-east-1",
    [Parameter(Mandatory = $true)] [string] $DbUrl,
    [Parameter(Mandatory = $true)] [string] $DbUser,
    [Parameter(Mandatory = $true)] [string] $CognitoUserPoolId,
    [Parameter(Mandatory = $true)] [string] $CognitoClientId
)

$ErrorActionPreference = "Continue"

if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
    throw "AWS CLI no esta instalado. Instale con: winget install Amazon.AWSCLI"
}
$version = (& aws --version) -join " "
if ($version -notmatch "aws-cli/2\.") {
    throw "Se requiere AWS CLI v2 (detectado: $version)"
}

$segura = Read-Host -AsSecureString "Contrasena de la base de datos de Supabase"
$DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura))
if (-not $DbPassword) { throw "La contrasena no puede quedar vacia" }

$parametros = [ordered]@{
    DB_URL               = $DbUrl
    DB_USER              = $DbUser
    DB_PASSWORD          = $DbPassword
    COGNITO_ISSUER       = "https://cognito-idp.$Region.amazonaws.com/$CognitoUserPoolId"
    COGNITO_CLIENT_ID    = $CognitoClientId
    COGNITO_USER_POOL_ID = $CognitoUserPoolId
}

foreach ($nombre in $parametros.Keys) {
    & aws ssm put-parameter --region $Region --name "/syncpass/prod/$nombre" `
        --value $parametros[$nombre] --type SecureString --overwrite | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "No se pudo guardar /syncpass/prod/$nombre" }
    Write-Host "    /syncpass/prod/$nombre guardado"
}
$DbPassword = $null

Write-Host "==> Listo. Comprobar con:"
Write-Host "    aws ssm get-parameters-by-path --path /syncpass/prod --region $Region --query 'Parameters[].Name'"
