package com.guilhermegoes.arraial.vila;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Each arraial's own identity, born from where it stands and what it lives on: its colours and
 * symbol (a flag hoisted by the Cruzeiro), its customs, and its traditions, festas of its own on
 * the calendar (see {@link Festa.Tipo#TRADICAO}). Two villages of the same blocks and NPCs end up
 * different: a fishing village by the river holds the Navegantes procession, a mining one in the
 * hills celebrates Santa Bárbara, one in the cold south the Festa do Pinhão.
 * <ul>
 * <li><b>Costumes</b> change how the village behaves: hospitable or wary of strangers, festive,
 * hard-working, devout, keen traders.</li>
 * <li><b>Intercâmbio</b>: friendly neighbours with busy roads or a trade treaty pick up each other's
 * traditions.</li>
 * <li><b>Reconhecimento</b>: a player who takes part in the festas and brings sweets to them earns
 * the village's cultural recognition: known, then a friend of the festa (they give their flag),
 * then a child of the land.</li>
 * <li><b>Cerimônias</b>: the swearing-in of a prefeito with fireworks; mourning when a morador dies.</li>
 * </ul>
 */
public final class Cultura {
	/** How a tradition is celebrated (see FestejarGoal). */
	public enum Estilo {
		RODA, PROCISSAO, FEIRA, AGUA
	}

	/** The traditions: id, month, style. */
	public record Tradicao(String id, int mes, Estilo estilo) {
		public Component nome() {
			return Component.translatable("arraial.tradicao." + id);
		}
	}

	public static final List<Tradicao> TRADICOES = List.of(
			new Tradicao("reisado", 1, Estilo.PROCISSAO), new Tradicao("navegantes", 2, Estilo.AGUA),
			new Tradicao("colheita", 4, Estilo.RODA), new Tradicao("congada", 5, Estilo.PROCISSAO),
			new Tradicao("divino", 7, Estilo.PROCISSAO), new Tradicao("pinhao", 7, Estilo.RODA),
			new Tradicao("bumba_boi", 8, Estilo.RODA), new Tradicao("cavalhada", 10, Estilo.PROCISSAO),
			new Tradicao("mutirao", 10, Estilo.RODA), new Tradicao("vaquejada", 11, Estilo.FEIRA),
			new Tradicao("santa_barbara", 12, Estilo.PROCISSAO));

	public static @Nullable Tradicao tradicao(String id) {
		for (Tradicao t : TRADICOES) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	private static final List<String> COSTUMES = List.of("hospitaleiros", "desconfiados", "festeiros", "trabalhadores", "devotos", "comerciantes");

	public static boolean costume(Registro.@Nullable Info v, String c) {
		return v != null && v.costumes.contains(c);
	}

	public static boolean costume(ServerLevel level, ArraialBlockEntity a, String c) {
		return costume(Registro.get(Economia.id(level, a.getBlockPos())), c);
	}

	// ================================================================ birth of a culture

	/** The region from the world generator (works for unloaded land too, without loading chunks). */
	private static String regiao(ServerLevel level, BlockPos pos) {
		var fonte = level.getChunkSource().getGenerator().getBiomeSource();
		var bioma = fonte.createCachingResolver(level.getChunkSource().randomState())
				.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(pos.getX()), net.minecraft.core.QuartPos.fromBlock(pos.getY()), net.minecraft.core.QuartPos.fromBlock(pos.getZ()));
		String b = bioma.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		if (Biomas.conhecido(b) >= 0) {
			return Biomas.regiao(Biomas.conhecido(b));
		}
		if (b.contains("snow") || b.contains("frozen") || b.contains("taiga") || b.contains("grove")) {
			return "frio";
		}
		if (b.contains("desert") || b.contains("savanna") || b.contains("badlands")) {
			return "sertao";
		}
		if (b.contains("jungle") || b.contains("bamboo") || b.contains("mangrove")) {
			return "mata";
		}
		if (b.contains("swamp") || b.contains("river") || b.contains("beach") || b.contains("ocean")) {
			return "ribeirinho";
		}
		if (b.contains("mountain") || b.contains("peak") || b.contains("hill") || b.contains("stony")) {
			return "serra";
		}
		return "campo";
	}

	/** Gives an arraial its culture the first time it is seen (and hoists its flag). */
	static void garantir(ServerLevel level, ArraialBlockEntity a, Registro.Info v) {
		if (v.simbolo.isEmpty()) {
			gerar(level, a.nome(), a.getBlockPos(), Economia.id(level, a.getBlockPos()), v);
		}
		if (!v.bandeiraHasteada && a.populacao >= 2) {
			v.bandeiraHasteada = hastear(level, a, v);
		}
	}

	/** An unloaded arraial gets its culture too (see Mundo). */
	static void garantirLonge(ServerLevel level, String id, Registro.Info v) {
		if (v.simbolo.isEmpty() && !v.nome.isEmpty()) {
			gerar(level, v.nome, v.posicao(), id, v);
		}
	}

	private static void gerar(ServerLevel level, String nome, BlockPos pos, String id, Registro.Info v) {
		// Mixed seed (nearby seeds give similar first draws in java.util.Random).
		long s = nome.hashCode() * 0x9E3779B97F4A7C15L ^ pos.asLong() * 0xC2B2AE3D27D4EB4FL;
		s = (s ^ (s >>> 33)) * 0xFF51AFD7ED558CCDL;
		Random r = new Random(s ^ (s >>> 29));
		r.nextLong();
		v.bioma = regiao(level, pos);
		Economia.Especialidade esp = Economia.Especialidade.de(v.especialidade);
		DyeColor[] paleta = switch (v.bioma) {
			case "frio" -> new DyeColor[] {DyeColor.BLUE, DyeColor.WHITE, DyeColor.CYAN, DyeColor.GREEN};
			case "sertao" -> new DyeColor[] {DyeColor.ORANGE, DyeColor.BROWN, DyeColor.RED, DyeColor.YELLOW};
			case "mata" -> new DyeColor[] {DyeColor.GREEN, DyeColor.LIME, DyeColor.YELLOW, DyeColor.RED};
			case "ribeirinho" -> new DyeColor[] {DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.WHITE, DyeColor.YELLOW};
			case "serra" -> new DyeColor[] {DyeColor.GRAY, DyeColor.PURPLE, DyeColor.WHITE, DyeColor.BLUE};
			default -> new DyeColor[] {DyeColor.YELLOW, DyeColor.GREEN, DyeColor.WHITE, DyeColor.RED};
		};
		DyeColor c1 = paleta[r.nextInt(paleta.length)], c2;
		do {
			c2 = paleta[r.nextInt(paleta.length)];
		} while (c2 == c1);
		v.cor1 = c1.getName();
		v.cor2 = c2.getName();
		v.simbolo = switch (esp) {
			case AGRICOLA -> "flor";
			case MINERADORA -> "muralha";
			case MADEIREIRA -> "tronco";
			case PESQUEIRA -> "ondas";
			case PASTORIL -> "serra";
			case ARTESANAL -> "sol";
			default -> switch (v.bioma) { // a mixed economy takes its symbol from the land
				case "frio", "serra" -> "serra";
				case "sertao" -> "sol";
				case "mata" -> "flor";
				case "ribeirinho" -> "ondas";
				default -> List.of("cruz", "sol", "flor", "tronco").get(r.nextInt(4));
			};
		};
		// Customs: two, never hospitable and wary together.
		if ("hispano".equals(v.povo)) {
			v.cor1 = DyeColor.WHITE.getName();
			v.cor2 = DyeColor.RED.getName();
			v.simbolo = "borgonha";
		}
		v.costumes.clear();
		if ("hispano".equals(v.povo)) {
			v.costumes.add("devotos");
		}
		while (v.costumes.size() < 2) {
			String c = COSTUMES.get(r.nextInt(COSTUMES.size()));
			if (!v.costumes.contains(c) && !(c.equals("hospitaleiros") && v.costumes.contains("desconfiados"))
					&& !(c.equals("desconfiados") && v.costumes.contains("hospitaleiros"))) {
				v.costumes.add(c);
			}
		}
		// Traditions: one from the land or the trade, one from the common stock.
		List<String> daTerra = new ArrayList<>();
		switch (v.bioma) {
			case "frio" -> daTerra.add("pinhao");
			case "sertao" -> daTerra.add("vaquejada");
			case "mata", "ribeirinho" -> {
				daTerra.add("bumba_boi");
				daTerra.add("navegantes");
			}
			default -> {
			}
		}
		switch (esp) {
			case AGRICOLA -> daTerra.add("colheita");
			case MINERADORA -> daTerra.add("santa_barbara");
			case MADEIREIRA -> daTerra.add("mutirao");
			case PESQUEIRA -> daTerra.add("navegantes");
			case PASTORIL -> daTerra.add("vaquejada");
			default -> daTerra.add(List.of("colheita", "mutirao", "vaquejada", "bumba_boi").get(r.nextInt(4)));
		}
		List<String> comuns = List.of("reisado", "divino", "congada", "cavalhada");
		v.tradicoes.clear();
		v.tradicoes.add(daTerra.get(r.nextInt(daTerra.size())));
		String outra;
		do {
			outra = comuns.get(r.nextInt(comuns.size()));
		} while (v.tradicoes.contains(outra));
		v.tradicoes.add(outra);
		Mundo.historia(id, Vilas.dia(level), "arraial.historia.cultura",
				Component.translatable("arraial.simbolo." + v.simbolo).getString());
	}

	// ================================================================ the flag

	private static ResourceKey<BannerPattern> padrao(String simbolo) {
		return switch (simbolo) {
			case "flor" -> BannerPatterns.FLOWER;
			case "muralha" -> BannerPatterns.BRICKS;
			case "tronco" -> BannerPatterns.STRIPE_CENTER;
			case "ondas" -> BannerPatterns.CURLY_BORDER;
			case "serra" -> BannerPatterns.TRIANGLES_BOTTOM;
			case "sol" -> BannerPatterns.CIRCLE_MIDDLE;
			case "borgonha" -> BannerPatterns.CROSS;
			default -> BannerPatterns.STRAIGHT_CROSS;
		};
	}

	private static DyeColor cor(String nome, DyeColor padrao) {
		DyeColor c = DyeColor.byName(nome, null);
		return c == null ? padrao : c;
	}

	/** The village flag as an item: its colour, its symbol, a border, and its name. */
	public static ItemStack bandeira(ServerLevel level, Registro.Info v) {
		DyeColor c1 = cor(v.cor1, DyeColor.WHITE), c2 = cor(v.cor2, DyeColor.RED);
		ItemStack s = new ItemStack(Items.BANNER.pick(c1));
		var padroes = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		Holder<BannerPattern> simbolo = padroes.getOrThrow(padrao(v.simbolo));
		Holder<BannerPattern> borda = padroes.getOrThrow(BannerPatterns.BORDER);
		s.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(simbolo, c2).add(borda, c2).build());
		s.set(DataComponents.CUSTOM_NAME, Component.translatable("arraial.cultura.bandeira_de", v.nome));
		return s;
	}

	/** Hoists the flag on a free spot near the Cruzeiro. */
	private static boolean hastear(ServerLevel level, ArraialBlockEntity a, Registro.Info v) {
		BlockPos c = a.getBlockPos();
		for (int r = 2; r <= 4; r++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos chao = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.relative(d, r).relative(d.getClockWise()));
				if (Math.abs(chao.getY() - c.getY()) > 2 || !Festa.livre(level, chao)) {
					continue;
				}
				ItemStack b = bandeira(level, v);
				level.setBlock(chao, Blocks.BANNER.pick(cor(v.cor1, DyeColor.WHITE)).defaultBlockState(), 3);
				if (level.getBlockEntity(chao) instanceof BannerBlockEntity be) {
					be.applyComponentsFromItemStack(b);
					be.setChanged();
				}
				return true;
			}
		}
		return false;
	}

	public static ChatFormatting cor(Registro.@Nullable Info v) {
		if (v == null) {
			return ChatFormatting.AQUA;
		}
		return switch (cor(v.cor1, DyeColor.WHITE)) {
			case RED -> ChatFormatting.RED;
			case BLUE -> ChatFormatting.BLUE;
			case LIGHT_BLUE, CYAN -> ChatFormatting.AQUA;
			case GREEN -> ChatFormatting.DARK_GREEN;
			case LIME -> ChatFormatting.GREEN;
			case YELLOW, ORANGE -> ChatFormatting.GOLD;
			case PURPLE, MAGENTA, PINK -> ChatFormatting.LIGHT_PURPLE;
			case GRAY, BROWN, BLACK -> ChatFormatting.GRAY;
			default -> ChatFormatting.WHITE;
		};
	}

	// ================================================================ the calendar

	/** The tradition celebrated on the 1st of this month, if the arraial keeps one. */
	public static @Nullable Tradicao doMes(Registro.@Nullable Info v, int mes) {
		if (v == null) {
			return null;
		}
		for (String id : v.tradicoes) {
			Tradicao t = tradicao(id);
			if (t != null) {
				int m = t.mes();
				if (m == 3 || m == 6 || m == 9) {
					m++; // never on top of the Feira Grande or São João
				}
				if (m == mes) {
					return t;
				}
			}
		}
		return null;
	}

	// ================================================================ the player's recognition

	public static int reconhecimento(ArraialBlockEntity a, UUID p) {
		return a.cultura.getOrDefault(p, 0);
	}

	public static Component titulo(int pontos) {
		return Component.translatable(pontos >= 60 ? "arraial.cultura.titulo.filho" : pontos >= 30 ? "arraial.cultura.titulo.amigo"
				: pontos >= 10 ? "arraial.cultura.titulo.conhecido" : "arraial.cultura.titulo.forasteiro");
	}

	/** Points for taking part: milestones bring the flag, reputation and a title. */
	public static void reconhecer(ServerLevel level, ArraialBlockEntity a, Player p, int pontos) {
		int antes = reconhecimento(a, p.getUUID());
		int depois = Math.min(100, antes + pontos);
		a.cultura.put(p.getUUID(), depois);
		a.setChanged();
		for (int marco : new int[] {10, 30, 60}) {
			if (antes < marco && depois >= marco) {
				if (p instanceof ServerPlayer sp) {
					Conquistas.dar(sp, marco == 60 ? "filho_da_terra" : "festeiro");
				}
				p.sendSystemMessage(Component.literal("⚑ ").append(Component.translatable("arraial.cultura.marco." + marco, a.nomeCompleto(), titulo(depois)))
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.reconhecimento", p.getName().getString(), titulo(depois)));
				if (marco == 30) {
					Registro.Info v = Registro.get(Economia.id(level, a.getBlockPos()));
					if (v != null) {
						ItemStack b = bandeira(level, v);
						if (!p.getInventory().add(b)) {
							net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), b);
						}
					}
				}
				if (marco == 60) {
					a.mudarReputacao(p.getUUID(), 10);
					ItemStack comenda = new ItemStack(com.guilhermegoes.arraial.registry.ModItems.COMENDA);
					if (!p.getInventory().add(comenda)) {
						net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), comenda);
					}
				}
			}
		}
	}

	/** During a festa: the players around it are taking part. */
	static void participacao(ServerLevel level, ArraialBlockEntity a, BlockPos centro) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(centro, 20)) {
				reconhecer(level, a, p, 2);
			}
		}
	}

	// ================================================================ exchange between villages

	/** Once a day: friendly, well-connected neighbours pick up each other's traditions. */
	static void intercambio(ServerLevel level, long hoje) {
		List<String> ids = new ArrayList<>(Registro.todas().keySet());
		for (String a : ids) {
			Registro.Info va = Registro.get(a);
			if (va == null || va.abandonada || va.simbolo.isEmpty() || va.tradicoes.size() >= 3) {
				continue;
			}
			for (String b : ids) {
				Registro.Info vb = Registro.get(b);
				if (b.equals(a) || vb == null || vb.simbolo.isEmpty() || va.reputacaoCom(b) < 30) {
					continue;
				}
				boolean ligadas = Economia.batida(a, b) || Territorio.tem(a, b, "comercio", hoje) || Territorio.tem(a, b, "alianca", hoje);
				if (!ligadas || level.getRandom().nextFloat() > 0.05F) {
					continue;
				}
				for (String t : vb.tradicoes) {
					if (!va.tradicoes.contains(t)) {
						va.tradicoes.add(t);
						Component nome = Component.translatable("arraial.tradicao." + t);
						Mundo.historia(a, hoje, "arraial.historia.intercambio", nome.getString(), vb.nome);
						Mundo.historia(b, hoje, "arraial.historia.intercambio_levou", va.nome, nome.getString());
						break;
					}
				}
				break;
			}
		}
	}

	// ================================================================ what the Cruzeiro tells

	public static void relatorio(ServerLevel level, ArraialBlockEntity a, Player p, Registro.@Nullable Info v) {
		if (v == null || v.simbolo.isEmpty()) {
			return;
		}
		MutableComponent l = Component.literal("⚑ ").append(Component.translatable("arraial.cultura.linha",
				Component.translatable("arraial.simbolo." + v.simbolo), Component.translatable("arraial.cor." + v.cor1),
				Component.translatable("arraial.cor." + v.cor2), Component.translatable("arraial.regiao." + v.bioma)));
		p.sendSystemMessage(l.withStyle(cor(v)));
		MutableComponent c = Component.translatable("arraial.cultura.costumes");
		for (int i = 0; i < v.costumes.size(); i++) {
			c.append(i == 0 ? " " : ", ").append(Component.translatable("arraial.costume." + v.costumes.get(i)));
		}
		c.append(" · ").append(Component.translatable("arraial.cultura.tradicoes"));
		for (int i = 0; i < v.tradicoes.size(); i++) {
			Tradicao t = tradicao(v.tradicoes.get(i));
			if (t != null) {
				int m = t.mes() == 3 || t.mes() == 6 || t.mes() == 9 ? t.mes() + 1 : t.mes();
				c.append(i == 0 ? " " : ", ").append(t.nome()).append(" (").append(Calendario.nomeMes(m)).append(")");
			}
		}
		p.sendSystemMessage(c.withStyle(ChatFormatting.GRAY));
		int pts = reconhecimento(a, p.getUUID());
		p.sendSystemMessage(Component.translatable("arraial.cultura.seu", titulo(pts), pts).withStyle(ChatFormatting.DARK_AQUA));
	}

	private Cultura() {
	}
}
