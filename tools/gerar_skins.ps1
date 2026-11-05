# Generates the 64x64 morador skins (classic wide-arm player layout), "low profile" style:
# flat colours, a short earthy palette, fixed shading per face (no noise), and each trade told
# apart by its silhouette (hat, apron, leather jacket...) rather than by small details.
#
# Files: <oficio>_<m|f>_<v>.png, where oficio is "morador" (no trade) or the trade id, and v (0..3)
# is the person's look (skin tone, hair, everyday colours), so someone who changes trade keeps
# their face.
#
# Usage: powershell -File tools\gerar_skins.ps1 -Saida src\main\resources\assets\arraial\textures\entity\morador
param([string]$Saida)
Add-Type -AssemblyName System.Drawing

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Tom([string]$hex, [double]$f) {
  $c = C $hex
  [System.Drawing.Color]::FromArgb(255, [int][Math]::Min(255, $c.R * $f), [int][Math]::Min(255, $c.G * $f), [int][Math]::Min(255, $c.B * $f))
}

# ---------------------------------------------------------------- cuboid UV layout
# A part: u,v = UV origin; w = width (x), h = height (y), d = depth (z).
function Parte([int]$u, [int]$v, [int]$w, [int]$h, [int]$d) { @{ u = $u; v = $v; w = $w; h = $h; d = $d } }
$CABECA = Parte 0 0 8 8 8;   $CHAPEU = Parte 32 0 8 8 8
$CORPO = Parte 16 16 8 12 4; $JAQUETA = Parte 16 32 8 12 4
$BRACO_D = Parte 40 16 4 12 4; $MANGA_D = Parte 40 32 4 12 4
$BRACO_E = Parte 32 48 4 12 4; $MANGA_E = Parte 48 48 4 12 4
$PERNA_D = Parte 0 16 4 12 4;  $CALCA_D = Parte 0 32 4 12 4
$PERNA_E = Parte 16 48 4 12 4; $CALCA_E = Parte 0 48 4 12 4

# Fixed light per face: front 1.0, sides a bit darker, back darker, top lighter, bottom darkest.
$LUZ = @{ frente = 1.0; direita = 0.9; esquerda = 0.9; tras = 0.82; topo = 1.06; baixo = 0.72 }

# The four side faces of a part: name, x, y, width.
function Lados($p) {
  @(
    @{ n = 'direita'; x = $p.u; w = $p.d },
    @{ n = 'frente'; x = $p.u + $p.d; w = $p.w },
    @{ n = 'esquerda'; x = $p.u + $p.d + $p.w; w = $p.d },
    @{ n = 'tras'; x = $p.u + $p.d + $p.w + $p.d; w = $p.w }
  )
}

# Paints rows [r0, r1) of all four sides of a part.
function Faixa($b, $p, [int]$r0, [int]$r1, [string]$hex, [string[]]$so = $null) {
  foreach ($l in (Lados $p)) {
    if ($so -and -not ($so -contains $l.n)) { continue }
    $cor = Tom $hex $LUZ[$l.n]
    for ($x = $l.x; $x -lt $l.x + $l.w; $x++) {
      for ($r = $r0; $r -lt $r1; $r++) { $b.SetPixel($x, $p.v + $p.d + $r, $cor) }
    }
  }
}
function Topo($b, $p, [string]$hex) {
  $cor = Tom $hex $LUZ.topo
  for ($x = 0; $x -lt $p.w; $x++) { for ($z = 0; $z -lt $p.d; $z++) { $b.SetPixel($p.u + $p.d + $x, $p.v + $z, $cor) } }
}
function Baixo($b, $p, [string]$hex) {
  $cor = Tom $hex $LUZ.baixo
  for ($x = 0; $x -lt $p.w; $x++) { for ($z = 0; $z -lt $p.d; $z++) { $b.SetPixel($p.u + $p.d + $p.w + $x, $p.v + $z, $cor) } }
}
# One pixel on a given face (x across the face, r = row).
function Px($b, $p, [string]$face, [int]$x, [int]$r, [string]$hex) {
  $l = (Lados $p) | Where-Object { $_.n -eq $face }
  $b.SetPixel($l.x + $x, $p.v + $p.d + $r, (Tom $hex $LUZ[$face]))
}
# Fills a whole part (sides + top + bottom).
function Tudo($b, $p, [string]$hex) { Faixa $b $p 0 $p.h $hex; Topo $b $p $hex; Baixo $b $p $hex }

# ---------------------------------------------------------------- palette
$PELES = @('#E3BC98', '#C48D66', '#956142', '#654131')
$CABELOS = @('#5C3D24', '#2E1F15', '#1C1410', '#3A2618')
$OLHO = '#2A211B'; $BRANCO = '#E9E2D4'
$ROUPAS_M = @('#4F6582', '#9A5A3C', '#5E6B45', '#B8923F')   # anil, terracota, musgo, mostarda
$CALCAS_M = @('#5A4A38', '#3F4652', '#6B5A44', '#4A3E33')
$VESTIDOS = @('#B8923F', '#4F6582', '#7A3B3B', '#D2C6AA')   # mostarda, anil, vinho, algodao cru
$CRU = '#D8CDB4'; $COURO = '#7A5236'; $COURO_ESC = '#563A27'; $SAPATO = '#3A2A1E'; $PALHA = '#CDB06A'

# ---------------------------------------------------------------- body pieces
function Cabeca($b, $o) {
  Tudo $b $CABECA $o.pele
  Topo $b $CABECA $o.cabelo
  Faixa $b $CABECA 0 8 $o.cabelo @('tras')
  if ($o.fem) {
    Faixa $b $CABECA 0 8 $o.cabelo @('direita', 'esquerda')
    Faixa $b $CABECA 0 2 $o.cabelo @('frente')
    for ($r = 2; $r -lt 8; $r++) { Px $b $CABECA 'frente' 0 $r $o.cabelo; Px $b $CABECA 'frente' 7 $r $o.cabelo }
  } else {
    Faixa $b $CABECA 0 3 $o.cabelo @('direita', 'esquerda')
    Faixa $b $CABECA 0 2 $o.cabelo @('frente')
  }
  # Face: eyes, nose shadow, mouth.
  Px $b $CABECA 'frente' 1 4 $BRANCO; Px $b $CABECA 'frente' 2 4 $OLHO
  Px $b $CABECA 'frente' 5 4 $OLHO; Px $b $CABECA 'frente' 6 4 $BRANCO
  $sombra = (Tom $o.pele 0.85).Name; $sombra = '#' + $sombra.Substring(2)
  Px $b $CABECA 'frente' 3 5 $sombra; Px $b $CABECA 'frente' 4 5 $sombra
  if ($o.fem) {
    Px $b $CABECA 'frente' 3 6 '#8E4A42'; Px $b $CABECA 'frente' 4 6 '#8E4A42'
  } elseif ($o.barba) {
    for ($x = 1; $x -lt 7; $x++) { Px $b $CABECA 'frente' $x 7 $o.cabelo }
    Px $b $CABECA 'frente' 2 6 $o.cabelo; Px $b $CABECA 'frente' 5 6 $o.cabelo
    Px $b $CABECA 'frente' 3 6 '#5A3424'; Px $b $CABECA 'frente' 4 6 '#5A3424'
  } else {
    Px $b $CABECA 'frente' 3 6 '#5A3424'; Px $b $CABECA 'frente' 4 6 '#5A3424'
  }
}

# Shirt + trousers (or a dress), sleeves of a given length, shoes.
function Roupa($b, $o) {
  $camisa = $o.camisa
  if ($o.vestido) {
    Tudo $b $CORPO $camisa
    Faixa $b $CORPO 7 8 (('#' + (Tom $camisa 0.8).Name.Substring(2)))       # waistline
    foreach ($perna in @($PERNA_D, $PERNA_E)) {
      Tudo $b $perna $o.pele
      Faixa $b $perna 0 7 $camisa
      Faixa $b $perna 6 7 (('#' + (Tom $camisa 0.8).Name.Substring(2)))     # hem
      Faixa $b $perna 10 12 $o.sapato; Baixo $b $perna $o.sapato
      Topo $b $perna $camisa
    }
  } else {
    Tudo $b $CORPO $camisa
    Faixa $b $CORPO 8 9 $o.cinto
    Faixa $b $CORPO 9 12 $o.calca
    Baixo $b $CORPO $o.calca
    foreach ($perna in @($PERNA_D, $PERNA_E)) {
      Tudo $b $perna $o.calca
      if ($o.arregacada) { Faixa $b $perna 7 10 $o.pele }
      Faixa $b $perna 10 12 $o.sapato; Baixo $b $perna $o.sapato
    }
  }
  foreach ($braco in @($BRACO_D, $BRACO_E)) {
    Tudo $b $braco $o.pele
    Faixa $b $braco 0 $o.manga $camisa
    Topo $b $braco $camisa
    if ($o.punho) { Faixa $b $braco ($o.manga - 1) $o.manga $o.punho }
  }
}

# ---------------------------------------------------------------- trade accessories (overlay layers)
function ChapeuPalha($b) {
  Topo $b $CHAPEU $PALHA
  Faixa $b $CHAPEU 0 2 $PALHA
  Faixa $b $CHAPEU 2 3 '#7A3B2A'                     # ribbon
}
function ChapeuCouro($b) {                           # sertanejo leather hat
  Topo $b $CHAPEU $COURO
  Faixa $b $CHAPEU 0 3 $COURO
  Faixa $b $CHAPEU 2 3 $COURO_ESC
}
function Lenco($b, [string]$cor) {                    # head scarf tied at the back
  Topo $b $CHAPEU $cor
  Faixa $b $CHAPEU 0 2 $cor
  Px $b $CHAPEU 'tras' 3 2 $cor; Px $b $CHAPEU 'tras' 4 2 $cor; Px $b $CHAPEU 'tras' 4 3 $cor
}
function Gorro($b, [string]$cor) {                    # wool cap with a folded rim
  Topo $b $CHAPEU $cor
  Faixa $b $CHAPEU 0 3 $cor
  Faixa $b $CHAPEU 2 3 (('#' + (Tom $cor 0.8).Name.Substring(2)))
}
function Avental($b, [string]$cor, [int]$ate) {       # apron: chest down onto the legs
  for ($x = 1; $x -lt 7; $x++) { for ($r = 2; $r -lt 12; $r++) { Px $b $JAQUETA 'frente' $x $r $cor } }
  Px $b $JAQUETA 'frente' 1 0 $cor; Px $b $JAQUETA 'frente' 6 0 $cor   # neck straps
  Px $b $JAQUETA 'frente' 1 1 $cor; Px $b $JAQUETA 'frente' 6 1 $cor
  foreach ($c in @($CALCA_D, $CALCA_E)) {
    for ($x = 0; $x -lt 4; $x++) { for ($r = 0; $r -lt $ate; $r++) { Px $b $c 'frente' $x $r $cor } }
  }
}
function Gibao($b) {                                  # leather jacket, open at the front
  Faixa $b $JAQUETA 0 10 $COURO
  for ($r = 0; $r -lt 10; $r++) { Px $b $JAQUETA 'frente' 3 $r $CRU; Px $b $JAQUETA 'frente' 4 $r $CRU }
  Faixa $b $JAQUETA 9 10 $COURO_ESC
  foreach ($m in @($MANGA_D, $MANGA_E)) { Faixa $b $m 0 9 $COURO; Faixa $b $m 8 9 $COURO_ESC }
}
function Perneira($b) {                               # leather chaps
  foreach ($c in @($CALCA_D, $CALCA_E)) { Faixa $b $c 0 10 $COURO_ESC }
}
function Suspensorio($b) {
  foreach ($face in @('frente', 'tras')) {
    for ($r = 0; $r -lt 8; $r++) { Px $b $CORPO $face 1 $r $COURO_ESC; Px $b $CORPO $face 6 $r $COURO_ESC }
  }
}
function Barro($b, [int]$de) {                        # mud on the legs
  foreach ($c in @($CALCA_D, $CALCA_E)) { Faixa $b $c $de 12 '#6B4E34' }
}
function Farda($b) {                                  # colonial militia coat
  for ($r = 1; $r -lt 8; $r += 2) { Px $b $CORPO 'frente' 3 $r '#C9A646' }
  Faixa $b $CORPO 8 9 '#E2DCCB'
  for ($r = 0; $r -lt 8; $r++) { Px $b $CORPO 'frente' (7 - $r) $r '#E2DCCB' }   # cross belt
}

# ---------------------------------------------------------------- one skin
function Skin([string]$oficio, [bool]$fem, [int]$v) {
  $b = New-Object System.Drawing.Bitmap 64, 64, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($b); $g.Clear([System.Drawing.Color]::Transparent); $g.Dispose()

  $o = @{
    fem = $fem; pele = $PELES[$v]; cabelo = $CABELOS[$v]; barba = (-not $fem) -and ($v % 2 -eq 0)
    camisa = $(if ($fem) { $VESTIDOS[$v] } else { $ROUPAS_M[$v] }); calca = $CALCAS_M[$v]
    cinto = $COURO_ESC; sapato = $SAPATO; manga = 6; vestido = $fem; arregacada = $false; punho = $null
  }
  # The trade's clothes (women of a trade wear its work clothes, not a dress).
  switch ($oficio) {
    'pedreiro'    { $o.camisa = $CRU; $o.calca = '#8C877C'; $o.vestido = $false; $o.manga = 4 }
    'lenhador'    { $o.camisa = '#8A3F30'; $o.calca = '#3F4652'; $o.vestido = $false; $o.manga = 9; $o.barba = -not $fem }
    'roceiro'     { $o.camisa = $CRU; $o.calca = '#6B5A44'; $o.vestido = $false; $o.arregacada = $true; $o.sapato = '#7A5236'; $o.manga = 5 }
    'oleiro'      { $o.camisa = $CRU; $o.calca = '#5A4A38'; $o.vestido = $false; $o.manga = 3 }
    'soldado'     { $o.camisa = '#2F4468'; $o.calca = '#D8D2C2'; $o.vestido = $false; $o.manga = 10; $o.punho = '#8A2E2E'; $o.sapato = '#1E1A17'; $o.cinto = '#E2DCCB' }
    'pastor'      { $o.camisa = $CRU; $o.calca = '#5A4A38'; $o.vestido = $false; $o.manga = 9 }
    'barreiro'    { $o.camisa = '#9A7B5A'; $o.calca = '#5A4A38'; $o.vestido = $false; $o.manga = 2; $o.arregacada = $true; $o.sapato = '#6B4E34' }
    'cavouqueiro' { $o.camisa = '#7D7A72'; $o.calca = '#45423D'; $o.vestido = $false; $o.manga = 7 }
  }
  Cabeca $b $o
  Roupa $b $o
  switch ($oficio) {
    'morador'     { if ($fem -and ($v % 2 -eq 1)) { Lenco $b $CRU } }
    'pedreiro'    { Lenco $b '#B8923F'; Avental $b $COURO 5 }
    'lenhador'    { Suspensorio $b; Gorro $b '#7A3B3B' }
    'roceiro'     { ChapeuPalha $b }
    'oleiro'      { Lenco $b '#9A5A3C'; Avental $b '#9A5A3C' 6 }
    'soldado'     { Farda $b }
    'pastor'      { ChapeuCouro $b; Gibao $b; Perneira $b }
    'barreiro'    { Lenco $b '#D2C6AA'; Barro $b 6 }
    'cavouqueiro' { Gorro $b '#5B5A55' }
  }
  $nome = '{0}_{1}_{2}.png' -f $oficio, $(if ($fem) { 'f' } else { 'm' }), $v
  $b.Save((Join-Path $Saida $nome), [System.Drawing.Imaging.ImageFormat]::Png)
  $b.Dispose()
}

New-Item -ItemType Directory -Force $Saida | Out-Null
$oficios = @('morador', 'pedreiro', 'lenhador', 'roceiro', 'oleiro', 'soldado', 'pastor', 'barreiro', 'cavouqueiro')
$n = 0
foreach ($of in $oficios) { foreach ($fem in @($false, $true)) { for ($v = 0; $v -lt 4; $v++) { Skin $of $fem $v; $n++ } } }
"skins geradas: $n"
