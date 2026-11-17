package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.fauna.Arara;
import com.guilhermegoes.arraial.fauna.Onca;
import com.guilhermegoes.arraial.fauna.Tamandua;
import com.guilhermegoes.arraial.npc.MoradorEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Brazilian fauna and the arraial:
 * <ul>
 * <li>an <b>onça</b> that kills an animal near an arraial is "rondando": the caçador and the soldados
 * go after it, and when it falls the arraial breathes again (and keeps its coat);</li>
 * <li><b>tamanduás</b> living in the arraial's land eat the ants and termites: the praga is rarer and
 * shorter (see Crise);</li>
 * <li><b>araras</b> about the arraial cheer the people up (see Satisfacao) and drop feathers.</li>
 * </ul>
 * Capivaras and jacarés are game for the caçador (see CacadorGoal); the jacaré's hide is a luxury
 * the ships pay well for.
 */
public final class Bichos {
	private static final long DIA = 24000L;
	/** How far from the Cruzeiro an onça counts as prowling around the arraial. */
	private static final int PERTO = Vilas.RAIO + 16;

	private Bichos() {
	}

	private static @Nullable ArraialBlockEntity arraialPerto(ServerLevel level, BlockPos pos) {
		BlockPos v = Vilas.maisProxima(level, pos);
		return v != null && v.closerThan(pos, PERTO) && level.getBlockEntity(v) instanceof ArraialBlockEntity a ? a : null;
	}

	/** Is an onça prowling around this arraial (it killed an animal here lately)? */
	public static boolean rondando(ServerLevel level, @Nullable BlockPos vila) {
		return vila != null && level.getBlockEntity(vila) instanceof ArraialBlockEntity a && a.oncaAte > level.getGameTime();
	}

	/** The onça killed an animal: if it was near an arraial, the arraial hears of it and the hunt is on. */
	public static void oncaMatou(ServerLevel level, Onca onca, LivingEntity presa) {
		ArraialBlockEntity a = arraialPerto(level, onca.blockPosition());
		if (a == null) {
			return;
		}
		boolean nova = a.oncaAte <= level.getGameTime();
		a.oncaAte = level.getGameTime() + 2 * DIA;
		a.setChanged();
		if (nova) {
			Component texto = Component.translatable("arraial.cronica.onca_rondando", presa.getName(), a.nome());
			Cronica.anotar(level, a.getBlockPos(), texto);
			Prefeitura.avisar(level, a, Component.literal("🐆 ").append(texto).withStyle(ChatFormatting.GOLD));
		}
	}

	/** The onça fell: if it was the one prowling around an arraial, the arraial breathes again. */
	public static void oncaMorreu(ServerLevel level, Onca onca, DamageSource causa) {
		ArraialBlockEntity a = arraialPerto(level, onca.blockPosition());
		if (a == null || a.oncaAte <= level.getGameTime()) {
			return;
		}
		a.oncaAte = 0;
		a.setChanged();
		String quem = causa.getEntity() instanceof MoradorEntity m ? m.nome()
				: causa.getEntity() instanceof Player p ? p.getName().getString() : "";
		Component texto = quem.isEmpty() ? Component.translatable("arraial.cronica.onca_sumiu")
				: Component.translatable("arraial.cronica.onca_abatida", quem);
		Cronica.anotar(level, a.getBlockPos(), texto);
		Prefeitura.avisar(level, a, Component.literal("🐆 ").append(texto).withStyle(ChatFormatting.GREEN));
		if (!quem.isEmpty()) {
			Satisfacao.agradar(level, a.getBlockPos(), 3);
		}
	}

	/** Tamanduás living in the arraial's land. */
	public static int tamanduas(ServerLevel level, BlockPos vila) {
		return level.getEntitiesOfClass(Tamandua.class, new AABB(vila).inflate(Vilas.RAIO + 8), t -> t.isAlive()).size();
	}

	/** Araras about the arraial. */
	public static int araras(ServerLevel level, BlockPos vila) {
		return level.getEntitiesOfClass(Arara.class, new AABB(vila).inflate(Vilas.RAIO + 8), t -> t.isAlive()).size();
	}
}
