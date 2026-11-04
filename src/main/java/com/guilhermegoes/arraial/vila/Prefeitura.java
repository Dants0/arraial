package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.Catalogo;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.obra.Terreno;
import com.guilhermegoes.arraial.registry.ModPois;
import com.guilhermegoes.arraial.trabalho.Demanda;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Self-government. Once the arraial has {@link #MIN_ELEITORES} moradores it elects its oldest
 * resident as prefeito. Every morning the prefeito looks at what the village lacks (houses,
 * a trade, food, defence, water), checks that the arraial can actually get the materials,
 * finds flat ground and puts down a Marco da Obra, hiring a pedreiro if there is none.
 * When something blocks the plan (a missing material nobody can make), the Cruzeiro says so,
 * and the player can step in.
 */
public final class Prefeitura {
	public static final int MIN_ELEITORES = 4;

	private static final Map<Profissao, String> OFICINA = Map.of(
			Profissao.LENHADOR, "serraria",
			Profissao.ROCEIRO, "roca",
			Profissao.OLEIRO, "olaria",
			Profissao.PASTOR, "curral",
			Profissao.BARREIRO, "barreira",
			Profissao.CAVOUQUEIRO, "pedreira",
			Profissao.SOLDADO, "guarita");

	/** Called every few seconds by the Cruzeiro. */
	static void cuidar(ServerLevel level, ArraialBlockEntity arraial, List<MoradorEntity> moradores) {
		MoradorEntity prefeito = eleger(level, arraial, moradores);
		if (prefeito == null) {
			return;
		}
		long hoje = Vilas.dia(level);
		if (hoje <= arraial.ultimoDespacho || !level.isBrightOutside()) {
			return;
		}
		arraial.ultimoDespacho = hoje;
		arraial.setChanged();
		despachar(level, arraial, prefeito, moradores);
	}

	// ------------------------------------------------------------ elections

	private static @Nullable MoradorEntity eleger(ServerLevel level, ArraialBlockEntity arraial, List<MoradorEntity> moradores) {
		MoradorEntity atual = null;
		for (MoradorEntity m : moradores) {
			if (arraial.prefeito != null && arraial.prefeito.equals(m.getUUID())) {
				atual = m;
			} else if (m.prefeito()) {
				m.setPrefeito(false);
			}
		}
		if (atual != null) {
			atual.setPrefeito(true);
			return atual;
		}
		if (arraial.prefeito != null && level.getEntity(arraial.prefeito) != null) {
			return null; // alive, just outside the village for now
		}
		if (moradores.size() < MIN_ELEITORES) {
			arraial.prefeito = null;
			return null;
		}
		// The oldest resident wins; ties go to whoever has a trade, then by name.
		MoradorEntity eleito = moradores.stream()
				.min(Comparator.comparingLong(MoradorEntity::diaChegada)
						.thenComparing(m -> m.profissao() == Profissao.NENHUMA)
						.thenComparing(MoradorEntity::nome))
				.orElse(null);
		if (eleito == null) {
			return null;
		}
		arraial.prefeito = eleito.getUUID();
		arraial.setChanged();
		eleito.setPrefeito(true);
		Component cargo = cargo(eleito);
		Cronica.anotar(level, arraial.getBlockPos(), Component.translatable("arraial.cronica.eleicao", eleito.nome(), cargo));
		avisar(level, arraial, Component.translatable("arraial.vila.eleicao", eleito.nome(), cargo, arraial.nome()).withStyle(ChatFormatting.GOLD));
		return eleito;
	}

	public static Component cargo(MoradorEntity m) {
		return Component.translatable(m.feminino() ? "arraial.prefeito.f" : "arraial.prefeito.m");
	}

	// ------------------------------------------------------------ the morning dispatch

	private static void despachar(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito, List<MoradorEntity> moradores) {
		BlockPos vila = arraial.getBlockPos();
		if (obraEmAndamento(level, vila)) {
			contratarPedreiro(level, arraial, prefeito, moradores);
			arraial.pedido = Component.empty();
			return;
		}
		if (Vilas.armazens(level, vila).isEmpty()) {
			arraial.pedido = Component.translatable("arraial.vila.pedido_armazem");
			return;
		}

		int n = moradores.size();
		long semCasa = moradores.stream().filter(m -> m.casa() == null).count();
		long camasLivres = arraial.camas(level) - n;
		int comida = Vilas.contar(Vilas.armazens(level, vila), s -> s.has(DataComponents.FOOD));

		List<String> desejos = new ArrayList<>();
		if (semCasa > 0 || camasLivres <= 0) {
			desejos.add("casa");
		}
		if (postos(level, vila, ModPois.CEPO) == 0) {
			desejos.add("serraria");
		}
		if (comida < n * 2 && postos(level, vila, ModPois.BALAIO) < 1 + n / 5) {
			desejos.add("roca");
		}
		if (n >= 5 && postos(level, vila, ModPois.POSTO_GUARDA) == 0) {
			desejos.add("guarita");
		}
		if (n >= 6 && postos(level, vila, ModPois.COCHO) == 0) {
			desejos.add("curral");
		}
		if (n >= 6 && postos(level, vila, ModPois.FORNO_OLARIA) == 0) {
			desejos.add("olaria");
		}
		if (n >= 3 && obras(level, vila, "poco") == 0) {
			desejos.add("poco");
		}
		if (desejos.isEmpty() && camasLivres <= 1) {
			desejos.add("casa");
		}

		Set<Profissao> oficios = Demanda.oficios(level, vila);
		Set<String> tentados = new HashSet<>();
		Component primeiroPedido = null;
		for (int i = 0; i < desejos.size() && i < 16; i++) {
			String desejo = desejos.get(i);
			if (!tentados.add(desejo)) {
				continue;
			}
			List<String> opcoes = desejo.equals("casa") ? List.of("casebre", "rancho") : List.of(desejo);
			Item falta = null;
			Catalogo.Projeto bloqueado = null;
			for (String id : opcoes) {
				Catalogo.Projeto projeto = Catalogo.porId(id).orElse(null);
				if (projeto == null) {
					continue;
				}
				Map<Item, Integer> materiais = PlanoDeObra.criar(level, projeto, vila, Direction.NORTH)
						.map(p -> p.materiais(0)).orElse(null);
				if (materiais == null) {
					continue;
				}
				Optional<Item> semFonte = Demanda.inviavel(level, vila, materiais, oficios);
				if (semFonte.isPresent()) {
					if (falta == null) {
						falta = semFonte.get();
						bloqueado = projeto;
					}
					continue;
				}
				Optional<Terreno.Lugar> lugar = Terreno.procurar(level, vila, projeto);
				if (lugar.isEmpty()) {
					if (primeiroPedido == null) {
						primeiroPedido = Component.translatable("arraial.vila.pedido_lugar", projeto.nome());
					}
					continue;
				}
				if (Terreno.marcar(level, lugar.get(), projeto)) {
					Cronica.anotar(level, vila, Component.translatable("arraial.cronica.ordem", prefeito.nome(), cargo(prefeito), projeto.nome()));
					avisar(level, arraial, Component.translatable("arraial.vila.ordem", cargo(prefeito), prefeito.nome(), projeto.nome())
							.withStyle(ChatFormatting.AQUA));
					contratarPedreiro(level, arraial, prefeito, moradores);
					arraial.pedido = Component.empty();
					return;
				}
			}
			if (falta != null && bloqueado != null) {
				if (primeiroPedido == null) {
					primeiroPedido = Component.translatable("arraial.vila.pedido", bloqueado.nome(), falta.getDefaultInstance().getHoverName());
				}
				// Nobody can make it: first build the workshop of the trade that would.
				Profissao quem = Demanda.quemFaz(falta);
				String oficina = quem == null ? null : OFICINA.get(quem);
				if (oficina != null && !tentados.contains(oficina) && postosDe(level, vila, quem) == 0) {
					desejos.add(i + 1, oficina);
				}
			}
		}
		arraial.pedido = primeiroPedido == null ? Component.empty() : primeiroPedido;
	}

	/** Makes sure some pedreiro will take the job: an unemployed morador is hired if needed. */
	private static void contratarPedreiro(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito, List<MoradorEntity> moradores) {
		if (moradores.stream().anyMatch(m -> m.profissao() == Profissao.PEDREIRO)) {
			return;
		}
		moradores.stream().filter(m -> m.profissao() == Profissao.NENHUMA).findFirst().ifPresent(m -> {
			m.setProfissao(Profissao.PEDREIRO);
			Cronica.anotar(level, arraial.getBlockPos(),
					Component.translatable("arraial.cronica.contratou", prefeito.nome(), m.nome(), m.titulo()));
		});
	}

	private static boolean obraEmAndamento(ServerLevel level, BlockPos vila) {
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c && c.temTrabalho()) {
				return true;
			}
		}
		return false;
	}

	/** Finished (or started) buildings of a given project inside the arraial. */
	private static int obras(ServerLevel level, BlockPos vila, String id) {
		int n = 0;
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c
					&& c.projeto().map(pr -> pr.id().equals(id)).orElse(false)) {
				n++;
			}
		}
		return n;
	}

	private static long postos(ServerLevel level, BlockPos vila, PoiType tipo) {
		return level.getPoiManager().getCountInRange(h -> h.value() == tipo, vila, Vilas.RAIO, PoiManager.Occupancy.ANY);
	}

	private static long postosDe(ServerLevel level, BlockPos vila, Profissao p) {
		PoiType tipo = ModPois.de(p);
		return tipo == null ? 0 : postos(level, vila, tipo);
	}

	private static void avisar(ServerLevel level, ArraialBlockEntity arraial, Component msg) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(arraial.getBlockPos(), Vilas.RAIO * 2)) {
				p.sendSystemMessage(msg);
			}
		}
	}

	private Prefeitura() {
	}
}
