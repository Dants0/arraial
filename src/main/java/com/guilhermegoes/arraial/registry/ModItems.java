package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.ContratoItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	/** Right-click a Marco da Obra with it to choose what will be built there. */
	public static final Item LIVRO_DE_PROJETOS = register("livro_de_projetos", Item::new,
			new Item.Properties().stacksTo(1));

	/** Hires a pedreiro: right-click the ground to make one appear. */
	public static final Item CONTRATO_PEDREIRO = register("contrato_pedreiro", ContratoItem::new,
			new Item.Properties().stacksTo(16));

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Arraial.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {
	}

	private ModItems() {
	}
}
