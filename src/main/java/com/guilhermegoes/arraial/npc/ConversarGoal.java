package com.guilhermegoes.arraial.npc;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/**
 * Two idle moradores who meet stop for a chat: one opens (the weather, the crisis, the obra going
 * up, the other's trade...), the other answers. The lines show in a speech bubble on their name
 * tags and in the chat of players nearby. Each morador chats at most every couple of minutes.
 */
public class ConversarGoal extends Goal {
	private static final int DURACAO = 120;
	private static final int INTERVALO = 2400;

	private final MoradorEntity morador;
	private @Nullable MoradorEntity outro;
	private boolean iniciou;
	private int t;
	private int perto;
	private List<Component> falas = List.of();
	private int checar;

	public ConversarGoal(MoradorEntity morador) {
		this.morador = morador;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private ServerLevel level() {
		return getServerLevel(morador);
	}

	@Override
	public boolean canUse() {
		// Someone started a chat with me.
		MoradorEntity quem = morador.interlocutor();
		if (quem != null && quem.isAlive() && quem.interlocutor() == morador) {
			outro = quem;
			iniciou = false;
			return true;
		}
		if (--checar > 0 || morador.isSleeping() || morador.vila() == null || morador.comFome()
				|| level().getGameTime() < morador.proximaConversa()) {
			return false;
		}
		checar = 40 + morador.getRandom().nextInt(40);
		for (MoradorEntity m : level().getEntitiesOfClass(MoradorEntity.class, morador.getBoundingBox().inflate(8),
				m -> m != morador && morador.vila().equals(m.vila()) && m.livreParaConversar(level().getGameTime()))) {
			outro = m;
			iniciou = true;
			morador.setInterlocutor(m);
			m.setInterlocutor(morador);
			falas = Falas.conversa(level(), morador, m);
			return true;
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return outro != null && outro.isAlive() && t < DURACAO + 100 && perto < DURACAO && outro.interlocutor() == morador
				&& morador.distanceToSqr(outro) < 12 * 12;
	}

	@Override
	public void start() {
		t = 0;
		perto = 0;
		morador.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (outro == null) {
			return;
		}
		t++;
		morador.getLookControl().setLookAt(outro, 30.0F, 30.0F);
		if (morador.distanceToSqr(outro) > 3.0 * 3.0) {
			if (iniciou && t % 10 == 1) {
				morador.getNavigation().moveTo(outro, 0.6);
			}
			return;
		}
		morador.getNavigation().stop();
		perto++;
		if (iniciou && !falas.isEmpty()) {
			if (perto == 10) {
				morador.dizer(falas.get(0), 60);
			} else if (perto == 70 && falas.size() > 1) {
				outro.dizer(falas.get(1), 50);
			}
		}
	}

	@Override
	public void stop() {
		long agora = level().getGameTime();
		morador.setProximaConversa(agora + INTERVALO + morador.getRandom().nextInt(INTERVALO));
		if (morador.interlocutor() == outro) {
			morador.setInterlocutor(null);
		}
		outro = null;
		falas = List.of();
	}
}
