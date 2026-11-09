# Converts every PNG in -Origem (except icone.png) into a JPEG of at most 1280 px wide in -Destino.
# Used by tools/gerar_wiki.js.
param([string]$Origem, [string]$Destino)
Add-Type -AssemblyName System.Drawing
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$par = New-Object System.Drawing.Imaging.EncoderParameters 1
$par.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), ([long]84)
Get-ChildItem -Path $Origem -Filter *.png | Where-Object { $_.Name -ne 'icone.png' } | ForEach-Object {
	$img = [System.Drawing.Image]::FromFile($_.FullName)
	$w = [Math]::Min(1280, $img.Width)
	$h = [int]($img.Height * $w / $img.Width)
	$bmp = New-Object System.Drawing.Bitmap $w, $h
	$g = [System.Drawing.Graphics]::FromImage($bmp)
	$g.InterpolationMode = 'HighQualityBicubic'
	$g.DrawImage($img, 0, 0, $w, $h)
	$bmp.Save((Join-Path $Destino ($_.BaseName + '.jpg')), $codec, $par)
	$g.Dispose(); $bmp.Dispose(); $img.Dispose()
}
