package com.guilhermegoes.arraial.rede;

import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.Catalogo;
import com.guilhermegoes.arraial.obra.LivroDeProjetosItem;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * What the Livro de Projetos screen shows: every project of the catalog with its category, size, a
 * top-down plan (map colours), the trades it brings, the materials (and how many the armazém holds),
 * how many the village already has; plus the village, the prefeito's request, the project the book
 * has chosen and the Marco da Obra it was opened on (if any).
 */
public final class LivroDados {
	/** Categories, in the order the screen lists them. */
	public static final List<String> CATEGORIAS = List.of("moradia", "producao", "comercio", "civico", "fe", "defesa", "outros");

	private static final Map<String, String> CATEGORIA = Map.ofEntries(
			Map.entry("casebre", "moradia"), Map.entry("rancho", "moradia"),
			Map.entry("serraria", "producao"), Map.entry("roca", "producao"), Map.entry("olaria", "producao"), Map.entry("curral", "producao"),
			Map.entry("barreira", "producao"), Map.entry("pedreira", "producao"), Map.entry("casa_de_farinha", "producao"),
			Map.entry("pesqueiro", "producao"), Map.entry("rancho_cacador", "producao"), Map.entry("engenho", "producao"),
			Map.entry("cafezal", "producao"), Map.entry("oficina_entalhe", "producao"), Map.entry("moinho", "producao"), Map.entry("monjolo", "producao"),
			Map.entry("feira", "comercio"), Map.entry("rancho_tropeiro", "comercio"), Map.entry("trapiche", "comercio"),
			Map.entry("porto", "comercio"), Map.entry("estacao", "comercio"),
			Map.entry("poco", "civico"), Map.entry("prefeitura", "civico"), Map.entry("botica", "civico"), Map.entry("cartorio", "civico"),
			Map.entry("oficina_engenheiro", "civico"), Map.entry("chafariz", "civico"),
			Map.entry("capela", "fe"), Map.entry("matriz", "fe"), Map.entry("missao", "fe"),
			Map.entry("guarita", "defesa"), Map.entry("muralha", "defesa"));

	public static String categoria(String id) {
		return CATEGORIA.getOrDefault(id, "outros");
	}

	public record Material(String item, int quantidade, int noArmazem) {
	}

	public record Projeto(String id, String categoria, int largura, int altura, int fundo, int naVila, List<String> oficios,
			List<Material> materiais, int plantaLargura, int plantaFundo, int[] planta) {
	}

	public String vila = "";
	public Component pedido = Component.empty();
	public String selecionado = "";
	/** The Marco da Obra the book was used on, or {@link Long#MIN_VALUE}. */
	public long marco = Long.MIN_VALUE;
	public boolean temArmazem;
	public final List<Projeto> projetos = new ArrayList<>();

	// ================================================================ building it (server)

	public static LivroDados montar(ServerPlayer player, ItemStack livro, @Nullable BlockPos marco) {
		ServerLevel level = player.level();
		LivroDados d = new LivroDados();
		d.selecionado = LivroDeProjetosItem.selecionado(livro).map(Catalogo.Projeto::id).orElse("");
		d.marco = marco == null ? Long.MIN_VALUE : marco.asLong();
		BlockPos aqui = marco != null ? marco : player.blockPosition();
		BlockPos vila = Vilas.maisProxima(level, aqui);
		Map<Item, Integer> estoque = Map.of();
		if (vila != null && vila.closerThan(aqui, Vilas.RAIO + 16) && level.getBlockEntity(vila) instanceof ArraialBlockEntity a) {
			d.vila = a.nomeCompleto();
			d.pedido = a.pedido();
			var armazens = Vilas.armazens(level, vila);
			d.temArmazem = !armazens.isEmpty();
			estoque = Vilas.inventario(armazens);
		} else {
			vila = null;
		}
		for (Catalogo.Projeto p : Catalogo.PROJETOS) {
			PlanoDeObra plano = PlanoDeObra.criar(level, p, aqui, Direction.SOUTH).orElse(null);
			if (plano == null) {
				continue;
			}
			BoundingBox area = plano.area();
			List<Material> mats = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : plano.materiais(0).entrySet()) {
				mats.add(new Material(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue(), estoque.getOrDefault(e.getKey(), 0)));
			}
			mats.sort((x, y) -> Integer.compare(y.quantidade(), x.quantidade()));
			Set<String> oficios = new LinkedHashSet<>();
			int w = area.getXSpan(), f = area.getZSpan();
			// A floor plan: the ground-floor walls, doors and workstations (dark) over the floor (light).
			int[] planta = new int[w * f];
			int[] piso = new int[w * f];
			for (PlanoDeObra.Etapa e : plano.etapas()) {
				if (e.limpeza()) {
					continue;
				}
				Profissao quem = Profissao.doPosto(e.state().getBlock());
				if (quem != null) {
					oficios.add(quem.id());
				}
				int i = (e.pos().getZ() - area.minZ()) * w + (e.pos().getX() - area.minX());
				int camada = e.pos().getY() - area.minY();
				MapColor cor = e.state().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
				if (i < 0 || i >= planta.length || cor == MapColor.NONE) {
					continue;
				}
				if (camada == 0) {
					piso[i] = cor.calculateARGBColor(MapColor.Brightness.HIGH);
				} else if (camada == 1) {
					planta[i] = cor.calculateARGBColor(MapColor.Brightness.LOW);
				}
			}
			for (int i = 0; i < planta.length; i++) {
				if (planta[i] == 0) {
					planta[i] = piso[i];
				}
			}
			int naVila = vila == null ? 0 : com.guilhermegoes.arraial.vila.Prefeitura.contarObras(level, vila, p.id());
			d.projetos.add(new Projeto(p.id(), categoria(p.id()), w, area.getYSpan(), f, naVila, List.copyOf(oficios), mats, w, f, planta));
		}
		return d;
	}

	// ================================================================ the wire

	void escrever(RegistryFriendlyByteBuf b) {
		b.writeUtf(vila);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, pedido);
		b.writeUtf(selecionado);
		b.writeLong(marco);
		b.writeBoolean(temArmazem);
		b.writeVarInt(projetos.size());
		for (Projeto p : projetos) {
			b.writeUtf(p.id());
			b.writeUtf(p.categoria());
			b.writeVarInt(p.largura());
			b.writeVarInt(p.altura());
			b.writeVarInt(p.fundo());
			b.writeVarInt(p.naVila());
			b.writeVarInt(p.oficios().size());
			p.oficios().forEach(b::writeUtf);
			b.writeVarInt(p.materiais().size());
			for (Material m : p.materiais()) {
				b.writeUtf(m.item());
				b.writeVarInt(m.quantidade());
				b.writeVarInt(m.noArmazem());
			}
			b.writeVarInt(p.plantaLargura());
			b.writeVarInt(p.plantaFundo());
			for (int c : p.planta()) {
				b.writeInt(c);
			}
		}
	}

	static LivroDados ler(RegistryFriendlyByteBuf b) {
		LivroDados d = new LivroDados();
		d.vila = b.readUtf();
		d.pedido = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b);
		d.selecionado = b.readUtf();
		d.marco = b.readLong();
		d.temArmazem = b.readBoolean();
		int n = b.readVarInt();
		for (int i = 0; i < n; i++) {
			String id = b.readUtf(), cat = b.readUtf();
			int w = b.readVarInt(), h = b.readVarInt(), f = b.readVarInt(), naVila = b.readVarInt();
			List<String> oficios = new ArrayList<>();
			for (int k = b.readVarInt(); k > 0; k--) {
				oficios.add(b.readUtf());
			}
			List<Material> mats = new ArrayList<>();
			for (int k = b.readVarInt(); k > 0; k--) {
				mats.add(new Material(b.readUtf(), b.readVarInt(), b.readVarInt()));
			}
			int pw = b.readVarInt(), pf = b.readVarInt();
			int[] planta = new int[pw * pf];
			for (int k = 0; k < planta.length; k++) {
				planta[k] = b.readInt();
			}
			d.projetos.add(new Projeto(id, cat, w, h, f, naVila, oficios, mats, pw, pf, planta));
		}
		return d;
	}

	/** The item behind a registry id (for the screen). */
	public static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
	}
}
