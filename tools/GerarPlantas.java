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
		escrever(out.resolve("casebre.nbt"), casebre, List.of(
				// y0: floor, cobblestone foundation around planks
				new String[] {"CCCCC", "CPPPC", "CPPPC", "CPPPC", "CCCCC"},
				// y1: walls, door at the front
				new String[] {"LMdML", "M...M", "M...M", "M...M", "LMMML"},
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
