package com.guilhermegoes.arraial.client.fauna;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.fauna.Arara;
import com.guilhermegoes.arraial.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/** Models and renderers of the Brazilian fauna. */
public final class FaunaClient {
	public static final ModelLayerLocation CAPIVARA = camada("capivara");
	public static final ModelLayerLocation TAMANDUA = camada("tamandua");
	public static final ModelLayerLocation JACARE = camada("jacare");
	public static final ModelLayerLocation ONCA = camada("onca");
	public static final ModelLayerLocation ARARA = camada("arara");

	private static ModelLayerLocation camada(String nome) {
		return new ModelLayerLocation(Arraial.id(nome), "main");
	}

	public static void init() {
		ModelLayerRegistry.registerModelLayer(CAPIVARA, ModelosFauna::capivara);
		ModelLayerRegistry.registerModelLayer(TAMANDUA, ModelosFauna::tamandua);
		ModelLayerRegistry.registerModelLayer(JACARE, ModelosFauna::jacare);
		ModelLayerRegistry.registerModelLayer(ONCA, ModelosFauna::onca);
		ModelLayerRegistry.registerModelLayer(ARARA, ModelosFauna::arara);
		EntityRendererRegistry.register(ModEntities.CAPIVARA, c -> new Bicho<>(c, new Quadrupede(c.bakeLayer(CAPIVARA)), 0.6F, "capivara"));
		EntityRendererRegistry.register(ModEntities.TAMANDUA, c -> new Bicho<>(c, new Tamandua(c.bakeLayer(TAMANDUA)), 0.55F, "tamandua"));
		EntityRendererRegistry.register(ModEntities.JACARE, c -> new Bicho<>(c, new Jacare(c.bakeLayer(JACARE)), 0.7F, "jacare"));
		EntityRendererRegistry.register(ModEntities.ONCA, c -> new Bicho<>(c, new Onca(c.bakeLayer(ONCA)), 0.6F, "onca"));
		EntityRendererRegistry.register(ModEntities.ARARA, AraraRenderer::new);
	}

	/** One texture, one model: the four-legged fauna. */
	static class Bicho<T extends Mob> extends MobRenderer<T, LivingEntityRenderState, QuadrupedModel<LivingEntityRenderState>> {
		private final Identifier textura;

		Bicho(EntityRendererProvider.Context context, QuadrupedModel<LivingEntityRenderState> model, float sombra, String nome) {
			super(context, model, sombra);
			textura = Arraial.id("textures/entity/fauna/" + nome + ".png");
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return textura;
		}
	}

	static class Quadrupede extends QuadrupedModel<LivingEntityRenderState> {
		Quadrupede(ModelPart root) {
			super(root);
		}
	}

	/** The tamanduá's big tail sways as it walks. */
	static class Tamandua extends Quadrupede {
		private final ModelPart cauda;

		Tamandua(ModelPart root) {
			super(root);
			cauda = root.getChild("body").getChild("cauda");
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			cauda.yRot += Mth.sin(state.ageInTicks * 0.08F) * 0.12F + Mth.cos(state.walkAnimationPos * 0.6662F) * 0.25F * state.walkAnimationSpeed;
		}
	}

	/** The jacaré snakes its tail and opens its jaw now and then, sunning itself. */
	static class Jacare extends Quadrupede {
		private final ModelPart cauda;
		private final ModelPart cauda2;
		private final ModelPart cauda3;
		private final ModelPart mandibula;

		Jacare(ModelPart root) {
			super(root);
			cauda = root.getChild("cauda");
			cauda2 = cauda.getChild("cauda2");
			cauda3 = cauda2.getChild("cauda3");
			mandibula = head.getChild("mandibula");
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			// Low legs: a shorter, sideways stride.
			float passo = state.walkAnimationPos * 0.6662F;
			float onda = Mth.sin(passo) * 0.35F * state.walkAnimationSpeed + Mth.sin(state.ageInTicks * 0.05F) * 0.05F;
			cauda.yRot += onda;
			cauda2.yRot += onda * 1.3F;
			cauda3.yRot += onda * 1.6F;
			head.xRot = 0.0F;
			float boca = Mth.sin(state.ageInTicks * 0.02F);
			mandibula.xRot += boca > 0.9F ? (boca - 0.9F) * 5.0F : 0.0F;
		}
	}

	/** The onça's tail swishes. */
	static class Onca extends Quadrupede {
		private final ModelPart cauda;

		Onca(ModelPart root) {
			super(root);
			cauda = root.getChild("cauda");
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			cauda.yRot += Mth.sin(state.ageInTicks * 0.1F) * 0.3F;
			cauda.xRot += state.walkAnimationSpeed * 0.5F;
		}
	}

	/** Araras: the parrot model and animations, bigger, with their own colours. */
	static class AraraRenderer extends MobRenderer<Arara, ParrotRenderState, ParrotModel> {
		private static final Identifier[] TEXTURAS = {
				Arraial.id("textures/entity/fauna/arara_vermelha.png"),
				Arraial.id("textures/entity/fauna/arara_caninde.png"),
				Arraial.id("textures/entity/fauna/arara_azul.png")};

		AraraRenderer(EntityRendererProvider.Context context) {
			super(context, new ParrotModel(context.bakeLayer(ARARA)), 0.4F);
		}

		@Override
		public ParrotRenderState createRenderState() {
			return new ParrotRenderState();
		}

		@Override
		public void extractRenderState(Arara entity, ParrotRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.variant = entity.getVariant();
			float flap = Mth.lerp(partialTicks, entity.oFlap, entity.flap);
			float flapSpeed = Mth.lerp(partialTicks, entity.oFlapSpeed, entity.flapSpeed);
			state.flapAngle = (Mth.sin(flap) + 1.0F) * flapSpeed;
			state.pose = ParrotModel.getPose(entity);
		}

		@Override
		public Identifier getTextureLocation(ParrotRenderState state) {
			return TEXTURAS[Math.floorMod(state.variant.ordinal(), TEXTURAS.length)];
		}

		@Override
		protected void scale(ParrotRenderState state, PoseStack poseStack) {
			poseStack.scale(1.4F, 1.4F, 1.4F);
		}
	}

	private FaunaClient() {
	}
}
