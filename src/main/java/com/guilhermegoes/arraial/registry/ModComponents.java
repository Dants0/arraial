package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModComponents {
	/** The project chosen in a Livro de Projetos (catalog id, e.g. "casebre"). */
	public static final DataComponentType<String> PROJETO = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Arraial.id("projeto"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	public static void init() {
	}

	private ModComponents() {
	}
}
