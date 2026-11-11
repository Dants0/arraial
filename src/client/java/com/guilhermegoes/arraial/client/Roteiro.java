package com.guilhermegoes.arraial.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Automated in-game test run for development (only with the environment variable ARRAIAL_ROTEIRO=1): opens the world
 * "teste", tries the new items (held in first and third person, the vaqueiro set, the inventory,
 * the advancements, a festa, the sickle, the flask, bandit loot), takes a screenshot of each step
 * into run/screenshots/arraial_*.png, logs the checks with [ROTEIRO] and closes the game.
 */
public final class Roteiro {
	private record Passo(int espera, Runnable acao, java.util.function.@org.jspecify.annotations.Nullable BooleanSupplier ate, int limite) {
	}

	private static final List<Passo> PASSOS = new ArrayList<>();
	private static int indice;
	private static int contador;
	private static boolean abriu;

	public static void init() {
		if (!"1".equals(System.getenv("ARRAIAL_ROTEIRO"))) {
			return;
		}
		montar();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!abriu) {
				if (mc.gui.screen() instanceof TitleScreen && ++contador > 60) {
					abriu = true;
					contador = 0;
					log("abrindo o mundo teste");
					mc.createWorldOpenFlows().openWorld("teste", () -> log("abertura cancelada"));
				}
				return;
			}
			if (mc.player == null || mc.level == null || indice >= PASSOS.size()) {
				return;
			}
			if (mc.player.isDeadOrDying()) {
				log("jogador morreu: renascendo");
				mc.player.respawn();
				contador = 0;
				return;
			}
			// The test window may lose focus: never leave the pause menu over the photos.
			if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
				mc.gui.setScreen(null);
			}
			if (++contador < PASSOS.get(indice).espera()) {
				return;
			}
			var condicao = PASSOS.get(indice).ate();
			int limite = PASSOS.get(indice).limite();
			if (condicao != null && !condicao.getAsBoolean()) {
				if (limite <= 0 || contador < PASSOS.get(indice).espera() + limite) {
					return;
				}
				log("passo " + indice + ": cansei de esperar (" + limite + " ticks), seguindo");
			}
			contador = 0;
			try {
				PASSOS.get(indice).acao().run();
			} catch (RuntimeException e) {
				log("ERRO no passo " + indice + ": " + e);
			}
			indice++;
		});
	}

	private static void log(String s) {
		System.out.println("[ROTEIRO] " + s);
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	/** Runs commands on the integrated server as the player, with full permission (no cheats needed). */
	private static void cmd(String... comandos) {
		var srv = mc().getSingleplayerServer();
		java.util.UUID id = mc().player.getUUID();
		srv.execute(() -> {
			var sp = srv.getPlayerList().getPlayer(id);
			for (String c : comandos) {
				var fonte = srv.createCommandSourceStack().withEntity(sp).withLevel(sp.level()).withPosition(sp.position())
						.withRotation(new net.minecraft.world.phys.Vec2(sp.getXRot(), sp.getYRot()));
				srv.getCommands().performPrefixedCommand(fonte, c);
			}
		});
	}

	private static void foto(String nome) {
		Minecraft mc = mc();
		Screenshot.grab(mc.gameDirectory, "arraial_" + nome + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> log("foto " + nome));
	}

	private static void passo(int espera, Runnable acao) {
		PASSOS.add(new Passo(espera, acao, null, 0));
	}

	/** Waits at least {@code espera} ticks and then until {@code ate} holds, then runs {@code acao}. */
	private static void passoAte(int espera, java.util.function.BooleanSupplier ate, Runnable acao) {
		PASSOS.add(new Passo(espera, acao, ate, 0));
	}

	/** Like {@link #passoAte}, but gives up waiting after {@code limite} ticks (logged) and runs the step anyway. */
	private static void passoAte(int espera, int limite, java.util.function.BooleanSupplier ate, Runnable acao) {
		PASSOS.add(new Passo(espera, acao, ate, limite));
	}

	private static String mao() {
		return BuiltInRegistries.ITEM.getKey(mc().player.getMainHandItem().getItem()).toString();
	}

	private static void montar() {
		passo(10, () -> {
			mc().options.pauseOnLostFocus = false; // the test window may not have focus: no pause menu over the photos
			cmd("gamemode creative", "kill @e[type=minecraft:vindicator]", "kill @e[type=minecraft:pillager]");
		});
		passo(190, () -> {
			log("no mundo; preparando");
			mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
			cmd("gamemode creative", "time set 6000", "weather clear", "effect clear @s", "kill @e[type=minecraft:vindicator]", "kill @e[type=minecraft:pillager]", "tp @s 0.5 121 -7.5 0 15");
		});
		// ARRAIAL_ROTEIRO_SO=integracoes: only the scenes of the optional integrations.
		if ("integracoes".equals(System.getenv("ARRAIAL_ROTEIRO_SO"))) {
			integracoes();
			passo(20, () -> {
				log("fim do roteiro");
				mc().stop();
			});
			return;
		}
		// ARRAIAL_ROTEIRO_SO=longo: a village founded by settlers on natural ground, left alone for many days.
		if ("longo".equals(System.getenv("ARRAIAL_ROTEIRO_SO"))) {
			testeLongo();
			passo(20, () -> {
				log("fim do roteiro");
				mc().stop();
			});
			return;
		}
		// ARRAIAL_ROTEIRO_SO=colonos: a caravan of settlers founds a village live.
		if ("colonos".equals(System.getenv("ARRAIAL_ROTEIRO_SO"))) {
			colonos();
			passo(20, () -> {
				log("fim do roteiro");
				mc().stop();
			});
			return;
		}
		// ARRAIAL_ROTEIRO_SO=icones: every item drawn as in the inventory, for the wiki (tools/gerar_wiki.js).
		if ("icones".equals(System.getenv("ARRAIAL_ROTEIRO_SO"))) {
			TelaIcones tela = new TelaIcones();
			passo(20, () -> {
				mc().gui.setScreen(tela);
			});
			passo(40, () -> {
				try {
					java.nio.file.Files.writeString(mc().gameDirectory.toPath().resolve("icones.json"), tela.manifesto(mc().getWindow().getGuiScale()));
				} catch (java.io.IOException e) {
					log("icones: " + e);
				}
				foto("icones");
			});
			passo(20, () -> {
				log("fim do roteiro");
				mc().stop();
			});
			return;
		}
		// ARRAIAL_ROTEIRO_SO=fauna: the Brazilian fauna, posed and then let loose.
		if ("fauna".equals(System.getenv("ARRAIAL_ROTEIRO_SO"))) {
			fauna();
			passo(20, () -> {
				log("fim do roteiro");
				mc().stop();
			});
			return;
		}
		// ARRAIAL_ROTEIRO_SO=rios: two villages on a river, a trapiche each, a canoe trip between them.
		if ("rios".equals(System.getenv("ARRAIAL_ROTEIRO_SO"))) {
			rios();
			passo(20, () -> {
				log("fim do roteiro");
				mc().stop();
			});
			return;
		}
		// ARRAIAL_ROTEIRO_SO=novas: only the newest scenes (quicker while working on them).
		if ("novas".equals(System.getenv("ARRAIAL_ROTEIRO_SO"))) {
			novidades();
			passo(20, () -> {
				log("fim do roteiro");
				mc().stop();
			});
			return;
		}
		// Held items, first person.
		String[] itens = {"facao", "peixeira", "foice", "cantil", "saco_saque", "comenda"};
		for (int i = 0; i < itens.length; i++) {
			String it = itens[i];
			int n = i + 1;
			passo(30, () -> cmd("item replace entity @s weapon.mainhand with arraial:" + it));
			passo(40, () -> foto("0" + n + "_mao_" + it));
		}
		// The vaqueiro, seen from the front, facão in hand.
		passo(20, () -> cmd("item replace entity @s armor.head with arraial:chapeu_couro", "item replace entity @s armor.chest with arraial:gibao",
				"item replace entity @s armor.legs with arraial:perneira", "item replace entity @s armor.feet with arraial:alpercatas",
				"item replace entity @s weapon.mainhand with arraial:facao"));
		passo(40, () -> mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		passo(140, () -> {
			log("vaqueiro: velocidade=" + mc().player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED));
			foto("07_vaqueiro_frente");
		});
		passo(10, () -> mc().options.setCameraType(CameraType.THIRD_PERSON_BACK));
		passo(40, () -> foto("08_vaqueiro_costas"));
		passo(10, () -> mc().options.setCameraType(CameraType.FIRST_PERSON));
		// The inventory with the new items (survival screen shows the plain grid).
		passo(10, () -> cmd("clear @s", "gamemode survival", "give @s arraial:facao", "give @s arraial:peixeira", "give @s arraial:foice",
				"give @s arraial:gibao", "give @s arraial:perneira", "give @s arraial:alpercatas", "give @s arraial:chapeu_couro",
				"give @s arraial:cantil", "give @s arraial:cantil_cheio", "give @s arraial:saco_saque 3", "give @s arraial:comenda",
				"give @s arraial:urna", "give @s arraial:marco_fronteira", "give @s arraial:bandeirinhas 8", "give @s arraial:quentao",
				"give @s arraial:pe_de_moleque 4"));
		passo(30, () -> mc().gui.setScreen(new InventoryScreen(mc().player)));
		passo(30, () -> foto("09_inventario"));
		// Advancements.
		passo(10, () -> {
			mc().gui.setScreen(null);
			cmd("gamemode creative", "advancement grant @s from arraial:root");
		});
		passo(40, () -> mc().gui.setScreen(new AdvancementsScreen(mc().player.connection.getAdvancements())));
		passo(40, () -> foto("10_conquistas"));
		// The sickle: a ripe 3x3 of wheat right below.
		passo(10, () -> {
			mc().gui.setScreen(null);
			cmd("tp @s 20.5 121 0.5 0 60", "fill 19 119 2 21 119 4 minecraft:farmland", "fill 19 120 2 21 120 4 minecraft:wheat[age=7]",
					"item replace entity @s weapon.mainhand with arraial:foice");
		});
		passo(40, () -> foto("11_trigo_maduro"));
		passo(5, () -> {
			BlockPos alvo = new BlockPos(20, 120, 3);
			mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(alvo), Direction.UP, alvo, false));
		});
		passo(30, () -> {
			int replantados = 0;
			for (BlockPos p : BlockPos.betweenClosed(19, 120, 2, 21, 120, 4)) {
				var s = mc().level.getBlockState(p);
				if (s.getBlock() instanceof net.minecraft.world.level.block.CropBlock c && c.getAge(s) == 0) {
					replantados++;
				}
			}
			log("foice: replantados=" + replantados + " de 9");
			foto("12_foice_colheu");
		});
		// The flask: water in front, use it.
		passo(10, () -> cmd("setblock 20 120 6 minecraft:water", "tp @s 20.5 121 4.5 0 35", "item replace entity @s weapon.mainhand with arraial:cantil"));
		passo(30, () -> mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND));
		passo(20, () -> log("cantil na mao depois de usar na agua: " + mao()));
		// Bandit loot.
		passo(10, () -> cmd("tp @s 0.5 121 -7.5 0 25", "summon minecraft:vindicator 0.5 121 -3.5 {Tags:[\"arraial_bandido\"],NoAI:1b}"));
		passo(20, () -> cmd("kill @e[tag=arraial_bandido]"));
		passo(40, () -> {
			long sacos = mc().level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, mc().player.getBoundingBox().inflate(12),
					e -> e.getItem().getItem() == com.guilhermegoes.arraial.registry.ModItems.SACO_SAQUE).size();
			log("sacos de saque no chao: " + sacos);
			foto("13_saque_bandido");
		});
		// A festa at the village.
		passo(10, () -> cmd("tp @s 0.5 126 -16.5 0 20", "arraial festa sao_joao"));
		passo(40, () -> {
			var srv = mc().getSingleplayerServer();
			srv.execute(() -> {
				var lv = srv.overworld();
				var v = com.guilhermegoes.arraial.vila.Vilas.maisProxima(lv, new BlockPos(0, 120, 0));
				if (v != null && lv.getBlockEntity(v) instanceof com.guilhermegoes.arraial.vila.ArraialBlockEntity a) {
					log("festa: vila=" + a.nomeCompleto() + " agora=" + a.festa().agora() + " centro=" + a.festa().centro(a));
				} else {
					log("festa: nenhuma vila perto de 0,120,0");
				}
			});
		});
		passo(260, () -> foto("14_festa"));
		passo(10, () -> cmd("arraial festa fim", "time set 6000"));

		// ---- Politics: the urna (empty hand: the council; paper: a proposal), then the vote.
		passo(20, () -> cmd("tp @s 0.5 121 -7.5 0 30", "setblock 0 120 -5 arraial:urna", "clear @s",
				"item replace entity @s weapon.mainhand with minecraft:paper 4"));
		passo(20, () -> clicarBloco(new BlockPos(0, 120, -5)));
		passo(20, () -> {
			cmd("item replace entity @s weapon.mainhand with minecraft:air");
		});
		passo(10, () -> clicarBloco(new BlockPos(0, 120, -5)));
		passo(30, () -> foto("15_conselho"));
		passo(10, () -> cmd("execute positioned 0 120 0 run arraial conselho votar"));
		passo(30, () -> foto("16_votacao"));

		// ---- A social mission: a morador asks for 3 apples; we bring them.
		passo(10, () -> servidor((srv, sp) -> {
			var m = moradorPerto(srv, sp, false);
			if (m == null) {
				log("missao: nenhum morador");
				return;
			}
			m.pessoa().pedirFavorTeste(sp.getUUID(), com.guilhermegoes.arraial.vila.Vilas.dia(srv.overworld()));
			m.setNoAi(true);
			sp.teleportTo(m.getX() + 2, m.getY(), m.getZ() + 2);
			olhar(sp, m);
			alvo = m.getId();
			log("missao: " + m.nome() + " pediu 3 macas");
		}));
		passo(20, () -> cmd("item replace entity @s weapon.mainhand with minecraft:apple 3"));
		passo(20, () -> clicarEntidade());
		passo(20, () -> log("missao: macas na mao depois de entregar = " + mc().player.getMainHandItem().getCount()));
		passo(10, () -> cmd("item replace entity @s weapon.mainhand with minecraft:air"));
		passo(10, () -> clicarEntidade());
		passo(30, () -> foto("17_ficha_morador"));
		passo(5, () -> servidor((srv, sp) -> {
			if (srv.overworld().getEntity(alvo) instanceof net.minecraft.world.entity.Mob mob) {
				mob.setNoAi(false);
			}
		}));

		// ---- Families: a wedding and a birth; look at the child.
		passo(10, () -> cmd("execute positioned 0 120 0 run arraial casamento", "execute positioned 0 120 0 run arraial nascimento"));
		passo(40, () -> servidor((srv, sp) -> {
			var m = moradorPerto(srv, sp, true);
			if (m == null) {
				log("familia: nenhuma crianca");
				return;
			}
			log("familia: nasceu " + m.nome() + " " + m.sobrenome() + " escala=" + m.getScale());
			sp.teleportTo(m.getX() + 2.5, m.getY() + 1, m.getZ() + 2.5);
			olhar(sp, m);
			alvo = m.getId();
		}));
		passo(30, () -> foto("18_crianca"));
		passo(5, () -> clicarEntidade());
		passo(30, () -> foto("19_ficha_crianca"));

		// ---- Territory: a Marco beyond the border, the border on screen, the Escrivaninha.
		passo(10, () -> cmd("tp @s 56.5 121 0.5 -90 40", "item replace entity @s weapon.mainhand with arraial:marco_fronteira"));
		passo(10, () -> servidor((srv, sp) -> chao = new BlockPos(58, sp.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 58, 0) - 1, 0)));
		passo(20, () -> clicarBloco(chao));
		passo(30, () -> foto("20_marco"));
		passo(10, () -> cmd("tp @s 140.5 121 0.5 -90 0"));
		passo(40, () -> foto("21_terra_de_ninguem"));
		passo(10, () -> cmd("tp @s 40.5 121 0.5 90 0"));
		passo(30, () -> foto("22_volta_ao_territorio"));
		passo(10, () -> cmd("tp @s 0.5 121 -7.5 0 30", "setblock 2 120 -5 arraial:escrivaninha",
				"item replace entity @s weapon.mainhand with minecraft:air"));
		// The Escrivaninha opens the Chancery: map, neighbours, a treaty, an embargo and lifting it.
		passo(10, () -> cmd("give @s minecraft:paper 4"));
		passo(20, () -> clicarBloco(new BlockPos(2, 120, -5)));
		passo(40, () -> {
			if (mc().gui.screen() instanceof TelaArraial t) {
				var d = t.dados();
				log("chancelaria: mesa=" + d.mesa + " vilas=" + d.vilas.size() + " meu=" + (d.meu >= 0 ? d.vilas.get(d.meu).nome : "-")
						+ " terras=" + d.terras.size() + " rotas=" + d.rotas.size() + " grade=" + d.w + "x" + d.h + "@" + d.celula + " papel=" + d.papel);
				vizinho = d.vilas.stream().filter(v -> d.meu < 0 || !v.id.equals(d.vilas.get(d.meu).id)).filter(v -> !v.abandonada)
						.map(v -> v.nome).findFirst().orElse("");
				log("chancelaria: vizinho escolhido = " + vizinho);
			} else {
				log("chancelaria: a tela NAO abriu (" + mc().gui.screen() + ")");
			}
		});
		passo(10, () -> foto("23_chancelaria"));
		passo(10, () -> {
			if (mc().gui.screen() instanceof TelaArraial t) {
				for (String acao : new String[] {"comercio", "alianca", "paz", "fronteira", "tributo"}) {
					if (t.testar(vizinho, acao)) {
						log("chancelaria: propus " + acao + " a " + vizinho);
						return;
					}
				}
				log("chancelaria: nenhuma proposta possivel a " + vizinho);
			}
		});
		passo(40, () -> {
			if (mc().gui.screen() instanceof TelaArraial t) {
				log("chancelaria: resposta = " + t.dados().mensagem.getString());
			}
		});
		passo(10, () -> foto("23b_proposta"));
		passo(10, () -> {
			if (mc().gui.screen() instanceof TelaArraial t) {
				log("chancelaria: embargo apertado = " + t.testar(vizinho, "embargo"));
			}
		});
		passo(40, () -> {
			if (mc().gui.screen() instanceof TelaArraial t) {
				var d = t.dados();
				var v = d.vilas.stream().filter(x -> x.nome.equals(vizinho)).findFirst().orElse(null);
				String meu = d.meu >= 0 ? d.vilas.get(d.meu).id : "";
				log("chancelaria: embargamos=" + (v != null && v.embargamos) + " estrada fechada no servidor="
						+ (v != null && com.guilhermegoes.arraial.vila.Economia.bloqueada(meu, v.id, 0)) + " msg=" + d.mensagem.getString());
			}
		});
		passo(10, () -> foto("23c_embargo"));
		passo(10, () -> {
			if (mc().gui.screen() instanceof TelaArraial t) {
				t.testar(vizinho, "embargo");
			}
		});
		passo(40, () -> {
			if (mc().gui.screen() instanceof TelaArraial t) {
				var v = t.dados().vilas.stream().filter(x -> x.nome.equals(vizinho)).findFirst().orElse(null);
				log("chancelaria: embargo suspenso = " + (v != null && !v.embargamos) + " msg=" + t.dados().mensagem.getString());
			}
			mc().gui.setScreen(null);
		});
		// The Mapa da Região from anywhere (a long way out, on the road).
		passo(10, () -> cmd("tp @s 90.5 121 40.5 0 0", "item replace entity @s weapon.mainhand with arraial:mapa_regiao"));
		passo(30, () -> mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND));
		passo(40, () -> {
			log("mapa: tela aberta = " + (mc().gui.screen() instanceof TelaArraial t ? "sim, mesa=" + t.dados().mesa + " vilas=" + t.dados().vilas.size() : "nao"));
		});
		passo(10, () -> foto("23d_mapa"));
		passo(10, () -> mc().gui.setScreen(null));

		// ---- Eras: new houses in colonial style, seen from above through the three eras.
		passo(10, () -> cmd("tp @s 0.5 121 -7.5 0 30", "execute positioned 0 120 0 run arraial era colonial",
				"execute positioned 0 120 0 run arraial obra_pronta casebre", "execute positioned 0 120 0 run arraial obra_pronta casebre",
				"execute positioned 0 120 0 run arraial obra_pronta rancho", "execute positioned 0 120 0 run arraial obra_pronta casebre"));
		passo(40, () -> cmd("setblock 22 140 22 minecraft:glass", "tp @s 22.5 141 22.5 135 40"));
		passo(60, () -> foto("24_era_colonial"));
		passo(5, () -> contarBlocos("colonial"));
		passo(10, () -> cmd("execute positioned 0 120 0 run arraial era imperial"));
		passo(60, () -> foto("25_era_imperial"));
		passo(5, () -> contarBlocos("imperial"));
		passo(10, () -> cmd("execute positioned 0 120 0 run arraial era republicana"));
		passo(60, () -> foto("26_era_republicana"));
		passo(5, () -> contarBlocos("republicana"));
		// The colonial blocks side by side.
		passo(10, () -> cmd("fill 28 139 30 37 139 32 minecraft:grass_block", "setblock 29 140 31 arraial:taipa", "setblock 30 140 31 arraial:telha",
				"setblock 31 140 31 arraial:telha_escada[facing=north]", "setblock 32 140 31 arraial:telha_laje", "setblock 33 140 31 arraial:cal",
				"setblock 34 140 31 arraial:azulejo", "setblock 35 140 31 arraial:azulejo", "setblock 35 141 31 arraial:cal",
				"setblock 34 141 31 arraial:cal", "setblock 34 142 31 arraial:telha_escada[facing=north]", "setblock 35 142 31 arraial:telha_escada[facing=north]",
				"setblock 32 140 27 minecraft:glass", "tp @s 32.5 141 27.5 0 15"));
		passo(60, () -> foto("27_blocos_coloniais"));
		// The skins: every trade, men and women, in a row on a platform in the sky; front and back.
		passo(10, () -> cmd("fill 36 149 36 62 149 56 minecraft:grass_block", "time set 6000", "weather clear"));
		for (int grupo = 0; grupo < 4; grupo++) {
			final int g = grupo;
			passo(10, () -> {
				List<String> c = new ArrayList<>();
				c.add("kill @e[tag=vitrine]");
				boolean fem = g >= 2;
				for (int k = 0; k < 9; k++) {
					int prof = (g % 2) * 9 + k;
					c.add("summon arraial:morador " + (45 + k) + " 150 46 {NoAI:1b,Invulnerable:1b,Tags:[\"vitrine\"],Rotation:[0f,0f],Profissao:" + prof
							+ ",Visual:" + ((prof + (fem ? 1 : 0)) % 6) + ",Feminino:" + (fem ? "1b" : "0b") + "}");
				}
				c.add("tp @s 49.5 150.2 53.5 180 4");
				cmd(c.toArray(new String[0]));
			});
			passo(50, () -> foto("29_skins_" + (g < 2 ? "homens_" : "mulheres_") + (g % 2 + 1)));
			if (g == 2) {
				passo(10, () -> cmd("tp @s 49.5 150.2 39.5 0 4"));
				passo(40, () -> foto("29_skins_mulheres_costas"));
			}
		}
		passo(10, () -> cmd("kill @e[tag=vitrine]"));
		novidades();
		// The Guia: every page must fit the book (114 px wide, 14 lines); photos of a few.
		passo(10, () -> cmd("item replace entity @s weapon.mainhand with arraial:guia"));
		passo(20, () -> mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND));
		passo(20, () -> {
			var livro = mc().player.getMainHandItem();
			var acesso = net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess.fromItem(livro);
			if (acesso == null) {
				log("guia: nao virou livro (" + mao() + ")");
				return;
			}
			// Measured in both languages (the dev client runs in English).
			var antes = net.minecraft.locale.Language.getInstance();
			for (String lingua : new String[] {"en_us", "pt_br"}) {
				net.minecraft.locale.Language.inject(net.minecraft.client.resources.language.ClientLanguage.loadFrom(mc().getResourceManager(),
						List.of("en_us", lingua), false));
				int grandes = 0;
				for (int i = 0; i < acesso.getPageCount(); i++) {
					int linhas = mc().font.split(acesso.getPage(i), 114).size();
					if (linhas > 14) {
						grandes++;
						log("guia " + lingua + ": pagina " + (i + 1) + " com " + linhas + " linhas: "
								+ acesso.getPage(i).getString().replace('\n', ' ').substring(0, 40));
					}
				}
				log("guia " + lingua + ": " + acesso.getPageCount() + " paginas, " + grandes + " grandes demais");
			}
			net.minecraft.locale.Language.inject(antes);
			mc().gui.setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(acesso));
		});
		for (int pg : new int[] {0, 1, 2, 7, 18, 28, 30, 39, 45}) {
			passo(15, () -> {
				if (mc().gui.screen() instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen b) {
					b.setPage(pg);
				}
			});
			passo(15, () -> foto("28_guia_p" + (pg + 1)));
		}
		passo(10, () -> mc().gui.setScreen(null));
		passo(20, () -> {
			log("fim do roteiro");
			mc().stop();
		});
	}

	/** Jade, Mod Menu and Xaero's Minimap (installed in the dev client). */
	private static void integracoes() {
		// Mod Menu knows us; our settings screen.
		passo(10, () -> {
			boolean registrado = net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class).stream()
					.anyMatch(e -> e.getProvider().getMetadata().getId().equals("arraial"));
			log("modmenu: arraial registrado=" + registrado + " | jade carregado=" + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jade")
					+ " | xaero carregado=" + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap"));
			mc().gui.setScreen(new TelaConfig(null));
		});
		passo(30, () -> foto("40_config"));
		passo(10, () -> mc().gui.setScreen(null));
		// Jade on a villager and on the Cruzeiro (the player walks over the village's land, so it becomes known).
		passo(10, () -> servidor((srv, sp) -> {
			BlockPos v = com.guilhermegoes.arraial.vila.Vilas.maisProxima(sp.level(), new BlockPos(0, 120, 0));
			alvoJade = v == null ? new BlockPos(0, 120, 0) : v;
			sp.getAbilities().flying = true;
			sp.onUpdateAbilities();
			sp.teleportTo(sp.level(), alvoJade.getX() + 0.5, alvoJade.getY() + 1.5, alvoJade.getZ() - 6.5, java.util.Set.of(), 0, 10, true);
			cmd("time set 6000", "summon arraial:morador " + (alvoJade.getX() + 0.5) + " " + (alvoJade.getY() + 1) + " " + (alvoJade.getZ() - 4.5)
					+ " {NoAI:1b,Invulnerable:1b,Profissao:3,Visual:1,Rotation:[180f,0f],Tags:[\"jade\"]}");
		}));
		passo(40, () -> servidor((srv, sp) -> sp.level().getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class,
				sp.getBoundingBox().inflate(8), e -> e.entityTags().contains("jade")).stream().findFirst().ifPresent(e -> olhar(sp, e))));
		passo(40, () -> foto("41_jade_morador"));
		passo(10, () -> servidor((srv, sp) -> {
			cmd("kill @e[tag=jade]");
			Vec3 olho = Vec3.atCenterOf(alvoJade).add(0, 1.6, -2.6);
			Vec3 d = Vec3.atCenterOf(alvoJade).add(0, -0.15, 0).subtract(olho);
			float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
			float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
			sp.teleportTo(sp.level(), olho.x, olho.y - 1.62, olho.z, java.util.Set.of(), yaw, pitch, true);
		}));
		passo(50, () -> foto("42_jade_cruzeiro"));
		// Xaero: the known villages become waypoints (the list is sent every 5 s).
		passo(140, () -> log("xaero: " + IntegracaoXaero.estado()));
		passo(10, () -> cmd("tp @s " + (alvoJade.getX() + 0.5) + " " + (alvoJade.getY() + 30) + " " + (alvoJade.getZ() - 30.5) + " 0 40"));
		passo(60, () -> foto("43_xaero_minimapa"));
		passo(10, () -> log("xaero depois: " + IntegracaoXaero.estado()));
		// Farmer's Delight: the dishes' recipes loaded for the cooking pot; their icons in the hotbar. REI loaded?
		passo(10, () -> servidor((srv, sp) -> {
			StringBuilder s = new StringBuilder("farmersdelight carregado=" + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("farmersdelight")
					+ " rei carregado=" + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("roughlyenoughitems") + " receitas:");
			for (String r : new String[] {"baiao_de_dois", "moqueca", "vaca_atolada"}) {
				var chave = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, com.guilhermegoes.arraial.Arraial.id(r));
				s.append(' ').append(r).append('=').append(srv.getRecipeManager().byKey(chave).map(h -> h.value().getType().toString()).orElse("ausente"));
			}
			log(s.toString());
			cmd("clear @s", "give @s arraial:baiao_de_dois", "give @s arraial:moqueca", "give @s arraial:vaca_atolada", "give @s arraial:peixeira",
					"give @s farmersdelight:cooking_pot", "tp @s " + (alvoJade.getX() + 0.5) + " " + (alvoJade.getY() + 1) + " " + (alvoJade.getZ() - 6.5) + " 0 20");
		}));
		passo(40, () -> foto("44_pratos"));
	}

	/** The Brazilian fauna on a clean stage in the sky: a posed line-up, close-ups, then the onça let loose on prey. */
	private static void fauna() {
		String[] bichos = {"capivara", "tamandua", "jacare", "onca", "arara"};
		// Stage: grass at y=200 around x=300, a pool of water on one side, walls of air above.
		passo(10, () -> cmd("gamemode creative", "tp @s 300.5 205 290.5 0 30", "time set 6000"));
		passo(40, () -> cmd("fill 280 199 290 320 199 330 minecraft:dirt", "fill 280 200 290 320 200 330 minecraft:grass_block",
				"fill 280 201 290 320 210 330 minecraft:air", "fill 305 200 315 315 200 325 minecraft:water",
				"fill 279 201 289 321 202 289 minecraft:oak_fence", "fill 279 201 331 321 202 331 minecraft:oak_fence",
				"fill 279 201 289 279 202 331 minecraft:oak_fence", "fill 321 201 289 321 202 331 minecraft:oak_fence"));
		passo(20, () -> {
			for (int i = 0; i < bichos.length; i++) {
				cmd("summon arraial:" + bichos[i] + " " + (293.5 + i * 3.5) + " 201 300.5 {NoAI:1b,Rotation:[160f,0f]}");
			}
			cmd("summon arraial:capivara 292.0 201 303.0 {NoAI:1b,Age:-24000,Rotation:[200f,0f]}",
					"summon arraial:arara 309.5 201 303.0 {NoAI:1b,Variant:1,Rotation:[160f,0f]}",
					"summon arraial:arara 311.5 201 303.0 {NoAI:1b,Variant:2,Rotation:[200f,0f]}");
			cmd("tp @s 300.5 203.5 293.5 0 20");
		});
		passo(60, () -> foto("70_fauna_todos"));
		for (int i = 0; i < bichos.length; i++) {
			final int n = i;
			passo(10, () -> cmd("tp @s " + (292.0 + n * 3.5) + " 202 297.5 -30 20"));
			passo(30, () -> foto("71_fauna_" + bichos[n]));
		}
		passo(10, () -> cmd("tp @s 310.5 202 299.0 0 20"));
		passo(30, () -> foto("72_fauna_araras"));
		// Let them loose: the onça with chickens and a capivara; the jacaré in the pool.
		passo(10, () -> cmd("kill @e[x=280,y=195,z=285,dx=45,dy=20,dz=50,type=!minecraft:player]", "gamemode spectator",
				"summon arraial:onca 290.5 201 310.5", "summon minecraft:chicken 296.5 201 318.5", "summon minecraft:chicken 297.5 201 318.5",
				"summon arraial:capivara 286.5 201 318.5", "summon arraial:tamandua 284.5 201 300.5", "summon arraial:arara 300.5 203 305.5",
				"summon arraial:jacare 310.5 201 320.5", "summon arraial:capivara 303.5 201 322.5",
				"tp @s 300.5 214 290.5 0 45"));
		passo(100, () -> foto("73_fauna_solta"));
		passo(500, () -> servidor((srv, sp) -> {
			var level = sp.level();
			var caixa = new net.minecraft.world.phys.AABB(278, 195, 288, 322, 215, 332);
			log("fauna solta: onca " + level.getEntities(com.guilhermegoes.arraial.registry.ModEntities.ONCA, caixa, e -> true).size()
					+ ", galinhas " + level.getEntities(net.minecraft.world.entity.EntityTypes.CHICKEN, caixa, e -> true).size()
					+ ", capivaras " + level.getEntities(com.guilhermegoes.arraial.registry.ModEntities.CAPIVARA, caixa, e -> true).size()
					+ ", jacare " + level.getEntities(com.guilhermegoes.arraial.registry.ModEntities.JACARE, caixa, e -> true).size()
					+ ", tamandua " + level.getEntities(com.guilhermegoes.arraial.registry.ModEntities.TAMANDUA, caixa, e -> true).size()
					+ ", araras " + level.getEntities(com.guilhermegoes.arraial.registry.ModEntities.ARARA, caixa, e -> true).size());
		}));
		passo(10, () -> foto("74_fauna_depois"));
	}

	/** A caravan of settlers walks in on natural ground, plants the Cruzeiro and builds the first works. */
	private static void colonos() {
		acharTerrenoNatural();
		passo(10, () -> servidor((srv, sp) -> {
			if (natural == null) {
				log("colonos: sem terreno natural");
				return;
			}
			cmd("tp @s " + natural.getX() + " " + (natural.getY() + 6) + " " + (natural.getZ() - 30) + " 0 10", "time set 1000", "gamemode spectator");
		}));
		passo(60, () -> cmd("arraial colonos"));
		passo(100, () -> colonosEstado("partiu"));
		passo(10, () -> foto("50_caravana"));
		for (int i = 0; i < 6; i++) {
			passo(200, () -> colonosEstado("andando"));
		}
		passo(10, () -> foto("51_chegada"));
		// The building, sped up.
		passo(10, () -> cmd("tick rate 100"));
		for (int i = 0; i < 6; i++) {
			final int n = i;
			passo(600, () -> {
				colonosEstado("obra");
				if (n == 2 || n == 5) {
					foto("52_obra_" + n);
				}
			});
		}
		passo(10, () -> cmd("tick rate 20"));
	}

	/**
	 * The long run: settlers found a village on natural ground and it is left alone (no commands)
	 * for {@code ARRAIAL_DIAS} days (20 by default), the game sped up with /tick sprint. Every day
	 * each arraial nearby is written to run/teste_longo.csv (see vila.Diagnostico); every few days a
	 * photo. The player only watches, from above, in spectator mode.
	 */
	private static void testeLongo() {
		int dias = Integer.parseInt(System.getenv().getOrDefault("ARRAIAL_DIAS", "20"));
		acharTerrenoNatural();
		passo(10, () -> servidor((srv, sp) -> {
			if (natural == null) {
				log("longo: sem terreno natural");
				return;
			}
			cmd("tp @s " + natural.getX() + " " + (natural.getY() + 30) + " " + (natural.getZ() - 40) + " 0 35", "time set 1000", "gamemode spectator");
			try {
				java.nio.file.Files.writeString(mc().gameDirectory.toPath().resolve("teste_longo.csv"),
						com.guilhermegoes.arraial.vila.Diagnostico.CABECALHO + "\n");
			} catch (java.io.IOException e) {
				log("longo: csv " + e);
			}
		}));
		passo(60, () -> cmd("arraial colonos"));
		// Let the caravan arrive at normal speed (it walks in from 30-40 blocks).
		for (int i = 0; i < 6; i++) {
			passo(200, () -> colonosEstado("chegada"));
		}
		for (int d = 1; d <= dias; d++) {
			final int dia = d;
			passo(5, () -> {
				inicioDia = System.currentTimeMillis();
				cmd("tick sprint 24000");
			});
			passoAte(40, () -> !mc().getSingleplayerServer().tickRateManager().isSprinting(), () -> servidor((srv, sp) -> {
				var level = sp.level();
				StringBuilder csv = new StringBuilder();
				var vilas = com.guilhermegoes.arraial.vila.Diagnostico.perto(level, natural == null ? sp.blockPosition() : natural, 400);
				for (var a : vilas) {
					var r = com.guilhermegoes.arraial.vila.Diagnostico.de(level, a);
					csv.append(r.csv(dia)).append('\n');
					log("longo dia " + dia + ": " + r.resumo());
				}
				log("longo dia " + dia + ": " + vilas.size() + " vila(s), dia simulado em " + (System.currentTimeMillis() - inicioDia) / 1000 + " s");
				try {
					java.nio.file.Files.writeString(mc().gameDirectory.toPath().resolve("teste_longo.csv"), csv.toString(),
							java.nio.file.StandardOpenOption.APPEND);
				} catch (java.io.IOException e) {
					log("longo: csv " + e);
				}
			}));
			if (d % 5 == 0) {
				passo(40, () -> foto("60_longo_dia" + dia));
			}
		}
	}

	private static long inicioDia;

	private static void colonosEstado(String fase) {
		servidor((srv, sp) -> {
			var level = sp.level();
			var perto = level.getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class, sp.getBoundingBox().inflate(120), e -> true);
			long colonos = perto.stream().filter(e -> e.fundacao() != null).count();
			StringBuilder s = new StringBuilder("colonos " + fase + ": a caminho=" + colonos + " moradores=" + perto.size());
			perto.stream().filter(e -> e.liderColonos()).findFirst().ifPresent(l -> s.append(" lider em ").append(l.blockPosition().toShortString())
					.append(" faltam ").append((int) Math.sqrt(l.blockPosition().distSqr(l.fundacao()))));
			BlockPos v = natural == null ? null : com.guilhermegoes.arraial.vila.Vilas.maisProxima(level, natural);
			if (v != null) {
				s.append(" | vila em ").append(v.toShortString());
				for (var c : com.guilhermegoes.arraial.obra.CanteiroBlockEntity.daVila(level, v)) {
					s.append(" [").append(c.projeto().map(p -> p.id()).orElse("?")).append(' ').append(c.progresso()).append(c.concluida() ? " pronta" : "").append(']');
				}
				perto.stream().filter(e -> e.profissao() == com.guilhermegoes.arraial.npc.Profissao.PEDREIRO).findFirst()
						.ifPresent(e -> s.append(" pedreiro: ").append(e.status().getString()));
			}
			log(s.toString());
		});
	}

	/** The fazendas, the armour, the Baroque and the Spanish missions. */
	private static void novidades() {
		// ---- Armour from other mods: a soldado takes the best gear in the armazém.
		passo(10, () -> cmd("time set 1000", "kill @e[tag=vitrine]", "tp @s 2.5 121 0.5 0 10",
				"summon arraial:morador 2.5 121 4.5 {NoAI:1b,Invulnerable:1b,Tags:[\"teste_soldado\"],Rotation:[180f,0f],Profissao:5,Visual:2}",
				"summon arraial:morador 6.5 121 4.5 {NoAI:1b,Invulnerable:1b,Profissao:6,Visual:4}"));
		passo(20, () -> servidor((srv, sp) -> {
			var level = sp.level();
			BlockPos vila = com.guilhermegoes.arraial.vila.Vilas.maisProxima(level, new BlockPos(0, 120, 0));
			var armazens = com.guilhermegoes.arraial.vila.Vilas.armazens(level, vila);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE, 1);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.IRON_LEGGINGS, 1);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.NETHERITE_SWORD, 1);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.BEEF, 12);
			for (var m : level.getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class, new net.minecraft.world.phys.AABB(new BlockPos(2, 121, 4)).inflate(20),
					e -> e.profissao() == com.guilhermegoes.arraial.npc.Profissao.SOLDADO && e.distanceToSqr(2.5, 121, 4.5) < 9)) {
				m.definirVila(vila);
				m.vestirDoArmazem(level);
				log("armadura: cabeca=" + id(m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) + " peito="
						+ id(m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) + " pernas="
						+ id(m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)) + " mao=" + id(m.getMainHandItem()));
			}
			for (var m : level.getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class, new net.minecraft.world.phys.AABB(new BlockPos(2, 121, 4)).inflate(20),
					e -> e.profissao() == com.guilhermegoes.arraial.npc.Profissao.PASTOR)) {
				m.definirVila(vila);
			}
			// Charque from the beef, cheese from the corral (the morning routine).
			if (level.getBlockEntity(vila) instanceof com.guilhermegoes.arraial.vila.ArraialBlockEntity a) {
				int antes = com.guilhermegoes.arraial.vila.Vilas.contar(armazens, com.guilhermegoes.arraial.registry.ModItems.CHARQUE);
				com.guilhermegoes.arraial.vila.Agro.diario(level, a, a.moradores(level));
				log("charque: " + antes + " -> " + com.guilhermegoes.arraial.vila.Vilas.contar(armazens, com.guilhermegoes.arraial.registry.ModItems.CHARQUE)
						+ " (pastor na vila: " + a.moradores(level).stream().anyMatch(m -> m.profissao() == com.guilhermegoes.arraial.npc.Profissao.PASTOR) + ")");
			}
		}));
		passo(10, () -> cmd("tp @s 2.5 121 0.5 0 10"));
		passo(40, () -> foto("30_soldado_armadura"));

		// ---- The fazendas, on a fresh flat field with a new Portuguese village.
		passo(10, () -> servidor((srv, sp) -> {
			for (String p : new String[] {"engenho", "cafezal", "oficina_entalhe", "matriz", "missao"}) {
				var t = sp.level().getStructureTemplateManager().get(com.guilhermegoes.arraial.Arraial.id(p));
				log("planta " + p + ": " + (t.isPresent() ? t.get().getSize().toShortString() : "NAO carregou"));
			}
		}));
		campoPlano(300, 0);
		passo(10, () -> cmd("tp @s 350.5 121 -24.5 0 0", "arraial vila_nova luso"));
		passo(40, () -> cmd("execute positioned 350 120 0 run arraial obra_pronta engenho", "execute positioned 350 120 0 run arraial obra_pronta cafezal",
				"execute positioned 350 120 0 run arraial obra_pronta matriz"));
		passo(20, () -> servidor((srv, sp) -> {
			var level = sp.level();
			for (String obra : new String[] {"engenho", "cafezal", "matriz"}) {
				BlockPos c = obra(level, obra);
				log("obra " + obra + ": " + (c == null ? "NAO construida" : c.toShortString()));
			}
			BlockPos eng = obra(level, "engenho"), caf = obra(level, "cafezal");
			if (eng != null) {
				cmd("summon arraial:morador " + (eng.getX() + 1) + " " + (eng.getY() + 1) + " " + (eng.getZ() + 1) + " {Profissao:18,Visual:1}");
			}
			if (caf != null) {
				cmd("summon arraial:morador " + (caf.getX() + 1) + " " + (caf.getY() + 1) + " " + (caf.getZ() + 1) + " {Profissao:19,Visual:3,Feminino:1b}",
						"fill " + (caf.getX() - 12) + " " + (caf.getY() - 2) + " " + (caf.getZ() - 12) + " " + (caf.getX() + 12) + " " + (caf.getY() + 3)
								+ " " + (caf.getZ() + 12) + " arraial:cafeeiro[age=3] replace arraial:cafeeiro");
			}
		}));
		passo(20, () -> cmd("time set 1000"));
		passo(900, () -> servidor((srv, sp) -> {
			var level = sp.level();
			var armazens = com.guilhermegoes.arraial.vila.Vilas.armazens(level, com.guilhermegoes.arraial.vila.Vilas.maisProxima(level, new BlockPos(350, 120, 0)));
			StringBuilder trab = new StringBuilder();
			for (var m : level.getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class, sp.getBoundingBox().inflate(80),
					e -> e.profissao() == com.guilhermegoes.arraial.npc.Profissao.MESTRE_ENGENHO || e.profissao() == com.guilhermegoes.arraial.npc.Profissao.CAFEICULTOR)) {
				trab.append(m.nome()).append(' ').append(m.profissao().id()).append(" posto=").append(m.posto() != null).append(" carga=").append(m.cargaCopia()).append("; ");
			}
			log("fazendas: acucar=" + com.guilhermegoes.arraial.vila.Vilas.contar(armazens, net.minecraft.world.item.Items.SUGAR)
					+ " rapadura=" + com.guilhermegoes.arraial.vila.Vilas.contar(armazens, com.guilhermegoes.arraial.registry.ModItems.RAPADURA)
					+ " cafe_torrado=" + com.guilhermegoes.arraial.vila.Vilas.contar(armazens, com.guilhermegoes.arraial.registry.ModItems.CAFE_TORRADO)
					+ " | " + trab);
		}));
		passo(10, () -> verObra("engenho", 9));
		passo(40, () -> foto("31_engenho"));
		passo(10, () -> verObra("cafezal", 8));
		passo(40, () -> foto("32_cafezal"));
		passo(10, () -> verObra("matriz", 16));
		passo(50, () -> foto("33_matriz"));

		// ---- A Spanish mission next door.
		campoPlano(300, 200);
		passo(10, () -> cmd("tp @s 350.5 121 176.5 0 0", "arraial vila_nova missao"));
		passo(60, () -> servidor((srv, sp) -> {
			var level = sp.level();
			BlockPos v = com.guilhermegoes.arraial.vila.Vilas.maisProxima(level, new BlockPos(350, 120, 200));
			if (v != null && level.getBlockEntity(v) instanceof com.guilhermegoes.arraial.vila.ArraialBlockEntity a) {
				StringBuilder nomes = new StringBuilder();
				for (var m : a.moradores(level)) {
					nomes.append(m.nome()).append(' ').append(m.sobrenome()).append(", ");
				}
				log("missao: " + a.nomeCompleto() + " hispano=" + a.hispano() + " igreja=" + (obra(level, "missao") != null) + " moradores: " + nomes);
			} else {
				log("missao: NAO fundada");
			}
		}));
		passo(10, () -> verObra("missao", 14));
		passo(50, () -> foto("34_missao"));

		// ---- Gallery: a real village on natural ground (dry, flat-ish grassland found by biome, then checked), for the mod pages.
		acharTerrenoNatural();
		passo(10, () -> servidor((srv, sp) -> {
			if (natural == null) {
				return;
			}
			cmd("tp @s " + natural.getX() + " " + (natural.getY() + 1) + " " + (natural.getZ() - 24) + " 0 0", "arraial vila_nova luso");
		}));
		passo(60, () -> servidor((srv, sp) -> {
			if (natural == null) {
				return;
			}
			BlockPos v = com.guilhermegoes.arraial.vila.Vilas.maisProxima(sp.level(), natural);
			if (v == null) {
				log("galeria: vila nao fundada");
				return;
			}
			natural = v;
			String em = "execute positioned " + v.getX() + " " + v.getY() + " " + v.getZ() + " run arraial ";
			cmd(em + "obra_pronta casebre", em + "obra_pronta casebre", em + "obra_pronta rancho", em + "obra_pronta roca", em + "obra_pronta capela",
					em + "obra_pronta engenho", em + "obra_pronta cafezal", em + "obra_pronta poco", em + "obra_pronta casebre", em + "era imperial");
		}));
		passo(80, () -> servidor((srv, sp) -> {
			if (natural != null) {
				Vec3 olho = Vec3.atCenterOf(natural).add(-26, 22, -26);
				sp.teleportTo(sp.level(), olho.x, olho.y, olho.z, java.util.Set.of(), -45, 32, true);
				sp.getAbilities().flying = true;
				sp.onUpdateAbilities();
			}
		}));
		passo(10, () -> {                                // clean shots for the mod pages (like F1)
			if (!mc().gui.hud.isHidden()) {
				mc().gui.hud.toggle();
			}
		});
		passo(60, () -> foto("35_vila_natural"));
		passo(10, () -> verObra("capela", 16));
		passo(50, () -> foto("36_capela_natural"));
		passo(10, () -> verObra("engenho", 12));
		passo(50, () -> foto("37_engenho_natural"));
		passo(10, () -> verObra("cafezal", 10));
		passo(50, () -> foto("38_cafezal_natural"));
		passo(10, () -> {
			if (mc().gui.hud.isHidden()) {
				mc().gui.hud.toggle();
			}
		});
	}

	// ---------------------------------------------------------------- helpers for the scenes

	private static int alvo = -1;
	private static BlockPos chao = BlockPos.ZERO;
	private static String vizinho = "";
	private static BlockPos alvoJade = BlockPos.ZERO;
	/** Finds dry, flat-ish grassland far from the test village (result in {@link #natural}). */
	private static void acharTerrenoNatural() {
		passo(10, () -> servidor((srv, sp) -> {
			var level = sp.level();
			var resolvedor = level.getChunkSource().getGenerator().getBiomeSource().createCachingResolver(level.getChunkSource().randomState());
			candidatos.clear();
			for (int r = 500; r <= 4000 && candidatos.size() < 6; r += 250) {
				for (int k = 0; k < 16 && candidatos.size() < 6; k++) {
					int x = (int) (Math.cos(k * Math.PI / 8) * r), z = (int) (Math.sin(k * Math.PI / 8) * r);
					boolean todos = true;
					for (int dx = -48; dx <= 48 && todos; dx += 24) {
						for (int dz = -48; dz <= 48 && todos; dz += 24) {
							String b = resolvedor.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x + dx), net.minecraft.core.QuartPos.fromBlock(64),
									net.minecraft.core.QuartPos.fromBlock(z + dz)).unwrapKey().map(c -> c.identifier().getPath()).orElse("");
							todos = b.equals("plains") || b.equals("savanna") || b.equals("sunflower_plains") || b.equals("meadow");
						}
					}
					if (todos) {
						candidatos.add(new BlockPos(x, 0, z));
					}
				}
			}
			log("galeria: " + candidatos.size() + " candidatos de planicie");
		}));
		for (int tentativa = 0; tentativa < 4; tentativa++) {
			final int t = tentativa;
			passo(10, () -> {
				if (natural == null && t < candidatos.size()) {
					BlockPos c = candidatos.get(t);
					cmd("tp @s " + c.getX() + " 220 " + c.getZ() + " 0 90", "time set 1000");
				}
			});
			passo(500, () -> servidor((srv, sp) -> {
				if (natural != null || t >= candidatos.size()) {
					return;
				}
				natural = lugarSecoPlano(sp.level(), candidatos.get(t));
				log("galeria: tentativa " + t + " em " + candidatos.get(t).toShortString() + " -> " + (natural == null ? "sem lugar seco e plano" : natural.toShortString()));
			}));
		}
	}

	private static @org.jspecify.annotations.Nullable BlockPos natural;
	private static final java.util.List<BlockPos> candidatos = new java.util.ArrayList<>();

	/** A flat grass field 100x100 at y=120 (cleared above), centred on x0+50, z0, for buildings that need room. */
	private static void campoPlano(int x0, int z0) {
		passo(10, () -> cmd("tp @s " + (x0 + 50) + " 140 " + z0 + " 0 90"));
		passo(80, () -> {
			java.util.List<String> c = new java.util.ArrayList<>();
			for (int i = 0; i < 100; i += 20) {
				c.add("fill " + (x0 + i) + " 116 " + (z0 - 50) + " " + (x0 + i + 19) + " 119 " + (z0 + 49) + " minecraft:dirt");
				c.add("fill " + (x0 + i) + " 120 " + (z0 - 50) + " " + (x0 + i + 19) + " 120 " + (z0 + 49) + " minecraft:grass_block");
				for (int y = 121; y < 160; y += 14) {
					c.add("fill " + (x0 + i) + " " + y + " " + (z0 - 50) + " " + (x0 + i + 19) + " " + Math.min(159, y + 13) + " " + (z0 + 49) + " minecraft:air");
				}
			}
			cmd(c.toArray(new String[0]));
		});
	}

	/** A dry, grassy, gently rolling 60x60 area near {@code c} (centre at ground level), or null. */
	private static @org.jspecify.annotations.Nullable BlockPos lugarSecoPlano(net.minecraft.server.level.ServerLevel level, BlockPos c) {
		for (int ox = -64; ox <= 64; ox += 32) {
			for (int oz = -64; oz <= 64; oz += 32) {
				int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, ruins = 0, agua = 0;
				boolean ok = true;
				for (int dx = -30; dx <= 30 && ok; dx += 5) {
					for (int dz = -30; dz <= 30 && ok; dz += 5) {
						int x = c.getX() + ox + dx, z = c.getZ() + oz + dz;
						BlockPos topo = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
						var st = level.getBlockState(topo);
						ok = level.isLoaded(topo);
						if (!st.getFluidState().isEmpty()) {
							agua++;            // a stream is welcome (the engenho needs water), a lake is not
							continue;
						}
						if (!st.is(net.minecraft.tags.BlockTags.SUBSTRATE_OVERWORLD)) {
							ruins++;          // a tree or a rock: a few are fine
							continue;
						}
						min = Math.min(min, topo.getY());
						max = Math.max(max, topo.getY());
					}
				}
				if (ox == 0 && oz == 0) {
					log("galeria: centro ok=" + ok + " ruins=" + ruins + " agua=" + agua + " desnivel=" + (max - min));
				}
				if (ok && ruins <= 10 && agua <= 8 && max - min <= 10) {
					int x = c.getX() + ox, z = c.getZ() + oz;
					return new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				}
			}
		}
		return null;
	}

	private static String id(net.minecraft.world.item.ItemStack s) {
		return s.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
	}

	/** The finished building of a project nearest to the player (its marker). */
	private static @org.jspecify.annotations.Nullable BlockPos obra(net.minecraft.server.level.ServerLevel level, String projeto) {
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		var jogador = level.players().isEmpty() ? BlockPos.ZERO : level.players().get(0).blockPosition();
		for (BlockPos p : List.copyOf(com.guilhermegoes.arraial.obra.CanteiroBlockEntity.ativos(level))) {
			if (level.getBlockEntity(p) instanceof com.guilhermegoes.arraial.obra.CanteiroBlockEntity c
					&& c.projeto().map(x -> x.id().equals(projeto)).orElse(false)) {
				double x = p.distSqr(jogador);
				if (x < d) {
					d = x;
					melhor = p;
				}
			}
		}
		return melhor;
	}

	/** A flat field crossed by a river (water 2 below the grass) at z0+25..z0+37, and a village north of it. */
	private static void campoComRio(int x0, int z0) {
		campoPlano(x0, z0);
		passo(20, () -> cmd("fill " + x0 + " 116 " + (z0 + 25) + " " + (x0 + 99) + " 119 " + (z0 + 37) + " minecraft:water",
				"fill " + x0 + " 120 " + (z0 + 25) + " " + (x0 + 99) + " 120 " + (z0 + 37) + " minecraft:air",
				"fill " + x0 + " 115 " + (z0 + 25) + " " + (x0 + 99) + " 115 " + (z0 + 37) + " minecraft:sand"));
		passo(10, () -> cmd("tp @s " + (x0 + 50) + ".5 121 " + (z0 - 24) + ".5 0 0", "arraial vila_nova luso"));
	}

	private static @org.jspecify.annotations.Nullable BlockPos trapicheDe(net.minecraft.server.level.ServerLevel level, int x, int z) {
		BlockPos melhor = null;
		for (BlockPos p : com.guilhermegoes.arraial.obra.CanteiroBlockEntity.ativos(level)) {
			if (Math.abs(p.getX() - x) < 60 && Math.abs(p.getZ() - z) < 60 && level.getBlockEntity(p) instanceof com.guilhermegoes.arraial.obra.CanteiroBlockEntity c
					&& c.projeto().map(pr -> pr.id().equals("trapiche")).orElse(false) && c.concluida()) {
				melhor = p;
			}
		}
		return melhor;
	}

	private static String barqueiros(net.minecraft.server.level.ServerLevel level, int x, int z) {
		StringBuilder b = new StringBuilder();
		for (var m : level.getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class, new net.minecraft.world.phys.AABB(x - 70, 100, z - 70, x + 70, 160, z + 70),
				e -> e.profissao() == com.guilhermegoes.arraial.npc.Profissao.BARQUEIRO)) {
			b.append(m.nome()).append(" fase=").append(m.viagemFase()).append(" posto=").append(m.posto() == null ? "-" : m.posto().toShortString())
					.append(" barco=").append(m.isPassenger()).append(" carga=").append(m.cargaCopia())
					.append(" status=").append(m.status().getString()).append(" fome=").append(m.comFome()).append(" cansado=").append(m.pessoa().cansado())
					.append(" pos=").append(m.blockPosition().toShortString()).append(" hora=").append(level.getOverworldClockTime() % 24000L)
					.append(" claro=").append(level.isBrightOutside()).append(" objetivo=").append(m.goalAtual()).append("; ");
		}
		return b.length() == 0 ? "nenhum" : b.toString();
	}

	/**
	 * Rivers: village B (downstream) gets a finished trapiche and a barqueiro; village A gets one
	 * too, a full armazém and a second trapiche left for its pedreiro. A's barqueiro takes the
	 * canoe to B (seen paddling), the trip is cut short, and the trade is logged.
	 */
	private static void rios() {
		// Village B, first, so the registry knows its barqueiro before A looks for a partner.
		campoComRio(300, 600);
		passo(160, () -> cmd("execute positioned 350 120 600 run arraial obra_pronta trapiche"));
		passo(40, () -> servidor((srv, sp) -> {
			if (trapicheDe(sp.level(), 350, 600) == null) {
				log("rios B: sem trapiche na 1a tentativa, refazendo o rio");
				cmd("fill 300 116 625 399 119 637 minecraft:water", "fill 300 120 625 399 120 637 minecraft:air",
						"execute positioned 350 120 600 run arraial obra_pronta trapiche");
			}
		}));
		passo(40, () -> servidor((srv, sp) -> {
			BlockPos t = trapicheDe(sp.level(), 350, 600);
			log("rios B: trapiche " + (t == null ? "NAO construido" : t.toShortString()));
			if (t != null) {
				cmd("summon arraial:morador " + t.getX() + " " + (t.getY() + 1) + " " + t.getZ() + " {Profissao:0,Visual:2}");
			}
		}));
		passo(200, () -> servidor((srv, sp) -> log("rios B: barqueiros " + barqueiros(sp.level(), 350, 600))));

		// Village A.
		campoComRio(300, 400);
		passo(60, () -> cmd("execute positioned 350 120 400 run arraial obra_pronta trapiche"));
		passo(20, () -> servidor((srv, sp) -> {
			var level = sp.level();
			BlockPos t = trapicheDe(level, 350, 400);
			log("rios A: trapiche " + (t == null ? "NAO construido" : t.toShortString()));
			if (t != null) {
				cmd("summon arraial:morador " + t.getX() + " " + (t.getY() + 1) + " " + t.getZ() + " {Profissao:0,Visual:4,Feminino:1b}");
			}
			BlockPos v = com.guilhermegoes.arraial.vila.Vilas.maisProxima(level, new BlockPos(350, 120, 400));
			var armazens = com.guilhermegoes.arraial.vila.Vilas.armazens(level, v);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.BREAD, 64);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.COBBLESTONE, 64);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.OAK_LOG, 64);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, net.minecraft.world.item.Items.OAK_PLANKS, 64);
			com.guilhermegoes.arraial.vila.Vilas.depositar(armazens, com.guilhermegoes.arraial.registry.ModItems.FARINHA, 48);
			// A second trapiche for the pedreiro to raise over the water.
			var projeto = com.guilhermegoes.arraial.obra.Catalogo.porId("trapiche").orElseThrow();
			var lugar = com.guilhermegoes.arraial.obra.Terreno.procurar(level, v, projeto);
			log("rios A: lugar do 2o trapiche " + lugar.map(l -> l.marco().toShortString() + " " + l.frente()).orElse("NENHUM"));
			lugar.ifPresent(l -> com.guilhermegoes.arraial.obra.Terreno.marcar(level, l, projeto, com.guilhermegoes.arraial.obra.Prioridade.NORMAL));
		}));
		passo(200, () -> servidor((srv, sp) -> log("rios A: barqueiros " + barqueiros(sp.level(), 350, 400)
				+ " | fluvial=" + com.guilhermegoes.arraial.vila.Economia.fluvial(
						com.guilhermegoes.arraial.vila.Economia.id(sp.level(), com.guilhermegoes.arraial.vila.Vilas.maisProxima(sp.level(), new BlockPos(350, 120, 400))),
						com.guilhermegoes.arraial.vila.Economia.id(sp.level(), com.guilhermegoes.arraial.vila.Vilas.maisProxima(sp.level(), new BlockPos(350, 120, 600)))))));
		passo(10, () -> verObra("trapiche", 12));
		passo(40, () -> foto("80_trapiche"));
		// The canoe trip.
		passo(10, () -> cmd("execute positioned 350 120 400 run arraial tropa"));
		passoAte(40, 1200, () -> {
			var srv = mc().getSingleplayerServer();
			return srv != null && !srv.overworld().getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class,
					new net.minecraft.world.phys.AABB(280, 100, 330, 420, 160, 470), e -> e.profissao() == com.guilhermegoes.arraial.npc.Profissao.BARQUEIRO
							&& (e.isPassenger() || e.viagemFase() >= 3)).isEmpty();
		}, () -> servidor((srv, sp) -> log("rios A: embarque " + barqueiros(sp.level(), 350, 400))));
		passo(10, () -> verObra("trapiche", 14));
		passo(20, () -> foto("81_canoa"));
		passoAte(40, 1200, () -> {
			var srv = mc().getSingleplayerServer();
			return srv != null && !srv.overworld().getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class,
					new net.minecraft.world.phys.AABB(280, 100, 330, 420, 160, 470), e -> e.profissao() == com.guilhermegoes.arraial.npc.Profissao.BARQUEIRO
							&& e.viagemFase() == 3).isEmpty();
		}, () -> servidor((srv, sp) -> {
			BlockPos v = com.guilhermegoes.arraial.vila.Vilas.maisProxima(sp.level(), new BlockPos(350, 120, 400));
			if (sp.level().getBlockEntity(v) instanceof com.guilhermegoes.arraial.vila.ArraialBlockEntity a) {
				log("rios A: partiu; tesouro antes da volta=" + a.tesouro() + " | " + barqueiros(sp.level(), 350, 400));
			}
		}));
		// Cut the trip short: back now.
		passo(40, () -> cmd("execute positioned 350 120 400 run arraial tropa"));
		passo(400, () -> servidor((srv, sp) -> {
			var level = sp.level();
			BlockPos v = com.guilhermegoes.arraial.vila.Vilas.maisProxima(level, new BlockPos(350, 120, 400));
			if (level.getBlockEntity(v) instanceof com.guilhermegoes.arraial.vila.ArraialBlockEntity a) {
				StringBuilder cr = new StringBuilder();
				var entradas = a.cronica();
				for (int i = Math.max(0, entradas.size() - 4); i < entradas.size(); i++) {
					cr.append(entradas.get(i).texto().getString()).append(" / ");
				}
				log("rios A: depois da volta tesouro=" + a.tesouro() + " | " + barqueiros(level, 350, 400) + " | cronica: " + cr);
			}
			for (BlockPos p : com.guilhermegoes.arraial.obra.CanteiroBlockEntity.ativos(level)) {
				if (Math.abs(p.getX() - 350) < 60 && Math.abs(p.getZ() - 400) < 60 && level.getBlockEntity(p) instanceof com.guilhermegoes.arraial.obra.CanteiroBlockEntity c
						&& c.projeto().map(pr -> pr.id().equals("trapiche")).orElse(false) && !c.concluida()) {
					int total = c.plano(level).map(pl -> pl.etapas().size()).orElse(1);
					log("rios A: 2o trapiche pelo pedreiro " + (c.progresso() * 100 / Math.max(1, total)) + "%");
				}
			}
		}));
		passo(10, () -> cmd("time set 1000"));
		passo(1200, () -> servidor((srv, sp) -> {
			var level = sp.level();
			for (BlockPos p : com.guilhermegoes.arraial.obra.CanteiroBlockEntity.ativos(level)) {
				if (Math.abs(p.getX() - 350) < 60 && Math.abs(p.getZ() - 400) < 60 && level.getBlockEntity(p) instanceof com.guilhermegoes.arraial.obra.CanteiroBlockEntity c
						&& c.projeto().map(pr -> pr.id().equals("trapiche")).orElse(false)) {
					int total = c.plano(level).map(pl -> pl.etapas().size()).orElse(1);
					log("rios A: trapiche " + p.toShortString() + " " + (c.concluida() ? "pronto" : (c.progresso() * 100 / Math.max(1, total)) + "%"));
				}
			}
		}));
		passo(20, () -> foto("82_trapiche_pedreiro"));
	}

	/** Puts the camera on a diagonal above a finished building, looking at its middle. */
	private static void verObra(String projeto, double distancia) {
		servidor((srv, sp) -> {
			var level = sp.level();
			BlockPos m = obra(level, projeto);
			if (m == null || !(level.getBlockEntity(m) instanceof com.guilhermegoes.arraial.obra.CanteiroBlockEntity c)) {
				log("ver obra: " + projeto + " nao encontrada");
				return;
			}
			var etapas = c.plano(level).map(com.guilhermegoes.arraial.obra.PlanoDeObra::etapas).orElse(List.of());
			double x = 0, y = 0, z = 0;
			for (var e : etapas) {
				x += e.pos().getX();
				y += e.pos().getY();
				z += e.pos().getZ();
			}
			int n = Math.max(1, etapas.size());
			Vec3 centro = new Vec3(x / n + 0.5, y / n, z / n + 0.5);
			Vec3 olho = centro.add(-distancia * 0.7, distancia * 0.55, -distancia * 0.7);
			double dx = centro.x - olho.x, dy = centro.y - olho.y, dz = centro.z - olho.z;
			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			sp.teleportTo(level, olho.x, olho.y, olho.z, java.util.Set.of(), yaw, pitch, true);
			sp.getAbilities().flying = true;
			sp.onUpdateAbilities();
		});
	}

	/** Logs how many of each era block stand around the test village. */
	private static void contarBlocos(String era) {
		servidor((srv, sp) -> {
			java.util.Map<String, Integer> n = new java.util.TreeMap<>();
			for (BlockPos p : BlockPos.betweenClosed(-48, 110, -48, 48, 140, 48)) {
				var b = sp.level().getBlockState(p).getBlock();
				String id = BuiltInRegistries.BLOCK.getKey(b).toString();
				if (id.startsWith("arraial:t") || id.equals("arraial:cal") || id.equals("arraial:azulejo") || id.equals("minecraft:mud_bricks")
						|| id.equals("minecraft:bricks") || id.contains("brick_stairs")) {
					n.merge(id, 1, Integer::sum);
				}
			}
			log("blocos na era " + era + ": " + n);
		});
	}

	/** Right-clicks the top of a block with whatever is in hand (as the player would). */
	private static void clicarBloco(BlockPos p) {
		mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(p).add(0, 0.5, 0), Direction.UP, p, false));
	}

	/** Right-clicks the entity chosen by the last scene. */
	private static void clicarEntidade() {
		var e = mc().level.getEntity(alvo);
		if (e == null) {
			log("clicar: entidade sumiu");
			return;
		}
		mc().gameMode.interact(mc().player, e, new net.minecraft.world.phys.EntityHitResult(e), InteractionHand.MAIN_HAND);
	}

	private static void servidor(java.util.function.BiConsumer<net.minecraft.server.MinecraftServer, net.minecraft.server.level.ServerPlayer> f) {
		var srv = mc().getSingleplayerServer();
		java.util.UUID id = mc().player.getUUID();
		srv.execute(() -> f.accept(srv, srv.getPlayerList().getPlayer(id)));
	}

	private static com.guilhermegoes.arraial.npc.@org.jspecify.annotations.Nullable MoradorEntity moradorPerto(net.minecraft.server.MinecraftServer srv,
			net.minecraft.server.level.ServerPlayer sp, boolean crianca) {
		return srv.overworld().getEntitiesOfClass(com.guilhermegoes.arraial.npc.MoradorEntity.class, sp.getBoundingBox().inflate(60),
				m -> m.crianca() == crianca && m.vila() != null && m.profissao() != com.guilhermegoes.arraial.npc.Profissao.MASCATE).stream()
				.min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(sp))).orElse(null);
	}

	/** Turns the player to face an entity, from where they stand. */
	private static void olhar(net.minecraft.server.level.ServerPlayer sp, net.minecraft.world.entity.Entity e) {
		double dx = e.getX() - sp.getX(), dz = e.getZ() - sp.getZ(), dy = e.getEyeY() - sp.getEyeY();
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.teleportTo(sp.level(), sp.getX(), sp.getY(), sp.getZ(), java.util.Set.of(), yaw, pitch, true);
	}

	private Roteiro() {
	}
}
