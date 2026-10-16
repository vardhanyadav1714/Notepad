param([Parameter(Mandatory=$true)][string]$Source)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot=Split-Path $PSScriptRoot -Parent
$brand=Join-Path $projectRoot 'branding'
New-Item -ItemType Directory -Force -Path $brand | Out-Null
Copy-Item -LiteralPath $Source -Destination (Join-Path $brand 'drawly-icon-master.png')
$art=[System.Drawing.Image]::FromFile((Join-Path $brand 'drawly-icon-master.png'))
function Export-Icon([int]$Size,[string]$Path) {
    $bitmap=New-Object System.Drawing.Bitmap($Size,$Size)
    $graphics=[System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CompositingQuality=[System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($art,0,0,$Size,$Size)
        $bitmap.Save($Path,[System.Drawing.Imaging.ImageFormat]::Png)
    } finally {$graphics.Dispose();$bitmap.Dispose()}
}
try {
    Export-Icon 1024 (Join-Path $brand 'drawly-icon-1024.png')
    Export-Icon 512 (Join-Path $brand 'drawly-icon-512.png')
    $densities=@{mdpi=48;hdpi=72;xhdpi=96;xxhdpi=144;xxxhdpi=192}
    foreach($density in $densities.Keys) {
        $directory=Join-Path $projectRoot "app/src/main/res/mipmap-$density"
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        Export-Icon $densities[$density] (Join-Path $directory 'drawly_launcher.png')
    }
} finally {$art.Dispose()}
'Exported Drawly master, 1024px and 512px PNGs, and five launcher densities.'
