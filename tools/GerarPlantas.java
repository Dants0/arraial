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

		System.out.println("plantas geradas em " + out.toAbsolutePath());
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
