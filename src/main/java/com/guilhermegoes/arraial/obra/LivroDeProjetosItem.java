package com.guilhermegoes.arraial.obra;

import com.guilhermegoes.arraial.rede.LivroDados;
import com.guilhermegoes.arraial.rede.Rede;
import com.guilhermegoes.arraial.registry.ModComponents;
import com.guilhermegoes.arraial.registry.ModItems;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Livro de Projetos. Right-click opens the catalog: every building, by category, with its size, a
 * top-down plan, the trades it brings and the materials (and how many the armazém holds). Opened on
 * a Marco da Obra, "Build here" hands the marker the project and outlines where it will stand;
 * opened in the air, the choice stays in the book for the next marker.
 */
public class LivroDeProjetosItem extends Item {
	public LivroDeProjetosItem(Item.Properties properties) {
		super(properties);
	}

	public static Optional<Catalogo.Projeto> selecionado(ItemStack stack) {
		String id = stack.get(ModComponents.PROJETO);
		return id == null ? Optional.empty() : Catalogo.porId(id);
	}

	@Override
	public Component getName(ItemStack stack) {
		Component base = super.getName(stack);
		return selecionado(stack)
				.<Component>map(p -> Component.translatable("item.arraial.livro_de_projetos.com", base, p.nome()))
				.orElse(base);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			abrir(sp, player.getItemInHand(hand), null);
		}
		return InteractionResult.SUCCESS;
	}

	/** Sends the catalog screen to the player (on a Marco da Obra when {@code marco} is given). */
	public static void abrir(ServerPlayer player, ItemStack livro, @Nullable BlockPos marco) {
		ServerPlayNetworking.send(player, new Rede.AbrirLivro(LivroDados.montar(player, livro, marco)));
	}

	/** The book in the player's hands, if any. */
	private static @Nullable ItemStack livro(Player p) {
		for (InteractionHand h : InteractionHand.values()) {
			if (p.getItemInHand(h).is(ModItems.LIVRO_DE_PROJETOS)) {
				return p.getItemInHand(h);
			}
		}
		return null;
	}

	/** The player picked a project on the screen (and, if it was opened on a marker, builds it there). */
	public static void escolher(ServerPlayer player, String id, long marco) {
		ItemStack livro = livro(player);
		Catalogo.Projeto projeto = Catalogo.porId(id).orElse(null);
		if (livro == null || projeto == null) {
			return;
		}
		livro.set(ModComponents.PROJETO, projeto.id());
		ServerLevel level = player.level();
		if (marco == Long.MIN_VALUE) {
			player.sendOverlayMessage(Component.translatable("arraial.livro.escolhido", projeto.nome().copy().withStyle(ChatFormatting.GOLD)));
			return;
		}
		BlockPos pos = BlockPos.of(marco);
		if (!pos.closerThan(player.blockPosition(), 10) || !(level.getBlockEntity(pos) instanceof CanteiroBlockEntity canteiro)) {
			return;
		}
		if (canteiro.iniciada() && !canteiro.concluida()) {
			player.sendOverlayMessage(Component.translatable("arraial.canteiro.obra_em_andamento").withStyle(ChatFormatting.RED));
			return;
		}
		CanteiroBlock.aplicar(level, player, canteiro, projeto);
		canteiro.plano(level).ifPresent(plano -> contorno(level, plano.area()));
	}

	/** A ring of sparks around the ground floor of where the building will stand, and its corners. */
	static void contorno(ServerLevel level, BoundingBox b) {
		double y = b.minY() + 1.1;
		for (int x = b.minX(); x <= b.maxX() + 1; x++) {
			level.sendParticles(ParticleTypes.END_ROD, x, y, b.minZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, x, y, b.maxZ() + 1, 1, 0, 0, 0, 0);
		}
		for (int z = b.minZ(); z <= b.maxZ() + 1; z++) {
			level.sendParticles(ParticleTypes.END_ROD, b.minX(), y, z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, b.maxX() + 1, y, z, 1, 0, 0, 0, 0);
		}
		for (int[] c : new int[][] {{b.minX(), b.minZ()}, {b.maxX() + 1, b.minZ()}, {b.minX(), b.maxZ() + 1}, {b.maxX() + 1, b.maxZ() + 1}}) {
			for (int h = 0; h <= b.getYSpan(); h++) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c[0], b.minY() + 1 + h, c[1], 1, 0, 0, 0, 0);
			}
		}
	}
}
