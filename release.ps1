# release.ps1 — Atualiza o main, regera o .exe + instalador E REINSTALA o app.
#
# Por padrao tambem REINSTALA a versao nova em C:\Program Files\SGPUR (silencioso),
# para que o atalho do Desktop/Menu Iniciar use SEMPRE o ultimo build. Sem isso,
# voce continua abrindo a versao velha instalada (bug do "CSS antigo").
#
# Uso:
#   .\release.ps1            -> git pull + rebuild + installer + REINSTALA
#   .\release.ps1 -SemPull   -> pula o git pull (so rebuild + installer + reinstala)
#   .\release.ps1 -SomenteInstaller -> so roda o Inno Setup (exe ja pronto) + reinstala
#   .\release.ps1 -NaoInstalar      -> gera os artefatos mas NAO reinstala

param(
    [switch]$SemPull,
    [switch]$SomenteInstaller,
    [switch]$NaoInstalar
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Titulo($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Ok($msg)     { Write-Host "    $msg" -ForegroundColor Green }
function Erro($msg)   { Write-Host "ERRO: $msg" -ForegroundColor Red; exit 1 }

# Encerra qualquer SGPUR rodando (instalado ou da pasta dist) para liberar os
# arquivos antes do build/instalacao. Sem isso, o jpackage/instalador falha por
# arquivo em uso e voce acaba abrindo a versao velha de novo.
function Encerrar-SGPUR {
    $procs = Get-Process -Name SGPUR -ErrorAction SilentlyContinue
    if ($procs) {
        Write-Host "    Encerrando $($procs.Count) instancia(s) do SGPUR em execucao..." -ForegroundColor Yellow
        $procs | Stop-Process -Force -Confirm:$false
        Start-Sleep -Seconds 2
    }
}

# --- 1. Git pull no main --------------------------------------------------
if (-not $SemPull -and -not $SomenteInstaller) {
    Titulo "Verificando branch..."
    $branch = git -C $root rev-parse --abbrev-ref HEAD
    if ($branch -ne "main") {
        Write-Host "    Branch atual: $branch (nao e main). Mudando para main..." -ForegroundColor Yellow
        git -C $root checkout main
    }

    $antes = git -C $root rev-parse HEAD
    Titulo "git pull origin main..."
    git -C $root pull origin main
    $depois = git -C $root rev-parse HEAD

    if ($antes -eq $depois) {
        Write-Host "    Nenhuma mudanca no main. Rebuild mesmo assim? (S/N)" -NoNewline
        $r = Read-Host " "
        if ($r -notmatch '^[Ss]') { Write-Host "Cancelado."; exit 0 }
    } else {
        Ok "Atualizado: $($antes.Substring(0,7)) -> $($depois.Substring(0,7))"
    }
}

# --- 2. Build exe (JAR + jpackage) ----------------------------------------
if (-not $SomenteInstaller) {
    Titulo "Encerrando instancias do SGPUR em execucao..."
    Encerrar-SGPUR

    Titulo "Gerando SGPUR.exe (package-desktop.ps1)..."
    & "$root\package-desktop.ps1"
    if ($LASTEXITCODE -ne 0) { Erro "package-desktop.ps1 falhou (exit $LASTEXITCODE)." }
    Ok "Executavel: dist\desktop\SGPUR\SGPUR.exe"
}

# --- 3. Installer (Inno Setup) ---------------------------------------------
$iscc = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) { Erro "Inno Setup nao encontrado em '$iscc'. Instale em https://jrsoftware.org/isinfo.php" }

$iss = "$root\installer\sgpur-setup.iss"
if (-not (Test-Path $iss)) { Erro "Arquivo .iss nao encontrado: $iss" }

Titulo "Gerando SGPUR-Setup.exe (Inno Setup)..."
& $iscc $iss
if ($LASTEXITCODE -ne 0) { Erro "Inno Setup falhou (exit $LASTEXITCODE)." }

$setup = "$root\dist\SGPUR-Setup.exe"
if (Test-Path $setup) {
    $tamanho = "{0:N1} MB" -f ((Get-Item $setup).Length / 1MB)
    Ok "Instalador: dist\SGPUR-Setup.exe ($tamanho)"
} else {
    Erro "SGPUR-Setup.exe nao foi gerado."
}

# --- 4. REINSTALA a versao nova em Program Files (silencioso) --------------
# Esta e a etapa que evita o bug do "CSS antigo": garante que o app instalado
# (o que abre pelo atalho) seja sempre o ultimo build.
if (-not $NaoInstalar) {
    Titulo "Reinstalando o SGPUR (atualiza C:\Program Files\SGPUR)..."
    Encerrar-SGPUR
    # /VERYSILENT: sem telas | /SUPPRESSMSGBOXES: sem caixas | /NORESTART
    # O instalador pede UAC (admin) — confirme o prompt do Windows quando aparecer.
    $p = Start-Process -FilePath $setup `
        -ArgumentList "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/NOCANCEL" `
        -Verb RunAs -Wait -PassThru
    if ($p.ExitCode -ne 0) {
        Erro "Instalacao falhou (exit $($p.ExitCode)). Rode o instalador manualmente: $setup"
    }
    Ok "Instalado/atualizado em C:\Program Files\SGPUR\SGPUR.exe"
}

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "  Release pronto!" -ForegroundColor Green
Write-Host "  Executavel : dist\desktop\SGPUR\SGPUR.exe"
Write-Host "  Instalador : dist\SGPUR-Setup.exe"
Write-Host "  ZIP        : dist\SGPUR-desktop.zip"
if (-not $NaoInstalar) {
    Write-Host "  Instalado  : C:\Program Files\SGPUR\SGPUR.exe (ATUALIZADO)"
}
Write-Host "========================================`n" -ForegroundColor Green
