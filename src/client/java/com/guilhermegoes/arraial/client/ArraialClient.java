package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.rede.Rede;
import com.guilhermegoes.arraial.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ArraialClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.MORADOR, MoradorRenderer::new);
		com.guilhermegoes.arraial.client.fauna.FaunaClient.init();
		ClientPlayNetworking.registerGlobalReceiver(Rede.AbrirTela.TYPE, (p, ctx) -> TelaArraial.receber(p.dados()));
		IntegracaoXaero.init();
		// The automated test script (dev only, left out of the published jar): loaded by name, only when asked for.
		if ("1".equals(System.getenv("ARRAIAL_ROTEIRO"))) {
			try {
				Class.forName("com.guilhermegoes.arraial.client.Roteiro").getDeclaredMethod("init").invoke(null);
			} catch (ReflectiveOperationException e) {
				Arraial.LOGGER.warn("Roteiro de teste indisponivel", e);
			}
		}
	}
}
