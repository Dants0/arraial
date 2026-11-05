package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.rede.MapaDados;
import com.guilhermegoes.arraial.rede.Rede;
import com.guilhermegoes.arraial.vila.Estagio;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * The region map, drawn like an old chart: biomes in muted colours, each arraial's land tinted with
 * its flag colour (disputed land hatched in red), roads between them (beaten, closed, at war, under
 * embargo), ruins, bandits, the player and their base. Opened at an Escrivaninha it is also the
 * chancery: pick a neighbour and propose treaties, break them, or declare an embargo.
 */
public class TelaArraial extends Screen {
	private static final Identifier TEXTURA = Arraial.id("mapa_regiao_tela");
	private static final int TAM = 256;
	private static @Nullable DynamicTexture textura;

	private static final int[] BIOMAS = {
			0xFF3F6E9E, 0xFF4F86B8, 0xFFE3D59B, 0xFFE0C98A, 0xFFC0703F, 0xFFB5B25C, 0xFF3D8A3A, 0xFF5E7550, 0xFFEDEFF2,
			0xFF4F7255, 0xFF55894A, 0xFF8D8F86, 0xFF8DB360};
	private static final int PERGAMINHO = 0xFFE8D9B0;
	private static final int[] ROTAS = {0xFF9C7A4E, 0xFF5A3A1C, 0xFF8A8A8A, 0xFFD02020, 0xFF9B3FC0};
	private static final String[] ACOES = {"paz", "comercio", "alianca", "fronteira", "tributo", "romper", "embargo"};

	private MapaDados d;
	private int selecionada = -1;
	private int rolagem;
	private final Map<String, Button> botoes = new LinkedHashMap<>();
	// Layout.
	private int mx0, my0, mt, px0, pw;

	public TelaArraial(MapaDados d) {
		super(Component.translatable("item.arraial.mapa_regiao"));
		this.d = d;
		this.selecionada = d.meu;
		pintar();
	}

	/** A packet arrived: open the screen, or refresh it keeping the selection. */
	public static void receber(MapaDados d) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof TelaArraial t) {
			String id = t.selecionada >= 0 && t.selecionada < t.d.vilas.size() ? t.d.vilas.get(t.selecionada).id : null;
			t.d = d;
			t.selecionada = d.meu;
			for (int i = 0; i < d.vilas.size(); i++) {
				if (d.vilas.get(i).id.equals(id)) {
					t.selecionada = i;
				}
			}
			t.pintar();
			t.atualizarBotoes();
		} else {
			mc.gui.setScreen(new TelaArraial(d));
		}
	}

	// ================================================================ the picture of the region

	private static int mistura(int a, int b, float t) {
		int ra = a >> 16 & 255, ga = a >> 8 & 255, ba = a & 255;
		int rb = b >> 16 & 255, gb = b >> 8 & 255, bb = b & 255;
		return 0xFF000000 | (int) (ra + (rb - ra) * t) << 16 | (int) (ga + (gb - ga) * t) << 8 | (int) (ba + (bb - ba) * t);
	}

	/** Bakes biomes and territory into a 256x256 texture (once per packet). */
	private void pintar() {
		if (textura == null) {
			textura = new DynamicTexture(() -> "arraial mapa da regiao", TAM, TAM, false);
			Minecraft.getInstance().getTextureManager().register(TEXTURA, textura);
		}
		NativeImage img = textura.getPixels();
		Map<Long, MapaDados.Terra> terras = new HashMap<>();
		for (MapaDados.Terra t : d.terras) {
			terras.put(ChunkPos.pack(t.cx(), t.cz()), t);
		}
		int largura = d.w * d.celula;
		int[] dono = new int[TAM * TAM];
		for (int py = 0; py < TAM; py++) {
			for (int px = 0; px < TAM; px++) {
				int x = d.x0 + (int) ((px + 0.5) * largura / TAM), z = d.z0 + (int) ((py + 0.5) * largura / TAM);
				int i = Math.min(d.w - 1, Math.max(0, (x - d.x0) / d.celula)), j = Math.min(d.h - 1, Math.max(0, (z - d.z0) / d.celula));
				int b = d.bioma.length == 0 ? MapaDados.CAMPO : d.bioma[j * d.w + i];
				int cor = mistura(BIOMAS[Math.max(0, Math.min(BIOMAS.length - 1, b))], PERGAMINHO, 0.3F);
				MapaDados.Terra t = terras.get(ChunkPos.pack(x >> 4, z >> 4));
				dono[py * TAM + px] = t == null ? -1 : t.disputada() ? -2 : t.dono();
				if (t != null) {
					cor = mistura(cor, d.vilas.get(t.dono()).cor, 0.38F);
					if (t.disputada() && (px + py) % 6 < 2) {
						cor = mistura(cor, 0xFFC02020, 0.7F);
					}
				}
				// A faint paper grain and a darker rim, like an old chart.
				int borda = Math.min(Math.min(px, py), Math.min(TAM - 1 - px, TAM - 1 - py));
				if (borda < 6) {
					cor = mistura(cor, 0xFF6B4F2A, (6 - borda) / 12.0F);
				}
				if (((px * 7 + py * 13) ^ (px * py)) % 23 == 0) {
					cor = mistura(cor, 0xFF000000, 0.06F);
				}
				img.setPixel(px, py, cor);
			}
		}
		// Borders between different owners.
		for (int py = 0; py < TAM - 1; py++) {
			for (int px = 0; px < TAM - 1; px++) {
				int a = dono[py * TAM + px], dir = dono[py * TAM + px + 1], baixo = dono[(py + 1) * TAM + px];
				if (a != dir || a != baixo) {
					int o = a >= 0 ? a : dir >= 0 ? dir : baixo;
					int cor = o >= 0 ? mistura(d.vilas.get(o).cor, 0xFF000000, 0.45F) : 0xFF5A1010;
					img.setPixel(px, py, cor);
				}
			}
		}
		textura.upload();
	}

	// ================================================================ layout and buttons

	@Override
	protected void init() {
		mx0 = 10;
		my0 = 22;
		// The chart is square and leaves two lines at the bottom for the last answer.
		mt = Math.max(110, Math.min(this.height - my0 - 26, (int) (this.width * 0.55)));
		px0 = mx0 + mt + 10;
		pw = this.width - px0 - 8;
		botoes.clear();
		int bw = (pw - 4) / 3, bh = BOTAO;
		for (int k = 0; k < ACOES.length; k++) {
			String acao = ACOES[k];
			Button b = Button.builder(Component.translatable("arraial.diplomacia.botao." + acao), x -> agir(acao))
					.bounds(px0 + (k % 3) * (bw + 2), topoBotoes() + (k / 3) * (bh + 2), k == ACOES.length - 1 ? pw : bw, bh)
					.tooltip(Tooltip.create(Component.translatable("arraial.diplomacia.dica." + acao))).build();
			botoes.put(acao, addRenderableWidget(b));
		}
		atualizarBotoes();
	}

	private MapaDados.@Nullable Vila escolhida() {
		return selecionada >= 0 && selecionada < d.vilas.size() && selecionada != d.meu ? d.vilas.get(selecionada) : null;
	}

	private void atualizarBotoes() {
		MapaDados.Vila v = escolhida();
		int minhaForca = d.meu >= 0 ? d.vilas.get(d.meu).forca : 0;
		for (Map.Entry<String, Button> e : botoes.entrySet()) {
			Button b = e.getValue();
			b.visible = d.mesa;
			String acao = e.getKey();
			if (v == null || v.abandonada) {
				b.active = false;
			} else if (acao.equals("romper")) {
				b.active = !v.tratado.isEmpty();
			} else if (acao.equals("embargo")) {
				b.active = true;
				b.setMessage(Component.translatable(v.embargamos ? "arraial.diplomacia.botao.suspender" : "arraial.diplomacia.botao.embargo"));
			} else {
				b.active = d.podeHoje && d.papel > 0 && MapaDados.podePropor(v, acao, minhaForca);
			}
		}
	}

	private void agir(String acao) {
		MapaDados.Vila v = escolhida();
		if (v != null && d.mesa) {
			ClientPlayNetworking.send(new Rede.Acao(d.escrivaninha, v.id, acao));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================ drawing

	private int telaX(int x) {
		return mx0 + (int) ((x - d.x0) * (long) mt / (d.w * d.celula));
	}

	private int telaZ(int z) {
		return my0 + (int) ((z - d.z0) * (long) mt / (d.h * d.celula));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0xC0100C08);
		Component titulo = d.mesa && d.meu >= 0 ? Component.translatable("arraial.mapa.chancelaria", d.vilas.get(d.meu).nome) : this.title;
		g.text(this.font, titulo.copy().withStyle(ChatFormatting.BOLD), mx0, 10, 0xFFF0D890);

		// The chart.
		g.fill(mx0 - 3, my0 - 3, mx0 + mt + 3, my0 + mt + 3, 0xFF3B2A16);
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURA, mx0, my0, 0, 0, mt, mt, TAM, TAM, TAM, TAM);
		g.enableScissor(mx0, my0, mx0 + mt, my0 + mt);
		for (MapaDados.Rota r : d.rotas) {
			MapaDados.Vila va = d.vilas.get(r.a()), vb = d.vilas.get(r.b());
			linha(g, telaX(va.x), telaZ(va.z), telaX(vb.x), telaZ(vb.z), ROTAS[r.estado()], r.estado() == 1, r.estado() >= 2);
		}
		if (d.temBase) {
			int x = telaX(d.bx), y = telaZ(d.bz);
			g.centeredText(this.font, "⌂", x, y - 4, d.baseOcupada ? 0xFFE03030 : 0xFF2A2A2A);
		}
		int hover = -1;
		for (int i = 0; i < d.vilas.size(); i++) {
			MapaDados.Vila v = d.vilas.get(i);
			int x = telaX(v.x), y = telaZ(v.z);
			if (Math.abs(mouseX - x) <= 5 && Math.abs(mouseY - y) <= 5) {
				hover = i;
			}
			if (v.abandonada) {
				g.centeredText(this.font, v.bandidos ? "☠" : "✕", x, y - 4, v.bandidos ? 0xFFB01010 : 0xFF404040);
			} else {
				int borda = i == d.meu ? 0xFFFFD040 : 0xFF1A1208;
				if (i == selecionada) {
					g.fill(x - 6, y - 6, x + 7, y + 7, 0xFFFFFFFF);
				}
				g.fill(x - 5, y - 5, x + 6, y + 6, borda);
				g.fill(x - 4, y - 4, x + 5, y + 5, v.cor);
				g.fill(x - 1, y - 1, x + 2, y + 2, v.cor2);
				if (v.estado == 2) {
					g.text(this.font, "⚔", x + 6, y - 10, 0xFFE02020);
				}
			}
		}
		rotulos(g);
		// The player.
		int jx = telaX(d.jx), jy = telaZ(d.jz);
		g.fill(jx - 2, jy - 2, jx + 3, jy + 3, 0xFF000000);
		g.fill(jx - 1, jy - 1, jx + 2, jy + 2, 0xFFFFFFFF);
		g.disableScissor();
		// North and the scale.
		g.centeredText(this.font, "N", mx0 + mt - 10, my0 + 4, 0xFF3B2A16);
		g.centeredText(this.font, "↑", mx0 + mt - 10, my0 + 13, 0xFF3B2A16);
		int escala = escala();
		int px = (int) (escala * (long) mt / (d.w * d.celula));
		g.fill(mx0 + 6, my0 + 14, mx0 + 6 + px, my0 + 16, 0xFF3B2A16);
		g.text(this.font, Component.translatable("arraial.mapa.escala", escala), mx0 + 6, my0 + 4, 0xFF3B2A16, false);
		legenda(g);
		if (!d.mensagem.getString().isEmpty()) {
			g.textWithWordWrap(this.font, d.mensagem, mx0, my0 + mt + 5, this.width - 2 * mx0, 0xFFFFFFFF);
		}

		painel(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, a);
		if (hover >= 0) {
			g.setComponentTooltipForNextFrame(this.font, ficha(d.vilas.get(hover), hover), mouseX, mouseY);
		}
	}

	/**
	 * Place names under (or else above) each marker, small; ours and the selected one first, and a
	 * label that would cover another is left out (the tooltip still names it).
	 */
	private void rotulos(GuiGraphicsExtractor g) {
		float s = 0.75F;
		List<int[]> ocupados = new ArrayList<>();
		List<Integer> ordem = new ArrayList<>();
		if (d.meu >= 0) {
			ordem.add(d.meu);
		}
		if (selecionada >= 0 && selecionada != d.meu && selecionada < d.vilas.size()) {
			ordem.add(selecionada);
		}
		for (int i = 0; i < d.vilas.size(); i++) {
			if (!ordem.contains(i)) {
				ordem.add(i);
			}
		}
		for (int i : ordem) {
			MapaDados.Vila v = d.vilas.get(i);
			int x = telaX(v.x), y = telaZ(v.z);
			// The stage shows on the marker; the label keeps the place name ("Povoado de Santa Luzia" -> "Santa Luzia").
			String rotulo = v.nome.replaceFirst("^(Povoado|Arraial|Vila próspera|Vila|Cidade) (de|do|da|dos|das) ", "");
			if (this.font.width(rotulo) > 110) {
				rotulo = this.font.plainSubstrByWidth(rotulo, 104) + "…";
			}
			int largura = this.font.width(rotulo);
			int lw = (int) (largura * s) + 2, lh = (int) (10 * s);
			int[] lugar = null;
			for (int ty : new int[] {y + 7, y - 7 - lh}) {
				int[] r = {x - lw / 2, ty, x + lw / 2, ty + lh};
				if (ocupados.stream().noneMatch(o -> r[0] < o[2] && o[0] < r[2] && r[1] < o[3] && o[1] < r[3])) {
					lugar = r;
					break;
				}
			}
			if (lugar == null) {
				continue;
			}
			ocupados.add(lugar);
			g.pose().pushMatrix();
			g.pose().translate(x, lugar[1]);
			g.pose().scale(s, s);
			g.fill(-largura / 2 - 1, -1, largura / 2 + 1, 9, 0x90000000);
			g.centeredText(this.font, rotulo, 0, 0, v.abandonada ? 0xFFB0B0B0 : i == d.meu ? 0xFFFFE080 : 0xFFFFFFFF);
			g.pose().popMatrix();
		}
	}

	private int escala() {
		int alvo = d.w * d.celula / 5;
		for (int e : new int[] {50, 100, 200, 250, 500, 1000}) {
			if (e >= alvo) {
				return e;
			}
		}
		return 1000;
	}

	private void linha(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int cor, boolean grossa, boolean tracejada) {
		int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy, passo = 0;
		while (true) {
			if (!tracejada || passo++ % 6 < 4) {
				g.fill(x0, y0, x0 + (grossa ? 2 : 1), y0 + (grossa ? 2 : 1), cor);
			}
			if (x0 == x1 && y0 == y1) {
				break;
			}
			int e2 = 2 * err;
			if (e2 >= dy) {
				err += dy;
				x0 += sx;
			}
			if (e2 <= dx) {
				err += dx;
				y0 += sy;
			}
		}
	}

	/** The road legend, small, in a box at the bottom of the chart. */
	private void legenda(GuiGraphicsExtractor g) {
		String[] chaves = {"rota", "batida", "fechada", "guerra", "embargo"};
		float s = 0.7F;
		int largura = 0;
		for (String k : chaves) {
			largura += 16 + this.font.width(Component.translatable("arraial.mapa.legenda." + k));
		}
		g.pose().pushMatrix();
		g.pose().translate(mx0 + 3, my0 + mt - 3 - 10 * s);
		g.pose().scale(s, s);
		g.fill(-2, -2, largura + 2, 10, 0xA0201810);
		int x = 0;
		for (int i = 0; i < chaves.length; i++) {
			g.fill(x, 3, x + 10, i == 1 ? 6 : 5, ROTAS[i]);
			Component t = Component.translatable("arraial.mapa.legenda." + chaves[i]);
			g.text(this.font, t, x + 13, 0, 0xFFF0E6D0, false);
			x += 16 + this.font.width(t);
		}
		g.pose().popMatrix();
	}

	private List<Component> ficha(MapaDados.Vila v, int i) {
		List<Component> l = new ArrayList<>();
		l.add(Component.literal(v.nome).withStyle(ChatFormatting.BOLD));
		if (v.abandonada) {
			l.add(Component.translatable(v.bandidos ? "arraial.mapa.bandidos" : "arraial.mapa.ruina").withStyle(ChatFormatting.RED));
			return l;
		}
		l.add(Component.translatable("arraial.mapa.linha1", Estagio.de(v.estagio).nome(), v.populacao).withStyle(ChatFormatting.GRAY));
		l.add(Component.translatable("arraial.mapa.linha2", Component.translatable("arraial.era." + (v.era.isEmpty() ? "colonial" : v.era)),
				Component.translatable("arraial.especialidade." + (v.especialidade.isEmpty() ? "mista" : v.especialidade))).withStyle(ChatFormatting.GRAY));
		if (i == d.meu) {
			l.add(Component.translatable("arraial.mapa.nosso").withStyle(ChatFormatting.GOLD));
		} else if (d.meu >= 0) {
			l.addAll(relacoes(v));
		}
		return l;
	}

	private List<Component> relacoes(MapaDados.Vila v) {
		List<Component> l = new ArrayList<>();
		int minha = d.meu >= 0 ? d.vilas.get(d.meu).forca : 0;
		l.add(Component.translatable("arraial.mapa.rep", v.rep, v.tensao).withStyle(v.rep < 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
		l.add(Component.translatable("arraial.mapa.forca", v.forca, minha).withStyle(ChatFormatting.GRAY));
		if (v.estado == 2) {
			l.add(Component.translatable("arraial.mapa.guerra").withStyle(ChatFormatting.RED));
		} else if (v.estado == 1) {
			l.add(Component.translatable("arraial.mapa.disputa").withStyle(ChatFormatting.GOLD));
		}
		if (!v.tratado.isEmpty()) {
			l.add(Component.translatable("arraial.mapa.tratado", Component.translatable("arraial.tratado." + v.tratado), v.dias)
					.withStyle(ChatFormatting.AQUA));
			if (v.tributo != 0) {
				l.add(Component.translatable(v.tributo > 0 ? "arraial.mapa.recebe" : "arraial.mapa.paga", Math.abs(v.tributo))
						.withStyle(ChatFormatting.AQUA));
			}
		}
		if (v.embargamos) {
			l.add(Component.translatable("arraial.mapa.embargamos").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (v.embargados) {
			l.add(Component.translatable("arraial.mapa.embargados").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (v.batida) {
			l.add(Component.translatable("arraial.mapa.batida").withStyle(ChatFormatting.DARK_GREEN));
		}
		return l;
	}

	/** Neighbours in order of distance from our arraial (or the player). */
	private List<Integer> ordem() {
		int cx = d.meu >= 0 ? d.vilas.get(d.meu).x : d.jx, cz = d.meu >= 0 ? d.vilas.get(d.meu).z : d.jz;
		List<Integer> l = new ArrayList<>();
		for (int i = 0; i < d.vilas.size(); i++) {
			if (i != d.meu) {
				l.add(i);
			}
		}
		l.sort(Comparator.comparingLong(i -> {
			MapaDados.Vila v = d.vilas.get(i);
			return (long) (v.x - cx) * (v.x - cx) + (long) (v.z - cz) * (v.z - cz);
		}));
		return l;
	}

	// ---------------------------------------------------------------- the side panel: list, details, buttons

	private static final int BOTAO = 14, LINHA = 10;

	private int topoBotoes() {
		return my0 + mt - 3 * (BOTAO + 2) + 2;
	}

	/** Bottom of the details block (above the paper line and the buttons, or the hint). */
	private int fimDetalhe() {
		return d.mesa ? topoBotoes() - 12 : my0 + mt - 22;
	}

	private int topoLista() {
		return my0 + 12;
	}

	/** Rows of the list: what is left after the details (at least two). */
	private int linhasLista() {
		int detalhe = 6 * LINHA;
		return Math.max(2, (fimDetalhe() - detalhe - 4 - topoLista()) / LINHA);
	}

	/** One line, cut to the panel width with an ellipsis. */
	private void linhaCortada(GuiGraphicsExtractor g, Component c, int x, int y, int largura, int cor) {
		String s = c.getString();
		if (this.font.width(s) > largura) {
			s = this.font.plainSubstrByWidth(s, largura - this.font.width("…")) + "…";
		}
		g.text(this.font, Component.literal(s).withStyle(c.getStyle()), x, y, cor);
	}

	/** The details of a neighbour in order of importance (the panel shows as many as fit). */
	private List<Component> detalhe(MapaDados.Vila v, int i) {
		List<Component> l = new ArrayList<>();
		l.add(Component.literal(v.nome).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
		if (v.abandonada) {
			l.add(Component.translatable(v.bandidos ? "arraial.mapa.bandidos" : "arraial.mapa.ruina").withStyle(ChatFormatting.RED));
			return l;
		}
		if (i == d.meu || d.meu < 0) {
			l.add(Component.translatable("arraial.mapa.linha1", Estagio.de(v.estagio).nome(), v.populacao).withStyle(ChatFormatting.GRAY));
			l.add(Component.translatable("arraial.mapa.linha2", Component.translatable("arraial.era." + (v.era.isEmpty() ? "colonial" : v.era)),
					Component.translatable("arraial.especialidade." + (v.especialidade.isEmpty() ? "mista" : v.especialidade))).withStyle(ChatFormatting.GRAY));
			if (i == d.meu) {
				l.add(Component.translatable("arraial.mapa.nosso").withStyle(ChatFormatting.GOLD));
			}
			return l;
		}
		List<Component> r = relacoes(v);
		l.addAll(r);
		l.add(Component.translatable("arraial.mapa.linha1", Estagio.de(v.estagio).nome(), v.populacao).withStyle(ChatFormatting.GRAY));
		return l;
	}

	private void painel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.fill(px0 - 4, my0 - 3, this.width - 4, my0 + mt + 3, 0x80000000);
		if (d.meu < 0 && d.vilas.isEmpty()) {
			g.textWithWordWrap(this.font, Component.translatable("arraial.mapa.vazio"), px0, my0, pw, 0xFFD8D0C0);
			return;
		}
		g.text(this.font, Component.translatable("arraial.mapa.vizinhos").withStyle(ChatFormatting.UNDERLINE), px0, my0, 0xFFF0D890);
		int y = topoLista();
		List<Integer> ordem = ordem();
		int n = linhasLista();
		rolagem = Math.max(0, Math.min(rolagem, ordem.size() - n));
		for (int k = rolagem; k < Math.min(ordem.size(), rolagem + n); k++) {
			int i = ordem.get(k);
			MapaDados.Vila v = d.vilas.get(i);
			if (i == selecionada) {
				g.fill(px0 - 2, y - 1, px0 + pw, y + LINHA - 1, 0x50FFFFFF);
			} else if (mouseX >= px0 && mouseX < px0 + pw && mouseY >= y - 1 && mouseY < y + LINHA - 1) {
				g.fill(px0 - 2, y - 1, px0 + pw, y + LINHA - 1, 0x25FFFFFF);
			}
			g.fill(px0, y + 1, px0 + 6, y + 7, v.abandonada ? 0xFF555555 : v.cor);
			String ic = icones(v);
			int wi = this.font.width(ic);
			linhaCortada(g, Component.literal(v.nome), px0 + 9, y, pw - 12 - wi, v.abandonada ? 0xFF909090 : 0xFFFFFFFF);
			g.text(this.font, ic, px0 + pw - wi - 2, y, 0xFFFFFFFF);
			y += LINHA;
		}
		if (ordem.size() > n) {
			g.text(this.font, "⇕", px0 + pw - 8, my0, 0xFFB0A080);
		}
		// The chosen neighbour, as much as fits.
		int topo = y + 4, fim = fimDetalhe();
		g.fill(px0 - 2, topo - 2, px0 + pw, topo - 1, 0x40FFFFFF);
		MapaDados.Vila v = escolhida();
		if (v == null && selecionada == d.meu && d.meu >= 0) {
			v = d.vilas.get(d.meu);
		}
		if (v != null) {
			for (Component c : detalhe(v, d.vilas.indexOf(v))) {
				if (topo + LINHA > fim + 2) {
					break;
				}
				linhaCortada(g, c, px0, topo, pw, 0xFFFFFFFF);
				topo += LINHA;
			}
		} else {
			g.textWithWordWrap(this.font, Component.translatable("arraial.mapa.dica"), px0, topo, pw, 0xFFB0A080);
		}
		if (d.mesa) {
			Component papel = d.podeHoje ? Component.translatable("arraial.mapa.papel", d.papel) : Component.translatable("arraial.territorio.ja_hoje");
			linhaCortada(g, papel, px0, topoBotoes() - 11, pw, d.podeHoje && d.papel > 0 ? 0xFFB0A080 : 0xFFE08060);
		} else {
			g.textWithWordWrap(this.font, Component.translatable("arraial.mapa.so_mesa"), px0, my0 + mt - 20, pw, 0xFFB0A080);
		}
	}

	private static String icones(MapaDados.Vila v) {
		StringBuilder s = new StringBuilder();
		if (v.abandonada) {
			s.append(v.bandidos ? "☠" : "✕");
		}
		if (v.estado == 2) {
			s.append("⚔");
		} else if (v.estado == 1) {
			s.append("⚠");
		}
		if (!v.tratado.isEmpty()) {
			s.append("✉");
		}
		if (v.embargamos || v.embargados) {
			s.append("⛔");
		}
		return s.toString();
	}

	// ================================================================ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double x = event.x(), y = event.y();
		for (int i = 0; i < d.vilas.size(); i++) {
			MapaDados.Vila v = d.vilas.get(i);
			if (Math.abs(x - telaX(v.x)) <= 5 && Math.abs(y - telaZ(v.z)) <= 5) {
				selecionar(i);
				return true;
			}
		}
		int ly = topoLista();
		List<Integer> ordem = ordem();
		int n = linhasLista();
		if (x >= px0 && x < px0 + pw && y >= ly - 1) {
			int k = (int) ((y - ly + 1) / LINHA) + rolagem;
			if (k >= rolagem && k < Math.min(ordem.size(), rolagem + n)) {
				selecionar(ordem.get(k));
				return true;
			}
		}
		return false;
	}

	private void selecionar(int i) {
		selecionada = i;
		atualizarBotoes();
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		rolagem -= (int) Math.signum(sy);
		return true;
	}

	/** For the test script: select a neighbour by name and press an action button. */
	public boolean testar(String nome, String acao) {
		for (int i = 0; i < d.vilas.size(); i++) {
			if (d.vilas.get(i).nome.equals(nome)) {
				selecionar(i);
				Button b = botoes.get(acao);
				if (b != null && b.active) {
					agir(acao);
					return true;
				}
			}
		}
		return false;
	}

	public MapaDados dados() {
		return d;
	}

}
