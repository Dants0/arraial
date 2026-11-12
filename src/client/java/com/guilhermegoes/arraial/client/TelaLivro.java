package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.rede.LivroDados;
import com.guilhermegoes.arraial.rede.Rede;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Livro de Projetos: a catalog of every building. On the left, the projects by category with a
 * search box (accents optional); on the right, the chosen one: what it is for, its size, a top-down
 * plan, the trades it brings, how many the village has, and the materials with what the armazém
 * already holds. Opened on a Marco da Obra, "Build here" starts it there; otherwise "Choose" keeps
 * it in the book. Arrow keys move through the list, Enter confirms.
 */
public class TelaLivro extends Screen {
	// Colours: a project book of parchment and dark wood.
	private static final int MOLDURA = 0xFF3B2A16, PERGAMINHO = 0xFFEADBB4, LISTA = 0xFFDCCA9C, ESCOLHIDA = 0xFFC49A55,
			SOBRE = 0x40FFFFFF, TINTA = 0xFF3B2A16, TINTA2 = 0xFF6E5838, OURO = 0xFF8A5A10, VERDE = 0xFF2E7D32, VERMELHO = 0xFFB03A2E,
			CABECALHO = 0xFF7A5A2A;
	private static final int LINHA = 18, CABECA = 13;
	/** GLFW key codes (fixed by GLFW): arrows and Enter. */
	private static final int TECLA_BAIXO = 264, TECLA_CIMA = 265, TECLA_ENTER = 257, TECLA_ENTER_NUM = 335;

	private record Linha(@Nullable String categoria, LivroDados.@Nullable Projeto projeto) {
	}

	private final LivroDados d;
	private final List<Linha> linhas = new ArrayList<>();
	private LivroDados.@Nullable Projeto escolhido;
	private @Nullable String pedido;
	private String filtro = "";
	private int rolagem;
	private @Nullable EditBox busca;
	private @Nullable Button confirmar;
	// Layout.
	private int x0, y0, w, h, lx, lw, ly, lh, rx, rw, ry;

	public TelaLivro(LivroDados d) {
		super(Component.translatable("item.arraial.livro_de_projetos"));
		this.d = d;
		this.pedido = projetoDoPedido(d.pedido);
		LivroDados.Projeto inicial = null;
		for (LivroDados.Projeto p : d.projetos) {
			if (p.id().equals(d.selecionado) || inicial == null && p.id().equals(pedido)) {
				inicial = p;
			}
		}
		this.escolhido = inicial != null ? inicial : d.projetos.isEmpty() ? null : d.projetos.get(0);
	}

	public static void abrir(LivroDados d) {
		Minecraft.getInstance().gui.setScreen(new TelaLivro(d));
	}

	/** The project the prefeito is asking for ("A vila precisa de <projeto> ..."), if any. */
	private static @Nullable String projetoDoPedido(Component pedido) {
		if (pedido.getContents() instanceof TranslatableContents t) {
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component c && c.getContents() instanceof TranslatableContents a && a.getKey().startsWith("projeto.arraial.")) {
					return a.getKey().substring("projeto.arraial.".length());
				}
			}
		}
		return null;
	}

	private static Component nome(String id) {
		return Component.translatable("projeto.arraial." + id);
	}

	private static String simples(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ================================================================ layout and widgets

	@Override
	protected void init() {
		w = Math.min(this.width - 16, 460);
		h = Math.min(this.height - 16, 280);
		x0 = (this.width - w) / 2;
		y0 = (this.height - h) / 2;
		lx = x0 + 8;
		lw = Math.max(130, Math.min(178, w * 38 / 100));
		int corpo = y0 + (d.pedido.getString().isEmpty() ? 26 : 36);
		ly = corpo + 18;
		lh = y0 + h - 30 - ly;
		rx = lx + lw + 10;
		rw = x0 + w - 8 - rx;
		ry = corpo;

		busca = new EditBox(this.font, lx, corpo, lw, 14, Component.translatable("arraial.livro.buscar"));
		busca.setHint(Component.translatable("arraial.livro.buscar").withStyle(ChatFormatting.GRAY));
		busca.setValue(filtro);
		busca.setResponder(s -> {
			filtro = s;
			rolagem = 0;
			montarLinhas();
		});
		addRenderableWidget(busca);

		boolean noMarco = d.marco != Long.MIN_VALUE;
		confirmar = addRenderableWidget(Button.builder(Component.translatable(noMarco ? "arraial.livro.construir" : "arraial.livro.escolher"),
				b -> confirmar()).bounds(x0 + w - 8 - 110, y0 + h - 24, 110, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("arraial.livro.fechar"), b -> onClose())
				.bounds(x0 + w - 8 - 110 - 6 - 70, y0 + h - 24, 70, 18).build());
		montarLinhas();
		setInitialFocus(busca);
	}

	private void montarLinhas() {
		linhas.clear();
		String f = simples(filtro.trim());
		for (String cat : LivroDados.CATEGORIAS) {
			List<LivroDados.Projeto> desta = new ArrayList<>();
			for (LivroDados.Projeto p : d.projetos) {
				if (p.categoria().equals(cat) && (f.isEmpty() || simples(nome(p.id()).getString()).contains(f) || p.id().contains(f))) {
					desta.add(p);
				}
			}
			if (desta.isEmpty()) {
				continue;
			}
			desta.sort((a, b) -> nome(a.id()).getString().compareTo(nome(b.id()).getString()));
			linhas.add(new Linha(cat, null));
			desta.forEach(p -> linhas.add(new Linha(null, p)));
		}
		if (escolhido == null || linhas.stream().noneMatch(l -> l.projeto() == escolhido)) {
			linhas.stream().filter(l -> l.projeto() != null).findFirst().ifPresent(l -> escolhido = l.projeto());
		}
		if (confirmar != null) {
			confirmar.active = escolhido != null;
		}
	}

	private int alturaLista() {
		int t = 0;
		for (Linha l : linhas) {
			t += l.projeto() == null ? CABECA : LINHA;
		}
		return t;
	}

	private void confirmar() {
		if (escolhido != null) {
			ClientPlayNetworking.send(new Rede.EscolherProjeto(escolhido.id(), d.marco));
			onClose();
		}
	}

	/** The building's icon: its workstation, or else its main material. */
	private static ItemStack icone(LivroDados.Projeto p) {
		for (String oficio : p.oficios()) {
			Item bloco = posto(oficio);
			if (bloco != null && bloco != Items.AIR) {
				return new ItemStack(bloco);
			}
		}
		return p.materiais().isEmpty() ? new ItemStack(Items.BRICKS) : new ItemStack(LivroDados.item(p.materiais().get(0).item()));
	}

	private static @Nullable Item posto(String oficio) {
		for (var prof : com.guilhermegoes.arraial.npc.Profissao.values()) {
			if (prof.id().equals(oficio) && prof.posto() != null) {
				return prof.posto().asItem();
			}
		}
		return null;
	}

	// ================================================================ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		g.fill(0, 0, this.width, this.height, 0xA0100C08);
		g.fill(x0 - 3, y0 - 3, x0 + w + 3, y0 + h + 3, MOLDURA);
		g.fill(x0, y0, x0 + w, y0 + h, PERGAMINHO);
		g.fill(x0, y0 + h - 30, x0 + w, y0 + h - 29, 0x40000000);

		// Header: title, village, the prefeito's request.
		g.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x0 + 8, y0 + 7, TINTA, false);
		if (!d.vila.isEmpty()) {
			int vw = this.font.width(d.vila);
			g.text(this.font, d.vila, x0 + w - 8 - vw, y0 + 7, TINTA2, false);
		}
		if (!d.pedido.getString().isEmpty()) {
			Component p = Component.translatable("arraial.livro.pedido").withStyle(ChatFormatting.BOLD).append(" ")
					.append(d.pedido.copy().withStyle(ChatFormatting.RESET));
			g.text(this.font, this.font.split(p, w - 16).get(0), x0 + 8, y0 + 19, OURO, false);
		}

		lista(g, mx, my);
		detalhe(g, mx, my);

		// Footer hint.
		Component dica = Component.translatable(d.marco != Long.MIN_VALUE ? "arraial.livro.dica_marco" : "arraial.livro.dica_ar");
		int largura = x0 + w - 8 - 110 - 6 - 70 - 8 - (x0 + 8);
		List<FormattedCharSequence> l = this.font.split(dica, largura);
		for (int i = 0; i < Math.min(2, l.size()); i++) {
			g.text(this.font, l.get(i), x0 + 8, y0 + h - 25 + i * 10, TINTA2, false);
		}
		super.extractRenderState(g, mx, my, a);
	}

	private void lista(GuiGraphicsExtractor g, int mx, int my) {
		g.fill(lx - 1, ly - 1, lx + lw + 1, ly + lh + 1, 0x60000000);
		g.fill(lx, ly, lx + lw, ly + lh, LISTA);
		int max = Math.max(0, alturaLista() - lh);
		rolagem = Math.max(0, Math.min(rolagem, max));
		g.enableScissor(lx, ly, lx + lw, ly + lh);
		int y = ly - rolagem;
		if (linhas.isEmpty()) {
			g.textWithWordWrap(this.font, Component.translatable("arraial.livro.nada"), lx + 4, ly + 4, lw - 8, TINTA2);
		}
		for (Linha l : linhas) {
			if (l.projeto() == null) {
				g.text(this.font, Component.translatable("arraial.livro.cat." + l.categoria()).copy().withStyle(ChatFormatting.BOLD), lx + 4, y + 3, CABECALHO, false);
				g.fill(lx + 4, y + CABECA - 1, lx + lw - 4, y + CABECA, 0x40000000);
				y += CABECA;
				continue;
			}
			LivroDados.Projeto p = l.projeto();
			boolean sobre = mx >= lx && mx < lx + lw && my >= Math.max(y, ly) && my < Math.min(y + LINHA, ly + lh);
			if (p == escolhido) {
				g.fill(lx, y, lx + lw, y + LINHA, ESCOLHIDA);
			} else if (sobre) {
				g.fill(lx, y, lx + lw, y + LINHA, SOBRE);
			}
			g.item(icone(p), lx + 3, y + 1);
			String marca = (p.id().equals(pedido) ? "★ " : "") + (p.naVila() > 0 ? "×" + p.naVila() : "");
			int mw = marca.isEmpty() ? 0 : this.font.width(marca) + 4;
			Component n = nome(p.id());
			String txt = this.font.substrByWidth(n, lw - 24 - mw).getString();
			g.text(this.font, txt.length() < n.getString().length() ? txt.substring(0, Math.max(0, txt.length() - 1)) + "…" : n.getString(),
					lx + 22, y + 5, TINTA, false);
			if (!marca.isEmpty()) {
				g.text(this.font, marca, lx + lw - mw, y + 5, p.id().equals(pedido) ? OURO : TINTA2, false);
			}
			y += LINHA;
		}
		g.disableScissor();
		// Scroll bar.
		if (max > 0) {
			int barra = Math.max(12, lh * lh / alturaLista());
			int by = ly + (lh - barra) * rolagem / max;
			g.fill(lx + lw - 3, by, lx + lw - 1, by + barra, 0x80000000);
		}
	}

	private void detalhe(GuiGraphicsExtractor g, int mx, int my) {
		LivroDados.Projeto p = escolhido;
		if (p == null) {
			return;
		}
		int y = ry;
		// Plan on the right: one pixel-square per column, the front (z = 0) at the bottom.
		int caixa = Math.min(84, rw / 3);
		int px = rx + rw - caixa;
		g.fill(px - 1, y - 1, px + caixa + 1, y + caixa + 1, MOLDURA);
		g.fill(px, y, px + caixa, y + caixa, 0xFFF4EBD2);
		int lado = Math.max(1, Math.min(caixa / Math.max(1, p.plantaLargura()), caixa / Math.max(1, p.plantaFundo())));
		int ox = px + (caixa - lado * p.plantaLargura()) / 2, oy = y + (caixa - lado * p.plantaFundo()) / 2;
		for (int z = 0; z < p.plantaFundo(); z++) {
			for (int x = 0; x < p.plantaLargura(); x++) {
				int c = p.planta()[z * p.plantaLargura() + x];
				if (c != 0) {
					int sx = ox + x * lado, sy = oy + (p.plantaFundo() - 1 - z) * lado;
					g.fill(sx, sy, sx + lado, sy + lado, c | 0xFF000000);
				}
			}
		}
		g.centeredText(this.font, Component.translatable("arraial.livro.frente"), px + caixa / 2, y + caixa + 3, TINTA2);

		// Text on the left of the plan.
		int tw = rw - caixa - 10;
		g.text(this.font, nome(p.id()).copy().withStyle(ChatFormatting.BOLD), rx, y, TINTA, false);
		y += 11;
		g.text(this.font, Component.translatable("arraial.livro.cat." + p.categoria()), rx, y, CABECALHO, false);
		y += 13;
		String chave = "projeto.arraial." + p.id() + ".desc";
		Component desc = Component.translatable(chave);
		if (!desc.getString().equals(chave)) {
			for (FormattedCharSequence linha : this.font.split(desc, tw)) {
				g.text(this.font, linha, rx, y, TINTA, false);
				y += 10;
			}
			y += 3;
		}
		g.text(this.font, Component.translatable("arraial.livro.tamanho", p.largura(), p.fundo(), p.altura()), rx, y, TINTA2, false);
		y += 10;
		if (!p.oficios().isEmpty()) {
			Component of = Component.empty();
			for (int i = 0; i < p.oficios().size(); i++) {
				of = of.copy().append(i == 0 ? "" : ", ").append(Component.translatable("arraial.profissao." + p.oficios().get(i) + ".m"));
			}
			for (FormattedCharSequence linha : this.font.split(Component.translatable("arraial.livro.oficio", of), tw)) {
				g.text(this.font, linha, rx, y, TINTA2, false);
				y += 10;
			}
		}
		if (p.naVila() > 0) {
			g.text(this.font, Component.translatable("arraial.livro.na_vila", p.naVila()), rx, y, TINTA2, false);
			y += 10;
		}
		if (p.id().equals(pedido)) {
			g.text(this.font, Component.translatable("arraial.livro.pedido_marca"), rx, y, OURO, false);
			y += 10;
		}

		// Materials: icon, "in the armazém / needed" (green when there is enough).
		y = Math.max(y + 4, ry + caixa + 16);
		g.text(this.font, Component.translatable(d.temArmazem ? "arraial.livro.materiais" : "arraial.livro.materiais_sem_armazem")
				.copy().withStyle(ChatFormatting.BOLD), rx, y, TINTA, false);
		y += 12;
		int col = 62, colunas = Math.max(1, rw / col);
		int fim = y0 + h - 34;
		int i = 0;
		for (LivroDados.Material m : p.materiais()) {
			int cx = rx + (i % colunas) * col, cy = y + (i / colunas) * 18;
			if (cy + 16 > fim) {
				g.text(this.font, "…", cx, cy + 4, TINTA2, false);
				break;
			}
			ItemStack st = new ItemStack(LivroDados.item(m.item()));
			g.item(st, cx, cy);
			boolean basta = m.noArmazem() >= m.quantidade();
			String t = d.temArmazem ? Math.min(m.noArmazem(), 999) + "/" + m.quantidade() : String.valueOf(m.quantidade());
			g.text(this.font, t, cx + 18, cy + 4, d.temArmazem ? (basta ? VERDE : VERMELHO) : TINTA, false);
			if (mx >= cx && mx < cx + 16 && my >= cy && my < cy + 16) {
				g.setTooltipForNextFrame(this.font, st.getHoverName(), mx, my);
			}
			i++;
		}
	}

	// ================================================================ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double x = event.x(), y = event.y();
		if (x >= lx && x < lx + lw && y >= ly && y < ly + lh) {
			int yy = ly - rolagem;
			for (Linha l : linhas) {
				int alt = l.projeto() == null ? CABECA : LINHA;
				if (l.projeto() != null && y >= yy && y < yy + alt) {
					if (escolhido == l.projeto() && doubleClick) {
						confirmar();
					}
					escolhido = l.projeto();
					if (confirmar != null) {
						confirmar.active = true;
					}
					return true;
				}
				yy += alt;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		rolagem -= (int) Math.signum(sy) * LINHA;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k == TECLA_BAIXO || k == TECLA_CIMA) {
			mover(k == TECLA_BAIXO ? 1 : -1);
			return true;
		}
		if (k == TECLA_ENTER || k == TECLA_ENTER_NUM) {
			confirmar();
			return true;
		}
		return super.keyPressed(event);
	}

	/** For the automated test script: type in the search box, pick, confirm. */
	void testarBusca(String texto) {
		if (busca != null) {
			busca.setValue(texto);
		}
	}

	void testarConfirmar() {
		confirmar();
	}

	@Nullable String testarEscolhido() {
		return escolhido == null ? null : escolhido.id();
	}

	/** Moves the selection along the visible list and keeps it in view. */
	private void mover(int passo) {
		List<LivroDados.Projeto> visiveis = linhas.stream().map(Linha::projeto).filter(java.util.Objects::nonNull).toList();
		if (visiveis.isEmpty()) {
			return;
		}
		int i = Math.max(0, visiveis.indexOf(escolhido));
		escolhido = visiveis.get(Math.floorMod(i + passo, visiveis.size()));
		int y = 0;
		for (Linha l : linhas) {
			if (l.projeto() == escolhido) {
				break;
			}
			y += l.projeto() == null ? CABECA : LINHA;
		}
		if (y < rolagem) {
			rolagem = Math.max(0, y - CABECA);
		} else if (y + LINHA > rolagem + lh) {
			rolagem = y + LINHA - lh;
		}
	}
}
