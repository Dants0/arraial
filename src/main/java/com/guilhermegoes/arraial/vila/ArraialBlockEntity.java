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

	public ArraialBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARRAIAL, pos, state);
	}

	public String nome() {
		return nome;
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
			setChanged();
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ArraialBlockEntity arraial) {
		if (!(level instanceof ServerLevel server) || server.getGameTime() % 100 != 0) {
			return;
		}
		arraial.fundar(server);

		long livres = arraial.camas(server) - arraial.moradores(server).size();
		if (livres <= 0) {
			// Nobody comes while there is nowhere to live; the clock starts when a bed frees up.
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
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Nome", nome);
		output.putLong("ProximaChegada", proximaChegada);
	}
}
