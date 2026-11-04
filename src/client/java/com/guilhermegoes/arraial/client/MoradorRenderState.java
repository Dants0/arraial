package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.npc.Profissao;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** What the renderer needs to know to pick a morador's skin. */
public class MoradorRenderState extends HumanoidRenderState {
	public Profissao profissao = Profissao.NENHUMA;
	public boolean feminino;
	public int visual;
}
