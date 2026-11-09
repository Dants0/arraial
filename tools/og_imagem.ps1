# Share image (Open Graph / Twitter), 1200x630: the village photo, a dark band, the icon and the title.
# Used by tools/gerar_wiki.js.
param([string]$Foto, [string]$Icone, [string]$Saida, [string]$Linha1, [string]$Linha2)
Add-Type -AssemblyName System.Drawing
$W = 1200; $H = 630
$bmp = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = 'HighQualityBicubic'
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'AntiAliasGridFit'

# Photo, cropped to fill.
$img = [System.Drawing.Image]::FromFile($Foto)
$escala = [Math]::Max($W / $img.Width, $H / $img.Height)
$w = [int]($img.Width * $escala); $h = [int]($img.Height * $escala)
$g.DrawImage($img, [int](($W - $w) / 2), [int](($H - $h) / 2), $w, $h)
$img.Dispose()

# Dark gradient from the bottom.
$rect = New-Object System.Drawing.Rectangle 0, 200, $W, 430
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.Color]::FromArgb(0, 20, 12, 4)), ([System.Drawing.Color]::FromArgb(235, 20, 12, 4)), 90
$g.FillRectangle($grad, $rect)

# Icon, pixel-sharp.
$ic = [System.Drawing.Image]::FromFile($Icone)
$g.InterpolationMode = 'NearestNeighbor'
$g.PixelOffsetMode = 'Half'
$g.DrawImage($ic, 64, 392, 150, 150)
$ic.Dispose()
$g.InterpolationMode = 'HighQualityBicubic'

$branco = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 247, 234))
$ocre = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 243, 217, 168))
$suave = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(230, 230, 215, 195))
$g.DrawString('Arraial', (New-Object System.Drawing.Font 'Segoe UI', 72, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)), $branco, 236, 378)
$g.DrawString($Linha1, (New-Object System.Drawing.Font 'Segoe UI Semibold', 34, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)), $ocre, 242, 470)
$g.DrawString($Linha2, (New-Object System.Drawing.Font 'Segoe UI', 26, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)), $suave, 242, 516)
$g.DrawString('arraial.dantas-dev.com', (New-Object System.Drawing.Font 'Segoe UI', 22, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)), $suave, 940, 590)

$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$par = New-Object System.Drawing.Imaging.EncoderParameters 1
$par.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), ([long]88)
$bmp.Save($Saida, $codec, $par)
$g.Dispose(); $bmp.Dispose()
