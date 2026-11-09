package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.registry.ModPois;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Eventos e crises: life in the sertão is not always kind. Now and then the arraial goes through
 * a hard time, and what it has built decides how hard:
 * <ul>
 * <li><b>Seca</b> (drought): the roça dries out and crops wither, unless the arraial has a poço to water them.</li>
 * <li><b>Enchente</b> (flood, only in a storm): crops next to water are washed away, and an armazém on low
 * ground by the water loses part of its food.</li>
 * <li><b>Praga</b> (crop plague): caterpillars eat the roça; chickens around the arraial eat the caterpillars.</li>
 * <li><b>Salteadores</b> (bandits): rumours in the morning, a raid at night. They go for the armazém and run
 * off with what they can carry; the soldados (and the player) can stop them and get the loot back.</li>
 * </ul>
 * Everything is written into the Crônica, and the prefeito reacts (a poço in a drought, a guarita
 * against bandits).
 */
public final class Crise {
	public enum Tipo {
		SECA, ENCHENTE, PRAGA, SALTEADORES, EPIDEMIA, INCENDIO;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Component nome() {
			return Component.translatable("arraial.crise." + id());
		}

		public static @Nullable Tipo de(String id) {
			for (Tipo t : values()) {
				if (t.id().equals(id)) {
					return t;
				}
			}
			return null;
		}
	}

	/** Entity tag that marks the bandits of a raid. */
	public static final String TAG = "arraial_salteador";
	private static final long DIA = 24000L;
	/** The roça: the ground the roceiro tends around each balaio. */
	private static final int RAIO_ROCA = 9;
	/** First crisis no sooner than this after the arraial starts rolling. */
	private static final long CARENCIA = 3 * DIA;
	private static final float CHANCE_POR_DIA = 0.25F;
	private static final int MIN_MORADORES = 4;

	@Nullable Tipo tipo;
	/** Game time when the current crisis ends. */
	long fim;
	/** Earliest game time for the next crisis (0 = not set yet). */
	long proxima;
	/** Last day the morning roll was made. */
	long ultimoSorteio;
	/** Salteadores: the raid has arrived. */
	boolean chegaram;
	/** What the crisis cost: crops lost, or items stolen. */
	int perdas;
	/** Incêndio: where the fire broke out (the moradores fight it from there). */
	@Nullable BlockPos fogo;
	/** Incêndio: ticks without any flame left. */
	int semFogo;

	public @Nullable BlockPos fogo() {
		return tipo == Tipo.INCENDIO ? fogo : null;
	}

	public @Nullable Tipo tipo() {
		return tipo;
	}

	// ------------------------------------------------------------ the clock

	/** Called every second by the Cruzeiro. */
	static void tick(ServerLevel level, ArraialBlockEntity arraial, int moradores) {
		Crise c = arraial.crise;
		long agora = level.getGameTime();
		if (c.tipo == null) {
			if (agora % 100 == 0 && com.guilhermegoes.arraial.config.ArraialConfig.get().crises) {
				// The frequency setting: below 100% some rolls are skipped, above it some happen twice.
				int f = com.guilhermegoes.arraial.config.ArraialConfig.get().frequenciaCrises;
				if (level.getRandom().nextInt(100) < Math.min(100, f)) {
					sortear(level, arraial, moradores);
				}
				if (arraial.crise.tipo == null && f > 100 && level.getRandom().nextInt(100) < f - 100) {
					sortear(level, arraial, moradores);
				}
			}
			return;
		}
		switch (c.tipo) {
			case SECA -> {
				if (level.isRaining()) {
					terminar(level, arraial, "chuva");
					return;
				}
				if (agora % 100 == 0) {
					secar(level, arraial);
				}
			}
			case PRAGA -> {
				if (agora % 100 == 0) {
					pragas(level, arraial);
				}
			}
			case SALTEADORES -> {
				salteadores(level, arraial, moradores);
				return;
			}
			case ENCHENTE -> {
			}
			case EPIDEMIA -> {
				if (agora % 1200 == 0) {
					contagio(level, arraial);
				}
			}
			case INCENDIO -> {
				if (queimando(level, arraial)) {
					return;
				}
			}
		}
		if (c.tipo != null && agora >= c.fim) {
			terminar(level, arraial, "fim");
		}
	}

	/** Once a morning (and now and then during a storm) the sertão decides whether trouble comes. */
	private static void sortear(ServerLevel level, ArraialBlockEntity arraial, int moradores) {
		Crise c = arraial.crise;
		long agora = level.getGameTime();
		if (c.proxima == 0) {
			c.proxima = agora + CARENCIA;
			arraial.setChanged();
			return;
		}
		if (agora < c.proxima || moradores < MIN_MORADORES) {
			return;
		}
		RandomSource r = level.getRandom();
		// A storm can bring a flood at any hour.
		if (level.isThundering() && temRoca(level, arraial.getBlockPos()) && r.nextFloat() < 0.02F) {
			comecar(level, arraial, Tipo.ENCHENTE);
			return;
		}
		long hoje = Vilas.dia(level);
		if (hoje < c.ultimoSorteio) {
			c.ultimoSorteio = hoje - 1; // clock set back
		}
		if (!level.isBrightOutside() || hoje <= c.ultimoSorteio) {
			return;
		}
		c.ultimoSorteio = hoje;
		arraial.setChanged();
		if (r.nextFloat() >= CHANCE_POR_DIA) {
			return;
		}
		boolean roca = temRoca(level, arraial.getBlockPos());
		// The season tips the scales: droughts in the tempo da seca, floods in the tempo das águas.
		boolean tempoSeco = Calendario.tempoDaSeca(hoje);
		int seca = roca && !level.isRaining() ? (tempoSeco ? 4 : 1) : 0;
		int enchente = roca && level.isRaining() ? (tempoSeco ? 1 : 4) : 0;
		int praga = roca ? 2 : 0;
		int salteadores = (moradores >= 8 ? 3 : 2) + Diplomacia.peso(level, arraial.getBlockPos());
		int epidemia = moradores >= 6 ? 1 : 0;
		int incendio = !level.isRaining() ? 1 : 0;
		int total = seca + enchente + praga + salteadores + epidemia + incendio;
		int x = r.nextInt(total);
		Tipo t = (x -= seca) < 0 ? Tipo.SECA : (x -= enchente) < 0 ? Tipo.ENCHENTE : (x -= praga) < 0 ? Tipo.PRAGA
				: (x -= salteadores) < 0 ? Tipo.SALTEADORES : (x -= epidemia) < 0 ? Tipo.EPIDEMIA : Tipo.INCENDIO;
		comecar(level, arraial, t);
	}

	/** Starts a crisis now (also used by the /arraial crise command). */
	public static void comecar(ServerLevel level, ArraialBlockEntity arraial, Tipo tipo) {
		Crise c = arraial.crise;
		if (tipo == Tipo.INCENDIO && !acender(level, arraial)) {
			return; // nothing made of wood to catch fire
		}
		if (c.tipo == Tipo.SALTEADORES) {
			fugir(level, arraial);
		}
		RandomSource r = level.getRandom();
		long agora = level.getGameTime();
		c.tipo = tipo;
		c.chegaram = false;
		c.perdas = 0;
		c.fim = agora + switch (tipo) {
			case SECA -> (3 + r.nextInt(3)) * DIA;
			case PRAGA -> (2 + r.nextInt(2)) * DIA / (galinhas(level, arraial.getBlockPos()) >= 4 ? 2 : 1);
			case ENCHENTE -> DIA / 2;
			case SALTEADORES -> DIA + DIA / 2; // a safety net: the raid ends at dawn
			case EPIDEMIA -> 4 * DIA;
			case INCENDIO -> DIA;
		};
		arraial.setChanged();
		BlockPos vila = arraial.getBlockPos();
		String extra = switch (tipo) {
			case SECA -> Prefeitura.obras(level, vila, "poco") > 0 ? "_poco" : "";
			case PRAGA -> galinhas(level, vila) >= 4 ? "_galinhas" : "";
			default -> "";
		};
		Cronica.anotar(level, vila, Component.translatable("arraial.cronica.crise." + tipo.id() + extra));
		Prefeitura.avisar(level, arraial, Component.literal("⚠ ")
				.append(Component.translatable("arraial.crise.aviso." + tipo.id(), arraial.nomeCompleto()))
				.withStyle(ChatFormatting.RED));
		if (tipo == Tipo.ENCHENTE) {
			alagar(level, arraial);
		}
		Diplomacia.socorro(level, arraial);
		if (tipo == Tipo.EPIDEMIA) {
			adoecer(level, arraial);
		}

	}

	/** Ends the current crisis; {@code motivo} picks the chronicle line (fim, chuva, expulsos, fugiram, roubaram, comando). */
	public static void terminar(ServerLevel level, ArraialBlockEntity arraial, String motivo) {
		Crise c = arraial.crise;
		if (c.tipo == null) {
			return;
		}
		Tipo t = c.tipo;
		if (t == Tipo.SALTEADORES) {
			fugir(level, arraial);
		}
		c.tipo = null;
		c.proxima = level.getGameTime() + (2 + level.getRandom().nextInt(3)) * DIA;
		arraial.setChanged();
		if (motivo.equals("comando")) {
			return;
		}
		String chave = "arraial.cronica.fim." + t.id() + (motivo.equals("fim") ? "" : "_" + motivo);
		Component texto = Component.translatable(chave, c.perdas);
		Cronica.anotar(level, arraial.getBlockPos(), texto);
		Prefeitura.avisar(level, arraial, texto.copy().withStyle(ChatFormatting.GREEN));
	}

	// ------------------------------------------------------------ seca, praga, enchente

	private static List<BlockPos> balaios(ServerLevel level, BlockPos vila) {
		return level.getPoiManager().findAll(h -> h.value() == ModPois.BALAIO, p -> true, vila, Vilas.RAIO, PoiManager.Occupancy.ANY).toList();
	}

	private static boolean temRoca(ServerLevel level, BlockPos vila) {
		return !balaios(level, vila).isEmpty();
	}

	/** Chickens loose in (or penned near) the arraial: they eat the caterpillars. */
	private static int galinhas(ServerLevel level, BlockPos vila) {
		return level.getEntitiesOfClass(Chicken.class, new AABB(vila).inflate(Vilas.RAIO)).size();
	}

	private static Iterable<BlockPos> roca(BlockPos balaio) {
		return BlockPos.betweenClosed(balaio.offset(-RAIO_ROCA, -2, -RAIO_ROCA), balaio.offset(RAIO_ROCA, 2, RAIO_ROCA));
	}

	/** Seca: the farmland dries and some crops wither back a stage. A poço waters the roça. */
	private static void secar(ServerLevel level, ArraialBlockEntity arraial) {
		BlockPos vila = arraial.getBlockPos();
		boolean poco = Prefeitura.obras(level, vila, "poco") > 0;
		float chance = poco ? 0.01F : 0.035F;
		RandomSource r = level.getRandom();
		for (BlockPos b : balaios(level, vila)) {
			for (BlockPos p : roca(b)) {
				BlockState s = level.getBlockState(p);
				if (!poco && s.getBlock() instanceof FarmlandBlock && s.getValue(FarmlandBlock.MOISTURE) > 0) {
					level.setBlock(p, s.setValue(FarmlandBlock.MOISTURE, 0), Block.UPDATE_CLIENTS);
				} else if (s.getBlock() instanceof CropBlock crop && crop.getAge(s) > 0 && r.nextFloat() < chance) {
					level.setBlock(p, crop.getStateForAge(crop.getAge(s) - 1), Block.UPDATE_CLIENTS);
					level.sendParticles(ParticleTypes.WHITE_ASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
					arraial.crise.perdas++;
				}
			}
		}
		arraial.setChanged();
	}

	/** Praga: now and then the caterpillars strip a patch of the roça bare. Chickens keep them down. */
	private static void pragas(ServerLevel level, ArraialBlockEntity arraial) {
		BlockPos vila = arraial.getBlockPos();
		float chance = 0.15F * Math.max(0.2F, 1.0F - 0.2F * galinhas(level, vila));
		RandomSource r = level.getRandom();
		for (BlockPos b : balaios(level, vila)) {
			if (r.nextFloat() >= chance) {
				continue;
			}
			// Pick a grown crop at random and eat it and its neighbours.
			BlockPos alvo = null;
			int vistos = 0;
			for (BlockPos p : roca(b)) {
				BlockState s = level.getBlockState(p);
				if (s.getBlock() instanceof CropBlock crop && crop.getAge(s) > 1 && r.nextInt(++vistos) == 0) {
					alvo = p.immutable();
				}
			}
			if (alvo == null) {
				continue;
			}
			for (BlockPos p : BlockPos.betweenClosed(alvo.offset(-1, 0, -1), alvo.offset(1, 0, 1))) {
				BlockState s = level.getBlockState(p);
				if (s.getBlock() instanceof CropBlock crop && crop.getAge(s) > 0) {
					level.setBlock(p, crop.getStateForAge(0), Block.UPDATE_CLIENTS);
					arraial.crise.perdas++;
				}
			}
			level.sendParticles(ParticleTypes.MYCELIUM, alvo.getX() + 0.5, alvo.getY() + 0.4, alvo.getZ() + 0.5, 30, 1.2, 0.3, 1.2, 0.0);
			level.playSound(null, alvo, SoundEvents.SILVERFISH_STEP, SoundSource.BLOCKS, 0.8F, 0.6F);
		}
		arraial.setChanged();
	}

	/** Enchente: the water rises. Crops next to water are washed away; a low armazém by the water gets soaked. */
	private static void alagar(ServerLevel level, ArraialBlockEntity arraial) {
		BlockPos vila = arraial.getBlockPos();
		RandomSource r = level.getRandom();
		for (BlockPos b : balaios(level, vila)) {
			for (BlockPos p : roca(b)) {
				BlockState s = level.getBlockState(p);
				if (s.getBlock() instanceof CropBlock && pertoDaAgua(level, p.below(), 2) && r.nextFloat() < 0.6F) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					level.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, 12, 0.4, 0.1, 0.4, 0.0);
					arraial.crise.perdas++;
				}
			}
		}
		for (ArmazemBlockEntity a : Vilas.armazens(level, vila)) {
			if (pertoDaAgua(level, a.getBlockPos(), 6)) {
				int comida = Vilas.contar(List.of(a), s -> s.has(DataComponents.FOOD));
				int molhou = Vilas.retirar(List.of(a), s -> s.has(DataComponents.FOOD), Math.min(24, comida / 4)).size();
				if (molhou > 0) {
					Cronica.anotar(level, vila, Component.translatable("arraial.cronica.enchente_armazem"));
				}
			}
		}
		arraial.setChanged();
	}

	/** Water within {@code raio} blocks sideways, at this height or one below. */
	private static boolean pertoDaAgua(ServerLevel level, BlockPos pos, int raio) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-raio, -1, -raio), pos.offset(raio, 0, raio))) {
			if (level.getFluidState(p).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------ epidemia

	/** One or two moradores fall ill. */
	private static void adoecer(ServerLevel level, ArraialBlockEntity arraial) {
		List<com.guilhermegoes.arraial.npc.MoradorEntity> moradores = arraial.moradores(level);
		java.util.Collections.shuffle(moradores, new java.util.Random(level.getRandom().nextLong()));
		for (int i = 0; i < Math.min(2, moradores.size()); i++) {
			moradores.get(i).adoecer(3 + level.getRandom().nextInt(3));
			arraial.crise.perdas++;
		}
		arraial.setChanged();
	}

	/** Every minute the sick may pass it on to whoever is close; with nobody sick, it is over. */
	private static void contagio(ServerLevel level, ArraialBlockEntity arraial) {
		List<com.guilhermegoes.arraial.npc.MoradorEntity> moradores = arraial.moradores(level);
		List<com.guilhermegoes.arraial.npc.MoradorEntity> doentes = moradores.stream().filter(com.guilhermegoes.arraial.npc.MoradorEntity::doente).toList();
		if (doentes.isEmpty()) {
			terminar(level, arraial, "curados");
			return;
		}
		// A botica halves the spread (the boticário's advice: boil the water, keep the sick apart).
		float chance = Vilas.postosDe(level, arraial.getBlockPos(), ModPois.BALCAO_BOTICA) > 0 ? 0.04F : 0.08F;
		for (var m : moradores) {
			if (!m.doente() && doentes.stream().anyMatch(d -> d.distanceToSqr(m) < 4 * 4) && level.getRandom().nextFloat() < chance) {
				m.adoecer(3 + level.getRandom().nextInt(3));
				arraial.crise.perdas++;
			}
		}
		arraial.setChanged();
	}

	// ------------------------------------------------------------ incêndio

	/** Fire breaks out in a finished wooden building of the arraial. */
	private static boolean acender(ServerLevel level, ArraialBlockEntity arraial) {
		BlockPos vila = arraial.getBlockPos();
		List<BlockPos> candidatos = new java.util.ArrayList<>();
		for (BlockPos p : com.guilhermegoes.arraial.obra.CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO) && level.getBlockEntity(p) instanceof com.guilhermegoes.arraial.obra.CanteiroBlockEntity c
					&& c.concluida() && !c.projeto().map(com.guilhermegoes.arraial.obra.Muralha::e).orElse(true)) {
				c.plano(level).ifPresent(pl -> pl.etapas().stream()
						.filter(e -> level.getBlockState(e.pos()).ignitedByLava() && level.getBlockState(e.pos().above()).isAir())
						.forEach(e -> candidatos.add(e.pos().above())));
			}
		}
		if (candidatos.isEmpty()) {
			return false;
		}
		BlockPos onde = candidatos.get(level.getRandom().nextInt(candidatos.size()));
		level.setBlock(onde, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
		arraial.crise.fogo = onde.immutable();
		arraial.crise.semFogo = 0;
		arraial.setChanged();
		return true;
	}

	/** Flames left around where the fire started. */
	public static List<BlockPos> chamas(ServerLevel level, BlockPos fogo) {
		List<BlockPos> l = new java.util.ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(fogo.offset(-8, -4, -8), fogo.offset(8, 6, 8))) {
			if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
				l.add(p.immutable());
			}
		}
		return l;
	}

	/** True while it burns; ends the crisis once every flame has been out for a while. */
	private static boolean queimando(ServerLevel level, ArraialBlockEntity arraial) {
		Crise c = arraial.crise;
		if (c.fogo == null) {
			terminar(level, arraial, "fim");
			return true;
		}
		if (!chamas(level, c.fogo).isEmpty()) {
			c.semFogo = 0;
			return false;
		}
		if (++c.semFogo >= 10) {
			terminar(level, arraial, "apagado");
			return true;
		}
		return false;
	}

	/** A morador put a flame out. */
	public static void apagou(ServerLevel level, BlockPos vila) {
		if (level.getBlockEntity(vila) instanceof ArraialBlockEntity a && a.crise.tipo == Tipo.INCENDIO) {
			a.crise.perdas++;
			a.setChanged();
		}
	}

	// ------------------------------------------------------------ salteadores

	private static List<Vindicator> bando(ServerLevel level, BlockPos vila) {
		return level.getEntitiesOfClass(Vindicator.class, new AABB(vila).inflate(Vilas.RAIO * 2), v -> v.entityTags().contains(TAG));
	}

	private static void salteadores(ServerLevel level, ArraialBlockEntity arraial, int moradores) {
		Crise c = arraial.crise;
		BlockPos vila = arraial.getBlockPos();
		if (!c.chegaram) {
			if (level.getGameTime() >= c.fim) {
				terminar(level, arraial, "fim");
			} else if (level.isDarkOutside()) {
				chegar(level, arraial, moradores);
			}
			return;
		}
		List<Vindicator> bando = bando(level, vila);
		if (bando.isEmpty()) {
			terminar(level, arraial, c.perdas > 0 ? "roubaram" : "expulsos");
			return;
		}
		if (level.isBrightOutside()) {
			terminar(level, arraial, c.perdas > 0 ? "roubaram" : "fugiram");
			return;
		}
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, vila);
		for (Vindicator v : bando) {
			ItemStack saque = v.getItemBySlot(EquipmentSlot.OFFHAND);
			if (!saque.isEmpty()) {
				// Got something: run for the woods. Once out of the arraial, the loot is gone.
				Vec3 dir = v.position().subtract(Vec3.atCenterOf(vila)).multiply(1, 0, 1);
				if (dir.length() > Vilas.RAIO + 8) {
					c.perdas += saque.getCount();
					arraial.setChanged();
					Cronica.anotar(level, vila, Component.translatable("arraial.cronica.levaram", saque.getCount(), saque.getHoverName()));
					v.discard();
					continue;
				}
				Vec3 fuga = Vec3.atCenterOf(vila).add(dir.normalize().scale(Vilas.RAIO + 16));
				v.getNavigation().moveTo(fuga.x, v.getY(), fuga.z, 1.15);
				continue;
			}
			if (v.getTarget() != null || armazens.isEmpty()) {
				continue;
			}
			ArmazemBlockEntity alvo = armazens.stream()
					.min(java.util.Comparator.comparingDouble(a -> a.getBlockPos().distSqr(v.blockPosition()))).get();
			if (v.position().distanceTo(Vec3.atCenterOf(alvo.getBlockPos())) < 2.8) {
				ItemStack levou = saquear(alvo);
				if (!levou.isEmpty()) {
					v.setItemSlot(EquipmentSlot.OFFHAND, levou);
					v.setDropChance(EquipmentSlot.OFFHAND, 2.0F); // killed, he drops it all back
					level.playSound(null, alvo.getBlockPos(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
			} else {
				v.getNavigation().moveTo(alvo.getBlockPos().getX() + 0.5, alvo.getBlockPos().getY(), alvo.getBlockPos().getZ() + 0.5, 1.0);
			}
		}
	}

	/** One armful from the armazém: food first, otherwise whatever is in the first slot. */
	private static ItemStack saquear(ArmazemBlockEntity a) {
		int slot = -1;
		for (int i = 0; i < a.getContainerSize(); i++) {
			ItemStack s = a.getItem(i);
			if (!s.isEmpty() && (slot < 0 || s.has(DataComponents.FOOD) && !a.getItem(slot).has(DataComponents.FOOD))) {
				slot = i;
			}
		}
		if (slot < 0) {
			return ItemStack.EMPTY;
		}
		ItemStack levou = a.removeItem(slot, 16);
		a.setChanged();
		return levou;
	}

	/** Nightfall: the band comes out of the woods a little outside the arraial. */
	private static void chegar(ServerLevel level, ArraialBlockEntity arraial, int moradores) {
		Crise c = arraial.crise;
		BlockPos vila = arraial.getBlockPos();
		RandomSource r = level.getRandom();
		int quantos = Math.min(6, 2 + moradores / 5);
		double angulo = r.nextDouble() * Math.PI * 2;
		int dist = 26 + r.nextInt(6);
		BlockPos base = vila.offset((int) (Math.cos(angulo) * dist), 0, (int) (Math.sin(angulo) * dist));
		int vieram = 0;
		for (int i = 0; i < quantos; i++) {
			BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2));
			Vindicator v = EntityTypes.VINDICATOR.spawn(level, p, EntitySpawnReason.EVENT);
			if (v == null) {
				continue;
			}
			v.addTag(TAG);
			v.setCustomName(Component.translatable("arraial.salteador"));
			v.setPatrolLeader(false);
			v.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			vieram++;
		}
		c.chegaram = true;
		arraial.setChanged();
		Cronica.anotar(level, vila, Component.translatable("arraial.cronica.salteadores_chegaram", vieram));
		Prefeitura.avisar(level, arraial, Component.literal("⚔ ")
				.append(Component.translatable("arraial.crise.ataque", vieram, arraial.nomeCompleto())).withStyle(ChatFormatting.DARK_RED));
	}

	/** The band melts back into the woods (dawn, or the crisis was called off). */
	private static void fugir(ServerLevel level, ArraialBlockEntity arraial) {
		for (Vindicator v : bando(level, arraial.getBlockPos())) {
			ItemStack saque = v.getItemBySlot(EquipmentSlot.OFFHAND);
			if (!saque.isEmpty()) {
				arraial.crise.perdas += saque.getCount();
			}
			level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 1, v.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			v.discard();
		}
	}

	// ------------------------------------------------------------ the Cruzeiro report and saving

	/** "⚠ Seca — about 2 more day(s)", or null when all is calm. */
	public @Nullable Component descricao(ServerLevel level) {
		if (tipo == null) {
			return null;
		}
		if (tipo == Tipo.SALTEADORES) {
			return Component.translatable(chegaram ? "arraial.crise.relatorio_ataque" : "arraial.crise.relatorio_boatos");
		}
		long dias = Math.max(1, (fim - level.getGameTime() + DIA - 1) / DIA);
		return Component.translatable("arraial.crise.relatorio", tipo.nome(), dias);
	}

	void ler(ValueInput in) {
		tipo = Tipo.de(in.getStringOr("Crise", ""));
		fim = in.getLongOr("CriseFim", 0L);
		proxima = in.getLongOr("ProximaCrise", 0L);
		ultimoSorteio = in.getLongOr("UltimoSorteio", 0L);
		chegaram = in.getBooleanOr("SalteadoresChegaram", false);
		perdas = in.getIntOr("CrisePerdas", 0);
		long f = in.getLongOr("Fogo", Long.MIN_VALUE);
		fogo = f == Long.MIN_VALUE ? null : BlockPos.of(f);
	}

	void gravar(ValueOutput out) {
		out.putString("Crise", tipo == null ? "" : tipo.id());
		out.putLong("CriseFim", fim);
		out.putLong("ProximaCrise", proxima);
		out.putLong("UltimoSorteio", ultimoSorteio);
		out.putBoolean("SalteadoresChegaram", chegaram);
		out.putInt("CrisePerdas", perdas);
		if (fogo != null) {
			out.putLong("Fogo", fogo.asLong());
		}
	}
}
