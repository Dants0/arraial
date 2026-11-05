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
	private enum Fase { TOSQUIAR, BUSCAR_RACAO, ALIMENTAR, ABATER, ENTREGAR, EXPEDICAO, TRAZER }

	private static final int RAIO = 12;
	/** Below this many of a kind he breeds them; above {@link #MAX_REBANHO} he culls. */
	private static final int MIN_REBANHO = 6;
	private static final int MAX_REBANHO = 10;
	private static final int CARGA_CHEIA = 32;
	/** What two sheep cost at the fair, in wheat. */
	private static final int PRECO_OVELHAS = 10;
	private static final List<Class<? extends Animal>> ESPECIES = List.of(Sheep.class, Cow.class, Pig.class, Chicken.class);
	/** How far into the countryside he looks for animals, how many he leads at once, and for how long. */
	private static final int RAIO_EXPEDICAO = 64;
	private static final int MAX_GUIADOS = 2;
	private static final int TEMPO_EXPEDICAO = 3600;
	private static final List<Item> RACOES = List.of(Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.WHEAT_SEEDS);

	private Fase fase = Fase.TOSQUIAR;
	private @Nullable Animal alvo;
	private @Nullable Animal par;
	private @Nullable Item racao;
	private long ultimaCompra = -10;
	private final List<Animal> guiados = new ArrayList<>();
	private long ultimaBusca = -100000;
	private int duracao;
	private @Nullable MoradorEntity escolta;
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

		// 4. A small herd: go out (with a soldier, if there is one) and bring animals in from the wild.
		if (total < MIN_REBANHO && level.getGameTime() - ultimaBusca > 1200) {
			ultimaBusca = level.getGameTime();
			Animal achado = procurarNoMato(level, cocho, morador.position(), RAIO_EXPEDICAO);
			if (achado != null) {
				alvo = achado;
				fase = Fase.EXPEDICAO;
				return true;
			}
		}

		// 5. No animals at all and none in the wild: buy a pair of sheep with wheat (at most every few days).
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
		if (fase == Fase.EXPEDICAO || fase == Fase.TRAZER) {
			return duracao < TEMPO_EXPEDICAO && (fase == Fase.TRAZER ? !guiados.isEmpty() : alvo != null && alvo.isAlive());
		}
		return fase == Fase.ENTREGAR || fase == Fase.BUSCAR_RACAO || (alvo != null && alvo.isAlive());
	}

	@Override
	public void start() {
		espera = 0;
		travado = 0;
		duracao = 0;
		if (fase == Fase.EXPEDICAO) {
			morador.setExpedicao(true);
			escolta = chamarEscolta();
			morador.segurar(new ItemStack(Items.LEAD));
			return;
		}
		morador.segurar(new ItemStack(fase == Fase.TOSQUIAR ? Items.SHEARS : fase == Fase.ABATER ? Items.IRON_AXE : Items.WHEAT));
	}

	@Override
	public void stop() {
		super.stop();
		// Interrupted (dusk, hunger, a fight): let go of whatever is still on the lead.
		for (Animal a : guiados) {
			if (a.getLeashHolder() == morador) {
				a.removeLeash();
			}
		}
		guiados.clear();
		morador.setExpedicao(false);
		escolta = null;
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
			case EXPEDICAO -> {
				duracao++;
				if (alvo == null || !alvo.isAlive() || alvo.isLeashed()) {
					proximoBicho(level);
					return;
				}
				morador.setStatus(Component.translatable("arraial.pastor.expedicao"));
				if (!chegar(alvo)) {
					return;
				}
				if (morador.distanceTo(alvo) <= 4.0) {
					alvo.setLeashedTo(morador, true);
					guiados.add(alvo);
					level.playSound(null, alvo, SoundEvents.LEAD_TIED, SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
				proximoBicho(level);
			}
			case TRAZER -> {
				duracao++;
				guiados.removeIf(a -> !a.isAlive() || a.getLeashHolder() != morador);
				BlockPos cocho = morador.posto();
				if (guiados.isEmpty() || cocho == null) {
					return; // nothing left on the lead: the goal ends
				}
				morador.setStatus(Component.translatable("arraial.pastor.trazendo", guiados.size()));
				if (!perto(cocho, 3.0)) {
					andarAte(cocho);
					return;
				}
				// Inside the curral: let them go, and he can go out again in a little while.
				int n = guiados.size();
				for (Animal a : guiados) {
					a.removeLeash();
				}
				guiados.clear();
				bater();
				ultimaBusca = level.getGameTime() - 600;
				Cronica.anotar(level, morador.vila(), escolta != null
						? Component.translatable("arraial.cronica.trouxe_escolta", morador.nome(), n, escolta.nome())
						: Component.translatable("arraial.cronica.trouxe", morador.nome(), n));
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

	/** Next animal to put on the lead (one near the last), or head home with what he has. */
	private void proximoBicho(ServerLevel level) {
		BlockPos cocho = morador.posto();
		Animal outro = guiados.size() < MAX_GUIADOS && cocho != null ? procurarNoMato(level, cocho, morador.position(), 16) : null;
		if (outro != null) {
			alvo = outro;
			return;
		}
		alvo = null;
		fase = Fase.TRAZER;
	}

	/** The nearest loose farm animal within {@code raio} of {@code de}, outside the curral. */
	private @Nullable Animal procurarNoMato(ServerLevel level, BlockPos cocho, Vec3 de, int raio) {
		Animal melhor = null;
		double d = Double.MAX_VALUE;
		double fora = (RAIO + 2) * (RAIO + 2);
		AABB area = new AABB(de, de).inflate(raio, 16, raio);
		for (Class<? extends Animal> especie : ESPECIES) {
			for (Animal a : level.getEntitiesOfClass(especie, area, a -> a.isAlive() && !a.isLeashed()
					&& a.distanceToSqr(Vec3.atCenterOf(cocho)) > fora
					&& a.distanceToSqr(Vec3.atCenterOf(cocho)) < (double) RAIO_EXPEDICAO * RAIO_EXPEDICAO)) {
				double x = a.distanceToSqr(de);
				if (x < d) {
					d = x;
					melhor = a;
				}
			}
		}
		return melhor;
	}

	/** Asks the nearest free soldado of the arraial to come along. */
	private @Nullable MoradorEntity chamarEscolta() {
		BlockPos vila = morador.vila();
		if (vila == null) {
			return null;
		}
		MoradorEntity melhor = null;
		double d = Double.MAX_VALUE;
		for (MoradorEntity s : level().getEntitiesOfClass(MoradorEntity.class, new AABB(vila).inflate(Vilas.RAIO),
				m -> m.profissao() == Profissao.SOLDADO && vila.equals(m.vila()) && m.escoltado() == null && m.getTarget() == null)) {
			double x = s.distanceToSqr(morador);
			if (x < d) {
				d = x;
				melhor = s;
			}
		}
		if (melhor != null) {
			melhor.setEscoltado(morador);
		}
		return melhor;
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
