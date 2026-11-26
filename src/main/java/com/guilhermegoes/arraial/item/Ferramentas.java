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

/** The sertanejo's tools: bateia, enxadão, laço, tarrafa, berrante, estilingue, peneira, candeeiro, cabaça, chicote and the facão. */
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

	// ================================================================ estilingue
	/** Estilingue: the slingshot. Shoots a seixo (pebble) from the inventory; 4 damage, cheap and quick. */
	public static class Estilingue extends Item {
		public Estilingue(Item.Properties p) {
			super(p);
		}

		private static ItemStack municao(Player player) {
			var inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.is(com.guilhermegoes.arraial.registry.ModItems.SEIXO)) {
					return s;
				}
			}
			return ItemStack.EMPTY;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			ItemStack pedra = municao(player);
			if (pedra.isEmpty() && !player.hasInfiniteMaterials()) {
				if (!level.isClientSide()) {
					player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("arraial.estilingue.sem_seixo"));
				}
				return InteractionResult.FAIL;
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.6F, 1.6F);
			if (level instanceof ServerLevel server) {
				ItemStack tiro = new ItemStack(com.guilhermegoes.arraial.registry.ModItems.SEIXO);
				net.minecraft.world.entity.projectile.Projectile.spawnProjectileFromRotation(com.guilhermegoes.arraial.item.Pedrada::new, server, tiro, player, 0.0F, 1.9F, 0.8F);
				if (!player.hasInfiniteMaterials()) {
					pedra.shrink(1);
				}
				stack.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(stack, 12);
			return InteractionResult.SUCCESS;
		}
	}

	// ================================================================ peneira
	/** Peneira: the sieve. Right-click gravel, sand or dirt: the block is sifted away and something useful stays behind. */
	public static class Peneira extends Item {
		public Peneira(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult useOn(UseOnContext ctx) {
			Level level = ctx.getLevel();
			BlockPos p = ctx.getClickedPos();
			BlockState s = level.getBlockState(p);
			int tipo = s.is(Blocks.GRAVEL) ? 0 : s.is(BlockTags.SAND) ? 1 : s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) ? 2 : -1;
			if (tipo < 0 || ctx.getPlayer() == null) {
				return InteractionResult.PASS;
			}
			Player player = ctx.getPlayer();
			if (level instanceof ServerLevel server) {
				RandomSource r = server.getRandom();
				float x = r.nextFloat();
				ItemStack achado = switch (tipo) {
					case 0 -> x < 0.35F ? new ItemStack(Items.FLINT) : x < 0.70F ? new ItemStack(com.guilhermegoes.arraial.registry.ModItems.SEIXO, 2 + r.nextInt(3))
							: x < 0.86F ? new ItemStack(Items.IRON_NUGGET, 1 + r.nextInt(2)) : x < 0.92F ? new ItemStack(Items.GOLD_NUGGET) : ItemStack.EMPTY;
					case 1 -> x < 0.30F ? new ItemStack(Items.CLAY_BALL, 1 + r.nextInt(2)) : x < 0.55F ? new ItemStack(com.guilhermegoes.arraial.registry.ModItems.SEIXO, 1 + r.nextInt(2))
							: x < 0.67F ? new ItemStack(Items.GOLD_NUGGET) : x < 0.75F ? new ItemStack(Items.QUARTZ) : ItemStack.EMPTY;
					default -> x < 0.28F ? new ItemStack(Items.WHEAT_SEEDS, 1 + r.nextInt(2)) : x < 0.40F ? new ItemStack(com.guilhermegoes.arraial.registry.ModItems.MANIVA)
							: x < 0.55F ? new ItemStack(Items.BONE_MEAL, 1 + r.nextInt(2)) : x < 0.62F ? new ItemStack(r.nextBoolean() ? Items.PUMPKIN_SEEDS : Items.MELON_SEEDS)
							: x < 0.70F ? new ItemStack(Items.POTATO) : x < 0.80F ? new ItemStack(com.guilhermegoes.arraial.registry.ModItems.SEIXO) : ItemStack.EMPTY;
				};
				server.removeBlock(p, false);
				server.playSound(null, p, tipo == 0 ? SoundEvents.GRAVEL_BREAK : tipo == 1 ? SoundEvents.SAND_BREAK : SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
				server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5, 14, 0.3, 0.2, 0.3, 0.0);
				if (!achado.isEmpty()) {
					dar(player, achado);
				}
				ctx.getItemInHand().hurtAndBreak(1, player, ctx.getHand());
				player.getCooldowns().addCooldown(ctx.getItemInHand(), 6);
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ================================================================ candeeiro
	/**
	 * Candeeiro de mão: the oil lamp. Held in either hand it lights the way (an invisible light block
	 * follows the player's head, removed a moment after it moves on); it burns down slowly and is
	 * refuelled with coal at the anvil.
	 */
	public static class Candeeiro extends Item {
		private record Luz(ServerLevel level, BlockPos pos) {
		}

		/** Light blocks placed and the tick they go out unless refreshed. */
		private static final java.util.Map<Luz, Long> LUZES = new java.util.HashMap<>();
		private static boolean ligado;

		public Candeeiro(Item.Properties p) {
			super(p);
		}

		public static void init() {
			if (ligado) {
				return;
			}
			ligado = true;
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> apagar(false));
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> apagar(true));
		}

		private static void apagar(boolean tudo) {
			if (LUZES.isEmpty()) {
				return;
			}
			var it = LUZES.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				Luz l = e.getKey();
				if (tudo || l.level().getGameTime() >= e.getValue()) {
					if (l.level().isLoaded(l.pos()) && l.level().getBlockState(l.pos()).is(Blocks.LIGHT)) {
						l.level().setBlock(l.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
					it.remove();
				}
			}
		}

		@Override
		public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity quem, net.minecraft.world.entity.@Nullable EquipmentSlot slot) {
			if (!(quem instanceof Player player) || (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND && slot != net.minecraft.world.entity.EquipmentSlot.OFFHAND)) {
				return;
			}
			BlockPos p = BlockPos.containing(player.getEyePosition());
			BlockState s = level.getBlockState(p);
			Luz luz = new Luz(level, p.immutable());
			if (s.isAir()) {
				level.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 14), Block.UPDATE_ALL);
			} else if (!s.is(Blocks.LIGHT) || !LUZES.containsKey(luz)) {
				return;
			}
			LUZES.put(luz, level.getGameTime() + 4);
			if (level.getGameTime() % 100 == 0 && !player.hasInfiniteMaterials()) {
				stack.hurtAndBreak(1, player, slot);
			}
		}
	}

	// ================================================================ cabaça de cachaça
	/**
	 * Cabaça de cachaça: a gourd of cane spirit, four swigs. Each one gives strength and resistance
	 * for a minute and a half, and the world spins a little. Empty, it is a cabaça again.
	 */
	public static class Cabaca extends Item {
		public Cabaca(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity quem) {
			return 32;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.DRINK;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity quem) {
			if (level instanceof ServerLevel server) {
				quem.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 20 * 90, 0));
				quem.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20 * 90, 0));
				quem.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, 20 * 7, 0));
				server.playSound(null, quem.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
				if (quem instanceof Player player && player.hasInfiniteMaterials()) {
					return stack;
				}
				stack.hurtAndBreak(1, quem, quem.getUsedItemHand());
				if (stack.isEmpty()) {
					return new ItemStack(com.guilhermegoes.arraial.registry.ModItems.CABACA);
				}
			}
			return stack;
		}
	}

	// ================================================================ chicote
	/**
	 * Chicote de vaqueiro: the whip. Its crack drives the herd ahead (farm animals within 12 blocks
	 * run on in the direction the player faces) and makes monsters in front reel back, slowed.
	 * With the berrante (which calls the herd in) it is how the vaqueiro moves cattle.
	 */
	public static class Chicote extends Item {
		public Chicote(Item.Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			Vec3 olhar = player.getViewVector(1.0F).multiply(1, 0, 1).normalize();
			level.playSound(player, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 1.7F);
			if (level instanceof ServerLevel server) {
				server.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.4F, 1.9F);
				Vec3 frente = player.getEyePosition().add(player.getViewVector(1.0F).scale(2.5));
				server.sendParticles(ParticleTypes.SWEEP_ATTACK, frente.x, frente.y - 0.4, frente.z, 1, 0, 0, 0, 0);
				server.sendParticles(ParticleTypes.CRIT, frente.x, frente.y - 0.4, frente.z, 8, 0.4, 0.2, 0.4, 0.1);
				int tocados = 0;
				for (Animal a : server.getEntitiesOfClass(Animal.class, new AABB(player.blockPosition()).inflate(12),
						a -> a.isAlive() && !(a instanceof TamableAnimal) && !(a instanceof com.guilhermegoes.arraial.fauna.Onca)
								&& !(a instanceof com.guilhermegoes.arraial.fauna.Jacare))) {
					Vec3 ate = a.position().add(olhar.scale(10));
					a.getNavigation().moveTo(ate.x, ate.y, ate.z, 1.5);
					tocados++;
				}
				for (net.minecraft.world.entity.monster.Monster m : server.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
						new AABB(player.blockPosition()).inflate(7), m -> m.isAlive())) {
					Vec3 ate = m.position().subtract(player.position());
					if (ate.length() > 7 || ate.normalize().dot(olhar) < 0.5) {
						continue;
					}
					m.hurtServer(server, server.damageSources().playerAttack(player), 2.0F);
					m.push(olhar.x * 1.1, 0.35, olhar.z * 1.1);
					m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 20 * 4, 1));
				}
				if (tocados > 0) {
					player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("arraial.chicote.tocou", tocados));
				}
				stack.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(stack, 15);
			return InteractionResult.SUCCESS;
		}
	}

	// ================================================================ facão
	/** Facão: the machete. Still a hard-hitting blade, and a right-click on leaves, vines or tall grass clears the bush around (3×3×3). */
	public static class Facao extends Item {
		public Facao(Item.Properties p) {
			super(p);
		}

		private static boolean mato(BlockState s) {
			return s.is(BlockTags.REPLACEABLE_BY_TREES) && s.getFluidState().isEmpty();
		}

		@Override
		public InteractionResult useOn(UseOnContext ctx) {
			Level level = ctx.getLevel();
			BlockPos centro = ctx.getClickedPos();
			if (!mato(level.getBlockState(centro))) {
				return InteractionResult.PASS;
			}
			Player player = ctx.getPlayer();
			if (level instanceof ServerLevel server) {
				int cortou = 0;
				for (BlockPos p : BlockPos.betweenClosed(centro.offset(-1, -1, -1), centro.offset(1, 1, 1))) {
					if (mato(server.getBlockState(p)) && server.destroyBlock(p, true, player, 512)) {
						cortou++;
					}
				}
				server.playSound(null, centro, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
				if (player != null) {
					ctx.getItemInHand().hurtAndBreak(Math.max(1, cortou / 4), player, ctx.getHand());
				}
			}
			return InteractionResult.SUCCESS;
		}
	}
}
