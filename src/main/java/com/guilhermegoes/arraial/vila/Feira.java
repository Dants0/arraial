package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.trabalho.Demanda;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * The feira (GDD 6): local prices from supply and demand, smoothed day by day so they never jump
 * around, and a stall where the player buys from and sells to the arraial in réis.
 * <pre>
 * preço = clamp(base × (falta + n) / (estoque + n), base/2, base×3), then 60% yesterday + 40% today
 * </pre>
 * At the banca de feira (with a feirante there, by day):
 * <ul>
 * <li>empty hand: the price list; sneaking with an empty hand picks the next good to buy;</li>
 * <li>réis in hand: buy a lot of the chosen good (the money goes to the arraial's coffers);</li>
 * <li>a good in hand: sell it (up to 16) for 80% of the price, paid from the coffers.</li>
 * </ul>
 */
public final class Feira {
	public record Mercadoria(Item item, double base) {
	}

	public static final List<Mercadoria> TABELA = List.of(
			new Mercadoria(Items.BREAD, 2), new Mercadoria(ModItems.FARINHA, 1), new Mercadoria(ModItems.BEIJU, 2),
			new Mercadoria(ModItems.PACOCA, 4), new Mercadoria(ModItems.RAPADURA, 2), new Mercadoria(Items.WHEAT, 1),
			new Mercadoria(ModItems.MANDIOCA, 1), new Mercadoria(ModItems.MANIVA, 1), new Mercadoria(Items.COOKED_BEEF, 3),
			new Mercadoria(Items.BEEF, 2), new Mercadoria(Items.EGG, 1), new Mercadoria(Items.LEATHER, 3),
			new Mercadoria(Items.WOOL.white(), 2), new Mercadoria(Items.OAK_LOG, 2), new Mercadoria(Items.SPRUCE_LOG, 2),
			new Mercadoria(Items.BIRCH_LOG, 2), new Mercadoria(Items.OAK_PLANKS, 0.5), new Mercadoria(Items.BRICKS, 3),
			new Mercadoria(Items.MUD_BRICKS, 1), new Mercadoria(Items.COBBLESTONE, 0.5), new Mercadoria(Items.CLAY_BALL, 1),
			new Mercadoria(Items.TORCH, 0.5), new Mercadoria(Items.IRON_INGOT, 8), new Mercadoria(ModItems.REMEDIO, 8),
			new Mercadoria(ModItems.CUIA, 2), new Mercadoria(Items.SUGAR, 1),
			new Mercadoria(ModItems.CAFE_CEREJA, 1), new Mercadoria(ModItems.CAFE_TORRADO, 4), new Mercadoria(ModItems.CAFEZINHO, 3),
			new Mercadoria(ModItems.QUEIJO_COALHO, 3), new Mercadoria(ModItems.CHARQUE, 4), new Mercadoria(Items.GOLD_NUGGET, 2),
			new Mercadoria(com.guilhermegoes.arraial.registry.ModBlocks.TALHA_DOURADA.asItem(), 6), new Mercadoria(com.guilhermegoes.arraial.registry.ModBlocks.LAMPIAO.asItem(), 10), new Mercadoria(ModItems.CHAPEU_COURO, 12),
			new Mercadoria(ModItems.SAL, 2), new Mercadoria(ModItems.CHITA, 4));

	/** The good each player has picked at the stalls (not saved). */
	private static final Map<UUID, Integer> ESCOLHA = new HashMap<>();
	private static final int LOTE = 8;

	static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static double base(Item item) {
		for (Mercadoria m : TABELA) {
			if (m.item() == item) {
				return m.base();
			}
		}
		return -1;
	}

	public static double preco(ArraialBlockEntity a, Item item) {
		double b = base(item);
		return b < 0 ? -1 : a.precos.getOrDefault(id(item), b);
	}

	/** Once a morning: today's prices from what is short and what is piling up. */
	static void atualizarPrecos(ServerLevel level, ArraialBlockEntity a, int moradores) {
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());
		if (armazens.isEmpty()) {
			return;
		}
		Map<Item, Integer> inv = Vilas.inventario(armazens);
		Demanda.Resultado demanda = Demanda.calcular(level, a.getBlockPos());
		int n = Math.max(4, moradores);
		String meu = Registro.id(level.dimension().identifier().toString(), a.getBlockPos());
		long hoje = Vilas.dia(level);
		for (Mercadoria m : TABELA) {
			int estoque = inv.getOrDefault(m.item(), 0);
			int falta = demanda.falta().getOrDefault(m.item(), 0);
			double fator = Math.max(0.5, Math.min(3.0, (falta + n) / (double) (estoque + n)));
			// Neighbours with plenty bring the price down; closed roads and war push it up.
			double alvo = Economia.regional(meu, a.getBlockPos(), id(m.item()), m.base() * fator, m.base(), hoje);
			double antes = a.precos.getOrDefault(id(m.item()), m.base());
			a.precos.put(id(m.item()), Math.round((antes * 0.6 + alvo * 0.4) * 10) / 10.0);
		}
		a.setChanged();
	}

	private static String reais(double v) {
		return String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	/** A feirante is at the stall, by day. */
	private static boolean aberta(ServerLevel level, BlockPos banca) {
		return level.isBrightOutside() && !level.getEntitiesOfClass(MoradorEntity.class, new AABB(banca).inflate(6),
				m -> m.profissao() == Profissao.FEIRANTE).isEmpty();
	}

	/** Goods the arraial has to sell. */
	private static List<Mercadoria> aVenda(Map<Item, Integer> inv) {
		List<Mercadoria> l = new ArrayList<>();
		for (Mercadoria m : TABELA) {
			if (inv.getOrDefault(m.item(), 0) > 0) {
				l.add(m);
			}
		}
		return l;
	}

	/** A player uses the banca de feira. */
	public static void usar(ServerLevel level, BlockPos banca, Player p, ItemStack mao) {
		BlockPos vila = Vilas.maisProxima(level, banca);
		if (vila == null || !(level.getBlockEntity(vila) instanceof ArraialBlockEntity a)) {
			p.sendOverlayMessage(Component.translatable("arraial.feira.sem_vila"));
			return;
		}
		if (!aberta(level, banca)) {
			p.sendOverlayMessage(Component.translatable("arraial.feira.fechada").withStyle(ChatFormatting.GRAY));
			return;
		}
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, vila);
		Map<Item, Integer> inv = Vilas.inventario(armazens);
		List<Mercadoria> venda = aVenda(inv);
		int escolha = ESCOLHA.getOrDefault(p.getUUID(), 0);
		Mercadoria escolhida = venda.isEmpty() ? null : venda.get(Math.floorMod(escolha, venda.size()));

		if (mao.isEmpty() && p.isShiftKeyDown()) {
			if (escolhida == null) {
				p.sendSystemMessage(Component.translatable("arraial.feira.vazia").withStyle(ChatFormatting.GRAY));
				return;
			}
			ESCOLHA.put(p.getUUID(), escolha + 1);
			Mercadoria m = venda.get(Math.floorMod(escolha + 1, venda.size()));
			p.sendOverlayMessage(Component.translatable("arraial.feira.escolhida", m.item().getDefaultInstance().getHoverName(),
					reais(preco(a, m.item()) * desconto(a, p)), inv.getOrDefault(m.item(), 0)).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (mao.isEmpty()) {
			p.sendSystemMessage(Component.translatable("arraial.feira.titulo", a.nomeCompleto(), a.tesouro).withStyle(ChatFormatting.GOLD));
			for (Mercadoria m : venda) {
				MutableComponent linha = Component.literal(m == escolhida ? " ▶ " : " • ").append(m.item().getDefaultInstance().getHoverName())
						.append(Component.literal(" — " + reais(preco(a, m.item()) * desconto(a, p)) + " réis (" + inv.get(m.item()) + ")"));
				p.sendSystemMessage(linha.withStyle(m == escolhida ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
			}
			p.sendSystemMessage(Component.translatable("arraial.feira.dica").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (mao.is(ModItems.REIS)) {
			// Buy a lot of the chosen good.
			if (escolhida == null) {
				p.sendSystemMessage(Component.translatable("arraial.feira.vazia").withStyle(ChatFormatting.GRAY));
				return;
			}
			double unidade = preco(a, escolhida.item()) * desconto(a, p);
			int qtd = Math.min(LOTE, inv.getOrDefault(escolhida.item(), 0));
			while (qtd > 0 && Math.ceil(unidade * qtd) > mao.getCount()) {
				qtd--;
			}
			if (qtd == 0) {
				p.sendOverlayMessage(Component.translatable("arraial.feira.sem_dinheiro", reais(unidade)).withStyle(ChatFormatting.RED));
				return;
			}
			int custo = (int) Math.ceil(unidade * qtd);
			int pegou = Vilas.retirar(armazens, escolhida.item(), qtd);
			mao.shrink(custo);
			a.mudarTesouro(custo);
			ItemStack compra = new ItemStack(escolhida.item(), pegou);
			if (!p.getInventory().add(compra)) {
				net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), compra);
			}
			a.mudarReputacao(p.getUUID(), 1);
			level.playSound(null, banca, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.2F);
			p.sendSystemMessage(Component.translatable("arraial.feira.comprou", pegou, escolhida.item().getDefaultInstance().getHoverName(), custo)
					.withStyle(ChatFormatting.GREEN));
			return;
		}
		// Supply orders: paper to see, sign or check one; the ordered good counts toward it.
		if (mao.is(Items.PAPER)) {
			Comercio.encomenda(level, a, p, mao);
			return;
		}
		if (Comercio.entregarEncomenda(level, a, p, mao, armazens)) {
			return;
		}
		double b = base(mao.getItem());
		if (b < 0) {
			p.sendOverlayMessage(Component.translatable("arraial.feira.nao_compra", mao.getHoverName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		// Sell to the arraial, paid from the coffers at 80% of the price.
		double unidade = preco(a, mao.getItem()) * 0.8 / desconto(a, p);
		int qtd = Math.min(16, mao.getCount());
		while (qtd > 0 && (int) Math.floor(unidade * qtd) > a.tesouro) {
			qtd--;
		}
		int pago = (int) Math.floor(unidade * qtd);
		if (qtd == 0 || pago == 0) {
			p.sendOverlayMessage(Component.translatable("arraial.feira.sem_tesouro", a.tesouro).withStyle(ChatFormatting.RED));
			return;
		}
		Item item = mao.getItem();
		int sobrou = Vilas.depositar(armazens, item, qtd);
		int vendido = qtd - sobrou;
		pago = (int) Math.floor(unidade * vendido);
		mao.shrink(vendido);
		a.mudarTesouro(-pago);
		ItemStack reis = new ItemStack(ModItems.REIS, pago);
		if (!p.getInventory().add(reis)) {
			net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), reis);
		}
		a.mudarReputacao(p.getUUID(), 1);
		level.playSound(null, banca, SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.6F, 1.0F);
		if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
			Conquistas.dar(sp, "bom_negocio");
		}
		p.sendSystemMessage(Component.translatable("arraial.feira.vendeu", vendido, item.getDefaultInstance().getHoverName(), pago)
				.withStyle(ChatFormatting.GREEN));
	}

	/** Friends of the arraial pay less (and get more): up to 20% either way with reputation. */
	private static double desconto(ArraialBlockEntity a, Player p) {
		double festa = a.festa().agora() == Festa.Tipo.FEIRA_GRANDE ? 0.85 : 1.0; // the feira grande: bargains
		if (a.politica().cidadao(p.getUUID())) {
			festa *= 0.9; // honorary citizens pay less
		}
		return (1.0 - a.reputacao(p.getUUID()) / 500.0) * festa;
	}

	private Feira() {
	}
}
