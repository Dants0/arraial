import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Writes vanilla structure files (.nbt, same format as the Structure Block) for the
 * Arraial starter catalog. Usage: java GerarPlantas.java <output dir>
 *
 * Layers are listed bottom (y=0) to top; inside a layer, rows go z=0 (front/north) to z=max,
 * characters go x=0 to x=max. '.' = air, ' ' = structure void (left untouched).
 * Palette entries use the 26.x keys "id" / "properties" (older versions used "Name" / "Properties").
 */
public class GerarPlantas {
	static final int DATA_VERSION = 5023; // Minecraft 26.3

	public static void main(String[] args) throws IOException {
		Path out = Paths.get(args[0]);
		Files.createDirectories(out);

		Map<Character, String> poco = Map.of(
				'C', "minecraft:cobblestone",
				'W', "minecraft:water",
				'F', "minecraft:oak_fence",
				'S', "minecraft:oak_slab[type=bottom]");
		escrever(out.resolve("poco.nbt"), poco, List.of(
				new String[] {"CCC", "CWC", "CCC"},
				new String[] {"CCC", "C.C", "CCC"},
				new String[] {"F.F", "...", "F.F"},
				new String[] {"SSS", "SSS", "SSS"}));

		Map<Character, String> casebre = new HashMap<>();
		casebre.put('C', "minecraft:cobblestone");
		casebre.put('P', "minecraft:oak_planks");
		casebre.put('L', "minecraft:oak_log[axis=y]");
		casebre.put('M', "minecraft:mud_bricks");
		casebre.put('d', "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false]");
		casebre.put('D', "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false]");
		casebre.put('t', "minecraft:wall_torch[facing=north]");
		casebre.put('e', "minecraft:brick_stairs[facing=east,half=bottom,shape=straight]");
		casebre.put('w', "minecraft:brick_stairs[facing=west,half=bottom,shape=straight]");
		casebre.put('s', "minecraft:brick_slab[type=bottom]");
		casebre.put('b', "minecraft:red_bed[facing=south,part=foot,occupied=false]");
		casebre.put('B', "minecraft:red_bed[facing=south,part=head,occupied=false]");
		escrever(out.resolve("casebre.nbt"), casebre, List.of(
				// y0: floor, cobblestone foundation around planks
				new String[] {"CCCCC", "CPPPC", "CPPPC", "CPPPC", "CCCCC"},
				// y1: walls, door at the front, a bed against the back wall
				new String[] {"LMdML", "M...M", "Mb..M", "MB..M", "LMMML"},
				// y2: walls with window holes and the upper half of the door
				new String[] {"LMDML", "M...M", "....." , "M...M", "LM.ML"},
				// y3: top of the walls, torch hanging on the back wall
				new String[] {"LMMML", "M...M", "M...M", "M.t.M", "LMMML"},
				// y4: roof eaves + gable ends
				new String[] {"eMMMw", "e...w", "e...w", "e...w", "eMMMw"},
				// y5: second roof row
				new String[] {" eMw ", " e.w ", " e.w ", " e.w ", " eMw "},
				// y6: ridge
				new String[] {"  s  ", "  s  ", "  s  ", "  s  ", "  s  "}));
		// ---- Marco 4: everything the prefeito can order. Only wood (and a little stone) where
		// possible, so a young arraial with just a lenhador can still build it.
		Map<Character, String> madeira = new HashMap<>();
		madeira.put('P', "minecraft:oak_planks");
		madeira.put('L', "minecraft:oak_log[axis=y]");
		madeira.put('x', "minecraft:oak_log[axis=x]");
		madeira.put('d', "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false]");
		madeira.put('D', "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false]");
		madeira.put('t', "minecraft:wall_torch[facing=north]");
		madeira.put('T', "minecraft:torch");
		madeira.put('e', "minecraft:oak_stairs[facing=east,half=bottom,shape=straight]");
		madeira.put('w', "minecraft:oak_stairs[facing=west,half=bottom,shape=straight]");
		madeira.put('s', "minecraft:oak_slab[type=bottom]");
		madeira.put('f', "minecraft:oak_fence");
		madeira.put('g', "minecraft:oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]");
		madeira.put('b', "minecraft:red_bed[facing=south,part=foot,occupied=false]");
		madeira.put('B', "minecraft:red_bed[facing=south,part=head,occupied=false]");
		madeira.put('W', "minecraft:water");
		madeira.put('c', "arraial:cepo");
		madeira.put('o', "arraial:forno_olaria");
		madeira.put('k', "arraial:balaio");
		madeira.put('q', "arraial:posto_guarda");
		madeira.put('h', "arraial:cocho");
		madeira.put('m', "arraial:gamela[facing=south]");
		madeira.put('n', "arraial:bancada_canteiro[facing=south]");

		// Rancho: a wooden house for when there are no mud bricks yet.
		escrever(out.resolve("rancho.nbt"), madeira, List.of(
				new String[] {"PPPPP", "PPPPP", "PPPPP", "PPPPP", "PPPPP"},
				new String[] {"LPdPL", "P...P", "Pb..P", "PB..P", "LPPPL"},
				new String[] {"LPDPL", "P...P", ".....", "P...P", "LP.PL"},
				new String[] {"LPPPL", "P...P", "P...P", "P.t.P", "LPPPL"},
				new String[] {"ePPPw", "e...w", "e...w", "e...w", "ePPPw"},
				new String[] {" ePw ", " e.w ", " e.w ", " e.w ", " ePw "},
				new String[] {"  s  ", "  s  ", "  s  ", "  s  ", "  s  "}));

		// Serraria: an open shed with the lenhador's cepo and a log pile.
		escrever(out.resolve("serraria.nbt"), madeira, List.of(
				new String[] {"PPPPP", "PPPPP", "PPPPP", "PPPPP"},
				new String[] {"L...L", ".....", "..c..", "LxxxL"},
				new String[] {"L...L", ".....", ".....", "L...L"},
				new String[] {"sssss", "sssss", "sssss", "sssss"}));

		// Roça: a water hole and the roceiro's balaio; he tills the wet ground around it himself.
		escrever(out.resolve("roca.nbt"), madeira, List.of(
				new String[] {"       ", "       ", "       ", "   W   ", "       ", "       ", "       "},
				new String[] {"...k...", ".......", ".......", ".......", ".......", ".......", "......."}));

		// Olaria: the potter's workshop around the kiln.
		escrever(out.resolve("olaria.nbt"), madeira, List.of(
				new String[] {"PPPPP", "PPPPP", "PPPPP", "PPPPP"},
				new String[] {"P...P", "P...P", "P.o.P", "PPPPP"},
				new String[] {"P...P", "P...P", "P...P", "PPPPP"},
				new String[] {"sssss", "sssss", "sssss", "sssss"}));

		// Guarita: a small watchtower with the soldados' weapon rack.
		escrever(out.resolve("guarita.nbt"), madeira, List.of(
				new String[] {"PPP", "PPP", "PPP"},
				new String[] {"L.L", ".q.", "L.L"},
				new String[] {"L.L", "...", "L.L"},
				new String[] {"L.L", "...", "L.L"},
				new String[] {"PPP", "PPP", "PPP"},
				new String[] {"f.f", ".T.", "f.f"}));

		// Curral: a fenced pen with the pastor's trough.
		escrever(out.resolve("curral.nbt"), madeira, List.of(
				new String[] {"       ", "       ", "       ", "       ", "       ", "       ", "       "},
				new String[] {"fffgfff", "f.....f", "f.....f", "f.....f", "f.....f", "f..h..f", "fffffff"}));

		// Barreira and Pedreira: the digging posts. The pit opens in front of them (the '.' area).
		escrever(out.resolve("barreira.nbt"), madeira, List.of(
				new String[] {"     ", "     ", "     ", "     ", "     ", "     "},
				new String[] {"..m..", ".....", ".....", ".....", ".....", "....."}));
		escrever(out.resolve("pedreira.nbt"), madeira, List.of(
				new String[] {"     ", "     ", "     ", "     ", "     ", "     ", "     "},
				new String[] {"..n..", ".....", ".....", ".....", ".....", ".....", "....."}));

		// Prefeitura: the town hall, in taipa with a tiled roof. The prefeito keeps office at the
		// lectern ('a'); benches for whoever comes with a request.
		Map<Character, String> prefeitura = new HashMap<>(casebre);
		prefeitura.put('a', "minecraft:lectern[facing=north,has_book=false,powered=false]");
		prefeitura.put('n', "minecraft:oak_stairs[facing=south,half=bottom,shape=straight]");
		prefeitura.put('T', "minecraft:torch");
		escrever(out.resolve("prefeitura.nbt"), prefeitura, List.of(
				// y0: stone foundation, plank floor
				new String[] {"CCCCCCC", "CPPPPPC", "CPPPPPC", "CPPPPPC", "CPPPPPC", "CCCCCCC"},
				// y1: door in the middle of the front, benches facing the lectern at the back
				new String[] {"LMMdMML", "M.....M", "M.n.n.M", "M.....M", "M..a..M", "LMMMMML"},
				// y2: windows on the sides
				new String[] {"LMMDMML", "M.....M", ".......", "M.....M", "M.....M", "LMMMMML"},
				// y3: top of the walls, torches on the back wall
				new String[] {"LMMMMML", "M.....M", "M.....M", "M.....M", "M.t.t.M", "LMMMMML"},
				// y4-y7: tiled roof with gable ends
				new String[] {"eMMMMMw", "e.....w", "e.....w", "e.....w", "e.....w", "eMMMMMw"},
				new String[] {" eMMMw ", " e...w ", " e...w ", " e...w ", " e...w ", " eMMMw "},
				new String[] {"  eMw  ", "  e.w  ", "  e.w  ", "  e.w  ", "  e.w  ", "  eMw  "},
				new String[] {"   s   ", "   s   ", "   s   ", "   s   ", "   s   ", "   s   "}));

		// ---- Fase "vale muito": casa de farinha, feira, rancho de tropeiro, botica.
		Map<Character, String> novas = new HashMap<>(madeira);
		novas.put('F', "arraial:forno_farinha[facing=south]");
		novas.put('K', "arraial:banca_feira[facing=south]");
		novas.put('G', "arraial:cangalha[facing=south]");
		novas.put('l', "arraial:lampiao[hanging=false,waterlogged=false]");
		novas.put('p', "minecraft:dirt_path");
		// Casa de farinha: an open shed of logs around the farinheiro's oven, lit by a lampião.
		escrever(out.resolve("casa_de_farinha.nbt"), novas, List.of(
				new String[] {"PPPPPPP", "PPPPPPP", "PPPPPPP", "PPPPPPP", "PPPPPPP"},
				new String[] {"L.....L", ".l.....", "...F...", ".......", "LPPPPPL"},
				new String[] {"L.....L", ".......", ".......", ".......", "LPPPPPL"},
				new String[] {"sssssss", "sssssss", "sssssss", "sssssss", "sssssss"}));
		// Feira: a market stall on beaten earth, four posts with lampiões.
		escrever(out.resolve("feira.nbt"), novas, List.of(
				new String[] {"ppppp", "ppppp", "ppppp", "ppppp", "ppppp"},
				new String[] {"f...f", ".....", "..K..", ".....", "f...f"},
				new String[] {"l...l", ".....", ".....", ".....", "l...l"}));
		// Rancho de tropeiro: the troop's shelter, with the cangalha and a hitching rail.
		escrever(out.resolve("rancho_tropeiro.nbt"), novas, List.of(
				new String[] {"PPPPP", "PPPPP", "PPPPP", "PPPPP"},
				new String[] {"L...L", ".l...", "..G..", "LfffL"},
				new String[] {"L...L", ".....", ".....", "L...L"},
				new String[] {"sssss", "sssss", "sssss", "sssss"}));
		// Botica: a small taipa house with the boticário's counter of remedies.
		Map<Character, String> botica = new HashMap<>(casebre);
		botica.put('O', "arraial:balcao_botica[facing=south]");
		escrever(out.resolve("botica.nbt"), botica, List.of(
				new String[] {"CCCCC", "CPPPC", "CPPPC", "CPPPC", "CCCCC"},
				new String[] {"LMdML", "M...M", "M...M", "M.O.M", "LMMML"},
				new String[] {"LMDML", "M...M", ".....", "M...M", "LM.ML"},
				new String[] {"LMMML", "M...M", "M...M", "M.t.M", "LMMML"},
				new String[] {"eMMMw", "e...w", "e...w", "e...w", "eMMMw"},
				new String[] {" eMw ", " e.w ", " e.w ", " e.w ", " eMw "},
				new String[] {"  s  ", "  s  ", "  s  ", "  s  ", "  s  "}));

		// ---- Pescador, caçador, engenheiro, relações públicas.
		Map<Character, String> oficios = new HashMap<>(novas);
		oficios.put('V', "arraial:covo[facing=south]");
		oficios.put('H', "arraial:varal_caca[facing=south]");
		// Pesqueiro: a little open shed with the covo and a lampião.
		escrever(out.resolve("pesqueiro.nbt"), oficios, List.of(
				new String[] {"PPPPP", "PPPPP", "PPPPP", "PPPPP"},
				new String[] {"L...L", ".l...", "..V..", "L...L"},
				new String[] {"L...L", ".....", ".....", "L...L"},
				new String[] {"sssss", "sssss", "sssss", "sssss"}));
		// Rancho de caçador: posts and a roof over the hide rack.
		escrever(out.resolve("rancho_cacador.nbt"), oficios, List.of(
				new String[] {"PPPPP", "PPPPP", "PPPPP", "PPPPP"},
				new String[] {"L...L", ".....", "..H..", "LfffL"},
				new String[] {"L...L", ".....", ".....", "L...L"},
				new String[] {"sssss", "sssss", "sssss", "sssss"}));
		Map<Character, String> casas = new HashMap<>(botica);
		casas.put('Q', "arraial:prancheta[facing=south]");
		casas.put('E', "arraial:escrivaninha[facing=south]");
		// Oficina do engenheiro and Cartório (relações públicas): small taipa houses.
		escrever(out.resolve("oficina_engenheiro.nbt"), casas, List.of(
				new String[] {"CCCCC", "CPPPC", "CPPPC", "CPPPC", "CCCCC"},
				new String[] {"LMdML", "M...M", "M...M", "M.Q.M", "LMMML"},
				new String[] {"LMDML", "M...M", ".....", "M...M", "LM.ML"},
				new String[] {"LMMML", "M...M", "M...M", "M.t.M", "LMMML"},
				new String[] {"eMMMw", "e...w", "e...w", "e...w", "eMMMw"},
				new String[] {" eMw ", " e.w ", " e.w ", " e.w ", " eMw "},
				new String[] {"  s  ", "  s  ", "  s  ", "  s  ", "  s  "}));
		escrever(out.resolve("cartorio.nbt"), casas, List.of(
				new String[] {"CCCCC", "CPPPC", "CPPPC", "CPPPC", "CCCCC"},
				new String[] {"LMdML", "M...M", "M...M", "M.E.M", "LMMML"},
				new String[] {"LMDML", "M...M", ".....", "M...M", "LM.ML"},
				new String[] {"LMMML", "M...M", "M...M", "M.t.M", "LMMML"},
				new String[] {"eMMMw", "e...w", "e...w", "e...w", "eMMMw"},
				new String[] {" eMw ", " e.w ", " e.w ", " e.w ", " eMw "},
				new String[] {"  s  ", "  s  ", "  s  ", "  s  ", "  s  "}));

		// ---- Eras: the Capela (Imperial era) and the Chafariz on the square (Republicana).
		Map<Character, String> capela = new HashMap<>();
		capela.put('C', "minecraft:cobblestone");
		capela.put('M', "minecraft:bricks");
		capela.put('d', "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false]");
		capela.put('D', "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false]");
		capela.put('G', "minecraft:glass_pane");
		capela.put('e', "minecraft:brick_stairs[facing=east,half=bottom,shape=straight]");
		capela.put('w', "minecraft:brick_stairs[facing=west,half=bottom,shape=straight]");
		capela.put('s', "minecraft:brick_slab[type=bottom]");
		capela.put('p', "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]");
		capela.put('A', "minecraft:chiseled_stone_bricks");
		capela.put('c', "minecraft:candle[candles=3,lit=false,waterlogged=false]");
		capela.put('B', "minecraft:bell[attachment=floor,facing=south,powered=false]");
		capela.put('f', "minecraft:oak_fence");
		capela.put('t', "minecraft:wall_torch[facing=north]");
		escrever(out.resolve("capela.nbt"), capela, List.of(
				new String[] {"CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC"},
				new String[] {"MMdMM", "M...M", "Mp.pM", "Mp.pM", "M...M", "M.A.M", "MMMMM"},
				new String[] {"MMDMM", "G...G", "M...M", "G...G", "M...M", "M.c.M", "MMMMM"},
				new String[] {"MMMMM", "M...M", "M...M", "M...M", "M...M", "M.t.M", "MMMMM"},
				new String[] {"eMMMw", "e...w", "e...w", "e...w", "e...w", "e...w", "eMMMw"},
				new String[] {" eMw ", " e.w ", " e.w ", " e.w ", " e.w ", " e.w ", " eMw "},
				new String[] {"  M  ", "  s  ", "  s  ", "  s  ", "  s  ", "  s  ", "  s  "},
				new String[] {"  B  ", "     ", "     ", "     ", "     ", "     ", "     "},
				new String[] {"  f  ", "     ", "     ", "     ", "     ", "     ", "     "},
				new String[] {" fff ", "     ", "     ", "     ", "     ", "     ", "     "},
				new String[] {"  f  ", "     ", "     ", "     ", "     ", "     ", "     "}));

		Map<Character, String> chafariz = Map.of(
				'C', "minecraft:stone_bricks",
				'W', "minecraft:water",
				'c', "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]");
		escrever(out.resolve("chafariz.nbt"), chafariz, List.of(
				new String[] {"CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC"},
				new String[] {"CCCCC", "CWWWC", "CWcWC", "CWWWC", "CCCCC"},
				new String[] {"     ", "     ", "  c  ", "     ", "     "},
				new String[] {"     ", "     ", "  W  ", "     ", "     "}));


		// ---- The fazendas: the engenho (sugar mill shed and a canavial along a water channel) and the cafezal.
		Map<Character, String> engenho = new HashMap<>();
		engenho.put('C', "minecraft:cobblestone");
		engenho.put('D', "minecraft:dirt");
		engenho.put('W', "minecraft:water");
		engenho.put('L', "minecraft:oak_log[axis=y]");
		engenho.put('O', "arraial:moenda[facing=south]");
		engenho.put('c', "minecraft:sugar_cane[age=0]");
		engenho.put('s', "minecraft:oak_slab[type=bottom]");
		engenho.put('b', "minecraft:barrel[facing=up,open=false]");
		Grade ge = new Grade(9, 5, 9);
		ge.caixa(0, 0, 0, 8, 0, 8, 'D');
		ge.caixa(0, 0, 0, 3, 0, 3, 'C');
		ge.caixa(6, 0, 1, 6, 0, 7, 'W');
		ge.caixa(0, 1, 0, 3, 3, 3, '.');
		for (int[] p : new int[][] {{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
			ge.caixa(p[0], 1, p[1], p[0], 3, p[1], 'L');
		}
		ge.p(1, 1, 1, 'O');
		ge.p(2, 1, 2, 'b');
		ge.caixa(4, 1, 0, 8, 2, 8, '.');
		ge.caixa(5, 1, 1, 5, 2, 7, 'c');
		ge.caixa(7, 1, 1, 7, 2, 7, 'c');
		ge.caixa(0, 4, 0, 3, 4, 3, 's');
		escrever(out.resolve("engenho.nbt"), engenho, ge.camadas());

		Map<Character, String> cafezal = new HashMap<>();
		cafezal.put('D', "minecraft:dirt");
		cafezal.put('C', "minecraft:cobblestone");
		cafezal.put('T', "arraial:terreiro");
		cafezal.put('f', "arraial:cafeeiro[age=1]");
		Grade gc = new Grade(9, 2, 7);
		gc.caixa(0, 0, 0, 8, 0, 6, 'D');
		gc.caixa(0, 0, 0, 8, 0, 0, 'C');
		gc.caixa(0, 1, 0, 8, 1, 6, '.');
		gc.p(4, 1, 0, 'T');
		for (int z = 2; z <= 6; z += 2) {
			for (int x = 1; x <= 7; x += 2) {
				gc.p(x, 1, z, 'f');
			}
		}
		escrever(out.resolve("cafezal.nbt"), cafezal, gc.camadas());

		// ---- The Baroque: the carver's workshop and the Igreja Matriz (soapstone, lime, gilded talha, two bell towers).
		casas.put('H', "arraial:bancada_entalhe[facing=south]");
		escrever(out.resolve("oficina_entalhe.nbt"), casas, List.of(
				new String[] {"CCCCC", "CPPPC", "CPPPC", "CPPPC", "CCCCC"},
				new String[] {"LMdML", "M...M", "M...M", "M.H.M", "LMMML"},
				new String[] {"LMDML", "M...M", ".....", "M...M", "LM.ML"},
				new String[] {"LMMML", "M...M", "M...M", "M.t.M", "LMMML"},
				new String[] {"eMMMw", "e...w", "e...w", "e...w", "eMMMw"},
				new String[] {" eMw ", " e.w ", " e.w ", " e.w ", " eMw "},
				new String[] {"  s  ", "  s  ", "  s  ", "  s  ", "  s  "}));

		Map<Character, String> matriz = new HashMap<>();
		matriz.put('S', "arraial:pedra_sabao");
		matriz.put('W', "arraial:cal");
		matriz.put('G', "minecraft:glass_pane");
		matriz.put('d', "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=false]");
		matriz.put('D', "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=false]");
		matriz.put('T', "arraial:talha_dourada");
		matriz.put('B', "minecraft:bell[attachment=floor,facing=south,powered=false]");
		matriz.put('e', "arraial:telha_escada[facing=east,half=bottom,shape=straight,waterlogged=false]");
		matriz.put('w', "arraial:telha_escada[facing=west,half=bottom,shape=straight,waterlogged=false]");
		matriz.put('s', "arraial:telha_laje[type=bottom,waterlogged=false]");
		matriz.put('p', "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]");
		matriz.put('c', "minecraft:candle[candles=3,lit=false,waterlogged=false]");
		Grade gm = new Grade(9, 11, 12);
		gm.caixa(0, 0, 0, 8, 0, 11, 'S');
		gm.parede(0, 1, 0, 8, 5, 11, 'W');
		gm.caixa(1, 1, 1, 7, 5, 10, '.');
		for (int[] q : new int[][] {{0, 0}, {8, 0}, {0, 11}, {8, 11}}) {
			gm.caixa(q[0], 1, q[1], q[0], 5, q[1], 'S');
		}
		gm.parede(0, 5, 0, 8, 5, 11, 'S');
		gm.p(4, 1, 0, 'd');
		gm.p(4, 2, 0, 'D');
		gm.caixa(3, 1, 0, 3, 3, 0, 'S');
		gm.caixa(5, 1, 0, 5, 3, 0, 'S');
		gm.p(4, 3, 0, 'S');
		gm.p(4, 4, 0, 'G');
		for (int z : new int[] {3, 6, 9}) {
			gm.caixa(0, 3, z, 0, 4, z, 'G');
			gm.caixa(8, 3, z, 8, 4, z, 'G');
		}
		for (int z : new int[] {3, 5, 7}) {
			for (int x : new int[] {2, 3, 5, 6}) {
				gm.p(x, 1, z, 'p');
			}
		}
		gm.caixa(3, 1, 10, 5, 1, 10, 'T');
		gm.p(3, 2, 10, 'c');
		gm.p(4, 2, 10, 'T');
		gm.p(5, 2, 10, 'c');
		gm.caixa(3, 3, 10, 5, 3, 10, 'T');
		gm.p(4, 4, 10, 'T');
		for (int r = 0; r < 4; r++) {
			gm.caixa(r, 6 + r, 0, r, 6 + r, 11, 'e');
			gm.caixa(8 - r, 6 + r, 0, 8 - r, 6 + r, 11, 'w');
			gm.caixa(r + 1, 6 + r, 1, 7 - r, 6 + r, 10, '.');
			gm.caixa(r + 1, 6 + r, 0, 7 - r, 6 + r, 0, 'W');
			gm.caixa(r + 1, 6 + r, 11, 7 - r, 6 + r, 11, 'W');
		}
		gm.caixa(4, 10, 0, 4, 10, 11, 's');
		for (int tx : new int[] {0, 7}) {
			gm.caixa(tx, 1, 0, tx + 1, 7, 1, 'W');
			gm.caixa(tx, 1, 0, tx, 7, 0, 'S');
			gm.caixa(tx + 1, 1, 0, tx + 1, 7, 0, 'S');
			gm.caixa(tx, 8, 0, tx + 1, 8, 1, 'S');
			gm.p(tx == 0 ? 1 : 7, 8, 0, 'B');
			gm.caixa(tx, 9, 0, tx + 1, 9, 1, 'S');
			gm.caixa(tx, 10, 0, tx + 1, 10, 1, ' ');
		}
		escrever(out.resolve("matriz.nbt"), matriz, gm.camadas());

		// ---- The Spanish missions: an adobe church with a bell-gable (espadaña) and a cross on top.
		Map<Character, String> missao = new HashMap<>();
		missao.put('A', "arraial:adobe");
		missao.put('C', "minecraft:cobblestone");
		missao.put('G', "minecraft:glass_pane");
		missao.put('d', "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false]");
		missao.put('D', "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false]");
		missao.put('B', "minecraft:bell[attachment=floor,facing=south,powered=false]");
		missao.put('e', "arraial:telha_escada[facing=east,half=bottom,shape=straight,waterlogged=false]");
		missao.put('w', "arraial:telha_escada[facing=west,half=bottom,shape=straight,waterlogged=false]");
		missao.put('s', "arraial:telha_laje[type=bottom,waterlogged=false]");
		missao.put('p', "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]");
		missao.put('P', "minecraft:spruce_planks");
		missao.put('c', "minecraft:candle[candles=1,lit=false,waterlogged=false]");
		missao.put('f', "minecraft:oak_fence");
		Grade gs = new Grade(7, 14, 11);
		gs.caixa(0, 0, 0, 6, 0, 10, 'C');
		gs.parede(0, 1, 0, 6, 4, 10, 'A');
		gs.caixa(1, 1, 1, 5, 4, 9, '.');
		gs.p(3, 1, 0, 'd');
		gs.p(3, 2, 0, 'D');
		gs.p(3, 4, 0, 'G');
		for (int z : new int[] {3, 7}) {
			gs.p(0, 3, z, 'G');
			gs.p(6, 3, z, 'G');
		}
		for (int z : new int[] {3, 5}) {
			for (int x : new int[] {1, 2, 4, 5}) {
				gs.p(x, 1, z, 'p');
			}
		}
		gs.caixa(2, 1, 9, 4, 1, 9, 'P');
		gs.p(2, 2, 9, 'c');
		gs.p(4, 2, 9, 'c');
		for (int r = 0; r < 3; r++) {
			gs.caixa(r, 5 + r, 1, r, 5 + r, 10, 'e');
			gs.caixa(6 - r, 5 + r, 1, 6 - r, 5 + r, 10, 'w');
			gs.caixa(r + 1, 5 + r, 1, 5 - r, 5 + r, 9, '.');
			gs.caixa(r + 1, 5 + r, 10, 5 - r, 5 + r, 10, 'A');
		}
		gs.caixa(3, 8, 1, 3, 8, 10, 's');
		gs.caixa(0, 5, 0, 6, 8, 0, 'A');
		gs.caixa(1, 9, 0, 5, 9, 0, 'A');
		gs.caixa(2, 10, 0, 4, 10, 0, 'A');
		gs.p(1, 7, 0, 'B');
		gs.p(5, 7, 0, 'B');
		gs.caixa(3, 11, 0, 3, 13, 0, 'f');
		gs.p(2, 12, 0, 'f');
		gs.p(4, 12, 0, 'f');
		escrever(out.resolve("missao.nbt"), missao, gs.camadas());

		// ---- Rios: the trapiche. A shed on the bank (z 0-2) and a deck out over the water (z 3-9);
		// ' ' leaves the river alone around the deck. The amarra (barqueiro) stands near the end.
		Map<Character, String> trapiche = new HashMap<>();
		trapiche.put('P', "minecraft:oak_planks");
		trapiche.put('L', "minecraft:oak_log[axis=y]");
		trapiche.put('F', "minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]");
		trapiche.put('s', "minecraft:oak_slab[type=bottom,waterlogged=false]");
		trapiche.put('b', "minecraft:barrel[facing=up,open=false]");
		trapiche.put('A', "arraial:amarra[facing=south]");
		trapiche.put('l', "arraial:lampiao[hanging=false,waterlogged=false]");
		escrever(out.resolve("trapiche.nbt"), trapiche, List.of(
				new String[] {"PPPPP", "PPPPP", "PPPPP", " PPP ", " PPP ", " PPP ", " PPP ", " PPP ", " PPP ", " PPP "},
				new String[] {"L...L", "b....", "L...L", " F.F ", " F.F ", " F.F ", " F.F ", " F.F ", " A.F ", " ... "},
				new String[] {"L...L", ".....", "L...L", "     ", "     ", "     ", "     ", "     ", "   l ", "     "},
				new String[] {"sssss", "sssss", "sssss", "     ", "     ", "     ", "     ", "     ", "     ", "     "}));

		// ---- Rios: the porto. A stone warehouse on the shore (z 0-4) and a long deck out to deep
		// water (z 5-15) with the guindaste (estivador) near the end; the ship moors alongside.
		Map<Character, String> porto = new HashMap<>();
		porto.put('C', "minecraft:cobblestone");
		porto.put('P', "minecraft:oak_planks");
		porto.put('L', "minecraft:oak_log[axis=y]");
		porto.put('F', "minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]");
		porto.put('s', "minecraft:oak_slab[type=bottom,waterlogged=false]");
		porto.put('b', "minecraft:barrel[facing=up,open=false]");
		porto.put('G', "arraial:guindaste[facing=south]");
		porto.put('l', "arraial:lampiao[hanging=false,waterlogged=false]");
		String[] deque0 = new String[11], deque1 = new String[11], nada = new String[11];
		for (int i = 0; i < 11; i++) {
			deque0[i] = " PPPPPPP ";
			deque1[i] = i >= 4 && i <= 6 ? " F...... " : " F.....F ";
			nada[i] = "         ";
		}
		deque1[9] = " F..G..F ";
		deque1[10] = " l.....l ";
		escrever(out.resolve("porto.nbt"), porto, List.of(
				concat(new String[] {"CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC"}, deque0),
				concat(new String[] {"LPP...PPL", "Pb.....bP", "P.......P", "Pb.....bP", "LPP...PPL"}, deque1),
				concat(new String[] {"LPP...PPL", "P.......P", ".........", "P.......P", "LPP...PPL"}, nada),
				concat(new String[] {"LPPPPPPPL", "P.......P", "P.......P", "P.......P", "LPPPPPPPL"}, nada),
				concat(new String[] {"sssssssss", "sssssssss", "sssssssss", "sssssssss", "sssssssss"}, nada)));

		// ---- The navio mercante (placed and removed by Navio, never built): a two-masted ship,
		// keel two blocks under the surface, a dry hold, a stern cabin and white sails.
		Map<Character, String> navio = new HashMap<>();
		navio.put('H', "minecraft:spruce_planks");
		navio.put('T', "minecraft:spruce_log[axis=y]");
		navio.put('W', "minecraft:white_wool");
		navio.put('R', "minecraft:red_wool");
		navio.put('f', "minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]");
		navio.put('s', "minecraft:spruce_slab[type=bottom,waterlogged=false]");
		navio.put('B', "minecraft:barrel[facing=up,open=false]");
		String[] casco = {" HHH ", "H...H", "H...H", "H...H", "H...H", "H...H", "H...H", "H...H", "H...H", "H...H", "H...H", " H.H ", "  H  "};
		String[] vela = {"     ", "     ", "     ", "     ", "WWTWW", "     ", "     ", "     ", "     ", "WWTWW", "     ", "     ", "     "};
		String[] mastro = {"     ", "     ", "     ", "     ", "  T  ", "     ", "     ", "     ", "     ", "  T  ", "     ", "     ", "     "};
		escrever(out.resolve("navio.nbt"), navio, List.of(
				new String[] {"     ", " HHH ", " HHH ", " HHH ", " HHH ", " HHH ", " HHH ", " HHH ", " HHH ", " HHH ", " HHH ", " HHH ", "     "},
				casco,
				casco,
				new String[] {" HHH ", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", " HHH ", "  H  "},
				new String[] {" fff ", "HHHHH", "H...H", "f...f", "f.T.f", "f...f", "fB.Bf", "f...f", "fB..f", "f.T.f", "f...f", " f.f ", "  f  "},
				new String[] {"     ", "HHHHH", "H...H", "     ", "  T  ", "     ", "     ", "     ", "     ", "  T  ", "     ", "     ", "     "},
				new String[] {"     ", "sssss", "sssss", "     ", "  T  ", "     ", "     ", "     ", "     ", "  T  ", "     ", "     ", "     "},
				vela, vela, vela, vela,
				mastro,
				new String[] {"     ", "     ", "     ", "     ", "  R  ", "     ", "     ", "     ", "     ", "  R  ", "     ", "     ", "     "}));

		// ---- Rios: the moinho d'água. A stone-and-plank mill on the bank with the mó (moleiro)
		// inside; the axle goes through the back wall to the wheel standing over the water.
		Map<Character, String> moinho = new HashMap<>();
		moinho.put('C', "minecraft:cobblestone");
		moinho.put('P', "minecraft:oak_planks");
		moinho.put('L', "minecraft:oak_log[axis=z]");
		moinho.put('s', "minecraft:oak_slab[type=bottom,waterlogged=false]");
		moinho.put('b', "minecraft:barrel[facing=up,open=false]");
		moinho.put('M', "arraial:mo[facing=south]");
		moinho.put('R', "arraial:roda_dagua[facing=south,fase=0]");
		moinho.put('l', "arraial:lampiao[hanging=false,waterlogged=false]");
		escrever(out.resolve("moinho.nbt"), moinho, List.of(
				new String[] {"CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "   R   "},
				new String[] {"CPP.PPC", "Pb...bP", "P..M..P", "P.....P", "Pb...lP", "CPPLPPC", "       "},
				new String[] {"CPP.PPC", "P.....P", ".......", "P.....P", "P.....P", "CPPPPPC", "       "},
				new String[] {"CPPPPPC", "P.....P", "P.....P", "P.....P", "P.....P", "CPPPPPC", "       "},
				new String[] {"sssssss", "sssssss", "sssssss", "sssssss", "sssssss", "sssssss", "       "}));

		// ---- Rios: the monjolo. A little roofed deck half over the water; the beam's trough fills
		// from the stream and the pestle pounds in the pilão under the roof.
		Map<Character, String> monjolo = new HashMap<>();
		monjolo.put('P', "minecraft:oak_planks");
		monjolo.put('L', "minecraft:oak_log[axis=y]");
		monjolo.put('s', "minecraft:oak_slab[type=bottom,waterlogged=false]");
		monjolo.put('M', "arraial:monjolo[batendo=false,facing=north]");
		escrever(out.resolve("monjolo.nbt"), monjolo, List.of(
				new String[] {" PPP ", " PPP ", " PPP ", " PPP "},
				new String[] {" L.L ", " ... ", " .M. ", " L.L "},
				new String[] {" L.L ", " ... ", " ... ", " L.L "},
				new String[] {" sss ", " sss ", " sss ", " sss "}));

		System.out.println("plantas geradas em " + out.toAbsolutePath());
	}

	static String[] concat(String[] a, String[] b) {
		String[] r = java.util.Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}

	/** A 3D grid of legend characters, filled by boxes, for the bigger buildings. ' ' = not part of the plan. */
	static final class Grade {
		final char[][][] c; // [y][z][x]

		Grade(int sx, int sy, int sz) {
			c = new char[sy][sz][sx];
			for (char[][] camada : c) {
				for (char[] linha : camada) {
					java.util.Arrays.fill(linha, ' ');
				}
			}
		}

		void p(int x, int y, int z, char ch) {
			c[y][z][x] = ch;
		}

		void caixa(int x0, int y0, int z0, int x1, int y1, int z1, char ch) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						c[y][z][x] = ch;
					}
				}
			}
		}

		/** The outer ring of a box (walls), layer by layer. */
		void parede(int x0, int y0, int z0, int x1, int y1, int z1, char ch) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						if (x == x0 || x == x1 || z == z0 || z == z1) {
							c[y][z][x] = ch;
						}
					}
				}
			}
		}

		List<String[]> camadas() {
			List<String[]> l = new ArrayList<>();
			for (char[][] camada : c) {
				String[] linhas = new String[camada.length];
				for (int z = 0; z < camada.length; z++) {
					linhas[z] = new String(camada[z]);
				}
				l.add(linhas);
			}
			return l;
		}
	}

	static void escrever(Path arquivo, Map<Character, String> legenda, List<String[]> camadas) throws IOException {
		int sy = camadas.size(), sz = camadas.get(0).length, sx = camadas.get(0)[0].length();
		List<String> paleta = new ArrayList<>();
		List<int[]> blocos = new ArrayList<>(); // x, y, z, state
		for (int y = 0; y < sy; y++) {
			String[] camada = camadas.get(y);
			for (int z = 0; z < sz; z++) {
				String linha = camada[z];
				if (linha.length() != sx) {
					throw new IllegalArgumentException(arquivo + " y=" + y + " z=" + z + ": linha com tamanho errado");
				}
				for (int x = 0; x < sx; x++) {
					char c = linha.charAt(x);
					if (c == ' ') {
						continue;
					}
					String estado = c == '.' ? "minecraft:air" : legenda.get(c);
					if (estado == null) {
						throw new IllegalArgumentException("sem legenda para '" + c + "'");
					}
					int idx = paleta.indexOf(estado);
					if (idx < 0) {
						paleta.add(estado);
						idx = paleta.size() - 1;
					}
					blocos.add(new int[] {x, y, z, idx});
				}
			}
		}

		try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(arquivo))))) {
			o.writeByte(10); o.writeUTF(""); // root compound
			o.writeByte(3); o.writeUTF("DataVersion"); o.writeInt(DATA_VERSION);
			listaInts(o, "size", sx, sy, sz);

			o.writeByte(9); o.writeUTF("palette"); o.writeByte(10); o.writeInt(paleta.size());
			for (String estado : paleta) {
				String nome = estado, props = null;
				int i = estado.indexOf('[');
				if (i >= 0) {
					nome = estado.substring(0, i);
					props = estado.substring(i + 1, estado.length() - 1);
				}
				o.writeByte(8); o.writeUTF("id"); o.writeUTF(nome);
				if (props != null) {
					o.writeByte(10); o.writeUTF("properties");
					for (String kv : props.split(",")) {
						String[] p = kv.split("=");
						o.writeByte(8); o.writeUTF(p[0]); o.writeUTF(p[1]);
					}
					o.writeByte(0);
				}
				o.writeByte(0);
			}

			o.writeByte(9); o.writeUTF("blocks"); o.writeByte(10); o.writeInt(blocos.size());
			for (int[] b : blocos) {
				listaInts(o, "pos", b[0], b[1], b[2]);
				o.writeByte(3); o.writeUTF("state"); o.writeInt(b[3]);
				o.writeByte(0);
			}

			o.writeByte(9); o.writeUTF("entities"); o.writeByte(0); o.writeInt(0);
			o.writeByte(0); // end root
		}
		System.out.printf("%s: %dx%dx%d, %d blocos, %d estados%n", arquivo.getFileName(), sx, sy, sz, blocos.size(), paleta.size());
	}

	static void listaInts(DataOutputStream o, String nome, int... valores) throws IOException {
		o.writeByte(9); o.writeUTF(nome); o.writeByte(3); o.writeInt(valores.length);
		for (int v : valores) {
			o.writeInt(v);
		}
	}
}
