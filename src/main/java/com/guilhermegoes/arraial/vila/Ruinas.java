package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * What becomes of an abandoned arraial (and an abandoned player base, see {@link Bases}).
 * <ul>
 * <li><b>Deterioration</b>: day by day its buildings lose blocks (stone turns mossy and cracked,
 * cobwebs in the corners) until it is a ruin, while nobody repairs them.</li>
 * <li><b>Occupation</b>: after a few days a neighbour with people to spare may resettle it (it
 * lives again, and remembers who founded it anew), or bandits make it their hideout: from there
 * they raid the villages around and make the roads more dangerous.</li>
 * <li><b>The player</b> can clear the hideout (the loot stays in the armazém) and, with food in
 * the armazém and beds, bring people back; the moradores then rebuild what fell.</li>
 * </ul>
 */
public final class Ruinas {
	public static final String TAG = "arraial_bandido";
	private static final double MAX_RUINA = 0.45;

	/** A loaded abandoned arraial, every few seconds. */
	static void tick(ServerLevel level, ArraialBlockEntity a) {
		// Moradores whose entities loaded after the village was found empty: they are gone too.
		a.moradores(level).forEach(net.minecraft.world.entity.Entity::discard);
		String id = Economia.id(level, a.getBlockPos());
		Registro.Info v = Registro.obter(id);
		long hoje = Vilas.dia(level);
		if (a.diaRuina == 0 || hoje < a.diaRuina) {
			a.diaRuina = hoje;
		}
		int dias = (int) Math.min(12, hoje - a.diaRuina + v.ruinaPendente);
		if (dias > 0) {
			for (int i = 0; i < dias; i++) {
				deteriorar(level, a);
			}
			for (long d = a.diaRuina + 1; d <= hoje; d++) {
				ocupar(level, id, v, d);
			}
			a.diaRuina = hoje;
			v.ruinaPendente = 0;
			a.setChanged();
		}
		if ("bandidos".equals(v.ocupadaPor)) {
			esconderijo(level, a, v);
		}
	}

	/** One day of decay: a few blocks of each finished building give way. */
	static void deteriorar(ServerLevel level, ArraialBlockEntity a) {
		RandomSource r = level.getRandom();
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (!p.closerThan(a.getBlockPos(), Vilas.RAIO) || !(level.getBlockEntity(p) instanceof CanteiroBlockEntity c) || !c.concluida()) {
				continue;
			}
			List<PlanoDeObra.Etapa> etapas = c.plano(level).map(PlanoDeObra::etapas).orElse(List.of());
			if (etapas.isEmpty()) {
				continue;
			}
			List<PlanoDeObra.Etapa> inteiras = new ArrayList<>();
			for (PlanoDeObra.Etapa e : etapas) {
				if (level.getBlockState(e.pos()).is(e.state().getBlock())) {
					inteiras.add(e);
				}
			}
			if (1.0 - inteiras.size() / (double) etapas.size() >= MAX_RUINA) {
				continue; // already a ruin
			}
			for (int i = 0; i < 3 && !inteiras.isEmpty(); i++) {
				PlanoDeObra.Etapa e = inteiras.remove(r.nextInt(inteiras.size()));
				level.setBlock(e.pos(), ruina(e.state(), r), Block.UPDATE_CLIENTS);
			}
		}
	}

	private static BlockState ruina(BlockState s, RandomSource r) {
		if (s.is(Blocks.COBBLESTONE)) {
			return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		}
		if (s.is(Blocks.STONE_BRICKS)) {
			return (r.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).defaultBlockState();
		}
		return r.nextFloat() < 0.15F ? Blocks.COBWEB.defaultBlockState() : Blocks.AIR.defaultBlockState();
	}

	/** One day of an abandoned arraial (loaded or not): who takes it, and what the bandits do. */
	static void ocupar(ServerLevel level, String id, Registro.Info v, long d) {
		if (!v.abandonada) {
			return;
		}
		RandomSource r = level.getRandom();
		v.diasAbandonada++;
		if ("bandidos".equals(v.ocupadaPor)) {
			saquear(level, id, v, d, r);
			return;
		}
		if (v.diasAbandonada < 3) {
			return;
		}
		// A neighbour with people and food to spare resettles it.
		String colono = null;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info o = e.getValue();
			if (!e.getKey().equals(id) && !o.abandonada && o.dimensao.equals(v.dimensao) && o.populacao >= 8
					&& Mundo.comida(o) >= o.populacao * 3 && o.posicao().distSqr(v.posicao()) < 400.0 * 400.0) {
				colono = e.getKey();
				break;
			}
		}
		if (colono != null && r.nextFloat() < 0.12F) {
			Registro.Info o = Registro.get(colono);
			v.abandonada = false;
			v.populacao = 2;
			v.saldoMoradores += 2;
			v.fome = 0;
			v.diasAbandonada = 0;
			v.colonia = colono;
			o.populacao -= 2;
			o.saldoMoradores -= 2;
			Registro.mudarReputacao(id, colono, 30);
			Mundo.historia(id, d, "arraial.historia.recolonizada", o.nome);
			Mundo.historia(colono, d, "arraial.historia.colonizou", v.nome);
		} else if (r.nextFloat() < 0.25F) {
			v.ocupadaPor = "bandidos";
			v.bandidos = 4;
			v.bandidosSpawnados = false;
			Mundo.historia(id, d, "arraial.historia.bandidos_ocuparam", v.nome);
			for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
				if (!e.getKey().equals(id) && !e.getValue().abandonada && e.getValue().posicao().distSqr(v.posicao()) < 400.0 * 400.0) {
					Mundo.historia(e.getKey(), d, "arraial.historia.esconderijo_perto", v.nome);
				}
			}
		}
	}

	/** The bandits of a hideout raid the nearest living village now and then. */
	private static void saquear(ServerLevel level, String id, Registro.Info v, long d, RandomSource r) {
		if (r.nextFloat() > 0.1F) {
			return;
		}
		String alvo = null;
		double menor = 400.0 * 400.0;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info o = e.getValue();
			double dist = o.posicao().distSqr(v.posicao());
			if (!e.getKey().equals(id) && !o.abandonada && o.dimensao.equals(v.dimensao) && dist < menor) {
				menor = dist;
				alvo = e.getKey();
			}
		}
		if (alvo == null) {
			return;
		}
		Registro.Info o = Registro.get(alvo);
		if (Mundo.carregada(level, o) && level.getBlockEntity(o.posicao()) instanceof ArraialBlockEntity a) {
			if (a.crise.tipo() == null) {
				Crise.comecar(level, a, Crise.Tipo.SALTEADORES);
				Prefeitura.avisar(level, a, Component.translatable("arraial.ruina.ataque", v.nome).withStyle(ChatFormatting.RED));
			}
		} else {
			for (Map.Entry<String, Integer> e : new ArrayList<>(o.estoque.entrySet())) {
				int leva = e.getValue() * 15 / 100;
				Mundo.somar(o, e.getKey(), -leva);
				Mundo.somar(v, e.getKey(), leva / 2); // the loot piles up in the hideout
			}
		}
		Mundo.historia(alvo, d, "arraial.historia.saqueados", v.nome);
	}

	/** Roads near a hideout are more dangerous. */
	static boolean esconderijoPerto(Registro.Info v) {
		for (Registro.Info o : Registro.todas().values()) {
			if (o != v && o.abandonada && "bandidos".equals(o.ocupadaPor) && o.posicao().distSqr(v.posicao()) < 400.0 * 400.0) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ the hideout, up close

	private static void esconderijo(ServerLevel level, ArraialBlockEntity a, Registro.Info v) {
		BlockPos c = a.getBlockPos();
		List<ServerPlayer> perto = level.players().stream().filter(p -> p.blockPosition().closerThan(c, 64)).toList();
		if (perto.isEmpty()) {
			a.esconderijoVazio = 0;
			return;
		}
		if (!v.bandidosSpawnados) {
			v.bandidosSpawnados = true;
			int n = Math.max(2, v.bandidos);
			int vieram = spawnar(level, c, n);
			Conquistas.bau(level, c);
			Component aviso = Component.literal("⚔ ").append(Component.translatable("arraial.ruina.esconderijo", a.nomeCompleto(), vieram));
			perto.forEach(p -> p.sendSystemMessage(aviso.copy().withStyle(ChatFormatting.DARK_RED)));
			return;
		}
		List<Mob> vivos = bandidos(level, c);
		if (!vivos.isEmpty()) {
			v.bandidos = vivos.size();
			a.esconderijoVazio = 0;
			return;
		}
		// Empty three checks in a row (entities may still be loading): cleared.
		if (++a.esconderijoVazio < 3) {
			return;
		}
		v.ocupadaPor = "";
		v.bandidos = 0;
		v.bandidosSpawnados = false;
		a.esconderijoVazio = 0;
		Cronica.anotar(level, c, Component.translatable("arraial.cronica.esconderijo_limpo", perto.get(0).getName().getString()));
		for (ServerPlayer p : perto) {
			p.sendSystemMessage(Component.literal("⚔ ").append(Component.translatable("arraial.ruina.limpo", a.nomeCompleto())).withStyle(ChatFormatting.GOLD));
			Conquistas.dar(p, "esconderijo_limpo");
			ItemStack honra = new ItemStack(com.guilhermegoes.arraial.registry.ModItems.COMENDA);
			if (!p.getInventory().add(honra)) {
				Block.popResource(level, p.blockPosition(), honra);
			}
			ItemStack premio = new ItemStack(com.guilhermegoes.arraial.registry.ModItems.REIS, 10);
			if (!p.getInventory().add(premio)) {
				Block.popResource(level, p.blockPosition(), premio);
			}
		}
		// The neighbours are grateful.
		for (BlockPos outra : Vilas.ativas(level)) {
			if (!outra.equals(c) && outra.closerThan(c, 400) && level.getBlockEntity(outra) instanceof ArraialBlockEntity b && !b.abandonada) {
				perto.forEach(p -> b.mudarReputacao(p.getUUID(), 10));
				Cronica.anotar(level, outra, Component.translatable("arraial.cronica.esconderijo_limpo_viz", perto.get(0).getName().getString(), a.nomeCompleto()));
			}
		}
	}

	public static List<Mob> bandidos(ServerLevel level, BlockPos c) {
		return level.getEntitiesOfClass(Mob.class, new AABB(c).inflate(56), m -> m.isAlive() && m.entityTags().contains(TAG));
	}

	/** Bandits around a spot (they stay: they live there now). */
	public static int spawnar(ServerLevel level, BlockPos c, int n) {
		RandomSource r = level.getRandom();
		int vieram = 0;
		for (int i = 0; i < n; i++) {
			BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(r.nextInt(13) - 6, 0, r.nextInt(13) - 6));
			AbstractIllager b = i % 2 == 0 ? EntityTypes.VINDICATOR.spawn(level, p, EntitySpawnReason.EVENT)
					: EntityTypes.PILLAGER.spawn(level, p, EntitySpawnReason.EVENT);
			if (b == null) {
				continue;
			}
			b.addTag(TAG);
			b.setPersistenceRequired();
			b.setCustomName(Component.translatable("arraial.bandido"));
			b.setPatrolLeader(false);
			b.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			vieram++;
		}
		return vieram;
	}

	private Ruinas() {
	}
}
