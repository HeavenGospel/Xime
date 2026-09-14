# Pack Mint Pinyin into TWO zips:
#   1) rime-mint.zip          — 键盘方案（schema/dicts/lua/opencc/xime.custom），不含个人词库
#   2) rime-mint-userdb.zip   — 个人词库（*.userdb.txt）
# Usage:
#   powershell -File tools/pack-rime-mint.ps1
# Output:
#   build/scheme-release/rime-mint.zip
#   build/scheme-release/rime-mint-userdb.zip

$ErrorActionPreference = "Stop"
# tools/ -> Xime/ -> 020-rime/
$xime = Split-Path $PSScriptRoot -Parent
$root = Split-Path $xime -Parent
$src = Join-Path $root "org.fcitx.fcitx5.android.fx\files\data\rime"
$outDir = Join-Path $xime "build\scheme-release"
$staging = Join-Path $outDir "rime-mint-staging"
$userStaging = Join-Path $outDir "rime-mint-userdb-staging"
$outZip = Join-Path $outDir "rime-mint.zip"
$outUserZip = Join-Path $outDir "rime-mint-userdb.zip"

if (-not (Test-Path $src)) {
  throw "Mint source not found: $src"
}

Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $userStaging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $staging | Out-Null
New-Item -ItemType Directory -Force -Path $userStaging | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$files = @(
  "rime_mint.schema.yaml", "rime_mint.dict.yaml",
  "melt_eng.schema.yaml", "melt_eng.dict.yaml",
  "stroke.schema.yaml", "stroke.dict.yaml",
  "radical_pinyin.schema.yaml", "radical_pinyin.dict.yaml",
  "wubi98_mint.schema.yaml", "wubi98_mint.dict.yaml",
  "symbols.yaml", "rime.lua"
)
foreach ($f in $files) {
  $p = Join-Path $src $f
  if (-not (Test-Path $p)) { throw "Missing $f" }
  Copy-Item $p $staging -Force
}

Copy-Item (Join-Path $src "dicts") (Join-Path $staging "dicts") -Recurse -Force
Copy-Item (Join-Path $src "lua") (Join-Path $staging "lua") -Recurse -Force
Copy-Item (Join-Path $src "opencc") (Join-Path $staging "opencc") -Recurse -Force

# Fcitx 键盘上滑/长按 → xime.custom.yaml（跟方案走，不是个人词库）
$gen = Join-Path $PSScriptRoot "gen-xime-custom-from-fcitx.py"
if (Get-Command python -ErrorAction SilentlyContinue) {
  & python $gen
  $customSrc = Join-Path $xime "app\src\main\assets\xime.custom.yaml"
  if (Test-Path $customSrc) {
    Copy-Item $customSrc (Join-Path $staging "xime.custom.yaml") -Force
  }
}

$ocConfig = Join-Path $xime "app\src\main\jni\librime\deps\opencc\data\config"
if (Test-Path $ocConfig) {
  foreach ($cfg in @("s2t.json", "t2s.json", "s2tw.json", "s2hk.json", "s2twp.json", "tw2s.json", "hk2s.json")) {
    $c = Join-Path $ocConfig $cfg
    if (Test-Path $c) { Copy-Item $c (Join-Path $staging "opencc") -Force }
  }
}

$schemaPath = Join-Path $staging "rime_mint.schema.yaml"
$schemaText = [IO.File]::ReadAllText($schemaPath, [Text.UTF8Encoding]::new($false))
$schemaText = $schemaText -replace '(?m)^(\s*name:\s*)\.\s*$', '${1}薄荷拼音'
[IO.File]::WriteAllText($schemaPath, $schemaText, [Text.UTF8Encoding]::new($false))

# 个人词库单独打包
$syncSrc = Get-ChildItem (Join-Path $src "sync") -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
$userCount = 0
if ($syncSrc) {
  foreach ($u in @("rime_mint.userdb.txt", "melt_eng.userdb.txt")) {
    $up = Join-Path $syncSrc.FullName $u
    if (Test-Path $up) {
      Copy-Item $up $userStaging -Force
      $userCount++
    }
  }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
Remove-Item $outZip -Force -ErrorAction SilentlyContinue
[System.IO.Compression.ZipFile]::CreateFromDirectory(
  $staging,
  $outZip,
  [System.IO.Compression.CompressionLevel]::Optimal,
  $false
)

Remove-Item $outUserZip -Force -ErrorAction SilentlyContinue
if ($userCount -gt 0) {
  [System.IO.Compression.ZipFile]::CreateFromDirectory(
    $userStaging,
    $outUserZip,
    [System.IO.Compression.CompressionLevel]::Optimal,
    $false
  )
}

$zi = Get-Item $outZip
Write-Host "OK scheme  $($zi.FullName) ($([math]::Round($zi.Length/1MB, 2)) MB)"
if (Test-Path $outUserZip) {
  $uz = Get-Item $outUserZip
  Write-Host "OK userdb  $($uz.FullName) ($([math]::Round($uz.Length/1MB, 2)) MB)"
} else {
  Write-Host "WARN no userdb files found, skipped rime-mint-userdb.zip"
}
