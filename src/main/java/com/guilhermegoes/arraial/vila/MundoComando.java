package com.guilhermegoes.arraial.vila;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/** The /arraial mundo and /arraial avancar commands: the region at a glance. */
final class MundoComando {
	static void listar(CommandSourceStack fonte) {
		ServerLevel level = fonte.getLevel();
		long hoje = Vilas.dia(level);
		fonte.sendSystemMessage(Component.literal("🗺 ").append(Component.translatable("arraial.mundo.titulo", Calendario.hoje(level)))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			fonte.sendSystemMessage(linha(level, e.getKey(), v, hoje));
			List<Registro.Evento> h = v.historia;
			for (int i = Math.max(0, h.size() - 3); i < h.size(); i++) {
				fonte.sendSystemMessage(Component.literal("    ").append(Component.translatable("arraial.cronica.dia", h.get(i).dia))
						.append(" ").append(h.get(i).texto()).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	static MutableComponent linha(ServerLevel level, String id, Registro.Info v, long hoje) {
		Component estado = v.abandonada ? Component.translatable("arraial.mundo.abandonada").withStyle(ChatFormatting.DARK_RED)
				: v.fome > 0 ? Component.translatable("arraial.mundo.fome").withStyle(ChatFormatting.RED)
				: !v.criseLonge.isEmpty() ? Component.translatable("arraial.historia.estado." + v.criseLonge).withStyle(ChatFormatting.RED)
				: Component.translatable(v.satisfacao >= 70 ? "arraial.mundo.prospera" : v.satisfacao < 35 ? "arraial.mundo.decadencia" : "arraial.mundo.estavel")
						.withStyle(v.satisfacao >= 70 ? ChatFormatting.GREEN : v.satisfacao < 35 ? ChatFormatting.GOLD : ChatFormatting.WHITE);
		MutableComponent l = Component.literal(" • " + v.nome + " ").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("arraial.mundo.linha", v.populacao, Economia.Especialidade.de(v.especialidade).nome(),
						v.tesouro, Mundo.comida(v)).withStyle(ChatFormatting.WHITE))
				.append(" · ").append(estado);
		if (Mundo.carregada(level, v)) {
			l.append(Component.translatable("arraial.mundo.carregada").withStyle(ChatFormatting.DARK_GRAY));
		}
		for (Map.Entry<String, Registro.Info> o : Registro.todas().entrySet()) {
			if (!o.getKey().equals(id) && Economia.bloqueada(id, o.getKey(), hoje)) {
				l.append(Component.literal(" ⛔ ").append(Component.translatable(Economia.emGuerra(id, o.getKey())
						? "arraial.mundo.guerra" : "arraial.mundo.rota_fechada", o.getValue().nome)).withStyle(ChatFormatting.RED));
			}
		}
		return l;
	}

	static void avancar(CommandSourceStack fonte, int dias) {
		int n = Mundo.forcarDias(fonte.getLevel(), dias);
		fonte.sendSuccess(() -> Component.translatable("arraial.comando.avancou", dias, n), true);
	}

	private MundoComando() {
	}
}
