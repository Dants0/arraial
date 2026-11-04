package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.PedreiroEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Draws the pedreiro with the standard player-shaped body and our own 64x64 skin. */
public class PedreiroRenderer extends HumanoidMobRenderer<PedreiroEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURA = Arraial.id("textures/entity/pedreiro.png");

	public PedreiroRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURA;
	}
}
