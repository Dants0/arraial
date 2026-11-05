package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.rede.Rede;
import com.guilhermegoes.arraial.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ArraialClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.MORADOR, MoradorRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(Rede.AbrirTela.TYPE, (p, ctx) -> TelaArraial.receber(p.dados()));
		Roteiro.init();
	}
}
