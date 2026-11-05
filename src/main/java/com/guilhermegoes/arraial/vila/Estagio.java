package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.Muralha;
import com.guilhermegoes.arraial.registry.ModPois;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/**
 * The settlement's stage (GDD 2): it grows from Povoado to Cidade as people arrive and the right
 * buildings stand. The stage is shown in the name ("Vila de Santa Luzia"), unlocks what the
 * prefeito may build, and never goes back down. Numbers are game-scale, not historical.
 */
public enum Estagio {
	POVOADO("Povoado", 0),
	ARRAIAL("Arraial", 6),
	VILA("Vila", 14),
	VILA_PROSPERA("Vila", 24),
	CIDADE("Cidade", 40);

	private final String prefixo;
	private final int populacao;

	Estagio(String prefixo, int populacao) {
		this.prefixo = prefixo;
		this.populacao = populacao;
	}

	public String prefixo() {
		return prefixo;
	}

	public Component nome() {
		return Component.translatable("arraial.estagio." + name().toLowerCase(java.util.Locale.ROOT));
	}

	public boolean pelo(Estagio outro) {
		return ordinal() >= outro.ordinal();
	}

	public static Estagio de(int i) {
		Estagio[] t = values();
		return i >= 0 && i < t.length ? t[i] : POVOADO;
	}

	/** What the arraial still lacks to reach this stage (empty when it qualifies). */
	public List<Component> falta(ServerLevel level, ArraialBlockEntity a, int moradores) {
		java.util.ArrayList<Component> f = new java.util.ArrayList<>();
		if (moradores < populacao) {
			f.add(Component.translatable("arraial.estagio.falta_gente", populacao));
		}
		switch (this) {
			case ARRAIAL -> {
				if (Vilas.armazens(level, a.getBlockPos()).isEmpty()) {
					f.add(Component.translatable("block.arraial.armazem"));
				}
				if (Prefeitura.obras(level, a.getBlockPos(), "poco") == 0) {
					f.add(Component.translatable("projeto.arraial.poco"));
				}
			}
			case VILA -> {
				if (Prefeitura.obras(level, a.getBlockPos(), "prefeitura") == 0) {
					f.add(Component.translatable("projeto.arraial.prefeitura"));
				}
				if (!a.temFeira(level)) {
					f.add(Component.translatable("projeto.arraial.feira"));
				}
			}
			case VILA_PROSPERA -> {
				if (postos(level, a, ModPois.BALCAO_BOTICA) == 0) {
					f.add(Component.translatable("projeto.arraial.botica"));
				}
				if (Prefeitura.obras(level, a.getBlockPos(), Muralha.ID) == 0) {
					f.add(Component.translatable("projeto.arraial.muralha"));
				}
			}
			case CIDADE -> {
				if (postos(level, a, ModPois.CANGALHA) == 0) {
					f.add(Component.translatable("projeto.arraial.rancho_tropeiro"));
				}
				if (a.satisfacao < 60) {
					f.add(Component.translatable("arraial.estagio.falta_satisfacao", 60));
				}
			}
			default -> {
			}
		}
		return f;
	}

	private static long postos(ServerLevel level, ArraialBlockEntity a, PoiType tipo) {
		return level.getPoiManager().getCountInRange(h -> h.value() == tipo, a.getBlockPos(), Vilas.RAIO, PoiManager.Occupancy.ANY);
	}

	/** Promotes the arraial as far as it qualifies; announces each new stage. */
	static void avaliar(ServerLevel level, ArraialBlockEntity a, int moradores) {
		while (a.estagio.ordinal() < values().length - 1) {
			Estagio proximo = values()[a.estagio.ordinal() + 1];
			if (!proximo.falta(level, a, moradores).isEmpty()) {
				return;
			}
			String antes = a.nomeCompleto();
			a.estagio = proximo;
			a.setChanged();
			Component texto = Component.translatable("arraial.cronica.estagio", antes, a.nomeCompleto(), proximo.nome());
			Cronica.anotar(level, a.getBlockPos(), texto);
			if (proximo == CIDADE) {
				Conquistas.darPerto(level, a.getBlockPos(), Vilas.RAIO * 2, "cidade");
			}
			Prefeitura.avisar(level, a, Component.literal("🎉 ").append(texto).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			var foguete = net.minecraft.world.entity.EntityTypes.FIREWORK_ROCKET.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (foguete != null) {
				foguete.setPos(a.getBlockPos().getX() + 0.5, a.getBlockPos().getY() + 2, a.getBlockPos().getZ() + 0.5);
				level.addFreshEntity(foguete);
			}
		}
	}
}
