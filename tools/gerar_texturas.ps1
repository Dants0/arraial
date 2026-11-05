# Generates the Arraial item and block textures (16x16 pixel art, flat and earthy like the
# skins). Grids: one string per row, one char per pixel, '.' = transparent; rows shorter
# than 16 are padded with '.'. Animated textures are vertical strips of 16x16 frames.
# Usage: powershell -File tools/gerar_texturas.ps1 -Raiz src/main/resources/assets/arraial/textures
param([string]$Raiz)
Add-Type -AssemblyName System.Drawing

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Pal([string]$spec) {
  $d = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
  foreach ($par in $spec.Split(' ')) { $kv = $par.Split('='); $d[$kv[0]] = $kv[1] }
  ,$d
}

function Grade($b, $pal, [string[]]$linhas, [int]$oy = 0) {
  for ($y = 0; $y -lt $linhas.Count; $y++) {
    $l = $linhas[$y].PadRight(16, '.')
    for ($x = 0; $x -lt 16; $x++) {
      $c = [string]$l[$x]
      if ($c -ne '.') { $b.SetPixel($x, $y + $oy, (C $pal[$c])) }
    }
  }
}

function Salvar($b, [string]$pasta, [string]$nome) {
  $dir = Join-Path $Raiz $pasta
  New-Item -ItemType Directory -Force $dir | Out-Null
  $b.Save((Join-Path $dir "$nome.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $b.Dispose()
}

function Item([string]$nome, $pal, [string[]]$linhas, [string]$pasta = 'item') {
  $b = New-Object System.Drawing.Bitmap 16, 16
  Grade $b $pal $linhas
  Salvar $b $pasta $nome
}

function Animacao([string]$pasta, [string]$nome, [int]$tempo) {
  $mcmeta = '{"animation": {"frametime": ' + $tempo + ', "interpolate": true}}'
  Set-Content -Path (Join-Path (Join-Path $Raiz $pasta) "$nome.png.mcmeta") -Value $mcmeta -Encoding ascii
}

# Leather: a calm base with a few lighter/darker grains and stitching rows.
function Couro([string]$nome, [string]$base, [string]$claro, [string]$escuro, [int]$semente) {
  $b = New-Object System.Drawing.Bitmap 16, 16
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $k = ($x * 7 + $y * 13 + $semente) % 17
    $cor = if ($k -eq 0) { $claro } elseif ($k -eq 5) { $escuro } else { $base }
    if (($y -eq 2 -or $y -eq 13) -and ($x % 2 -eq 0)) { $cor = '#C9A66B' }
    $b.SetPixel($x, $y, (C $cor))
  } }
  Salvar $b 'block' $nome
}

# ------------------------------------------------------------ items
$madeira = (Pal 'a=#8A6A45 b=#B89468 n=#5C4128 g=#6E8F3A')
Item 'maniva' $madeira @(
  '', '...........g', '..........gnb', '.........aab', '........aab', '.......aab', '......nnb', '.....aab',
  '....aab', '...aab', '..nnb', '.aab', '.ab')

Item 'mandioca' (Pal 'c=#5E3F26 m=#8C6440 h=#A97E52 w=#F2EBD9 p=#E3D6B8') @(
  '', '............pp', '...........pwwp', '..........mpwwp', '.........mhmppc', '........mhmmmc', '.......mhmmmc',
  '......mhmmmc', '.....mhmmmc', '....mhmmmc', '...mhmmcc', '...hmmc', '..mmcc', '..cc', '.c')

Item 'farinha' (Pal 'r=#7A5A3A s=#D9C9A3 t=#B8A67E f=#F4EED8') @(
  '', '......rrrr', '.......rr', '......tsst', '.....tsssst', '....tssssssst', '...tsssffsssst', '...tssffffssst',
  '..tssssffsssssst', '..tsssssssssssst', '..tsssssssssssst', '..tsssssssssssst', '...tssssssssst', '....tttttttt')

Item 'beiju' (Pal 'e=#D9CBA8 w=#F3EEDF q=#C9A46A t=#B9A47C') @(
  '', '', '', '', '.....eeeeee', '...eewwwwwwee', '..ewwwqwwwwwwe', '.ewwwwwwwwqwwwe', '.ewwqwwwwwwwwwe',
  '.ewwwwwwqwwwwwe', '..ewwwwwwwwqwe', '...eewwwwwwee', '....teeeeeet', '......tttt')

Item 'rapadura' (Pal 'H=#B9773A m=#8B4F1E d=#5E3312 s=#D49A5A') @(
  '', '', '', '....sHHHHHHHs', '...sHHHHHHHHHd', '..sHHHHHHHHHHdd', '..mmmmmmmmmmmdd', '..mmmmmmmmmmmdd',
  '..mmmdmmmmmmmdd', '..mmmmmmmdmmmd', '..mmmmmmmmmmmd', '..mmdmmmmmmmm', '..ddddddddddd')

Item 'pacoca' (Pal 'c=#C9A06A k=#E2C08A r=#8A3A2A b=#6B4A2E d=#4A321E l=#8A6640') @(
  '', '', '', '.....kkkk', '....kcckrck', '...kcrcckcck', '..kcckcrcckck', '..bccccccccccb', '..bllllllllllb',
  '...bllllllllb', '...dbllllllbd', '....dbbbbbbd', '......dddd')

$cuia = (Pal 'r=#5E4526 c=#8A6A3A h=#A9874E d=#4A361E')
Item 'cuia' $cuia @(
  '', '', '', '', '', '..rrrrrrrrrrrr', '..rddddddddddr', '..hccccccccccr', '...hcccccccccr', '...hccccccccr',
  '....hcccccccr', '.....hcccccr', '......rrrrr')
$garapa = Pal 'r=#5E4526 c=#8A6A3A h=#A9874E d=#4A361E g=#C8C46A f=#ECE6B4'
Item 'cuia_garapa' $garapa @(
  '', '', '', '', '', '..rrrrrrrrrrrr', '..rgfggfgggfgr', '..hccccccccccr', '...hcccccccccr', '...hccccccccr',
  '....hcccccccr', '.....hcccccr', '......rrrrr')

Item 'reis' (Pal 'o=#B87333 O=#DC9A5C d=#7E4A22 x=#5E3618') @(
  '', '', '', '.....dddddd', '....dOOOOOOd', '...dOooooooOd', '...dOoxoxooOd', '...dOoxxooooOd', '...dOoxoxoooOd',
  '...dOoxooxooOd', '...dOooooooOd', '....dOOOOOOd', '.....dddddd')

# ------------------------------------------------------------ mandioca crop stages
$planta = (Pal 'a=#7C6A3A l=#4F7A2E k=#6B9A3A d=#3A5C22 r=#9A3E2E')
Item 'mandioca_estagio0' $planta @('', '', '', '', '', '', '', '', '', '', '', '', '.......k', '......klk', '.......a', '.......a') 'block'
Item 'mandioca_estagio1' $planta @('', '', '', '', '', '', '', '', '....k.....k', '...klk...klk', '....ld...dl',
  '.....da.ad', '......a.a', '.......a', '.......a', '.......a') 'block'
Item 'mandioca_estagio2' $planta @('', '', '', '.......kk', '......klll', '..kk...lr...kk', '.klll..r...llk', '..dllr.a..rlld',
  '....r..a.r', '.....r.ar', '......aa', '.......a', '.......a', '.......a', '.......a', '.......a') 'block'
Item 'mandioca_estagio3' $planta @('....kk....kk', '...klll..lllk', '..kklldrrdllkk', '.klld..rr..dllk', '..d..kkrrkk..d',
  '.kk.kllaallk.kk', 'klll.ddaadd.lllk', '.dlr...aa...rld', '...r...aa...r', '....r..aa..r', '.....r.aa.r',
  '......raar', '.......aa', '.......aa', '.......aa', '.......aa') 'block'

# ------------------------------------------------------------ blocks
# Tacho: the copper pan on top of the forno de farinha, with flour being toasted.
Item 'tacho' (Pal 'b=#8A5A3C B=#6E452C x=#5E3618 o=#B8733F O=#D9925A f=#EFE6CC g=#D8CCA8') @(
  'bBbbbBbbbbBbbbBb', 'bbbxxxxxxxxxxbbb', 'bbxoOOOOOOOOoxbb', 'bxoOffffffffOoxb', 'bxOffggffffgfOxb', 'bxOfffffgffffOxb',
  'bxOffgffffffgOxb', 'BxOffffffgfffOxB', 'bxOfgffffffgfOxb', 'bxOffffgffffOOxb', 'bxOffgfffffffOxb', 'bxoOfffffgffOoxb',
  'bbxoOOOOOOOOoxbb', 'bbbxxxxxxxxxxbbb', 'bBbbbBbbbbBbbbBb', 'bbbbbbbbbbbbbbbb') 'block'

# Forno front: mud bricks around the fire mouth; the fire flickers (3 frames).
$forno = (Pal 'm=#8C6A4E j=#6E5038 k=#2A1A12 f=#E07A2A F=#F5B342 r=#B8441E')
$tijolos = @('mmmjmmmmmmmjmmmm', 'mmmjmmmmmmmjmmmm', 'jjjjjjjjjjjjjjjj', 'mmmmmmmjmmmmmmmj', 'mmmmmmmjmmmmmmmj',
  'jjjjjjjjjjjjjjjj', 'mmmjmmmmmmmjmmmm', 'mmmjmkkkkkkjmmmm', 'jjjjkkkkkkkkjjjj', 'mmmkkkkkkkkkkmmj', 'mmmkkkkkkkkkkmmj',
  'jjjkkkkkkkkkkjjj', 'mmmkkkkkkkkkkmmm', 'mmmkkkkkkkkkkmmm', 'jjjkkkkkkkkkkjjj', 'mmmkkkkkkkkkkmmm')
$chamas = @(
  @('', '', '', '', '', '', '', '', '', '', '', '.......r', '......rfr', '.....rfFfr.r', '....rfFFFfrfr', '...rfFFFFFFfr'),
  @('', '', '', '', '', '', '', '', '', '', '.......r', '......rfr..r', '.....rfFfrrfr', '.....fFFFffF', '....rfFFFFFfr', '...rfFFFFFFfr'),
  @('', '', '', '', '', '', '', '', '', '', '', '........r', '...r..rfr', '..rfr.fFfr', '...fFrFFFfr', '...rfFFFFFFfr'))
$b = New-Object System.Drawing.Bitmap 16, 48
for ($i = 0; $i -lt 3; $i++) { Grade $b $forno $tijolos ($i * 16); Grade $b $forno $chamas[$i] ($i * 16) }
Salvar $b 'block' 'forno_farinha_boca'
Animacao 'block' 'forno_farinha_boca' 4

# Lampião: tin frame, glass glowing with an oil flame that flickers (4 frames).
$lamp = (Pal 'x=#2E2E30 t=#5A5A5E g=#E8B35A G=#F6D88A w=#FFF4C8 o=#C9862E')
$moldura = @('xxxxxxxxxxxxxxxx', 'xttttttttttttttx') + (1..12 | ForEach-Object { 'xtggggggggggggtx' }) + @('xttttttttttttttx', 'xxxxxxxxxxxxxxxx')
$chamasL = @(
  @('', '', '', '', '......GGG', '.....GGwGG', '.....GwwwG', '.....GwwwG', '......GwG', '......oGo', '.......o'),
  @('', '', '', '', '.......GG', '......GGwG', '......GwwwG', '......GwwwG', '.......GwG', '.......oGo', '........o'),
  @('', '', '', '.......G', '......GGG', '.....GGwGG', '....GGwwwGG', '.....GwwwG', '.....GGwGG', '......oGo', '.......o'),
  @('', '', '', '', '......GG', '.....GGwG', '.....GwwwG', '.....GwwGG', '......GwG', '......oGo', '.......o'))
$b = New-Object System.Drawing.Bitmap 16, 64
for ($i = 0; $i -lt 4; $i++) { Grade $b $lamp $moldura ($i * 16); Grade $b $lamp $chamasL[$i] ($i * 16) }
Salvar $b 'block' 'lampiao'
Animacao 'block' 'lampiao' 3
Item 'lampiao_metal' (Pal 'x=#2E2E30 t=#5A5A5E r=#8A8A90') @(
  'xxxxxxxxxxxxxxxx', 'xttttttttttttttx', 'xtrttttttttttrtx', 'xttttttttttttttx', 'xttttttttttttttx', 'xttttttttttttttx',
  'xttttttttttttttx', 'xttttttttttttttx', 'xttttttttttttttx', 'xttttttttttttttx', 'xttttttttttttttx', 'xttttttttttttttx',
  'xttttttttttttttx', 'xtrttttttttttrtx', 'xttttttttttttttx', 'xxxxxxxxxxxxxxxx') 'block'

# Striped awning of the market stall.
$b = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
  $cor = if ((([math]::Floor($x / 2)) % 2) -eq 0) { '#B03A2E' } else { '#EDE3C8' }
  if ($y -eq 15 -and ($x % 4 -eq 3)) { $cor = '#7A2A20' }
  $b.SetPixel($x, $y, (C $cor))
} }
Salvar $b 'block' 'toldo'

Couro 'couro' '#7A4E2C' '#9A6838' '#5E3A1F' 3
Couro 'chapeu_couro' '#6E4426' '#8E5C32' '#4E2E16' 9

# Jars of the botica: cork, coloured glass, a paper label.
Item 'frascos' (Pal 'k=#9A7448 K=#7A5634 v=#4F7F6E V=#7FB09C a=#8A4F7A A=#B07AA2 p=#E8E0C8') @(
  '.kk...KK....kk', '.KK...kk....KK', 'vvvv.aaaa..vvvv', 'vVvv.aAaa..vVvv', 'vVvv.aAaa..vVvv', 'vppv.appa..vppv',
  'vppv.appa..vppv', 'vVvv.aAaa..vVvv', 'vvvv.aaaa..vvvv', '', '.kk...kk....KK', '.KK...KK....kk', 'aaaa.vvvv..aaaa',
  'aAaa.vVvv..aAaa', 'appa.vppv..appa', 'aaaa.vvvv..aaaa') 'block'

"texturas geradas em $Raiz"
