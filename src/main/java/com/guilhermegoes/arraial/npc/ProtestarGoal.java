package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Politica;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A group unhappy for days takes to the street (see {@link Politica}): its members stop work and
 * stand around the Cruzeiro all day, shouting their demands.
 */
public class ProtestarGoal extends Goal {
	private final MoradorEntity morador;
	private int checar;
	private int t;
	private double angulo;

	public ProtestarGoal(MoradorEntity morador) {
		this.morador = morador;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private Politica.@org.jspecify.annotations.Nullable Grupo protesto() {
		ArraialBlockEntity a = morador.arraial();
		return a == null ? null : a.politica().protestoHoje(getServerLevel(morador));
	}

	private boolean pode() {
		Politica.Grupo g = protesto();
		return g != null && g == Politica.Grupo.de(morador.profissao()) && !morador.prefeito() && !morador.isSleeping()
				&& !morador.viajando() && !morador.doente() && getServerLevel(morador).isBrightOutside();
	}

	@Override
	public boolean canUse() {
		if (--checar > 0) {
			return false;
		}
		checar = 40 + morador.getRandom().nextInt(20);
		return pode();
	}

	@Override
	public boolean canContinueToUse() {
		return pode();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		t = 0;
		angulo = morador.getRandom().nextDouble() * Math.PI * 2;
	}

	@Override
	public void stop() {
		morador.getNavigation().stop();
	}

	@Override
	public void tick() {
		ArraialBlockEntity a = morador.arraial();
		Politica.Grupo g = protesto();
		if (a == null || g == null) {
			return;
		}
		t++;
		BlockPos c = a.getBlockPos();
		double x = c.getX() + 0.5 + Math.cos(angulo) * 4, z = c.getZ() + 0.5 + Math.sin(angulo) * 4;
		if (t % 20 == 1 && morador.distanceToSqr(x, morador.getY(), z) > 2.0) {
			morador.getNavigation().moveTo(x, c.getY(), z, 0.6);
		}
		morador.getLookControl().setLookAt(c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5);
		if (t % 120 == 0 && morador.getRandom().nextFloat() < 0.3F) {
			morador.dizer(Component.translatable("arraial.fala.protesto." + g.id() + "." + morador.getRandom().nextInt(2)), 60);
		}
	}
}
