package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.registry.ModBlocks;
import com.guilhermegoes.arraial.registry.ModItems;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The festas of the calendar ({@link Calendario}). On a festa day the arraial announces it in the
 * morning; from noon until dusk the moradores drop their work and celebrate around the Cruzeiro:
 * <ul>
 * <li><b>São João</b>: a fogueira is lit and they dance the quadrilha around it; fireworks at dusk.</li>
 * <li><b>Padroeiro</b>: a procession circles the Cruzeiro, praising the patron saint; fireworks.</li>
 * <li><b>Feira Grande</b>: everyone goes to the bancas; the feira sells cheaper and buys dearer.</li>
 * </ul>
 * The festa eats into the armazém (a feast of one food per two moradores, festa sweets first) and
 * the coffers (fireworks). A good festa raises satisfaction and the prefeito's legitimacy; plenty
 * of food and bandeirinhas near the Cruzeiro make it better. A crisis cancels the festa.
 */
public final class Festa {
	public enum Tipo {
		SAO_JOAO, PADROEIRO, FEIRA_GRANDE;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public Component nome(ArraialBlockEntity a) {
			return Component.translatable("arraial.festa." + id(), Calendario.padroeiro(a.nome()));
		}

		static @Nullable Tipo de(String id) {
			for (Tipo t : values()) {
				if (t.id().equals(id)) {
					return t;
				}
			}
			return null;
		}
	}

	/** Noon and dusk (time of day). */
	public static final int INICIO = 6000;
	public static final int FIM = 11800;
	private static final int MIN_MORADORES = 3;
	private static final int CUSTO_FOGOS = 3;
	/** A forced festa (command) lasts this long. */
	private static final int DURACAO_FORCADA = 3600;

	@Nullable Tipo tipo;
	long dia;
	boolean comecou;
	boolean farta;
	boolean pagouFogos;
	boolean fogos;
	long fimForcado;
	long ultima;
	@Nullable BlockPos fogueira;

	/** The festa going on right now (after noon), if any. */
	public @Nullable Tipo agora() {
		return comecou ? tipo : null;
	}

	/** Where the moradores gather: the fogueira for São João, the Cruzeiro otherwise. */
	public BlockPos centro(ArraialBlockEntity a) {
		return fogueira != null ? fogueira : a.getBlockPos();
	}

	static void tick(ServerLevel level, ArraialBlockEntity a, int moradores) {
		Festa f = a.festa;
		long hoje = Vilas.dia(level);
		long hora = level.getOverworldClockTime() % 24000L;
		if (hoje < f.ultima) {
			f.ultima = hoje - 1; // clock set back
		}
		if (f.tipo != null) {
			if (a.crise.tipo() != null) {
				terminar(level, a, true);
				return;
			}
			boolean acabou = f.fimForcado > 0 ? level.getGameTime() >= f.fimForcado : hoje != f.dia || hora >= FIM;
			if (acabou) {
				terminar(level, a, false);
				return;
			}
			if (!f.comecou && (f.fimForcado > 0 || hora >= INICIO)) {
				comecar(level, a, moradores);
			}
			if (f.comecou && f.pagouFogos && !f.fogos
					&& (f.fimForcado > 0 ? level.getGameTime() >= f.fimForcado - 600 : hora >= FIM - 800)) {
				f.fogos = true;
				soltarFogos(level, f.centro(a), 5);
			}
			return;
		}
		// A festa voted by the council: today at noon.
		if (a.politica.festaVotada == hoje && hora >= INICIO && hora < FIM && a.crise.tipo() == null) {
			a.politica.festaVotada = 0;
			forcar(level, a, Tipo.SAO_JOAO);
			return;
		}
		if (hoje <= f.ultima || hora >= FIM || moradores < MIN_MORADORES) {
			return;
		}
		Tipo t = Calendario.festaDoDia(level, a, hoje);
		if (t == null) {
			return;
		}
		f.ultima = hoje;
		a.setChanged();
		if (a.crise.tipo() != null) {
			Component texto = Component.translatable("arraial.cronica.festa_cancelada", t.nome(a), a.crise.tipo().nome());
			Cronica.anotar(level, a.getBlockPos(), texto);
			Prefeitura.avisar(level, a, texto.copy().withStyle(ChatFormatting.GRAY));
			return;
		}
		anunciar(level, a, t);
	}

	private static void anunciar(ServerLevel level, ArraialBlockEntity a, Tipo t) {
		Festa f = a.festa;
		f.tipo = t;
		f.dia = Vilas.dia(level);
		f.comecou = false;
		f.fogos = false;
		a.setChanged();
		Prefeitura.avisar(level, a, Component.literal("🎊 ").append(Component.translatable("arraial.festa.hoje", t.nome(a), a.nomeCompleto()))
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
	}

	/** Noon: the fogueira is lit, the feast comes out of the armazém, and work stops. */
	private static void comecar(ServerLevel level, ArraialBlockEntity a, int moradores) {
		Festa f = a.festa;
		f.comecou = true;
		if (f.tipo == Tipo.SAO_JOAO) {
			f.fogueira = acenderFogueira(level, a.getBlockPos());
		}
		// The feast: festa sweets first, then any food.
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());
		int precisa = Math.max(1, moradores / 2);
		int comeu = Vilas.retirar(armazens, s -> s.is(ModItems.QUENTAO) || s.is(ModItems.PE_DE_MOLEQUE)
				|| s.is(ModItems.PACOCA) || s.is(ModItems.RAPADURA), precisa).stream().mapToInt(ItemStack::getCount).sum();
		if (comeu < precisa) {
			comeu += Vilas.retirar(armazens, s -> s.has(DataComponents.FOOD), precisa - comeu).stream().mapToInt(ItemStack::getCount).sum();
		}
		f.farta = comeu >= precisa;
		f.pagouFogos = f.tipo != Tipo.FEIRA_GRANDE && a.tesouro >= CUSTO_FOGOS;
		if (f.pagouFogos) {
			a.mudarTesouro(-CUSTO_FOGOS);
		}
		a.setChanged();
		Prefeitura.avisar(level, a, Component.translatable("arraial.festa.comecou." + f.tipo.id(), a.nomeCompleto(),
				Calendario.padroeiro(a.nome())).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/**
	 * Dusk (or a crisis): the fogueira goes out and the arraial remembers how it went.
	 * @param interrompida a crisis broke out in the middle of it
	 */
	static void terminar(ServerLevel level, ArraialBlockEntity a, boolean interrompida) {
		Festa f = a.festa;
		Tipo t = f.tipo;
		if (f.fogueira != null && level.getBlockState(f.fogueira).is(Blocks.CAMPFIRE)) {
			level.setBlock(f.fogueira, Blocks.AIR.defaultBlockState(), 3);
		}
		if (t != null && f.comecou) {
			BlockPos c = f.centro(a);
			int gente = level.getEntitiesOfClass(MoradorEntity.class, new AABB(c).inflate(16),
					m -> a.getBlockPos().equals(m.vila())).size();
			Component texto;
			if (interrompida) {
				texto = Component.translatable("arraial.cronica.festa_interrompida", t.nome(a));
			} else {
				boolean enfeitada = bandeirinhas(level, a.getBlockPos()) >= 4;
				int bonus = 4 + (f.farta ? 4 : 0) + (enfeitada ? 3 : 0) + (gente >= 5 ? 2 : 0);
				Satisfacao.agradar(level, a.getBlockPos(), bonus);
				if (a.prefeito != null) {
					a.legitimidade = Math.min(100, a.legitimidade + 3);
				}
				if (t == Tipo.FEIRA_GRANDE) {
					a.mudarTesouro(2 + gente);
				}
				String como = f.farta && enfeitada ? "linda" : f.farta ? "boa" : "fraca";
				texto = Component.translatable("arraial.cronica.festa_" + como, t.nome(a), gente);
			}
			Cronica.anotar(level, a.getBlockPos(), texto);
			Prefeitura.avisar(level, a, Component.literal("🎊 ").append(texto).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		f.tipo = null;
		f.comecou = false;
		f.fogos = false;
		f.pagouFogos = false;
		f.fimForcado = 0;
		f.fogueira = null;
		a.setChanged();
	}

	/** /arraial festa: a festa right now, for a few minutes, whatever the calendar says. */
	public static void forcar(ServerLevel level, ArraialBlockEntity a, Tipo t) {
		if (a.festa.tipo != null) {
			terminar(level, a, false);
		}
		anunciar(level, a, t);
		a.festa.ultima = Vilas.dia(level);
		a.festa.fimForcado = level.getGameTime() + DURACAO_FORCADA;
		comecar(level, a, a.populacao);
	}

	public static void encerrar(ServerLevel level, ArraialBlockEntity a) {
		if (a.festa.tipo != null) {
			terminar(level, a, false);
		}
	}

	/** A spot near the Cruzeiro with room to dance around; the campfire goes there. */
	private static @Nullable BlockPos acenderFogueira(ServerLevel level, BlockPos cruzeiro) {
		for (int r = 5; r <= 9; r++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos chao = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cruzeiro.relative(d, r));
				if (Math.abs(chao.getY() - cruzeiro.getY()) > 2 || !livre(level, chao)) {
					continue;
				}
				level.setBlock(chao, Blocks.CAMPFIRE.defaultBlockState(), 3);
				return chao;
			}
		}
		return null;
	}

	/** Open ground: nothing built in the 3x3 around and above, firm soil below. */
	private static boolean livre(ServerLevel level, BlockPos p) {
		if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
			return false;
		}
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, 0, -1), p.offset(1, 1, 1))) {
			BlockState s = level.getBlockState(q);
			if (!s.isAir() && !s.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	private static int bandeirinhas(ServerLevel level, BlockPos c) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-14, -3, -14), c.offset(14, 10, 14))) {
			if (level.getBlockState(p).is(ModBlocks.BANDEIRINHAS)) {
				n++;
			}
		}
		return n;
	}

	private static final int[] CORES = {0xD23A2A, 0xF0C030, 0x2A6AD2, 0x3A9A3A, 0xFFFFFF, 0xE07AC0};

	public static void soltarFogos(ServerLevel level, BlockPos c, int n) {
		RandomSource r = level.getRandom();
		FireworkExplosion.Shape[] formas = {FireworkExplosion.Shape.SMALL_BALL, FireworkExplosion.Shape.LARGE_BALL,
				FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};
		for (int i = 0; i < n; i++) {
			ItemStack s = new ItemStack(Items.FIREWORK_ROCKET);
			s.set(DataComponents.FIREWORKS, new Fireworks(1 + r.nextInt(2), List.of(new FireworkExplosion(
					formas[r.nextInt(formas.length)], IntList.of(CORES[r.nextInt(CORES.length)], CORES[r.nextInt(CORES.length)]),
					IntList.of(), r.nextBoolean(), r.nextBoolean()))));
			level.addFreshEntity(new FireworkRocketEntity(level, c.getX() + 0.5 + r.nextInt(7) - 3, c.getY() + 1,
					c.getZ() + 0.5 + r.nextInt(7) - 3, s));
		}
	}

	void ler(ValueInput in) {
		tipo = Tipo.de(in.getStringOr("Festa", ""));
		dia = in.getLongOr("FestaDia", 0L);
		comecou = in.getBooleanOr("FestaComecou", false);
		farta = in.getBooleanOr("FestaFarta", false);
		pagouFogos = in.getBooleanOr("FestaPagouFogos", false);
		fogos = in.getBooleanOr("FestaFogos", false);
		fimForcado = in.getLongOr("FestaFimForcado", 0L);
		ultima = in.getLongOr("UltimaFesta", 0L);
		long p = in.getLongOr("Fogueira", Long.MIN_VALUE);
		fogueira = p == Long.MIN_VALUE ? null : BlockPos.of(p);
	}

	void gravar(ValueOutput out) {
		out.putString("Festa", tipo == null ? "" : tipo.id());
		out.putLong("FestaDia", dia);
		out.putBoolean("FestaComecou", comecou);
		out.putBoolean("FestaFarta", farta);
		out.putBoolean("FestaPagouFogos", pagouFogos);
		out.putBoolean("FestaFogos", fogos);
		out.putLong("FestaFimForcado", fimForcado);
		out.putLong("UltimaFesta", ultima);
		if (fogueira != null) {
			out.putLong("Fogueira", fogueira.asLong());
		}
	}
}
