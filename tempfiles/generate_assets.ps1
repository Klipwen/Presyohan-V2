Add-Type -AssemblyName System.Drawing

$resDir = "Presyohan Mobile\app\src\main\res\drawable"
$outDir = "tempfiles\PlayStore_Assets"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$iconSrc = Join-Path $resDir "icon_presyohan_launcher.png"
if (-not (Test-Path $iconSrc)) {
    $iconSrc = Join-Path $resDir "logo_presyohan.png"
}

Write-Host "Source image: $iconSrc"
$srcImg = [System.Drawing.Image]::FromFile((Resolve-Path $iconSrc))
Write-Host "Source image size: $($srcImg.Width) x $($srcImg.Height)"

# 1. Generate 512x512 App Icon
$icon512 = New-Object System.Drawing.Bitmap(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gIcon = [System.Drawing.Graphics]::FromImage($icon512)
$gIcon.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gIcon.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$gIcon.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$gIcon.Clear([System.Drawing.Color]::Transparent)

# Draw scaled centered
$gIcon.DrawImage($srcImg, 0, 0, 512, 512)
$iconOutPath = Join-Path $outDir "playstore_icon_512x512.png"
$icon512.Save((Resolve-Path $outDir).Path + "\playstore_icon_512x512.png", [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Generated: $iconOutPath (512x512)"
$gIcon.Dispose()
$icon512.Dispose()

# 2. Generate 1024x500 Feature Graphic
$feature = New-Object System.Drawing.Bitmap(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gFeat = [System.Drawing.Graphics]::FromImage($feature)
$gFeat.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gFeat.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$gFeat.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

# Background gradient: Presyohan Market Cyan (#00BCD4) to Warm Tangerine (#FF8C00)
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point(0, 0)),
    (New-Object System.Drawing.Point(1024, 500)),
    [System.Drawing.Color]::FromArgb(255, 0, 150, 180),
    [System.Drawing.Color]::FromArgb(255, 230, 110, 0)
)
$gFeat.FillRectangle($brush, 0, 0, 1024, 500)
$brush.Dispose()

# Draw centered icon/logo (approx 360x360 centered vertically, left-ish)
$logoSize = 340
$logoX = 80
$logoY = (500 - $logoSize) / 2
$gFeat.DrawImage($srcImg, $logoX, $logoY, $logoSize, $logoSize)

# Draw Title text & Subtitle text
$fontTitle = New-Object System.Drawing.Font("Arial", 46, [System.Drawing.FontStyle]::Bold)
$fontSub = New-Object System.Drawing.Font("Arial", 22, [System.Drawing.FontStyle]::Regular)
$brushWhite = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$brushYellow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 220, 100))

$gFeat.DrawString("Presyohan", $fontTitle, $brushWhite, 460, 140)
$gFeat.DrawString("Smart Store Assistant & Price Sync", $fontSub, $brushYellow, 465, 220)
$gFeat.DrawString("Role-Based Staff * AI Scanner * Suki Hub", (New-Object System.Drawing.Font("Arial", 16)), $brushWhite, 465, 270)

$featOutPath = (Resolve-Path $outDir).Path + "\playstore_feature_graphic_1024x500.png"
$feature.Save($featOutPath, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Generated: $featOutPath (1024x500)"

$fontTitle.Dispose()
$fontSub.Dispose()
$brushWhite.Dispose()
$brushYellow.Dispose()
$gFeat.Dispose()
$feature.Dispose()
$srcImg.Dispose()
