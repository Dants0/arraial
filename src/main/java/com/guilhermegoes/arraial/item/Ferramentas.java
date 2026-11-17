package com.guilhermegoes.arraial.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The sertanejo's tools: bateia, enxadão, laço, tarrafa and berrante. */
public final class Ferramentas {
	private Ferramentas() {
	}

	/** Water the player is looking at (within reach), or null. */
	private static @Nullable BlockPos agua(Level level, Player player) {
		Vec3 olho = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(olho, olho.add(player.getViewVector(1.0F).scale(5.0)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.SOURCE_ONLY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos p = hit.getBlockPos();
		return level.getFluidState(p).is(FluidTags.WATER) ? p : null;
	}

	private static void dar(Player player, ItemStack premio) {
		if (!player.getInventory().add(premio) && player.level() instanceof ServerLevel server) {
			player.spawnAtLocation(server, premio);
		}
	}

	// ================================================================ bateia
	/**
	 * Bateia: the gold pan of the garimpeiros. Standing over shallow water with sand, gravel or clay
	 * at the bottom, the player swirls it for a couple of seconds: flint, iron and gold nuggets, and
	 * once in a long while a diamond (Diamantina).
	 */
	public static class Bateia extends Item {
		public Bateia(Item.Properties p) {
			super(p);
		}

		private static boolean leito(Level level, BlockPos agua) {
			for (int i = 1; i <= 3; i++) {
				BlockState s = level.getBlockState(agua.below(i));
				if (s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD)) {
					return true;
				}
				if (!level.getFluidState(agua.below(i)).is(FluidTags.WATER)) {
					return false;
				}
			}
			return false;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			BlockPos a = agua(level, player);
			if (a == null || !leito(level, a)) {
				if (!level.isClientSide()) {
					player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("arraial.bateia.sem_leito"));
				}
				return InteractionResult.FAIL;
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity quem) {
			return 50;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BRUSH;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity quem) {
			if (level instanceof ServerLevel server && quem instanceof Player player) {
				RandomSource r = server.getRandom();
				float x = r.nextFloat();
				ItemStack premio = x < 0.004F ? new ItemStack(Items.DIAMOND)
						: x < 0.16F ? new ItemStack(Items.GOLD_NUGGET, 1 + r.nextInt(2))
						: x < 0.42F ? new ItemStack(Items.IRON_NUGGET, 1 + r.nextInt(3))
						: x < 0.70F ? new ItemStack(Items.FLINT)
						: ItemStack.EMPTY;
				if (!premio.isEmpty()) {
					dar(player, premio);
				}
				server.playSound(null, player.blockPosition(), premio.is(Items.DIAMOND) ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BUCKET_EMPTY,
						SoundSource.PLAYERS, 0.8F, 1.2F);
				server.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.5, player.getZ(), 8, 0.4, 0.1, 0.4, 0.0);
				stack.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
			}
			return stack;
		}
	}

	// ================================================================ enxadão
	/** Enxadão: the broad field hoe. Tills a 3×3 patch of dirt or grass at once. */
	public static class Enxadao extends Item {
		public Enxadao(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult useOn(UseOnContext ctx) {
			Level level = ctx.getLevel();
			BlockPos centro = ctx.getClickedPos();
			Player player = ctx.getPlayer();
			int arou = 0;
			for (BlockPos p : BlockPos.betweenClosed(centro.offset(-1, 0, -1), centro.offset(1, 0, 1))) {
				BlockState s = level.getBlockState(p);
				if ((s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.COARSE_DIRT))
						&& level.getBlockState(p.above()).isAir()) {
					if (!level.isClientSide()) {
						level.setBlock(p, s.is(Blocks.COARSE_DIRT) ? Blocks.DIRT.defaultBlockState() : Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
					}
					arou++;
				}
			}
			if (arou == 0) {
				return InteractionResult.PASS;
			}
			level.playSound(player, centro, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 0.9F);
			if (player != null && !level.isClientSide()) {
				ctx.getItemInHand().hurtAndBreak(Math.max(1, arou / 3), player, ctx.getHand());
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ================================================================ laço
	/** Laço: the vaqueiro's lasso. Catches the animal the player aims at, up to eight blocks away, and ties it to them. */
	public static class Laco extends Item {
		private static final double ALCANCE = 8.0;

		public Laco(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			Vec3 olho = player.getEyePosition(), olhar = player.getViewVector(1.0F);
			Mob melhor = null;
			double d = Double.MAX_VALUE;
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().expandTowards(olhar.scale(ALCANCE)).inflate(1.5),
					e -> e instanceof Animal && e instanceof Leashable l && l.canBeLeashed() && !l.isLeashed() && e.isAlive())) {
				Vec3 ate = m.getBoundingBox().getCenter().subtract(olho);
				double dist = ate.length();
				if (dist > ALCANCE || ate.normalize().dot(olhar) < 0.96 || !player.hasLineOfSight(m)) {
					continue;
				}
				if (dist < d) {
					d = dist;
					melhor = m;
				}
			}
			if (melhor == null) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && melhor instanceof Leashable l) {
				l.setLeashedTo(player, true);
				Vec3 puxa = player.position().subtract(melhor.position()).normalize().scale(0.6);
				melhor.setDeltaMovement(puxa.x, 0.35, puxa.z);
				level.playSound(null, melhor.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.8F);
				player.getItemInHand(hand).hurtAndBreak(1, player, hand);
				player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ================================================================ tarrafa
	/** Tarrafa: the round cast net. Thrown on water, it brings up one to three fish; then it must be gathered. */
	public static class Tarrafa extends Item {
		public Tarrafa(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			BlockPos a = agua(level, player);
			if (a == null) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel server) {
				RandomSource r = server.getRandom();
				int n = 1 + r.nextInt(3);
				for (int i = 0; i < n; i++) {
					float x = r.nextFloat();
					dar(player, new ItemStack(x < 0.55F ? Items.COD : x < 0.85F ? Items.SALMON : x < 0.95F ? Items.TROPICAL_FISH : Items.PUFFERFISH));
				}
				server.sendParticles(ParticleTypes.SPLASH, a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5, 20, 1.2, 0.1, 1.2, 0.0);
				server.playSound(null, a, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0F, 0.8F);
				stack.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(stack, 120);
			return InteractionResult.SUCCESS;
		}
	}

	// ================================================================ berrante
	/** Berrante: the cattle horn. Its call brings the herd (farm animals within 24 blocks) to the player. */
	public static class Berrante extends Item {
		private static final double RAIO = 24.0;

		public Berrante(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			level.playSound(player, player.blockPosition(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 3.0F, 0.6F);
			if (level instanceof ServerLevel server) {
				server.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 3.0F, 0.6F);
				List<Animal> gado = server.getEntitiesOfClass(Animal.class, new AABB(player.blockPosition()).inflate(RAIO),
						a -> a.isAlive() && !(a instanceof TamableAnimal) && !(a instanceof com.guilhermegoes.arraial.fauna.Onca)
								&& !(a instanceof com.guilhermegoes.arraial.fauna.Jacare));
				for (Animal a : gado) {
					a.getNavigation().moveTo(player, 1.2);
				}
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("arraial.berrante.chamou", gado.size()));
				stack.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(stack, 100);
			return InteractionResult.SUCCESS;
		}
	}
}
