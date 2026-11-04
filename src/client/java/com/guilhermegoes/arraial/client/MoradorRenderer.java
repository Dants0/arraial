package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Draws moradores with the player-shaped body. Skins live in textures/entity/morador/:
 * homem_N / mulher_N for ordinary moradores and pedreiro / pedreira for the builders.
 */
public class MoradorRenderer extends HumanoidMobRenderer<MoradorEntity, MoradorRenderState, HumanoidModel<MoradorRenderState>> {
	private static final Identifier[] HOMENS = skins("homem");
	private static final Identifier[] MULHERES = skins("mulher");
	private static final Identifier PEDREIRO = Arraial.id("textures/entity/morador/pedreiro.png");
	private static final Identifier PEDREIRA = Arraial.id("textures/entity/morador/pedreira.png");

	public MoradorRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
	}

	private static Identifier[] skins(String base) {
		Identifier[] ids = new Identifier[MoradorEntity.VISUAIS];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = Arraial.id("textures/entity/morador/" + base + "_" + i + ".png");
		}
		return ids;
	}

	@Override
	public MoradorRenderState createRenderState() {
		return new MoradorRenderState();
	}

	@Override
	public void extractRenderState(MoradorEntity entity, MoradorRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.profissao = entity.profissao();
		state.feminino = entity.feminino();
		state.visual = entity.visual();
	}

	@Override
	public Identifier getTextureLocation(MoradorRenderState state) {
		if (state.profissao == Profissao.PEDREIRO) {
			return state.feminino ? PEDREIRA : PEDREIRO;
		}
		Identifier[] lista = state.feminino ? MULHERES : HOMENS;
		return lista[Math.floorMod(state.visual, lista.length)];
	}
}
