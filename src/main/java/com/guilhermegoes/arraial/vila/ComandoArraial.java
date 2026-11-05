package com.guilhermegoes.arraial.vila;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * /arraial crise &lt;seca|enchente|praga|salteadores|fim&gt;: starts (or calls off) a crisis in the
 * nearest arraial, for testing and for showing it off on video. Operators only.
 */
public final class ComandoArraial {
	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, ambiente) -> {
			LiteralArgumentBuilder<CommandSourceStack> crise = Commands.literal("crise");
			for (Crise.Tipo t : Crise.Tipo.values()) {
				crise.then(Commands.literal(t.id()).executes(c -> {
					ArraialBlockEntity a = arraial(c.getSource());
					if (a == null) {
						return 0;
					}
					Crise.comecar(c.getSource().getLevel(), a, t);
					c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.crise", t.nome(), a.nome()), true);
					return 1;
				}));
			}
			crise.then(Commands.literal("fim").executes(c -> {
				ArraialBlockEntity a = arraial(c.getSource());
				if (a == null) {
					return 0;
				}
				Crise.terminar(c.getSource().getLevel(), a, "comando");
				c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.crise_fim", a.nome()), true);
				return 1;
			}));
			dispatcher.register(Commands.literal("arraial")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(crise));
		});
	}

	private static ArraialBlockEntity arraial(CommandSourceStack fonte) {
		ServerLevel level = fonte.getLevel();
		BlockPos pos = BlockPos.containing(fonte.getPosition());
		BlockPos vila = Vilas.maisProxima(level, pos);
		if (vila != null && level.getBlockEntity(vila) instanceof ArraialBlockEntity a) {
			return a;
		}
		fonte.sendFailure(Component.translatable("arraial.comando.sem_arraial"));
		return null;
	}

	private ComandoArraial() {
	}
}
