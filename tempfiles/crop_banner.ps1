Add-Type -AssemblyName System.Drawing

$src = if ($args[0]) { $args[0] } else { "tempfiles\PlayStore_Assets\playstore_feature_graphic_1024x500.png" }
$out = "tempfiles\PlayStore_Assets\feature_graphic_1024x500.png"

if (Test-Path $src) {
    $srcImg = [System.Drawing.Image]::FromFile((Resolve-Path $src))
    $dest = New-Object System.Drawing.Bitmap(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($dest)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $g.DrawImage($srcImg, 0, 0, 1024, 500)
    $dest.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)

    $g.Dispose()
    $dest.Dispose()
    $srcImg.Dispose()

    Write-Host "Cropped & saved exactly to 1024x500 at: $out"
}
