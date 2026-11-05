param([string]$Dir, [string]$Saida, [string]$Filtro = '*_0.png')
Add-Type -AssemblyName System.Drawing
$arquivos = Get-ChildItem $Dir -Filter $Filtro | Sort-Object Name
$E = 6
$larg = 16 * $E + 8
$alt = 32 * $E + 8
$cols = 6
$linhas = [Math]::Ceiling($arquivos.Count / $cols)
$out = New-Object System.Drawing.Bitmap ($cols * $larg), ($linhas * $alt)
$g = [System.Drawing.Graphics]::FromImage($out)
$g.Clear([System.Drawing.Color]::FromArgb(255, 120, 150, 110))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
function Peca($src, $sx, $sy, $w, $h, $dx, $dy, $ox, $oy) {
  $g.DrawImage($src, (New-Object System.Drawing.Rectangle (($ox + $dx) * $E + $script:bx), (($oy + $dy) * $E + $script:by), ($w * $E), ($h * $E)),
    $sx, $sy, $w, $h, [System.Drawing.GraphicsUnit]::Pixel)
}
$i = 0
foreach ($a in $arquivos) {
  $src = [System.Drawing.Image]::FromFile($a.FullName)
  $script:bx = ($i % $cols) * $larg + 4; $script:by = [Math]::Floor($i / $cols) * $alt + 4
  # base then overlay: head, body, right arm (viewer's left), left arm, legs
  foreach ($ov in @(0, 1)) {
    if ($ov -eq 0) {
      Peca $src 8 8 8 8 4 0 0 0; Peca $src 20 20 8 12 4 8 0 0
      Peca $src 44 20 4 12 0 8 0 0; Peca $src 36 52 4 12 12 8 0 0
      Peca $src 4 20 4 12 4 20 0 0; Peca $src 20 52 4 12 8 20 0 0
    } else {
      Peca $src 40 8 8 8 4 0 0 0; Peca $src 20 36 8 12 4 8 0 0
      Peca $src 44 36 4 12 0 8 0 0; Peca $src 52 52 4 12 12 8 0 0
      Peca $src 4 36 4 12 4 20 0 0; Peca $src 4 52 4 12 8 20 0 0
    }
  }
  $src.Dispose(); $i++
}
$g.Dispose(); $out.Save($Saida); $out.Dispose()
"ok $i"
