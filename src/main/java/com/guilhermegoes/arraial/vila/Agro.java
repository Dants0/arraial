package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * The daily work of the fazenda that happens in the morning rather than step by step:
 * <ul>
 * <li>the pastor milks the cows of the curral and makes queijo coalho (one cheese for two cows,
 * at most four a day);</li>
 * <li>raw beef piling up in the armazém is salted and dried into charque, which keeps;</li>
 * <li>in a mining arraial the cavouqueiros pan the streams for a little gold (faiscação), the gold
 * of the Baroque talha.</li>
 * </ul>
 */
public final class Agro {
	public static void diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());
		if (armazens.isEmpty()) {
			return;
		}
		int queijo = 0, cavouqueiros = 0;
		for (MoradorEntity m : moradores) {
			BlockPos posto = m.posto();
			if (m.profissao() == Profissao.PASTOR && posto != null && !m.comFome()) {
				int vacas = level.getEntitiesOfClass(Cow.class, new AABB(posto).inflate(14), c -> !c.isBaby()).size();
				queijo += Math.min(4, vacas / 2);
			}
			if (m.profissao() == Profissao.CAVOUQUEIRO && !m.comFome()) {
				cavouqueiros++;
			}
		}
		if (queijo > 0) {
			Vilas.depositar(armazens, ModItems.QUEIJO_COALHO, queijo);
		}
		// Charque: half the raw beef above a small reserve, as long as someone looks after the herd.
		boolean temPastor = moradores.stream().anyMatch(m -> m.profissao() == Profissao.PASTOR);
		int carne = Vilas.contar(armazens, s -> s.is(Items.BEEF));
		if (temPastor && carne > 4) {
			int salgar = Math.min(8, (carne - 4) / 2);
			if (salgar > 0 && Vilas.retirar(armazens, Items.BEEF, salgar) == salgar) {
				Vilas.depositar(armazens, ModItems.CHARQUE, salgar);
			}
		}
		// Faiscação: gold dust from the streams in a mining arraial.
		Registro.Info v = Registro.get(Registro.id(level.dimension().identifier().toString(), a.getBlockPos()));
		if (cavouqueiros > 0 && v != null && Economia.Especialidade.de(v.especialidade) == Economia.Especialidade.MINERADORA) {
			Vilas.depositar(armazens, Items.GOLD_NUGGET, cavouqueiros + level.getRandom().nextInt(2));
		}
	}

	private Agro() {
	}
}
