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
					c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.crise", t.nome(), a.nomeCompleto()), true);
					return 1;
				}));
			}
			crise.then(Commands.literal("fim").executes(c -> {
				ArraialBlockEntity a = arraial(c.getSource());
				if (a == null) {
					return 0;
				}
				Crise.terminar(c.getSource().getLevel(), a, "comando");
				c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.crise_fim", a.nomeCompleto()), true);
				return 1;
			}));
			dispatcher.register(Commands.literal("arraial")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(crise)
					// A povoado right here, ignoring the distance rules (for testing and videos).
					.then(Commands.literal("vila_nova").executes(c -> {
						var p = c.getSource().getPlayerOrException();
						ServerLevel level = c.getSource().getLevel();
						BlockPos centro = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
								p.blockPosition().relative(p.getDirection(), 24));
						VilaNova.fundar(level, centro, p);
						return 1;
					}))
					// The tropeiro sets out now, or comes back now if he is away.
					.then(Commands.literal("tropa").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						ServerLevel level = c.getSource().getLevel();
						for (var m : a.moradores(level)) {
							if (m.profissao() == com.guilhermegoes.arraial.npc.Profissao.TROPEIRO) {
								if (m.viajando()) {
									m.setViagem(3, m.viagemDestino(), level.getGameTime());
								} else if (m.viagemFase() == 0) {
									Caravana.diarioForcado(level, a, m);
								}
								c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.tropa", m.nome()), true);
								return 1;
							}
						}
						c.getSource().sendFailure(Component.translatable("arraial.comando.sem_tropeiro"));
						return 0;
					}))
					// Relações públicas sets out on a mission now, or comes back now.
					.then(Commands.literal("missao").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						ServerLevel level = c.getSource().getLevel();
						for (var m : a.moradores(level)) {
							if (m.profissao() == com.guilhermegoes.arraial.npc.Profissao.RELACOES) {
								if (m.viajando()) {
									m.setViagem(3, m.viagemDestino(), level.getGameTime());
								} else if (m.viagemFase() == 0) {
									Diplomacia.forcar(level, a, m);
								}
								c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.tropa", m.nome()), true);
								return 1;
							}
						}
						c.getSource().sendFailure(Component.translatable("arraial.comando.sem_relacoes"));
						return 0;
					}))
					.then(Commands.literal("tesouro").then(Commands.argument("reis", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
							.executes(c -> {
								ArraialBlockEntity a = arraial(c.getSource());
								if (a == null) {
									return 0;
								}
								a.mudarTesouro(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "reis"));
								c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.tesouro", a.nomeCompleto(), a.tesouro()), true);
								return 1;
							}))));
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
