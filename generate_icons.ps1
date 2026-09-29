Add-Type -AssemblyName System.Drawing

$srcPath = "d:\Desktop\PDF\android\app\src\main\res\drawable\app_logo.png"
$srcBytes = [System.IO.File]::ReadAllBytes($srcPath)
$ms = New-Object System.IO.MemoryStream(,$srcBytes)
$src = [System.Drawing.Bitmap]::FromStream($ms)

$densities = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($entry in $densities.GetEnumerator()) {
    $folder = $entry.Key
    $sz = $entry.Value
    $destDir = "d:\Desktop\PDF\android\app\src\main\res\$folder"

    # Remove old webp if present
    if (Test-Path "$destDir\ic_launcher.webp") { Remove-Item "$destDir\ic_launcher.webp" -Force }
    if (Test-Path "$destDir\ic_launcher_round.webp") { Remove-Item "$destDir\ic_launcher_round.webp" -Force }

    # 1. Square icon
    $bmp = New-Object System.Drawing.Bitmap($sz, $sz)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 152, 180, 212))
    $g.FillRectangle($bgBrush, 0, 0, $sz, $sz)

    $scale = [Math]::Min($sz / $src.Width, $sz / $src.Height)
    $w = [int]($src.Width * $scale)
    $h = [int]($src.Height * $scale)
    $x = [int](($sz - $w) / 2)
    $y = [int](($sz - $h) / 2)
    $g.DrawImage($src, $x, $y, $w, $h)

    $bmp.Save("$destDir\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()

    # 2. Round icon
    $bmpR = New-Object System.Drawing.Bitmap($sz, $sz)
    $gR = [System.Drawing.Graphics]::FromImage($bmpR)
    $gR.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gR.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $gR.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(1, 1, $sz - 2, $sz - 2)
    $gR.SetClip($path)
    $gR.FillRectangle($bgBrush, 0, 0, $sz, $sz)
    $gR.DrawImage($src, $x, $y, $w, $h)
    $gR.ResetClip()

    # Red ring around circular icon
    $borderW = [Math]::Max(2, [int]($sz * 0.04))
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 227, 38, 54), $borderW)
    $gR.DrawEllipse($pen, 1, 1, $sz - 2, $sz - 2)

    $bmpR.Save("$destDir\ic_launcher_round.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $gR.Dispose()
    $bmpR.Dispose()
}

$src.Dispose()
$ms.Dispose()
Write-Host "Icons generated successfully!"
