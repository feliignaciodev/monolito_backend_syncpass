<#
.SYNOPSIS
  Crea (o completa) el User Pool de Cognito de SyncPass: App Client publico, los 4 grupos
  y los usuarios demo FICTICIOS "coordinador", "voluntario" y "admin".

.DESCRIPTION
  Requiere AWS CLI v2 configurado (aws configure). Es idempotente: si algo ya existe, lo deja igual.
  - Sin -UserPoolId: crea el User Pool "syncpass-dev" y el App Client "syncpass-app".
  - Con -UserPoolId: usa ese pool y crea el App Client si falta.
  Politica de contrasenas: minimo 8 caracteres sin requisitos extra, para aceptar las cuentas demo
  del contrato de la app (coord123 / volun123). Solo para el pool de desarrollo academico.
  Tokens: el access token dura 12 horas (igual que las sesiones del backend de referencia), para que
  un voluntario en terreno no tenga que reingresar cada hora.

.EXAMPLE
  .\deploy\cognito-setup.ps1 -PasswordAdmin 'UnaClaveSegura2026'
#>
param(
    [string] $Region = "us-east-1",
    [string] $UserPoolId,
    [string] $PoolName = "syncpass-dev",
    [string] $PasswordCoordinador = "coord123",
    [string] $PasswordVoluntario = "volun123",
    [Parameter(Mandatory = $true)] [string] $PasswordAdmin
)

$ErrorActionPreference = "Continue"

function Invoke-Aws {
    $salida = & aws @args
    # Solo se muestra la operacion: los argumentos pueden incluir contrasenas
    if ($LASTEXITCODE -ne 0) { throw "Fallo el comando: aws $($args[0]) $($args[1])" }
    return $salida
}

if (-not (Get-Command aws -ErrorAction SilentlyContinue)) {
    throw "AWS CLI no esta instalado. Instale con: winget install Amazon.AWSCLI"
}

# 1) User Pool
if (-not $UserPoolId) {
    $existente = Invoke-Aws cognito-idp list-user-pools --region $Region --max-results 60 `
        --query "UserPools[?Name=='$PoolName'].Id | [0]" --output text
    if ($existente -and $existente -ne "None") {
        $UserPoolId = $existente
        Write-Host "==> User Pool existente: $UserPoolId"
    } else {
        $UserPoolId = Invoke-Aws cognito-idp create-user-pool --region $Region --pool-name $PoolName `
            --alias-attributes email `
            --username-configuration CaseSensitive=false `
            --policies "PasswordPolicy={MinimumLength=8,RequireUppercase=false,RequireLowercase=false,RequireNumbers=false,RequireSymbols=false}" `
            --admin-create-user-config AllowAdminCreateUserOnly=true `
            --query UserPool.Id --output text
        Write-Host "==> User Pool creado: $UserPoolId"
    }
}

# 2) App Client publico (sin secreto) con login por usuario/contrasena
$ClientId = Invoke-Aws cognito-idp list-user-pool-clients --region $Region --user-pool-id $UserPoolId `
    --query "UserPoolClients[?ClientName=='syncpass-app'].ClientId | [0]" --output text
if (-not $ClientId -or $ClientId -eq "None") {
    $ClientId = Invoke-Aws cognito-idp create-user-pool-client --region $Region --user-pool-id $UserPoolId `
        --client-name syncpass-app --no-generate-secret `
        --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH `
        --access-token-validity 12 --id-token-validity 12 --refresh-token-validity 30 `
        --token-validity-units AccessToken=hours,IdToken=hours,RefreshToken=days `
        --prevent-user-existence-errors ENABLED `
        --query UserPoolClient.ClientId --output text
    Write-Host "==> App Client creado: $ClientId"
} else {
    Write-Host "==> App Client existente: $ClientId"
}

# 3) Grupos = roles (precedencia menor = mayor privilegio)
$grupos = [ordered]@{ ADMINISTRADOR = 1; COORDINADOR = 2; VOLUNTARIO = 3; BENEFICIARIO = 4 }
$existentes = @(Invoke-Aws cognito-idp list-groups --region $Region --user-pool-id $UserPoolId `
    --query "Groups[].GroupName" --output text) -split "\s+"
foreach ($g in $grupos.Keys) {
    if ($existentes -contains $g) {
        Write-Host "    grupo $g ya existe"
    } else {
        Invoke-Aws cognito-idp create-group --region $Region --user-pool-id $UserPoolId `
            --group-name $g --precedence $grupos[$g] | Out-Null
        Write-Host "    grupo $g creado"
    }
}

# 4) Usuarios demo FICTICIOS
$usuarios = @(
    @{ Username = "coordinador"; Nombre = "Coordinadora Demo"; Grupo = "COORDINADOR"; Password = $PasswordCoordinador },
    @{ Username = "voluntario"; Nombre = "Voluntario Demo"; Grupo = "VOLUNTARIO"; Password = $PasswordVoluntario },
    @{ Username = "admin"; Nombre = "Administrador Demo"; Grupo = "ADMINISTRADOR"; Password = $PasswordAdmin }
)
$usuariosExistentes = @(Invoke-Aws cognito-idp list-users --region $Region --user-pool-id $UserPoolId `
    --query "Users[].Username" --output text) -split "\s+"
foreach ($u in $usuarios) {
    if ($usuariosExistentes -contains $u.Username) {
        Write-Host "    usuario $($u.Username) ya existe (no se cambia su contrasena)"
        continue
    }
    $email = "$($u.Username)@syncpass.test"
    Invoke-Aws cognito-idp admin-create-user --region $Region --user-pool-id $UserPoolId `
        --username $u.Username --message-action SUPPRESS `
        --user-attributes "Name=name,Value=$($u.Nombre)" "Name=email,Value=$email" "Name=email_verified,Value=true" | Out-Null
    Invoke-Aws cognito-idp admin-set-user-password --region $Region --user-pool-id $UserPoolId `
        --username $u.Username --password $u.Password --permanent | Out-Null
    Invoke-Aws cognito-idp admin-add-user-to-group --region $Region --user-pool-id $UserPoolId `
        --username $u.Username --group-name $u.Grupo | Out-Null
    Write-Host "    usuario $($u.Username) creado en el grupo $($u.Grupo)"
}

$issuer = "https://cognito-idp.$Region.amazonaws.com/$UserPoolId"
Write-Host ""
Write-Host "==> Listo. Valores para IntelliJ y para deploy\parameter-store.ps1:"
Write-Host "    AWS_REGION=$Region"
Write-Host "    COGNITO_USER_POOL_ID=$UserPoolId"
Write-Host "    COGNITO_CLIENT_ID=$ClientId"
Write-Host "    COGNITO_ISSUER=$issuer"
