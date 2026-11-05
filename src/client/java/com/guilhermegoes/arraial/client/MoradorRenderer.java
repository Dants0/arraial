package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;

/**
 * Draws moradores with the player-shaped body. Skins live in textures/entity/morador/ as
 * {@code <oficio>_<m|f>_<visual>.png}: the trade sets the clothes, the visual sets the face
 * (skin tone, hair), so a morador who changes trade keeps looking like the same person.
 * Worn armour (the soldier's helmet and coat) is drawn on top, and the name tag only shows
 * up close or when looked at, so a busy arraial is not covered in floating text.
 */
public class MoradorRenderer extends HumanoidMobRenderer<MoradorEntity, MoradorRenderState, HumanoidModel<MoradorRenderState>> {
	/** Name tags show within this distance (or when the crosshair is on the morador). */
	private static final double DISTANCIA_PLACA = 8.0;

	private static final Map<Profissao, Identifier[][]> SKINS = new EnumMap<>(Profissao.class);

	static {
		for (Profissao p : Profissao.values()) {
			String oficio = p == Profissao.NENHUMA ? "morador" : p.name().toLowerCase(Locale.ROOT);
			Identifier[][] porSexo = new Identifier[2][MoradorEntity.VISUAIS];
			for (int i = 0; i < MoradorEntity.VISUAIS; i++) {
				porSexo[0][i] = Arraial.id("textures/entity/morador/" + oficio + "_m_" + i + ".png");
				porSexo[1][i] = Arraial.id("textures/entity/morador/" + oficio + "_f_" + i + ".png");
			}
			SKINS.put(p, porSexo);
		}
	}

	public MoradorRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
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
	protected boolean shouldShowName(MoradorEntity entity, double distanceToCameraSq) {
		boolean perto = distanceToCameraSq < DISTANCIA_PLACA * DISTANCIA_PLACA;
		return (perto || entity == entityRenderDispatcher.crosshairPickEntity) && super.shouldShowName(entity, distanceToCameraSq);
	}

	@Override
	public Identifier getTextureLocation(MoradorRenderState state) {
		Identifier[] lista = SKINS.get(state.profissao)[state.feminino ? 1 : 0];
		return lista[Math.floorMod(state.visual, lista.length)];
	}
}
