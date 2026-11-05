package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared plumbing for daytime jobs: who may work, walking around, and delivering to the armazém. */
public abstract class OficioGoal extends Goal {
	protected final MoradorEntity morador;
	private final Profissao profissao;
	private int recalcular;
	private int proximaChecagem;

	protected OficioGoal(MoradorEntity morador, Profissao profissao) {
		this.morador = morador;
		this.profissao = profissao;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/** Only the right trade, by day, fed, and only re-checked every ~2 s (the selector polls every other tick). */
	protected boolean podeTrabalhar() {
		if (morador.profissao() != profissao || morador.comFome() || morador.posto() == null) {
			return false;
		}
		ServerLevel level = getServerLevel(morador);
		return level.isBrightOutside();
	}

	protected boolean hora() {
		if (--proximaChecagem > 0) {
			return false;
		}
		proximaChecagem = 20;
		return true;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		morador.getNavigation().stop();
		morador.segurar(ItemStack.EMPTY);
		morador.setStatus(net.minecraft.network.chat.Component.empty());
	}

	protected ServerLevel level() {
		return getServerLevel(morador);
	}

	protected List<ArmazemBlockEntity> armazens() {
		return Vilas.armazens(level(), morador.vila());
	}

	protected @Nullable ArmazemBlockEntity armazemMaisProximo() {
		ArmazemBlockEntity melhor = null;
		double d = Double.MAX_VALUE;
		for (ArmazemBlockEntity a : armazens()) {
			double x = a.getBlockPos().distSqr(morador.blockPosition());
			if (x < d) {
				d = x;
				melhor = a;
			}
		}
		return melhor;
	}

	protected boolean perto(BlockPos pos, double distancia) {
		return morador.position().distanceTo(Vec3.atCenterOf(pos)) <= distancia;
	}

	protected void andarAte(BlockPos pos) {
		if (--recalcular <= 0 || morador.getNavigation().isDone()) {
			recalcular = Porteira.caminhar(morador, pos, 0.9);
		}
	}

	protected void bater() {
		morador.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
	}

	/** Picks up the loose items lying around {@code centro} (e.g. what an animal dropped). */
	protected void recolherItens(Vec3 centro, double raio) {
		for (net.minecraft.world.entity.item.ItemEntity item : level().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(centro, centro).inflate(raio))) {
			ItemStack s = item.getItem();
			if (!s.isEmpty()) {
				morador.guardar(s.getItem(), s.getCount());
			}
			item.discard();
		}
	}

	/** Takes up to {@code quantidade} of {@code item} from the armazéns into the hands. */
	protected int pegar(Item item, int quantidade) {
		int n = Vilas.retirar(armazens(), item, quantidade);
		if (n > 0) {
			morador.guardar(item, n);
		}
		return n;
	}

	/** Puts the carried items (minus {@code manter}) into the armazém; returns false if there is none. */
	protected boolean entregar(Map<Item, Integer> manter) {
		List<ArmazemBlockEntity> lista = armazens();
		if (lista.isEmpty()) {
			return false;
		}
		for (Map.Entry<Item, Integer> e : morador.cargaCopia().entrySet()) {
			int guardar = e.getValue() - manter.getOrDefault(e.getKey(), 0);
			if (guardar > 0) {
				int sobrou = Vilas.depositar(lista, e.getKey(), guardar);
				morador.gastar(e.getKey(), guardar - sobrou);
			}
		}
		bater();
		return true;
	}
}
