package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.config.ArraialConfig;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The settings screen (opened from Mod Menu): toggles and sliders for {@link ArraialConfig}, in two
 * columns, with Done (saves) and Cancel. Each option has a tooltip explaining it.
 */
public class TelaConfig extends Screen {
	private final @Nullable Screen anterior;
	private final ArraialConfig c = ArraialConfig.get();
	// A copy to restore on Cancel.
	private final String antes = new com.google.gson.Gson().toJson(ArraialConfig.get());

	public TelaConfig(@Nullable Screen anterior) {
		super(Component.translatable("arraial.config.titulo"));
		this.anterior = anterior;
	}

	private int coluna, linha;

	private int x() {
		return this.width / 2 + (coluna == 0 ? -155 : 5);
	}

	private int y() {
		return 44 + linha * 24;
	}

	private void proxima() {
		if (++coluna > 1) {
			coluna = 0;
			linha++;
		}
	}

	private void alternar(String chave, boolean valor, Consumer<Boolean> definir) {
		addRenderableWidget(CycleButton.onOffBuilder(valor).create(x(), y(), 150, 20, Component.translatable("arraial.config." + chave),
				(b, v) -> definir.accept(v))).setTooltip(Tooltip.create(Component.translatable("arraial.config." + chave + ".dica")));
		proxima();
	}

	private void deslizar(String chave, int valor, int min, int max, String unidade, IntConsumer definir) {
		var s = new AbstractSliderButton(x(), y(), 150, 20, Component.empty(), (valor - min) / (double) (max - min)) {
			{
				updateMessage();
			}

			private int atual() {
				return (int) Math.round(min + value * (max - min));
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.translatable("arraial.config." + chave).append(": " + atual() + unidade));
			}

			@Override
			protected void applyValue() {
				definir.accept(atual());
			}
		};
		s.setTooltip(Tooltip.create(Component.translatable("arraial.config." + chave + ".dica")));
		addRenderableWidget(s);
		proxima();
	}

	@Override
	protected void init() {
		coluna = 0;
		linha = 0;
		alternar("vizinhas_perto", c.vizinhasPerto, v -> c.vizinhasPerto = v);
		deslizar("max_autonomas", c.maxAutonomas, 0, 40, "", v -> c.maxAutonomas = v);
		deslizar("chance_missao", c.chanceMissao, 0, 100, "%", v -> c.chanceMissao = v);
		alternar("fundacao_ao_vivo", c.fundacaoAoVivo, v -> c.fundacaoAoVivo = v);
		alternar("bases_ocupadas", c.basesOcupadas, v -> c.basesOcupadas = v);
		alternar("crises", c.crises, v -> c.crises = v);
		deslizar("frequencia_crises", c.frequenciaCrises, 0, 300, "%", v -> c.frequenciaCrises = v);
		deslizar("chegada_segundos", c.chegadaSegundos, 10, 300, " s", v -> c.chegadaSegundos = v);
		alternar("soldados_armazem", c.soldadosUsamArmazem, v -> c.soldadosUsamArmazem = v);
		alternar("aviso_fronteira", c.avisoFronteira, v -> c.avisoFronteira = v);
		alternar("waypoints_mapa", c.waypointsNoMapa, v -> c.waypointsNoMapa = v);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
			ArraialConfig.salvar();
			IntegracaoXaero.configMudou();
			onClose();
		}).bounds(this.width / 2 - 155, this.height - 28, 150, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
			// Put the old values back into the live config.
			ArraialConfig velha = new com.google.gson.Gson().fromJson(antes, ArraialConfig.class);
			for (var f : ArraialConfig.class.getFields()) {
				try {
					f.set(c, f.get(velha));
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
			onClose();
		}).bounds(this.width / 2 + 5, this.height - 28, 150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, 14, 0xFFFFFFFF);
		g.centeredText(this.font, Component.translatable("arraial.config.servidor").withStyle(ChatFormatting.GRAY), this.width / 2, 28, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(anterior);
	}
}
