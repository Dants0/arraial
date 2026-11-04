# Generates the 64x64 morador skins (classic wide-arm player layout).
# Usage: powershell -File tools\gerar_skins.ps1 -Saida src\main\resources\assets\arraial\textures\entity\morador
param([string]$Saida)
Add-Type -AssemblyName System.Drawing
$rng = New-Object System.Random 42

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Jitter([System.Drawing.Color]$c, [int]$amt) {
  $d = $rng.Next(-$amt, $amt + 1)
  [System.Drawing.Color]::FromArgb(255, [Math]::Max(0, [Math]::Min(255, $c.R + $d)), [Math]::Max(0, [Math]::Min(255, $c.G + $d)), [Math]::Max(0, [Math]::Min(255, $c.B + $d)))
}
function Rect($b, [int]$x, [int]$y, [int]$w, [int]$h, [string]$hex, [int]$noise = 5) {
  $c = C $hex
  for ($i = $x; $i -lt $x + $w; $i++) { for ($j = $y; $j -lt $y + $h; $j++) { $b.SetPixel($i, $j, (Jitter $c $noise)) } }
}
function Px($b, [int]$x, [int]$y, [string]$hex) { $b.SetPixel($x, $y, (C $hex)) }

# Paints a leg (or both arms/legs) region: x0,y0 is the top-left of the 16x16 limb block.
function Leg($b, [int]$x0, [int]$y0, [string]$cima, [string]$baixo, [int]$corte, [string]$pe) {
  Rect $b ($x0 + 4) $y0 4 4 $cima            # top
  Rect $b ($x0 + 8) $y0 4 4 $pe 3            # bottom (sole)
  Rect $b $x0 ($y0 + 4) 16 $corte $cima      # upper sides
  Rect $b $x0 ($y0 + 4 + $corte) 16 (12 - $corte) $baixo
  Rect $b $x0 ($y0 + 14) 16 2 $pe 3          # feet
}
function Arm($b, [int]$x0, [int]$y0, [string]$manga, [string]$pele, [int]$comprimento) {
  Rect $b $x0 $y0 16 16 $pele
  Rect $b ($x0 + 4) $y0 4 4 $manga
  Rect $b $x0 ($y0 + 4) 16 $comprimento $manga
}

function Skin([hashtable]$p) {
  $b = New-Object System.Drawing.Bitmap 64, 64, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($b); $g.Clear([System.Drawing.Color]::Transparent); $g.Dispose()

  # Head
  Rect $b 0 0 32 16 $p.pele
  Rect $b 8 0 8 8 $p.cabelo 4; Rect $b 24 8 8 8 $p.cabelo 4
  if ($p.fem) {
    Rect $b 0 8 8 7 $p.cabelo 4; Rect $b 16 8 8 7 $p.cabelo 4          # long hair on the sides
    Rect $b 8 8 8 2 $p.cabelo 4; Rect $b 8 10 1 5 $p.cabelo 4; Rect $b 15 10 1 5 $p.cabelo 4
  } else {
    Rect $b 0 8 8 3 $p.cabelo 4; Rect $b 16 8 8 3 $p.cabelo 4; Rect $b 8 8 8 2 $p.cabelo 4
  }
  Px $b 9 11 '#FFFFFF'; Px $b 10 11 '#3B2A1E'; Px $b 13 11 '#3B2A1E'; Px $b 14 11 '#FFFFFF'
  if ($p.bigode) { Rect $b 10 13 4 1 $p.cabelo 2 }
  if ($p.fem) { Px $b 11 14 '#9C4A42'; Px $b 12 14 '#9C4A42' } else { Px $b 11 14 '#5A3424'; Px $b 12 14 '#5A3424' }

  # Straw hat on the hat layer
  if ($p.chapeu) { Rect $b 40 0 8 8 '#D9B964' 10; Rect $b 32 8 32 2 '#D9B964' 10; Rect $b 32 10 32 1 '#8C3B2A' 3 }

  # Body
  Rect $b 16 16 24 16 $p.roupa
  if ($p.vestido) {
    Rect $b 16 27 24 1 '#FFFFFF' 2                                       # waist ribbon
  } else {
    Rect $b 16 28 24 1 '#3B2618' 2                                       # belt
  }
  if ($p.avental) { Rect $b 21 24 6 8 '#7A5230' 6 }

  # Arms (short sleeves for dresses, rolled sleeves otherwise)
  $mangas = if ($p.vestido) { 3 } else { 5 }
  Arm $b 40 16 $p.roupa $p.pele $mangas
  Arm $b 32 48 $p.roupa $p.pele $mangas

  # Legs: a skirt covers the upper legs, trousers cover them all
  if ($p.vestido) {
    Leg $b 0 16 $p.roupa $p.pele 6 '#3F2A1A'
    Leg $b 16 48 $p.roupa $p.pele 6 '#3F2A1A'
  } else {
    Leg $b 0 16 $p.calca $p.calca 10 '#3F2A1A'
    Leg $b 16 48 $p.calca $p.calca 10 '#3F2A1A'
  }
  $b.Save((Join-Path $Saida ($p.nome + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
  $b.Dispose()
}

New-Item -ItemType Directory -Force $Saida | Out-Null
$todas = @(
  @{ nome = 'homem_0'; pele = '#8D5A3B'; cabelo = '#2B1B10'; roupa = '#3E6FA8'; calca = '#4A3B2A'; bigode = $true }
  @{ nome = 'homem_1'; pele = '#E0B48C'; cabelo = '#6B4423'; roupa = '#A8463E'; calca = '#3E4A5C' }
  @{ nome = 'homem_2'; pele = '#5A3824'; cabelo = '#141010'; roupa = '#4F7F3A'; calca = '#5C4A36'; bigode = $true }
  @{ nome = 'mulher_0'; fem = $true; pele = '#8D5A3B'; cabelo = '#2B1B10'; roupa = '#E3B23C'; vestido = $true }
  @{ nome = 'mulher_1'; fem = $true; pele = '#E0B48C'; cabelo = '#8A5A2B'; roupa = '#4E79C4'; vestido = $true }
  @{ nome = 'mulher_2'; fem = $true; pele = '#5A3824'; cabelo = '#141010'; roupa = '#C0473F'; vestido = $true }
  @{ nome = 'pedreiro'; pele = '#8D5A3B'; cabelo = '#2B1B10'; roupa = '#E8E1CF'; calca = '#6B4E32'; bigode = $true; chapeu = $true; avental = $true }
  @{ nome = 'pedreira'; fem = $true; pele = '#B07A52'; cabelo = '#3A2414'; roupa = '#E8E1CF'; calca = '#6B4E32'; chapeu = $true; avental = $true }
)
foreach ($p in $todas) { Skin $p }
"skins geradas: $($todas.Count)"
