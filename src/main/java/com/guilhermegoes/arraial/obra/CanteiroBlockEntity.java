package com.guilhermegoes.arraial.obra;

import com.guilhermegoes.arraial.registry.ModBlockEntities;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Remembers which project a Marco da Obra holds and how far the work has gone. */
public class CanteiroBlockEntity extends BlockEntity {
	/** Loaded construction sites per dimension, so pedreiros can find work without scanning the world. */
	private static final Map<ResourceKey<Level>, Set<BlockPos>> ATIVOS = new ConcurrentHashMap<>();

	private String projeto = "";
	private int progresso = 0;
	private boolean concluida = false;
	// Sites from before priorities existed were all placed by the player.
	private Prioridade prioridade = Prioridade.ALTA;
	/** Game time the project was chosen: older sites go first among equal priorities. */
	private long criada = 0;
	private @Nullable PlanoDeObra plano;

	public CanteiroBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CANTEIRO, pos, state);
	}

	public static Set<BlockPos> ativos(Level level) {
		return ATIVOS.getOrDefault(level.dimension(), Set.of());
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			ATIVOS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(worldPosition.immutable());
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null) {
			ATIVOS.getOrDefault(level.dimension(), new HashSet<>()).remove(worldPosition);
		}
	}

	public Optional<Catalogo.Projeto> projeto() {
		return Catalogo.porId(projeto);
	}

	public int progresso() {
		return progresso;
	}

	public boolean concluida() {
		return concluida;
	}

	public boolean temTrabalho() {
		return !concluida && projeto().isPresent();
	}

	public boolean iniciada() {
		return progresso > 0;
	}

	public void escolherProjeto(Catalogo.Projeto novo) {
		projeto = novo.id();
		criada = level != null ? level.getGameTime() : 0;
		progresso = 0;
		concluida = false;
		plano = null;
		setChanged();
	}

	public Prioridade prioridade() {
		return prioridade;
	}

	public void setPrioridade(Prioridade p) {
		prioridade = p;
		setChanged();
	}

	/** Unfinished sites of the arraial at {@code vila}, most urgent first (then oldest first). */
	public static java.util.List<CanteiroBlockEntity> daVila(ServerLevel level, BlockPos vila) {
		java.util.List<CanteiroBlockEntity> lista = new java.util.ArrayList<>();
		for (BlockPos p : ativos(level)) {
			if (p.closerThan(vila, com.guilhermegoes.arraial.vila.Vilas.RAIO) && level.isLoaded(p)
					&& level.getBlockEntity(p) instanceof CanteiroBlockEntity c && c.temTrabalho()) {
				lista.add(c);
			}
		}
		lista.sort(ORDEM);
		return lista;
	}

	public static final java.util.Comparator<CanteiroBlockEntity> ORDEM = java.util.Comparator
			.comparing((CanteiroBlockEntity c) -> -c.prioridade.ordinal())
			.thenComparingLong(c -> c.criada)
			.thenComparing(c -> c.worldPosition);

	public void avancar() {
		progresso++;
		setChanged();
	}

	public void concluir() {
		concluida = true;
		setChanged();
	}

	public Direction frente() {
		return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
	}

	/** The block-by-block plan, built lazily from the structure file. */
	public Optional<PlanoDeObra> plano(ServerLevel level) {
		if (plano == null) {
			projeto().flatMap(p -> PlanoDeObra.criar(level, p, worldPosition, frente())).ifPresent(p -> plano = p);
		}
		return Optional.ofNullable(plano);
	}

	/** The supply chest: any container within 2 blocks of the marker, outside the building's footprint. */
	public @Nullable Container bau(ServerLevel level) {
		PlanoDeObra p = plano(level).orElse(null);
		for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-2, -1, -2), worldPosition.offset(2, 1, 2))) {
			if (pos.equals(worldPosition) || (p != null && p.area().isInside(pos))) {
				continue;
			}
			if (level.getBlockEntity(pos) instanceof Container container) {
				return container;
			}
		}
		return null;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		projeto = input.getStringOr("Projeto", "");
		progresso = input.getIntOr("Progresso", 0);
		concluida = input.getBooleanOr("Concluida", false);
		prioridade = Prioridade.de(input.getIntOr("Prioridade", Prioridade.ALTA.ordinal()));
		criada = input.getLongOr("Criada", 0L);
		plano = null;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Projeto", projeto);
		output.putInt("Progresso", progresso);
		output.putBoolean("Concluida", concluida);
		output.putInt("Prioridade", prioridade.ordinal());
		output.putLong("Criada", criada);
	}
}
