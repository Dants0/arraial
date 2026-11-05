# Textures of the colonial building blocks (taipa, telha, cal, azulejo): 16x16, tileable.
# Usage: powershell -File tools/gerar_blocos_coloniais.ps1 -Raiz src/main/resources/assets/arraial/textures
param([string]$Raiz)
Add-Type -AssemblyName System.Drawing
function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
# Deterministic noise in [0,1).
function Ruido([long]$x, [long]$y, [long]$s) { $h = ($x * 374761393 + $y * 668265263 + $s * 982451653) -band 0x7fffffff; $h = (($h -bxor ($h -shr 13)) * 1274126177) -band 0x7fffffff; ($h % 1000) / 1000.0 }
function Mistura([string]$a, [string]$b, [double]$t) { $ca = C $a; $cb = C $b
  [System.Drawing.Color]::FromArgb([int]($ca.R + ($cb.R - $ca.R) * $t), [int]($ca.G + ($cb.G - $ca.G) * $t), [int]($ca.B + ($cb.B - $ca.B) * $t)) }
function Salvar($b, [string]$nome) { $d = Join-Path $Raiz 'block'; New-Item -ItemType Directory -Force $d | Out-Null
  $b.Save((Join-Path $d "$nome.png"), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose() }

# Taipa: reddish-brown clay daub, rough, with the stick lattice showing where the plaster fell off.
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
  $n = Ruido $x $y 1
  $c = if ($n -lt 0.18) { C '#7E5236' } elseif ($n -gt 0.85) { C '#B58A5E' } else { Mistura '#94653F' '#A27350' (Ruido $x $y 2) }
  # Lattice: vertical posts at x=3 and 11, horizontal laths at y=4 and 12, visible in patches.
  $vara = (($x -eq 3 -or $x -eq 11) -and (Ruido ([math]::Floor($x / 8)) ([math]::Floor($y / 5)) 3) -lt 0.55) -or (($y -eq 4 -or $y -eq 12) -and (Ruido ([math]::Floor($x / 5)) $y 4) -lt 0.45)
  if ($vara) { $c = if ((Ruido $x $y 5) -lt 0.3) { C '#4E3520' } else { C '#6B4A2B' } }
  $b.SetPixel($x, $y, $c) } }
Salvar $b 'taipa'

# Telha: capa-e-canal clay tiles running down the roof: rounded caps (lit in the middle) over
# darker channels, each tile overlapping the one below with a shadowed lip; rows staggered.
$b = New-Object System.Drawing.Bitmap 16, 16
$capa = @('#9A4A28', '#C46A3C', '#DA8655', '#C46A3C')
$canal = @('#7A361C', '#8E4224', '#8E4224', '#7A361C')
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
  $col = [math]::Floor($x / 4); $dx = $x % 4; $eCapa = ($col % 2) -eq 0
  $desloc = 0; if (-not $eCapa) { $desloc = 4 }
  $dy = ($y + $desloc) % 8
  $hex = if ($eCapa) { $capa[$dx] } else { $canal[$dx] }
  $c = C $hex
  if ($dy -eq 7) { $c = Mistura $hex '#3E1A0C' 0.55 }          # the lip of the tile above
  elseif ($dy -eq 0) { $c = Mistura $hex '#F2B07E' 0.3 }       # the worn edge
  if ((Ruido $x $y 7) -lt 0.08 -and $dy -ne 7) { $c = Mistura $hex '#6E7A3A' 0.35 }   # a little moss
  $b.SetPixel($x, $y, $c) } }
Salvar $b 'telha'

# Cal: whitewashed plaster, warm off-white with soft blotches.
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
  $n = (Ruido $x $y 11) * 0.6 + (Ruido ([math]::Floor($x / 3)) ([math]::Floor($y / 3)) 12) * 0.4
  $c = if ($n -lt 0.12) { C '#D9D3C4' } elseif ($n -gt 0.9) { C '#FBF9F2' } else { Mistura '#E7E2D5' '#F2EEE4' $n }
  $b.SetPixel($x, $y, $c) } }
Salvar $b 'cal'

# Azulejo: four 8x8 glazed tiles, white with a blue flower and corner quarter-circles, grout lines.
$tile = @(
  'gggggggg',
  'gbwwwwwb',
  'gwwwbwww',
  'gwwbabww',
  'gwbaaabw',
  'gwwbabww',
  'gwwwbwww',
  'gbwwwwwb')
$pal = @{ g = '#C9C6BC'; w = '#F1F3F6'; b = '#2C56A8'; a = '#16357A' }
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
  $ch = [string]$tile[$y % 8][$x % 8]
  $c = C $pal[$ch]
  if ($ch -eq 'w' -and (Ruido $x $y 21) -lt 0.1) { $c = C '#E2E6EE' }   # glaze unevenness
  $b.SetPixel($x, $y, $c) } }
Salvar $b 'azulejo'

# Mapa da Região (item): a rolled-open parchment with green land, a blue river, red and gold
# marks for arraiais and a dotted road.
$mapa = @(
  '................',
  '.nnnnnnnnnnnnnn.',
  '.npppppppppppppn',
  '.npggpppbpppggpn',
  '.npgggppbbpggggn',
  '.nppgrppbppgggpn',
  '.npppdpppbppppnn',
  '.nppppdppbpyppn.',
  '.nppgppddbdppppn',
  '.npgggppppbppgpn',
  '.nppgppppbbpgggn',
  '.npppppppbpppgpn',
  '.npppppppbppppnn',
  '.nnpppppppbppn..',
  '..nnnnnnnnnnnn..',
  '................')
$pm = @{ n = '#6B4A24'; p = '#E6D3A0'; g = '#7FA65A'; b = '#3F78B5'; r = '#C0302A'; y = '#D9A520'; d = '#8A6A45' }
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
  $ch = [string]$mapa[$y][$x]
  if ($ch -ne '.') { $b.SetPixel($x, $y, (C $pm[$ch])) } } }
$d = Join-Path $Raiz 'item'; New-Item -ItemType Directory -Force $d | Out-Null
$b.Save((Join-Path $d 'mapa_regiao.png'), [System.Drawing.Imaging.ImageFormat]::Png); $b.Dispose()
