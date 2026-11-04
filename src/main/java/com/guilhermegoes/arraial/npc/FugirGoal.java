package com.guilhermegoes.arraial.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;

/** Everyone but the soldados runs from monsters (and calls for help by being hurt). */
public class FugirGoal extends AvoidEntityGoal<Monster> {
	private final MoradorEntity morador;

	public FugirGoal(MoradorEntity morador) {
		super(morador, Monster.class, 10.0F, 0.8, 1.1);
		this.morador = morador;
	}

	@Override
	public boolean canUse() {
		return morador.profissao() != Profissao.SOLDADO && !morador.isSleeping() && super.canUse();
	}

	@Override
	public void start() {
		super.start();
		morador.setStatus(Component.translatable("arraial.morador.fugindo"));
	}

	@Override
	public void stop() {
		super.stop();
		morador.setStatus(Component.empty());
	}
}
