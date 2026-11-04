package com.guilhermegoes.arraial.npc;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Digging trades. The pit ("cava") opens right in front of the workstation, in the direction
 * it was placed facing, and goes down layer by layer:
 * <ul>
 * <li>the barreiro digs earth, sand, gravel and clay (and first any natural clay nearby);</li>
 * <li>the cavouqueiro quarries stone, turning it into cobblestone, plus the ores he finds.</li>
 * </ul>
 * He never digs next to water or lava, so the pit stays dry, and never under his own posto.
 */
public class CavarGoal extends OficioGoal {
	private enum Fase { CAVAR, ENTREGAR }

	private static final int CARGA_CHEIA = 48;
	/** Natural clay the barreiro looks for before opening his own pit. */
	private static final int RAIO_ARGILA = 16;

	private final boolean pedra;
	private final int largura;
	private final int profundidade;
	private Fase fase = Fase.CAVAR;
	private @Nullable BlockPos alvo;
	private int espera;
	private int travado;

	public CavarGoal(MoradorEntity morador, Profissao profissao) {
		super(morador, profissao);
		this.pedra = profissao == Profissao.CAVOUQUEIRO;
		this.largura = pedra ? 5 : 4;
		this.profundidade = pedra ? 10 : 5;
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar()) {
			return false;
		}
		if (morador.cargaTotal() >= CARGA_CHEIA && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		alvo = proximoBloco();
		if (alvo != null) {
			fase = Fase.CAVAR;
			return true;
		}
		if (morador.cargaTotal() > 0 && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		morador.setStatus(Component.translatable("arraial.cava.acabou"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase == Fase.ENTREGAR || alvo != null);
	}

	@Override
	public void start() {
		espera = 0;
		travado = 0;
		morador.segurar(new ItemStack(pedra ? Items.IRON_PICKAXE : Items.IRON_SHOVEL));
	}

	@Override
	public void stop() {
		super.stop();
		alvo = null;
	}

	@Override
	public void tick() {
		if (espera > 0) {
			espera--;
			return;
		}
		if (fase == Fase.ENTREGAR) {
			var a = armazemMaisProximo();
			if (a == null) {
				fase = Fase.CAVAR;
				alvo = null;
				return;
			}
			sairDoBuraco();
			morador.setStatus(Component.translatable("arraial.oficio.entregando",
					Component.translatable(pedra ? "arraial.cava.pedra" : "arraial.cava.barro")));
			if (!perto(a.getBlockPos(), 2.5)) {
				andarAte(a.getBlockPos());
				return;
			}
			entregar(Map.of());
			fase = Fase.CAVAR;
			alvo = null; // ends the goal; canUse decides what's next
			return;
		}

		BlockPos p = alvo;
		if (p == null) {
			return;
		}
		ServerLevel level = level();
		BlockState s = level.getBlockState(p);
		if (!cavavel(level, p, s)) {
			alvo = proximoBloco();
			return;
		}
		morador.setStatus(Component.translatable(pedra ? "arraial.cava.quebrando_pedra" : "arraial.cava.cavando"));
		double dist = morador.getEyePosition().distanceTo(Vec3.atCenterOf(p));
		// Long-handled tools: after trying to get close he works the pit from its edge.
		if (dist > 4.5 && !(travado > 40 && dist <= profundidade + 4)) {
			travado++;
			andarAte(p.above());
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(p));
		ItemStack ferramenta = new ItemStack(pedra ? Items.IRON_PICKAXE : Items.IRON_SHOVEL);
		for (ItemStack drop : Block.getDrops(s, level, p, null, morador, ferramenta)) {
			morador.guardar(drop.getItem(), drop.getCount());
		}
		level.destroyBlock(p, false, morador, 512);
		bater();
		travado = 0;
		espera = pedra ? 14 : 8;
		sairDoBuraco();
		alvo = morador.cargaTotal() >= CARGA_CHEIA ? null : proximoBloco();
	}

	/** If he fell into a deep pit, he climbs out (by the ladder nobody sees) next to his posto. */
	private void sairDoBuraco() {
		BlockPos posto = morador.posto();
		if (posto != null && morador.getY() < posto.getY() - 2) {
			morador.teleportTo(posto.getX() + 0.5, posto.getY() + 1, posto.getZ() + 0.5);
		}
	}

	/** Next block to dig: natural clay nearby (barreiro), else the highest diggable block of the pit. */
	private @Nullable BlockPos proximoBloco() {
		BlockPos posto = morador.posto();
		if (posto == null) {
			return null;
		}
		ServerLevel level = level();
		if (!pedra) {
			BlockPos argila = argilaPerto(level, posto);
			if (argila != null) {
				return argila;
			}
		}
		BlockState st = level.getBlockState(posto);
		Direction frente = st.hasProperty(HorizontalDirectionalBlock.FACING) ? st.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
		Direction lado = frente.getClockWise();
		for (int dy = 0; dy >= -profundidade; dy--) {
			BlockPos melhor = null;
			double melhorDist = Double.MAX_VALUE;
			for (int f = 2; f < 2 + largura; f++) {
				for (int l = -(largura / 2); l < largura - largura / 2; l++) {
					BlockPos p = posto.relative(frente, f).relative(lado, l).offset(0, dy - 1, 0);
					// The surface layer may be a bit higher (uneven ground): include up to two blocks above.
					for (int extra = dy == 0 ? 2 : 0; extra >= 0; extra--) {
						BlockPos q = p.above(extra);
						if (cavavel(level, q, level.getBlockState(q))) {
							double d = q.distSqr(morador.blockPosition());
							if (d < melhorDist) {
								melhor = q;
								melhorDist = d;
							}
						}
					}
				}
			}
			if (melhor != null) {
				return melhor;
			}
		}
		return null;
	}

	private @Nullable BlockPos argilaPerto(ServerLevel level, BlockPos centro) {
		BlockPos melhor = null;
		double melhorDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO_ARGILA, -6, -RAIO_ARGILA), centro.offset(RAIO_ARGILA, 3, RAIO_ARGILA))) {
			if (level.getBlockState(p).is(Blocks.CLAY) && !perigoso(level, p)) {
				double d = p.distSqr(centro);
				if (d < melhorDist) {
					melhor = p.immutable();
					melhorDist = d;
				}
			}
		}
		return melhor;
	}

	private boolean cavavel(ServerLevel level, BlockPos p, BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, p) < 0
				|| s.getDestroySpeed(level, p) > 10 || perigoso(level, p)) {
			return false;
		}
		BlockPos posto = morador.posto();
		if (posto != null && p.getX() == posto.getX() && p.getZ() == posto.getZ()) {
			return false;
		}
		if (pedra) {
			return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL)
					|| s.is(BlockTags.SAND) || s.is(net.minecraft.tags.BlockItemTags.COAL_ORES.block()) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
					|| s.is(Blocks.CLAY);
		}
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD);
	}

	/** Digging here would let water or lava in. */
	private static boolean perigoso(ServerLevel level, BlockPos p) {
		for (Direction d : Direction.values()) {
			if (d != Direction.DOWN && !level.getFluidState(p.relative(d)).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
