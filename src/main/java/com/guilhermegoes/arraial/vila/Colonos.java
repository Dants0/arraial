package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * A village founded before the player's eyes: instead of a povoado appearing ready, a caravan of
 * settlers (a family or two and a pack donkey) walks in from the horizon to the chosen land. There
 * the leader plants the Cruzeiro and a campfire, the donkey's load goes into the armazém (exactly
 * what the first works need), and the pedreiro raises the first house, the roça and the well block
 * by block. From then on it is an arraial like any other: the prefeito orders the rest.
 */
public final class Colonos {
	private static final int PESSOAS = 5;
	/** Destinations of caravans on the road (game time they set out), so only one travels at a time. */
	private static final Map<BlockPos, Long> A_CAMINHO = new ConcurrentHashMap<>();
	/** A caravan that never arrived (its chunks unloaded for good) no longer blocks new ones. */
	private static final long ESQUECER = 72000;

	/** A caravan is on the road. */
	static boolean aCaminho(ServerLevel level) {
		A_CAMINHO.values().removeIf(desde -> level.getGameTime() - desde > ESQUECER);
		return !A_CAMINHO.isEmpty();
	}

	/**
	 * Sends a caravan to {@code centro}: it appears 28 to 40 blocks beyond it, on the side away from
	 * the player, and walks in.
	 * @return false if there was no loaded, dry ground to start from
	 */
	public static boolean partir(ServerLevel level, ServerPlayer p, BlockPos centro, boolean missao) {
		double base = Math.atan2(centro.getZ() - p.getZ(), centro.getX() - p.getX());
		for (int tentativa = 0; tentativa < 8; tentativa++) {
			double a = base + (level.getRandom().nextDouble() - 0.5) * Math.PI * 0.8;
			int dist = 28 + level.getRandom().nextInt(13);
			BlockPos alvo = centro.offset((int) (Math.cos(a) * dist), 0, (int) (Math.sin(a) * dist));
			if (!level.isPositionEntityTicking(alvo)) {
				continue;
			}
			BlockPos inicio = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, alvo);
			if (!level.getBlockState(inicio.below()).getFluidState().isEmpty() || !level.getBlockState(inicio).getFluidState().isEmpty()) {
				continue;
			}
			chegar(level, inicio, centro, missao);
			p.sendSystemMessage(Component.translatable(missao ? "arraial.colonos.chegando_missao" : "arraial.colonos.chegando").withStyle(ChatFormatting.AQUA));
			return true;
		}
		return false;
	}

	/** The caravan itself: the leader (a pedreiro), the others behind him, and the pack donkey. */
	public static void chegar(ServerLevel level, BlockPos inicio, BlockPos centro, boolean missao) {
		long agora = level.getGameTime();
		A_CAMINHO.put(centro.immutable(), agora);
		MoradorEntity lider = null;
		for (int i = 0; i < PESSOAS; i++) {
			BlockPos onde = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					inicio.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2));
			MoradorEntity m = MoradorEntity.criar(level, onde, i == 0 ? Profissao.PEDREIRO : Profissao.NENHUMA);
			if (m == null) {
				continue;
			}
			m.setColono(centro.immutable(), i == 0, missao, agora);
			if (i == 0) {
				lider = m;
			}
		}
		Donkey burro = EntityTypes.DONKEY.create(level, EntitySpawnReason.EVENT);
		if (burro != null && lider != null) {
			burro.snapTo(inicio.getX() + 0.5, inicio.getY(), inicio.getZ() + 1.5, 0, 0);
			burro.setTamed(true);
			burro.setChest(true);
			burro.setPersistenceRequired();
			level.addFreshEntity(burro);
			burro.setLeashedTo(lider, true);
		}
	}

	/** The leader arrived: the povoado is founded at {@code onde} and the settlers become its moradores. */
	public static void fundar(ServerLevel level, MoradorEntity lider, BlockPos onde) {
		BlockPos destino = lider.fundacao();
		if (destino == null) {
			return;
		}
		A_CAMINHO.remove(destino);
		List<MoradorEntity> grupo = level.getEntitiesOfClass(MoradorEntity.class, lider.getBoundingBox().inflate(64),
				m -> destino.equals(m.fundacao()));
		// Another leader of the same caravan got here first: just join it.
		BlockPos vila = Vilas.maisProxima(level, onde);
		ArraialBlockEntity arraial = null;
		if (vila == null || vila.distSqr(onde) > 32 * 32) {
			BlockPos centro = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, onde);
			arraial = VilaNova.fundarAoVivo(level, centro, lider.colonosHispanos());
			vila = arraial == null ? null : centro;
		}
		for (MoradorEntity m : grupo) {
			m.setColono(null, false, false, 0);
			m.setStatus(Component.empty());
			if (vila != null) {
				m.definirVila(vila);
			}
		}
		// The donkey is unloaded and stays in the povoado.
		for (Donkey burro : level.getEntitiesOfClass(Donkey.class, new AABB(lider.blockPosition()).inflate(16), b -> b.getLeashHolder() == lider)) {
			burro.removeLeash();
		}
		if (arraial != null) {
			Component msg = Component.translatable("arraial.colonos.fundaram", arraial.nomeCompleto()).withStyle(ChatFormatting.AQUA);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(lider) < 160 * 160) {
					p.sendSystemMessage(msg);
				}
			}
		}
	}

	private Colonos() {
	}
}
