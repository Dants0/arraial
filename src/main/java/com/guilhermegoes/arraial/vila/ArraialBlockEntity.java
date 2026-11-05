package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The arraial itself, stored in its Cruzeiro: its name and when the next newcomer arrives.
 * Every few seconds it counts beds and moradores and, if a bed is free, welcomes someone new.
 */
public class ArraialBlockEntity extends BlockEntity {
	private String nome = "";
	private long proximaChegada = 0;
	private final java.util.ArrayList<Cronica.Entrada> cronica = new java.util.ArrayList<>();
	/** The elected prefeito (a morador's UUID), managed by {@link Prefeitura}. */
	java.util.@org.jspecify.annotations.Nullable UUID prefeito;
	/** Last day the prefeito made the morning rounds. */
	long ultimoDespacho = 0;
	/** What the prefeito wants but cannot do (shown at the Cruzeiro); empty when all is well. */
	Component pedido = Component.empty();
	/** The crisis the arraial is going through, if any (see {@link Crise}). */
	final Crise crise = new Crise();
	/** Population at the last count (for the per-second crisis tick). */
	int populacao;

	public Crise crise() {
		return crise;
	}

	public java.util.@org.jspecify.annotations.Nullable UUID prefeito() {
		return prefeito;
	}

	public Component pedido() {
		return pedido;
	}

	public ArraialBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARRAIAL, pos, state);
	}

	public String nome() {
		return nome;
	}

	public List<Cronica.Entrada> cronica() {
		return java.util.Collections.unmodifiableList(cronica);
	}

	void anotar(Cronica.Entrada entrada) {
		cronica.add(entrada);
		while (cronica.size() > Cronica.MAXIMO) {
			cronica.remove(0);
		}
		setChanged();
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			Vilas.registrar(level, worldPosition);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null) {
			Vilas.remover(level, worldPosition);
		}
	}

	public List<MoradorEntity> moradores(ServerLevel level) {
		return level.getEntitiesOfClass(MoradorEntity.class, new AABB(worldPosition).inflate(Vilas.RAIO),
				m -> worldPosition.equals(m.vila()));
	}

	public long camas(ServerLevel level) {
		return level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), worldPosition, Vilas.RAIO, PoiManager.Occupancy.ANY);
	}

	/** Gives the arraial its name; called when the Cruzeiro is placed (or on first tick as a fallback). */
	void fundar(ServerLevel level) {
		if (nome.isEmpty()) {
			nome = Vilas.nomeAleatorio(level.getRandom());
			proximaChegada = level.getGameTime() + Vilas.INTERVALO_CHEGADA;
			anotar(new Cronica.Entrada(Vilas.dia(level), Component.translatable("arraial.cronica.fundacao", nome)));
			setChanged();
		}
	}

	/** Workstations (cepo, balaio, forno) inside the arraial that nobody has claimed yet. */
	public long postosLivres(ServerLevel level) {
		return level.getPoiManager().getCountInRange(com.guilhermegoes.arraial.registry.ModPois::ePosto,
				worldPosition, Vilas.RAIO, PoiManager.Occupancy.HAS_SPACE);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ArraialBlockEntity arraial) {
		if (!(level instanceof ServerLevel server) || server.getGameTime() % 20 != 0) {
			return;
		}
		Crise.tick(server, arraial, arraial.populacao);
		if (server.getGameTime() % 100 != 0) {
			return;
		}
		arraial.fundar(server);

		List<MoradorEntity> moradores = arraial.moradores(server);
		arraial.populacao = moradores.size();
		Prefeitura.cuidar(server, arraial, moradores);
		long camasLivres = arraial.camas(server) - moradores.size();
		// Free work also draws people in, even with nowhere to sleep (they arrive homeless,
		// which pushes the player to build houses), but only while there are few homeless.
		long semCasa = moradores.stream().filter(m -> m.casa() == null).count();
		boolean atraiPorTrabalho = arraial.postosLivres(server) > 0 && semCasa < Vilas.MAX_SEM_CASA
				&& moradores.stream().noneMatch(m -> m.profissao() == Profissao.NENHUMA && m.gabinete() == null);
		if (camasLivres <= 0 && !atraiPorTrabalho) {
			// Nobody comes while there is nowhere to live or work; the clock starts when that changes.
			arraial.proximaChegada = Math.max(arraial.proximaChegada, server.getGameTime() + Vilas.INTERVALO_CHEGADA);
			return;
		}
		if (server.getGameTime() >= arraial.proximaChegada) {
			arraial.receberMorador(server);
			arraial.proximaChegada = server.getGameTime() + Vilas.INTERVALO_CHEGADA;
			arraial.setChanged();
		}
	}

	/** A newcomer walks into town: spawned on the surface a few blocks from the Cruzeiro. */
	private void receberMorador(ServerLevel level) {
		double angulo = level.getRandom().nextDouble() * Math.PI * 2;
		int dist = 6 + level.getRandom().nextInt(6);
		BlockPos alvo = worldPosition.offset((int) (Math.cos(angulo) * dist), 0, (int) (Math.sin(angulo) * dist));
		BlockPos chao = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, alvo);
		MoradorEntity novo = MoradorEntity.criar(level, chao, Profissao.NENHUMA);
		if (novo == null) {
			return;
		}
		novo.definirVila(worldPosition);
		anotar(new Cronica.Entrada(Vilas.dia(level), Component.translatable("arraial.cronica.chegada", novo.nome())));
		Component aviso = Component.translatable("arraial.vila.chegou", novo.nome(), nome).withStyle(ChatFormatting.AQUA);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(worldPosition, Vilas.RAIO * 2)) {
				p.sendSystemMessage(aviso);
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		nome = input.getStringOr("Nome", "");
		proximaChegada = input.getLongOr("ProximaChegada", 0L);
		cronica.clear();
		input.read("Cronica", Cronica.LISTA).ifPresent(cronica::addAll);
		prefeito = input.read("Prefeito", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		ultimoDespacho = input.getLongOr("UltimoDespacho", 0L);
		pedido = input.read("Pedido", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(Component.empty());
		crise.ler(input);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Nome", nome);
		output.putLong("ProximaChegada", proximaChegada);
		output.store("Cronica", Cronica.LISTA, List.copyOf(cronica));
		if (prefeito != null) {
			output.store("Prefeito", net.minecraft.core.UUIDUtil.CODEC, prefeito);
		}
		output.putLong("UltimoDespacho", ultimoDespacho);
		output.store("Pedido", net.minecraft.network.chat.ComponentSerialization.CODEC, pedido);
		crise.gravar(output);
	}
}
