package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Cronica;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pastor looks after the animals around his cocho: shears the sheep, feeds pairs so the
 * herd grows, culls the surplus for meat, and, if there are no animals at all, trades some
 * wheat for a pair of sheep at the fair. Wool, meat, leather and eggs go to the armazém.
 */
public class PastorGoal extends OficioGoal {
	private enum Fase { TOSQUIAR, BUSCAR_RACAO, ALIMENTAR, ABATER, ENTREGAR }

	private static final int RAIO = 12;
	/** Below this many of a kind he breeds them; above {@link #MAX_REBANHO} he culls. */
	private static final int MIN_REBANHO = 6;
	private static final int MAX_REBANHO = 10;
	private static final int CARGA_CHEIA = 32;
	/** What two sheep cost at the fair, in wheat. */
	private static final int PRECO_OVELHAS = 10;
	private static final List<Class<? extends Animal>> ESPECIES = List.of(Sheep.class, Cow.class, Pig.class, Chicken.class);
	private static final List<Item> RACOES = List.of(Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.WHEAT_SEEDS);

	private Fase fase = Fase.TOSQUIAR;
	private @Nullable Animal alvo;
	private @Nullable Animal par;
	private @Nullable Item racao;
	private long ultimaCompra = -10;
	private int travado;
	private int espera;

	public PastorGoal(MoradorEntity morador) {
		super(morador, Profissao.PASTOR);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar()) {
			return false;
		}
		BlockPos cocho = morador.posto();
		if (cocho == null) {
			return false;
		}
		if (cargaSemRacao() >= CARGA_CHEIA && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		ServerLevel level = level();
		AABB area = new AABB(cocho).inflate(RAIO, 4, RAIO);

		// 1. Wool.
		Sheep lanosa = maisPerto(level.getEntitiesOfClass(Sheep.class, area, Sheep::readyForShearing));
		if (lanosa != null) {
			alvo = lanosa;
			fase = Fase.TOSQUIAR;
			return true;
		}

		int total = 0;
		for (Class<? extends Animal> especie : ESPECIES) {
			List<? extends Animal> todos = level.getEntitiesOfClass(especie, area, Animal::isAlive);
			total += todos.size();
			List<Animal> adultos = new ArrayList<>();
			for (Animal a : todos) {
				if (!a.isBaby()) {
					adultos.add(a);
				}
			}
			// 2. Too many: one goes to the table.
			if (todos.size() > MAX_REBANHO && adultos.size() > 2) {
				alvo = adultos.get(0);
				fase = Fase.ABATER;
				return true;
			}
			// 3. Too few: feed a pair so they breed.
			if (todos.size() < MIN_REBANHO) {
				List<Animal> prontos = adultos.stream().filter(a -> a.getAge() == 0 && a.canFallInLove()).map(a -> (Animal) a).toList();
				if (prontos.size() >= 2) {
					Item comida = racaoPara(prontos.get(0));
					if (comida != null) {
						alvo = prontos.get(0);
						par = prontos.get(1);
						racao = comida;
						fase = morador.carregando(comida) >= 2 ? Fase.ALIMENTAR : Fase.BUSCAR_RACAO;
						return true;
					}
				}
			}
		}

		// 4. No animals at all: buy a pair of sheep with wheat (at most every few days).
		if (total == 0 && Vilas.dia(level) - ultimaCompra >= 3 && Vilas.contar(armazens(), Items.WHEAT) >= PRECO_OVELHAS) {
			comprarOvelhas(level, cocho);
			return false;
		}

		if (cargaSemRacao() > 0 && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		morador.setStatus(Component.translatable(total == 0 ? "arraial.pastor.sem_bicho" : "arraial.pastor.cuidando"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		if (!podeTrabalhar()) {
			return false;
		}
		return fase == Fase.ENTREGAR || fase == Fase.BUSCAR_RACAO || (alvo != null && alvo.isAlive());
	}

	@Override
	public void start() {
		espera = 0;
		travado = 0;
		morador.segurar(new ItemStack(fase == Fase.TOSQUIAR ? Items.SHEARS : fase == Fase.ABATER ? Items.IRON_AXE : Items.WHEAT));
	}

	@Override
	public void stop() {
		super.stop();
		alvo = null;
		par = null;
	}

	@Override
	public void tick() {
		if (espera > 0) {
			espera--;
			return;
		}
		ServerLevel level = level();
		switch (fase) {
			case TOSQUIAR -> {
				if (!(alvo instanceof Sheep ovelha) || !ovelha.readyForShearing()) {
					alvo = null;
					return;
				}
				morador.setStatus(Component.translatable("arraial.pastor.tosquiando"));
				if (!chegar(ovelha)) {
					return;
				}
				level.playSound(null, ovelha, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
				ovelha.setSheared(true);
				Item la = Items.WOOL.pick(ovelha.getColor());
				morador.guardar(la, 1 + morador.getRandom().nextInt(3));
				bater();
				alvo = null;
			}
			case BUSCAR_RACAO -> {
				var a = armazemMaisProximo();
				if (a == null || racao == null) {
					alvo = null;
					fase = Fase.TOSQUIAR;
					return;
				}
				morador.setStatus(Component.translatable("arraial.pastor.buscando_racao"));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				pegar(racao, 8);
				bater();
				fase = morador.carregando(racao) >= 2 ? Fase.ALIMENTAR : Fase.TOSQUIAR;
				if (fase == Fase.TOSQUIAR) {
					alvo = null;
				}
			}
			case ALIMENTAR -> {
				if (alvo == null || par == null || !alvo.isAlive() || !par.isAlive() || racao == null || morador.carregando(racao) < 1) {
					alvo = null;
					return;
				}
				morador.setStatus(Component.translatable("arraial.pastor.tratando"));
				Animal bicho = alvo.isInLove() ? par : alvo;
				if (!chegar(bicho)) {
					return;
				}
				morador.gastar(racao, 1);
				bicho.setInLove(null);
				bater();
				if (alvo.isInLove() && par.isInLove()) {
					alvo = null; // both fed; they'll find each other
				}
			}
			case ABATER -> {
				if (alvo == null || !alvo.isAlive()) {
					alvo = null;
					return;
				}
				morador.setStatus(Component.translatable("arraial.pastor.abatendo"));
				if (!chegar(alvo)) {
					return;
				}
				Vec3 onde = alvo.position();
				alvo.hurtServer(level, morador.damageSources().mobAttack(morador), Float.MAX_VALUE);
				bater();
				recolherItens(onde, 2.5);
				alvo = null;
			}
			case ENTREGAR -> {
				var a = armazemMaisProximo();
				if (a == null) {
					alvo = null;
					fase = Fase.TOSQUIAR;
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("arraial.pastor.producao")));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				entregar(Map.of());
				fase = Fase.TOSQUIAR;
				alvo = null; // ends the goal
			}
		}
	}

	/** Walks up to the animal; true once close enough to handle it. */
	private boolean chegar(Animal bicho) {
		if (morador.distanceTo(bicho) > 2.2 && travado++ < 200) {
			andarAte(bicho.blockPosition());
			return false;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(bicho);
		travado = 0;
		espera = 10;
		return true;
	}

	private void comprarOvelhas(ServerLevel level, BlockPos cocho) {
		if (Vilas.retirar(armazens(), Items.WHEAT, PRECO_OVELHAS) < PRECO_OVELHAS) {
			return;
		}
		ultimaCompra = Vilas.dia(level);
		for (int i = 0; i < 2; i++) {
			net.minecraft.world.entity.EntityTypes.SHEEP.spawn(level, cocho.offset(i * 2 - 1, 1, 1), EntitySpawnReason.MOB_SUMMONED);
		}
		Cronica.anotar(level, morador.vila(), Component.translatable("arraial.cronica.ovelhas", morador.nome()));
	}

	private @Nullable Item racaoPara(Animal bicho) {
		for (Item r : RACOES) {
			if (bicho.isFood(new ItemStack(r)) && (morador.carregando(r) >= 2 || Vilas.contar(armazens(), r) >= 2)) {
				return r;
			}
		}
		return null;
	}

	private int cargaSemRacao() {
		int total = 0;
		for (Map.Entry<Item, Integer> e : morador.cargaCopia().entrySet()) {
			if (!RACOES.contains(e.getKey())) {
				total += e.getValue();
			}
		}
		return total;
	}

	private <T extends Animal> @Nullable T maisPerto(List<T> lista) {
		T melhor = null;
		double d = Double.MAX_VALUE;
		for (T a : lista) {
			double x = a.distanceToSqr(morador);
			if (x < d) {
				d = x;
				melhor = a;
			}
		}
		return melhor;
	}
}
