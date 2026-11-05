package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.obra.Muralha;
import com.guilhermegoes.arraial.registry.ModPois;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.jspecify.annotations.Nullable;

/**
 * How content the moradores are, and how much they trust the prefeito (GDD 9). Every morning the
 * satisfaction drifts toward what the arraial deserves: food in the armazém, a roof for everyone,
 * safety, services, crises and the sick. Gifts from the player help a little.
 * <ul>
 * <li>Very unhappy: now and then someone packs up and leaves.</li>
 * <li>Very happy: word gets around and newcomers arrive sooner.</li>
 * <li>Legitimacy follows satisfaction; too low for two days and the arraial elects someone else.</li>
 * </ul>
 */
public final class Satisfacao {
	/** A small boost (or blow) right now, e.g. a gift from the player. */
	public static void agradar(ServerLevel level, @Nullable BlockPos vila, int delta) {
		if (vila != null && level.isLoaded(vila) && level.getBlockEntity(vila) instanceof ArraialBlockEntity a) {
			a.satisfacao = Math.max(0, Math.min(100, a.satisfacao + delta));
			a.setChanged();
		}
	}

	/** What the arraial deserves today (0..100). */
	public static int alvo(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		int n = Math.max(1, moradores.size());
		BlockPos vila = a.getBlockPos();
		double alvo = 50;
		int comida = Vilas.contar(Vilas.armazens(level, vila), s -> s.has(DataComponents.FOOD));
		double dias = comida / (double) n;
		alvo += dias >= 3 ? 15 : dias >= 1 ? 5 : -20;
		long semCasa = moradores.stream().filter(m -> m.casa() == null).count();
		alvo -= 25.0 * semCasa / n;
		long doentes = moradores.stream().filter(MoradorEntity::doente).count();
		alvo -= 15.0 * doentes / n;
		if (postos(level, vila, ModPois.POSTO_GUARDA)) {
			alvo += 5;
		}
		if (Prefeitura.obras(level, vila, Muralha.ID) > 0) {
			alvo += 5;
		}
		if (a.temFeira(level)) {
			alvo += 5;
		}
		if (postos(level, vila, ModPois.BALCAO_BOTICA)) {
			alvo += 5;
		}
		if (Prefeitura.obras(level, vila, "prefeitura") > 0) {
			alvo += 5;
		}
		if (a.crise.tipo() != null) {
			alvo -= a.crise.tipo() == Crise.Tipo.SALTEADORES ? 15 : 10;
		}
		return (int) Math.max(0, Math.min(100, Math.round(alvo)));
	}

	private static boolean postos(ServerLevel level, BlockPos vila, net.minecraft.world.entity.ai.village.poi.PoiType tipo) {
		return level.getPoiManager().getCountInRange(h -> h.value() == tipo, vila, Vilas.RAIO, PoiManager.Occupancy.ANY) > 0;
	}

	/** The morning update; returns true when the arraial wants a new prefeito. */
	static boolean diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		int alvo = alvo(level, a, moradores);
		a.satisfacao += (int) Math.round((alvo - a.satisfacao) * 0.3);
		a.legitimidade += (int) Math.round((a.satisfacao - a.legitimidade) * 0.25);
		a.satisfacao = Math.max(0, Math.min(100, a.satisfacao));
		a.legitimidade = Math.max(0, Math.min(100, a.legitimidade));
		a.setChanged();

		// Very unhappy: someone leaves (never the prefeito).
		if (a.satisfacao < 25 && moradores.size() > 3 && level.getRandom().nextFloat() < 0.3F) {
			moradores.stream().filter(m -> !m.prefeito()).skip(level.getRandom().nextInt(moradores.size() - 1)).findFirst()
					.ifPresent(m -> {
						Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.insatisfeito", m.nome()));
						m.irEmboraDaVila(level);
					});
		}
		if (a.prefeito == null) {
			a.diasLegitimidadeBaixa = 0;
			return false;
		}
		a.diasLegitimidadeBaixa = a.legitimidade < 25 ? a.diasLegitimidadeBaixa + 1 : 0;
		if (a.diasLegitimidadeBaixa >= 2) {
			a.diasLegitimidadeBaixa = 0;
			a.legitimidade = 60;
			Prefeitura.avisar(level, a, Component.translatable("arraial.vila.deposto", a.nomeCompleto()).withStyle(ChatFormatting.RED));
			return true;
		}
		return false;
	}

	private Satisfacao() {
	}
}
