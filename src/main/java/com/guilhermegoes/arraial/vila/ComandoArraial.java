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
			// The era now, with every building and street reformed at once (free).
			LiteralArgumentBuilder<CommandSourceStack> era = Commands.literal("era");
			for (Era e : Era.values()) {
				era.then(Commands.literal(e.id()).executes(c -> {
					ArraialBlockEntity a = arraial(c.getSource());
					if (a == null) {
						return 0;
					}
					Era.mudar(c.getSource().getLevel(), a, e, false);
					int n = Era.reformar(c.getSource().getLevel(), a, 999, 99999, true);
					c.getSource().sendSuccess(() -> Component.translatable("arraial.comando.era", e.nome(), n), true);
					return 1;
				}));
			}
			dispatcher.register(Commands.literal("arraial")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(crise)
					.then(festa)
					.then(era)
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
					// Everybody leaves the nearest arraial (to see ruins and occupation).
					.then(Commands.literal("abandonar").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						ServerLevel lv = c.getSource().getLevel();
						a.abandonar(lv, a.moradores(lv));
						Registro.obter(Economia.id(lv, a.getBlockPos())).abandonada = true;
						return 1;
					}))
					// Bandits take the nearest abandoned arraial now.
					.then(Commands.literal("ocupar").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null || !a.abandonada()) {
							return 0;
						}
						Registro.Info v = Registro.obter(Economia.id(c.getSource().getLevel(), a.getBlockPos()));
						v.ocupadaPor = "bandidos";
						v.bandidos = 4;
						v.bandidosSpawnados = false;
						v.ruinaPendente = 6;
						return 1;
					}))
					// Your base (where you last slept) is taken by bandits, as if you had been away for days.
					.then(Commands.literal("base").executes(c -> {
						var p = c.getSource().getPlayerOrException();
						Bases.Base b = Bases.de(p.getUUID());
						if (b == null) {
							c.getSource().sendFailure(Component.translatable("arraial.base.sem_base"));
							return 0;
						}
						Bases.ocupar(b);
						c.getSource().sendSuccess(() -> Component.translatable("arraial.base.forcada"), false);
						return 1;
					}))
					// A wedding and a birth now (testing families).
					.then(Commands.literal("casamento").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						return a != null && Familias.casamento(c.getSource().getLevel(), a, a.moradores(c.getSource().getLevel()), true) ? 1 : 0;
					}))
					.then(Commands.literal("nascimento").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						return a != null && Familias.nascimento(c.getSource().getLevel(), a, a.moradores(c.getSource().getLevel()), true) != null ? 1 : 0;
					}))
					// A finished building right now, at the nearest arraial (testing eras and ruins).
					.then(Commands.literal("obra_pronta").then(Commands.argument("projeto", com.mojang.brigadier.arguments.StringArgumentType.word())
							.executes(c -> {
								ArraialBlockEntity a = arraial(c.getSource());
								String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "projeto");
								boolean ok = a != null && VilaNova.construirPronto(c.getSource().getLevel(), a.getBlockPos(), id);
								c.getSource().sendSystemMessage(Component.literal(id + (ok ? ": pronta" : ": sem lugar ou projeto desconhecido")));
								return ok ? 1 : 0;
							})))
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
					}).then(Commands.literal("missao").executes(c -> {
						var p = c.getSource().getPlayer();
						ServerLevel level = c.getSource().getLevel();
						BlockPos onde = p != null ? p.blockPosition().relative(p.getDirection(), 24) : BlockPos.containing(c.getSource().getPosition());
						VilaNova.fundar(level, level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, onde), p, true);
						return 1;
					})).then(Commands.literal("luso").executes(c -> {
						var p = c.getSource().getPlayer();
						ServerLevel level = c.getSource().getLevel();
						BlockPos onde = p != null ? p.blockPosition().relative(p.getDirection(), 24) : BlockPos.containing(c.getSource().getPosition());
						VilaNova.fundar(level, level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, onde), p, false);
						return 1;
					})))
					// A caravan of settlers walks in and founds a povoado 30 blocks ahead (for testing and videos).
					.then(Commands.literal("colonos").executes(c -> {
						var p = c.getSource().getPlayerOrException();
						ServerLevel level = c.getSource().getLevel();
						BlockPos centro = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
								p.blockPosition().relative(p.getDirection(), 30));
						return Colonos.partir(level, p, centro, VilaNova.sorteiaMissao(level)) ? 1 : 0;
					}))
					// A snapshot of the arraial (for bug reports).
					.then(Commands.literal("diagnostico").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						String r = Diagnostico.de(c.getSource().getLevel(), a).resumo();
						c.getSource().sendSuccess(() -> Component.literal(r), false);
						com.guilhermegoes.arraial.Arraial.LOGGER.info("Diagnostico: {}", r);
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
							if (m.profissao() == com.guilhermegoes.arraial.npc.Profissao.TROPEIRO || m.profissao() == com.guilhermegoes.arraial.npc.Profissao.BARQUEIRO) {
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
					// The navio mercante comes in now, or sails now if it is in port.
					.then(Commands.literal("navio").executes(c -> {
						ArraialBlockEntity a = arraial(c.getSource());
						if (a == null) {
							return 0;
						}
						ServerLevel level = c.getSource().getLevel();
						if (Navio.atracado(a)) {
							Navio.partir(level, a);
							return 1;
						}
						if (Navio.chegar(level, a)) {
							return 1;
						}
						c.getSource().sendFailure(Component.translatable("arraial.comando.sem_porto"));
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
