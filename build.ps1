# 构建 quick-monitor（不依赖 Loom）
#
# 用法：
#   .\build.ps1 -ServerDir 'D:\path\to\fabric-server' -Jdk 'D:\path\to\jdk-25'
#
# ServerDir 只需要是一个能跑的 Fabric 服务端目录：
#   versions\**\server-*.jar     提供 Minecraft 类
#   libraries\**\*.jar           提供 loader / 依赖
#   mods\fabric-api-*.jar        提供 Fabric API 的类（会解出内部模块）

param(
    [Parameter(Mandatory = $true)][string]$ServerDir,
    [string]$Jdk = 'D:\workspace-dsh\mcwork\toolchain\jdk-25',
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$root     = $PSScriptRoot
$build    = Join-Path $root 'build'
$classes  = Join-Path $build 'classes'
$apiCache = Join-Path $build 'fabric-api-modules'

if ($Clean) {
    Remove-Item $classes, $apiCache -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $classes, $apiCache | Out-Null

$javac = Join-Path $Jdk 'bin\javac.exe'
$jarTool = Join-Path $Jdk 'bin\jar.exe'
if (-not (Test-Path $javac)) { throw "找不到 javac: $javac" }
if (-not (Test-Path $ServerDir)) { throw "找不到服务端目录: $ServerDir" }

$serverJar = Get-ChildItem (Join-Path $ServerDir 'versions') -Recurse -Filter 'server-*.jar' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $serverJar) { throw "在 $ServerDir\versions 下找不到 server-*.jar" }

$jars = @($serverJar.FullName)
$jars += Get-ChildItem (Join-Path $ServerDir 'libraries') -Recurse -Filter *.jar | Select-Object -ExpandProperty FullName

$apiJar = Get-ChildItem (Join-Path $ServerDir 'mods') -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue |
    Select-Object -Last 1
if ($apiJar) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apiJar.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like 'META-INF/jars/*.jar') {
                $dest = Join-Path $apiCache (Split-Path $entry.FullName -Leaf)
                if (-not (Test-Path $dest)) {
                    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $false)
                }
            }
        }
    } finally {
        $zip.Dispose()
    }
    $jars += Get-ChildItem $apiCache -Filter *.jar | Select-Object -ExpandProperty FullName
} else {
    Write-Warning "mods 下没有 fabric-api-*.jar，涉及 Fabric API 的类可能编译失败"
}

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName
if (-not $sources) { throw '没有找到 Java 源文件' }

# javac 的 @argfile：路径一律用正斜杠，文件必须无 BOM，否则第一个参数会被读成乱码
$cp = ($jars -join ';').Replace('\', '/')
$argsFile = Join-Path $build 'javac.args'
$lines = @('-encoding', 'UTF-8', '--release', '25', '-cp', $cp, '-d', $classes.Replace('\', '/'))
$lines += $sources | ForEach-Object { $_.Replace('\', '/') }
[System.IO.File]::WriteAllLines($argsFile, $lines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "编译 $($sources.Count) 个源文件，classpath $($jars.Count) 个 jar ..."
& $javac "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw "javac 失败: $LASTEXITCODE" }

Copy-Item -Path (Join-Path $root 'src\main\resources\*') -Destination $classes -Recurse -Force
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
Set-Content -LiteralPath (Join-Path $classes 'quick-monitor-build.properties') -Value "built=$stamp" -Encoding utf8

$meta = Get-Content (Join-Path $root 'src\main\resources\fabric.mod.json') -Raw | ConvertFrom-Json
$outJar = Join-Path $build ("quick-monitor-{0}.jar" -f $meta.version)
Remove-Item $outJar -ErrorAction SilentlyContinue
& $jarTool --create --file $outJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar 失败: $LASTEXITCODE" }

Write-Host "built: $outJar"
Write-Host "size:  $((Get-Item $outJar).Length) bytes"
Write-Host "stamp: $stamp"
