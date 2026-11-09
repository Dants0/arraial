# Cuts the item icons out of the Roteiro screenshot (ARRAIAL_ROTEIRO_SO=icones): each box listed in
# run/icones.json becomes docs/icones/<item>.png, with the magenta background made transparent.
param(
	[string]$Foto = "$PSScriptRoot\..\run\screenshots\arraial_icones.png",
	[string]$Manifesto = "$PSScriptRoot\..\run\icones.json",
	[string]$Saida = "$PSScriptRoot\..\docs\icones"
)
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $Saida | Out-Null
$m = Get-Content $Manifesto -Raw | ConvertFrom-Json
$img = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Foto))
$n = 0
foreach ($p in $m.itens.PSObject.Properties) {
	$x = [int]$p.Value[0]; $y = [int]$p.Value[1]; $t = [int]$p.Value[2]
	$bmp = New-Object System.Drawing.Bitmap $t, $t
	for ($i = 0; $i -lt $t; $i++) {
		for ($j = 0; $j -lt $t; $j++) {
			$c = $img.GetPixel($x + $i, $y + $j)
			# Magenta (and the blend at the edges) becomes transparent.
			if ($c.R -gt 200 -and $c.G -lt 60 -and $c.B -gt 200) { $c = [System.Drawing.Color]::Transparent }
			$bmp.SetPixel($i, $j, $c)
		}
	}
	$bmp.Save((Join-Path $Saida ($p.Name + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
	$n++
}
$img.Dispose()
"$n icones em $Saida"
