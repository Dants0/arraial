package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The arraial's storehouse: a 54-slot container opened with the vanilla large-chest screen. */
public class ArmazemBlockEntity extends BaseContainerBlockEntity {
	public static final int TAMANHO = 54;
	private NonNullList<ItemStack> itens = NonNullList.withSize(TAMANHO, ItemStack.EMPTY);

	public ArmazemBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARMAZEM, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			Vilas.registrarArmazem(level, worldPosition);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null) {
			Vilas.removerArmazem(level, worldPosition);
		}
	}

	@Override
	public int getContainerSize() {
		return TAMANHO;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return itens;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.itens = items;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.arraial.armazem");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return ChestMenu.sixRows(containerId, inventory, this);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		itens = NonNullList.withSize(TAMANHO, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, itens);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, itens);
	}
}
