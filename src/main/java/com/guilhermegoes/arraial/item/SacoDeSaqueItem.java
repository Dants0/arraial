package com.guilhermegoes.arraial.item;

import com.guilhermegoes.arraial.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Saco de saque: what a bandit was carrying. Right-click to open: réis, and maybe a weapon, a
 * piece of the vaqueiro's gear, iron, gold, festa sweets or a flask.
 */
public class SacoDeSaqueItem extends Item {
	public SacoDeSaqueItem(Properties properties) {
		super(properties);
	}

	public static List<ItemStack> sortear(RandomSource r) {
		List<ItemStack> l = new ArrayList<>();
		l.add(new ItemStack(ModItems.REIS, 3 + r.nextInt(6)));
		float x = r.nextFloat();
		if (x < 0.12F) {
			l.add(new ItemStack(r.nextBoolean() ? ModItems.FACAO : ModItems.PEIXEIRA));
		} else if (x < 0.22F) {
			Item[] couro = {ModItems.GIBAO, ModItems.PERNEIRA, ModItems.ALPERCATAS, ModItems.CHAPEU_COURO};
			l.add(new ItemStack(couro[r.nextInt(couro.length)]));
		} else if (x < 0.45F) {
			l.add(new ItemStack(r.nextBoolean() ? Items.IRON_INGOT : Items.GOLD_NUGGET, 1 + r.nextInt(4)));
		} else if (x < 0.65F) {
			l.add(new ItemStack(r.nextBoolean() ? ModItems.RAPADURA : ModItems.PE_DE_MOLEQUE, 1 + r.nextInt(3)));
		} else if (x < 0.72F) {
			l.add(new ItemStack(ModItems.CANTIL));
		}
		return l;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			ItemStack saco = player.getItemInHand(hand);
			saco.shrink(1);
			for (ItemStack s : sortear(level.getRandom())) {
				player.sendSystemMessage(Component.translatable("arraial.saque.achou", s.getCount(), s.getHoverName()).withStyle(ChatFormatting.GOLD));
				if (!player.getInventory().add(s)) {
					net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), s);
				}
			}
			level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}
}
