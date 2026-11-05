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
			LiteralArgumentBuilder<CommandSourceStack> festa = Commands.literal("festa");
			for (Festa.Tipo t : Festa.Tipo.values()) {
				festa.then(Commands.literal(t.id()).executes(c -> {
					ArraialBlockEntity a = arraial(c.getSource());
					if (a == null) {
						return 0;
					}
					Festa.forcar(c.getSource().getLevel(), a, t);
					c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.festa", t.nome(a), a.nomeCompleto()), true);
					return 1;
				}));
			}
			festa.then(Commands.literal("fim").executes(c -> {
				ArraialBlockEntity a = arraial(c.getSource());
				if (a == null) {
					return 0;
				}
				Festa.encerrar(c.getSource().getLevel(), a);
				return 1;
			}));
			dispatcher.register(Commands.literal("arraial")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(crise)
					.then(festa)
					// The last entries of the Crônica, here in the chat.
					.then(Commands.literal("cronica").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						var l = a.cronica();
						for (int i = Math.max(0, l.size() - 10); i < l.size(); i++) {
							c.getSource().sendSystemMessage(l.get(i).linha());
						}
						return 1;
					}))
					// The council: put a proposal forward now, or vote the one in course now.
					.then(Commands.literal("conselho")
							.then(Commands.literal("propor").executes(c -> {
								ArraialBlockEntity a = arraial(c.getSource());
								return a == null ? 0 : Politica.forcar(c.getSource().getLevel(), a, false, c.getSource());
							}))
							.then(Commands.literal("votar").executes(c -> {
								ArraialBlockEntity a = arraial(c.getSource());
								return a == null ? 0 : Politica.forcar(c.getSource().getLevel(), a, true, c.getSource());
							})))
					// Bad blood with the nearest neighbour: war breaks out on the next day.
					.then(Commands.literal("guerra").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						String meu = Economia.id(c.getSource().getLevel(), a.getBlockPos());
						String alvo = Registro.todas().entrySet().stream().filter(e -> !e.getKey().equals(meu) && !e.getValue().abandonada)
								.min(java.util.Comparator.comparingDouble(e -> e.getValue().posicao().distSqr(a.getBlockPos()))).map(java.util.Map.Entry::getKey).orElse(null);
						if (alvo == null) {
							return 0;
						}
						Registro.mudarReputacao(meu, alvo, -60 - Registro.obter(meu).reputacaoCom(alvo));
						c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.guerra", a.nomeCompleto(), Registro.get(alvo).nome), true);
						return 1;
					}))
					// A mascate from a neighbour, right now.
					.then(Commands.literal("mascate").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						Comercio.mascate(c.getSource().getLevel(), a, a.moradores(c.getSource().getLevel()));
						return 1;
					}))
					// The region at a glance: every arraial, its specialty, people, state and roads.
					.then(Commands.literal("mundo").executes(c -> {
						MundoComando.listar(c.getSource());
						return 1;
					}))
					// Lives N days in every unloaded arraial right now (testing the living world).
					.then(Commands.literal("avancar").then(Commands.argument("dias", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
							.executes(c -> {
								int dias = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "dias");
								MundoComando.avancar(c.getSource(), dias);
								return 1;
							})))
					// A povoado right here, ignoring the distance rules (for testing and videos).
					.then(Commands.literal("vila_nova").executes(c -> {
						var p = c.getSource().getPlayer();
						ServerLevel level = c.getSource().getLevel();
						BlockPos onde = p != null ? p.blockPosition().relative(p.getDirection(), 24) : BlockPos.containing(c.getSource().getPosition());
						BlockPos centro = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, onde);
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
