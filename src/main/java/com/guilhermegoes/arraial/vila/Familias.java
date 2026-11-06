package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Families. Two adult moradores who get on well (see Pessoa: friendship) marry: a wedding at the
 * Cruzeiro, fireworks, and they share a surname. A married couple with a free bed and food to spare
 * has children: small moradores who play, eat, and grow up in a few days into adults who can take
 * a trade. Families matter in politics: relatives vote for relatives, and under a coronel the
 * office passes to his eldest grown child (or his spouse).
 */
public final class Familias {
	private static final int AFINIDADE_CASAMENTO = 30;
	private static final int MAX_FILHOS = 3;

	static void diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		if (a.crise.tipo() != null) {
			return; // no weddings or babies in hard times
		}
		casamento(level, a, moradores, false);
		nascimento(level, a, moradores, false);
	}

	private static boolean adulto(MoradorEntity m) {
		return !m.crianca() && m.profissao() != Profissao.MASCATE && !m.viajando();
	}

	private static @Nullable MoradorEntity achar(List<MoradorEntity> l, @Nullable UUID id) {
		if (id == null) {
			return null;
		}
		for (MoradorEntity m : l) {
			if (m.getUUID().equals(id)) {
				return m;
			}
		}
		return null;
	}

	/** Two single adults who like each other marry (forced: the best pair, whatever the dice). */
	public static boolean casamento(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores, boolean forcado) {
		MoradorEntity melhorA = null, melhorB = null;
		int melhor = forcado ? Integer.MIN_VALUE : AFINIDADE_CASAMENTO - 1;
		for (MoradorEntity x : moradores) {
			if (!adulto(x) || x.conjuge() != null) {
				continue;
			}
			for (MoradorEntity y : moradores) {
				if (y == x || !adulto(y) || y.conjuge() != null || y.feminino() == x.feminino() || x.filhoDe(y.getUUID())
						|| y.filhoDe(x.getUUID()) || x.pai() != null && x.pai().equals(y.pai())) {
					continue;
				}
				int af = x.pessoa().afinidadeCom(y);
				if (af > melhor) {
					melhor = af;
					melhorA = x;
					melhorB = y;
				}
			}
		}
		if (melhorA == null || !forcado && level.getRandom().nextFloat() > 0.3F) {
			return false;
		}
		// They take the surname of whoever has lived here longer.
		String sobrenome = melhorA.diaChegada() <= melhorB.diaChegada() ? melhorA.sobrenome() : melhorB.sobrenome();
		melhorA.setConjuge(melhorB.getUUID());
		melhorB.setConjuge(melhorA.getUUID());
		melhorA.setSobrenome(sobrenome);
		melhorB.setSobrenome(sobrenome);
		Component texto = Component.translatable("arraial.cronica.casamento", melhorA.nome(), melhorB.nome(), sobrenome);
		Cronica.anotar(level, a.getBlockPos(), texto);
		Prefeitura.avisar(level, a, Component.literal("💍 ").append(texto).withStyle(ChatFormatting.LIGHT_PURPLE));
		Festa.soltarFogos(level, a.getBlockPos(), 2);
		Conquistas.darPerto(level, a.getBlockPos(), Vilas.RAIO * 2, "casamento");
		Satisfacao.agradar(level, a.getBlockPos(), 3);
		melhorA.dizer(Component.translatable("arraial.fala.casamento", melhorB.nome()), 100);
		melhorB.dizer(Component.translatable("arraial.fala.casamento", melhorA.nome()), 100);
		return true;
	}

	/** A married couple has a child, if there is a bed and food to spare. */
	public static @Nullable MoradorEntity nascimento(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores, boolean forcado) {
		long camasLivres = a.camas(level) - moradores.size();
		int comida = Vilas.contar(Vilas.armazens(level, a.getBlockPos()), s -> s.has(DataComponents.FOOD));
		if (!forcado && (camasLivres <= 0 || comida < moradores.size() * 3 || level.getRandom().nextFloat() > 0.2F)) {
			return null;
		}
		for (MoradorEntity m : moradores) {
			MoradorEntity c = achar(moradores, m.conjuge());
			if (!adulto(m) || c == null || m.feminino() || !adulto(c) || m.comFome() || c.comFome()) {
				continue;
			}
			UUID pai = m.getUUID(), mae = c.getUUID();
			long filhos = moradores.stream().filter(f -> f.filhoDe(pai)).count();
			if (filhos >= MAX_FILHOS) {
				continue;
			}
			BlockPos onde = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.blockPosition());
			MoradorEntity bebe = MoradorEntity.criar(level, onde, Profissao.NENHUMA);
			if (bebe == null) {
				return null;
			}
			bebe.definirVila(a.getBlockPos());
			bebe.nascer(pai, mae, m.sobrenome(), Vilas.dia(level));
			bebe.puxar(level.getRandom().nextBoolean() ? m : c);
			Component texto = Component.translatable(bebe.feminino() ? "arraial.cronica.nasceu.f" : "arraial.cronica.nasceu.m",
					bebe.nome(), bebe.sobrenome(), m.nome(), c.nome());
			Cronica.anotar(level, a.getBlockPos(), texto);
			Prefeitura.avisar(level, a, Component.literal("👶 ").append(texto).withStyle(ChatFormatting.LIGHT_PURPLE));
			Conquistas.darPerto(level, a.getBlockPos(), Vilas.RAIO * 2, "nascimento");
			Satisfacao.agradar(level, a.getBlockPos(), 2);
			return bebe;
		}
		return null;
	}

	/** Under a coronel: the eldest grown child of the last chief, else the spouse. */
	static @Nullable MoradorEntity herdeiro(List<MoradorEntity> aptos, @Nullable UUID chefe) {
		if (chefe == null) {
			return null;
		}
		MoradorEntity filho = aptos.stream().filter(m -> !m.crianca() && m.filhoDe(chefe))
				.min(Comparator.comparingLong(MoradorEntity::nascimento)).orElse(null);
		if (filho != null) {
			return filho;
		}
		return aptos.stream().filter(m -> chefe.equals(m.conjuge())).findFirst().orElse(null);
	}

	/** Same family: married, parent and child, or siblings. */
	static boolean parentes(MoradorEntity a, MoradorEntity b) {
		return a.getUUID().equals(b.conjuge()) || a.filhoDe(b.getUUID()) || b.filhoDe(a.getUUID())
				|| a.pai() != null && a.pai().equals(b.pai());
	}

	/** "Família: casado(a) com X; filhos: Y, Z" for the morador's card. */
	public static @Nullable Component ficha(ServerLevel level, MoradorEntity m) {
		ArraialBlockEntity a = m.arraial();
		List<MoradorEntity> todos = a == null ? List.of() : a.moradores(level);
		MutableComponent l = Component.translatable("arraial.familia.titulo", m.nome(), m.sobrenome());
		boolean algo = false;
		if (m.crianca()) {
			MoradorEntity p = achar(todos, m.pai()), q = achar(todos, m.mae());
			l.append(" · ").append(Component.translatable("arraial.familia.crianca", p == null ? "?" : p.nome(), q == null ? "?" : q.nome()));
			algo = true;
		}
		MoradorEntity c = achar(todos, m.conjuge());
		if (c != null) {
			l.append(" · ").append(Component.translatable(m.feminino() ? "arraial.familia.casada" : "arraial.familia.casado", c.nome()));
			algo = true;
		}
		List<MoradorEntity> filhos = todos.stream().filter(f -> f.filhoDe(m.getUUID())).toList();
		if (!filhos.isEmpty()) {
			l.append(" · ").append(Component.translatable("arraial.familia.filhos",
					String.join(", ", filhos.stream().map(MoradorEntity::nome).toList())));
			algo = true;
		}
		return algo ? l.withStyle(ChatFormatting.LIGHT_PURPLE) : Component.translatable("arraial.familia.solteiro", m.nome(), m.sobrenome())
				.withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	private Familias() {
	}
}
