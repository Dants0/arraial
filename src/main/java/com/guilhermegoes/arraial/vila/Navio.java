package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.Catalogo;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The navio mercante. An arraial on the coast with a finished porto is visited every few days by a
 * ship from overseas: it moors alongside the deck (real blocks, taken away again when it sails), its
 * captain walks the quay selling sal, ferro and chita to the player and buying the colony's goods,
 * and the estivador carries lots between the armazém and the guindaste: each trip ships açúcar,
 * café, rapadura, couro or charque (paid in réis) and brings back salt, iron and cloth. At dusk the
 * ship sails, and the Crônica keeps the account.
 */
public final class Navio {
	/** The captain's "origin" (he is a mascate from overseas, not from a Registro village). */
	public static final String ORIGEM = "@navio";
	private static final long DIA = 24000L;
	/** Days between visits, at least. */
	private static final int INTERVALO = 3;
	/** Round trips of the estivador per visit. */
	public static final int LOTES = 4;
	private static final Catalogo.Projeto CASCO = new Catalogo.Projeto("navio");
	private static final String[] NOMES = {"Nossa Senhora da Conceição", "São José", "Bom Jesus dos Navegantes", "Flor do Mar",
			"Estrela do Norte", "Santa Rita", "Princesa do Atlântico", "Boa Esperança"};

	/** What the ship buys (the colony's exports), with its premium over the base price. */
	public static final List<Item> EXPORTA = List.of(Items.SUGAR, ModItems.CAFE_TORRADO, ModItems.RAPADURA, Items.LEATHER, ModItems.CHARQUE);
	/** What the ship brings. */
	public static final List<Item> IMPORTA = List.of(ModItems.SAL, Items.IRON_INGOT, ModItems.CHITA);
	private static final double PREMIO = 1.3;

	private Navio() {
	}

	/** "minX,minY,minZ,maxX,maxY,maxZ" of the moored ship's blocks ("" when offshore or gone). */
	public static String area(ArraialBlockEntity a) {
		return a.navioArea;
	}

	public static int agua(ArraialBlockEntity a) {
		return a.navioAgua;
	}

	/** Réis the arraial made selling to the ship in port, and spent buying from it. */
	public static int vendeu(ArraialBlockEntity a) {
		return a.navioVendeu;
	}

	public static int comprou(ArraialBlockEntity a) {
		return a.navioComprou;
	}

	/** Round trips the estivador has made for the ship in port. */
	public static int lotes(ArraialBlockEntity a) {
		return a.navioLotes;
	}

	/** Is the ship in port right now? */
	public static boolean atracado(ArraialBlockEntity a) {
		return a.navioAte > 0;
	}

	// ================================================================ arrival and departure

	/** Each morning: a ship may come in. */
	static void diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		if (atracado(a) || moradores.size() < 4 || Vilas.dia(level) - a.navioUltimo < INTERVALO || level.getRandom().nextFloat() > 0.5F) {
			return;
		}
		chegar(level, a);
	}

	/** The ship comes in now (if the arraial has a porto); returns whether it did. */
	public static boolean chegar(ServerLevel level, ArraialBlockEntity a) {
		if (atracado(a) || a.abandonada) {
			return false;
		}
		CanteiroBlockEntity porto = porto(level, a.getBlockPos());
		if (porto == null) {
			return false;
		}
		String nome = NOMES[level.getRandom().nextInt(NOMES.length)];
		a.navioNome = nome;
		a.navioAte = level.getGameTime() + DIA / 2;
		a.navioUltimo = Vilas.dia(level);
		a.navioLotes = 0;
		a.navioVendeu = 0;
		a.navioComprou = 0;
		a.navioArea = "";
		BlockPos cais = cais(porto);
		atracar(level, a, porto);
		// The captain steps on the quay with his goods and a purse to buy with.
		MoradorEntity capitao = MoradorEntity.criar(level, cais.above(), Profissao.NENHUMA);
		if (capitao != null) {
			capitao.virarMascate(ORIGEM, cais, a.navioAte, 160 + level.getRandom().nextInt(80));
			capitao.guardar(ModItems.SAL, 24 + level.getRandom().nextInt(16));
			capitao.guardar(Items.IRON_INGOT, 8 + level.getRandom().nextInt(8));
			capitao.guardar(ModItems.CHITA, 12 + level.getRandom().nextInt(12));
		}
		level.playSound(null, cais, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.5F, 0.7F);
		Prefeitura.avisar(level, a, Component.literal("⛵ ").append(Component.translatable("arraial.navio.chegou", nome, a.nome(),
				capitao == null ? "?" : capitao.nome())).withStyle(ChatFormatting.AQUA));
		Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.navio_chegou", nome));
		a.setChanged();
		return true;
	}

	/** Every few seconds: time to sail? */
	static void tick(ServerLevel level, ArraialBlockEntity a) {
		if (atracado(a) && level.getGameTime() >= a.navioAte) {
			partir(level, a);
		}
	}

	/** The ship sails: the blocks go, the captain boards, the account goes into the Crônica. */
	public static void partir(ServerLevel level, ArraialBlockEntity a) {
		if (!atracado(a)) {
			return;
		}
		desatracar(level, a);
		for (MoradorEntity m : level.getEntitiesOfClass(MoradorEntity.class, new AABB(a.getBlockPos()).inflate(Vilas.RAIO * 2),
				m -> m.profissao() == Profissao.MASCATE && ORIGEM.equals(m.mascateOrigem()))) {
			m.esvaziarCarga();
			m.discard();
		}
		Prefeitura.avisar(level, a, Component.literal("⛵ ").append(Component.translatable("arraial.navio.partiu", a.navioNome,
				a.navioVendeu, a.navioComprou)).withStyle(ChatFormatting.AQUA));
		Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.navio_partiu", a.navioNome, a.navioVendeu, a.navioComprou));
		a.navioAte = 0;
		a.setChanged();
	}

	/** The finished porto of the arraial, if it has one. */
	public static @Nullable CanteiroBlockEntity porto(ServerLevel level, BlockPos vila) {
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO + 8) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c && c.concluida()
					&& c.projeto().map(pr -> pr.id().equals("porto")).orElse(false)) {
				return c;
			}
		}
		return null;
	}

	/** The end of the deck, where the captain walks and the estivador hands the lots over. */
	public static BlockPos cais(CanteiroBlockEntity porto) {
		return porto.getBlockPos().relative(porto.frente(), 13);
	}

	// ================================================================ the ship's blocks

	/** Puts the ship alongside the deck, in clear deep water; if there is none, it anchors offshore. */
	private static void atracar(ServerLevel level, ArraialBlockEntity a, CanteiroBlockEntity porto) {
		Direction frente = porto.frente();
		BlockPos marco = porto.getBlockPos();
		for (Direction lado : new Direction[] {frente.getClockWise(), frente.getCounterClockWise()}) {
			for (int ao = 4; ao <= 8; ao++) {
				for (int largo = 7; largo <= 9; largo++) {
					BlockPos coluna = marco.relative(frente, ao).relative(lado, largo);
					Integer agua = superficie(level, coluna);
					if (agua == null) {
						continue;
					}
					BlockPos casco = new BlockPos(coluna.getX(), agua - 1, coluna.getZ());
					PlanoDeObra plano = PlanoDeObra.criar(level, CASCO, casco, frente).orElse(null);
					if (plano != null && cabe(level, plano, agua)) {
						for (PlanoDeObra.Etapa e : plano.etapas()) {
							level.setBlock(e.pos(), e.state(), Block.UPDATE_CLIENTS);
						}
						BoundingBox b = plano.area();
						a.navioArea = b.minX() + "," + b.minY() + "," + b.minZ() + "," + b.maxX() + "," + b.maxY() + "," + b.maxZ();
						a.navioAgua = agua;
						return;
					}
				}
			}
		}
	}

	/** The surface of open water at this column (y of the top water block), or null. */
	private static @Nullable Integer superficie(ServerLevel level, BlockPos coluna) {
		if (!level.isLoaded(coluna)) {
			return null;
		}
		int topo = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, coluna.getX(), coluna.getZ()) - 1;
		BlockPos p = new BlockPos(coluna.getX(), topo, coluna.getZ());
		return level.getFluidState(p).is(FluidTags.WATER) ? topo : null;
	}

	/** Every block of the ship falls on water (at or below the surface) or open air (above it). */
	private static boolean cabe(ServerLevel level, PlanoDeObra plano, int agua) {
		BoundingBox b = plano.area();
		for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
			if (!level.isLoaded(p)) {
				return false;
			}
			BlockState s = level.getBlockState(p);
			if (p.getY() <= agua ? !(s.is(Blocks.WATER) && s.getFluidState().isSource()) : !s.isAir()) {
				return false;
			}
		}
		return true;
	}

	/** Takes the ship away: water back under the surface, air above. */
	private static void desatracar(ServerLevel level, ArraialBlockEntity a) {
		if (a.navioArea.isEmpty()) {
			return;
		}
		String[] v = a.navioArea.split(",");
		if (v.length != 6) {
			a.navioArea = "";
			return;
		}
		int[] n = new int[6];
		for (int i = 0; i < 6; i++) {
			n[i] = Integer.parseInt(v[i].trim());
		}
		for (BlockPos p : BlockPos.betweenClosed(n[0], n[1], n[2], n[3], n[4], n[5])) {
			BlockState s = level.getBlockState(p);
			if (p.getY() <= a.navioAgua) {
				if (!s.is(Blocks.WATER)) {
					level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			} else if (!s.isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		a.navioArea = "";
	}

	/** Is there a ship's block at this position (the area of the moored ship)? */
	public static boolean noNavio(ArraialBlockEntity a, BlockPos p) {
		if (a.navioArea.isEmpty()) {
			return false;
		}
		String[] v = a.navioArea.split(",");
		return v.length == 6 && BoundingBox.fromCorners(new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])),
				new BlockPos(Integer.parseInt(v[3]), Integer.parseInt(v[4]), Integer.parseInt(v[5]))).isInside(p);
	}

	// ================================================================ the estivador's lots

	/** One lot goes aboard (from what the estivador carries): paid at the ship's price. */
	public static int embarcar(ServerLevel level, ArraialBlockEntity a, Map<Item, Integer> carga) {
		double valor = 0;
		for (Map.Entry<Item, Integer> e : carga.entrySet()) {
			valor += e.getValue() * preco(a, e.getKey()) * PREMIO;
		}
		int pago = (int) Math.floor(valor);
		if (pago > 0) {
			a.mudarTesouro(pago);
			a.navioVendeu += pago;
		}
		a.setChanged();
		return pago;
	}

	/** What the arraial buys this lot, within a third of the treasury: salt, iron and cloth. */
	public static Map<Item, Integer> desembarcar(ServerLevel level, ArraialBlockEntity a) {
		Map<Item, Integer> compra = new LinkedHashMap<>();
		int verba = a.tesouro() / 3;
		int[] quer = {12, 4, 8};
		for (int i = 0; i < IMPORTA.size(); i++) {
			Item it = IMPORTA.get(i);
			int tem = Vilas.contar(Vilas.armazens(level, a.getBlockPos()), it);
			int qtd = Math.max(0, Math.min(quer[i], 48 - tem));
			double unidade = Math.max(0.5, Feira.base(it));
			while (qtd > 0 && unidade * qtd > verba) {
				qtd--;
			}
			if (qtd > 0) {
				int custo = (int) Math.ceil(unidade * qtd);
				verba -= custo;
				a.mudarTesouro(-custo);
				a.navioComprou += custo;
				compra.put(it, qtd);
			}
		}
		a.navioLotes++;
		a.setChanged();
		return compra;
	}

	/** The surplus the estivador takes aboard: exportable goods beyond what the arraial keeps. */
	public static Map<Item, Integer> lote(ServerLevel level, ArraialBlockEntity a, int pop) {
		Map<Item, Integer> lote = new LinkedHashMap<>();
		var armazens = Vilas.armazens(level, a.getBlockPos());
		int total = 0;
		for (Item it : EXPORTA) {
			int reserva = it.components().has(net.minecraft.core.component.DataComponents.FOOD) ? pop * 2 : 4;
			int sobra = Vilas.contar(armazens, it) - reserva;
			if (sobra > 0 && total < 48) {
				int leva = Math.min(Math.min(sobra, 24), 48 - total);
				lote.put(it, leva);
				total += leva;
			}
		}
		return lote;
	}

	private static double preco(ArraialBlockEntity a, Item item) {
		return Math.max(0.5, Feira.base(item));
	}

	// ================================================================ the captain and the player

	/** The captain's price for his goods: straight off the ship, a little over the base. */
	public static double precoVenda(Item item) {
		return Math.round(Math.max(0.5, Feira.base(item)) * 1.1 * 10) / 10.0;
	}

	/** The captain buys the colony's exports from the player, at the ship's premium. */
	public static void comprarDoJogador(ServerLevel level, MoradorEntity m, Player p, ItemStack mao) {
		if (!EXPORTA.contains(mao.getItem())) {
			m.dizer(Component.translatable("arraial.fala.capitao_nao_quer"), 60);
			return;
		}
		double unidade = Math.max(0.5, Feira.base(mao.getItem())) * PREMIO;
		int qtd = Math.min(16, mao.getCount());
		while (qtd > 0 && Math.floor(unidade * qtd) > m.bolsa()) {
			qtd--;
		}
		int pago = (int) Math.floor(unidade * qtd);
		if (qtd == 0 || pago == 0) {
			m.dizer(Component.translatable("arraial.fala.mascate_sem_dinheiro"), 60);
			return;
		}
		Item item = mao.getItem();
		mao.shrink(qtd);
		m.mudarBolsa(-pago);
		ItemStack reis = new ItemStack(ModItems.REIS, pago);
		if (!p.getInventory().add(reis)) {
			Block.popResource(level, p.blockPosition(), reis);
		}
		level.playSound(null, m.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 0.9F);
		if (p instanceof ServerPlayer sp) {
			Conquistas.dar(sp, "mascate");
		}
		p.sendSystemMessage(Component.translatable("arraial.navio.vendeu", qtd, item.getDefaultInstance().getHoverName(), pago)
				.withStyle(ChatFormatting.GREEN));
	}

	/** Names of a few goods, for the chat. */
	static MutableComponent lista(Map<Item, Integer> bens) {
		MutableComponent l = Component.empty();
		int i = 0;
		for (Map.Entry<Item, Integer> e : bens.entrySet()) {
			l.append(Component.literal((i++ == 0 ? "" : ", ") + e.getValue() + " ")).append(e.getKey().getDefaultInstance().getHoverName());
		}
		return l;
	}
}
