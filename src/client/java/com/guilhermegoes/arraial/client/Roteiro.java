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
	private record Passo(int espera, Runnable acao) {
	}

	private static final List<Passo> PASSOS = new ArrayList<>();
	private static int indice;
	private static int contador;
	private static boolean abriu;

	static void init() {
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
			if (++contador < PASSOS.get(indice).espera()) {
				return;
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
		PASSOS.add(new Passo(espera, acao));
	}

	private static String mao() {
		return BuiltInRegistries.ITEM.getKey(mc().player.getMainHandItem().getItem()).toString();
	}

	private static void montar() {
		passo(10, () -> cmd("gamemode creative", "kill @e[type=minecraft:vindicator]", "kill @e[type=minecraft:pillager]"));
		passo(190, () -> {
			log("no mundo; preparando");
			mc().getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
			cmd("gamemode creative", "time set 6000", "weather clear", "effect clear @s", "kill @e[type=minecraft:vindicator]", "kill @e[type=minecraft:pillager]", "tp @s 0.5 121 -7.5 0 15");
		});
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
		passo(20, () -> clicarBloco(new BlockPos(2, 120, -5)));
		passo(10, () -> cmd("item replace entity @s weapon.mainhand with minecraft:paper"));
		passo(20, () -> clicarBloco(new BlockPos(2, 120, -5)));
		passo(30, () -> foto("23_escrivaninha"));

		// ---- Eras: new houses in colonial style, seen from above through the three eras.
		passo(10, () -> cmd("tp @s 0.5 121 -7.5 0 30", "execute positioned 0 120 0 run arraial era colonial",
				"execute positioned 0 120 0 run arraial obra_pronta casebre", "execute positioned 0 120 0 run arraial obra_pronta casebre",
				"execute positioned 0 120 0 run arraial obra_pronta rancho", "execute positioned 0 120 0 run arraial obra_pronta casebre"));
		passo(40, () -> cmd("setblock 22 140 22 minecraft:glass", "tp @s 22.5 141 22.5 135 40"));
		passo(60, () -> foto("24_era_colonial"));
		passo(10, () -> cmd("execute positioned 0 120 0 run arraial era imperial"));
		passo(60, () -> foto("25_era_imperial"));
		passo(10, () -> cmd("execute positioned 0 120 0 run arraial era republicana"));
		passo(60, () -> foto("26_era_republicana"));
		passo(20, () -> {
			log("fim do roteiro");
			mc().stop();
		});
	}

	// ---------------------------------------------------------------- helpers for the scenes

	private static int alvo = -1;
	private static BlockPos chao = BlockPos.ZERO;

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
