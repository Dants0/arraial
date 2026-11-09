package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Era;
import com.guilhermegoes.arraial.vila.Estagio;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: what you see when looking at the arraial. A morador shows trade and village, what they are
 * doing, family, hunger, sickness and mood; the Cruzeiro shows stage, era, people, satisfaction,
 * treasury, mayor and crisis; a workstation shows its trade and who works there. The details come
 * from the server ({@code appendServerData}); each part can be switched off in Jade's settings.
 */
@WailaPlugin(Arraial.MOD_ID)
public class ArraialJade implements IWailaPlugin {
	static final Identifier MORADOR = Arraial.id("morador");
	static final Identifier CRUZEIRO = Arraial.id("cruzeiro");
	static final Identifier POSTO = Arraial.id("posto");

	@Override
	public void register(IWailaCommonRegistration r) {
		r.registerEntityDataProvider(MoradorDados.INSTANCE, MoradorEntity.class);
		r.registerBlockDataProvider(CruzeiroDados.INSTANCE, ArraialBlockEntity.class);
		r.registerBlockDataProvider(PostoDados.INSTANCE, Block.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration r) {
		r.registerEntityComponent(Morador.INSTANCE, MoradorEntity.class);
		r.registerBlockComponent(Cruzeiro.INSTANCE, com.guilhermegoes.arraial.vila.ArraialBlock.class);
		r.registerBlockComponent(Posto.INSTANCE, Block.class);
	}

	private static String texto(CompoundTag t, String k) {
		return t.getStringOr(k, "");
	}

	// ================================================================ the morador

	enum Morador implements IEntityComponentProvider {
		INSTANCE;


		@Override
		public void appendTooltip(ITooltip tip, EntityAccessor a, IPluginConfig config) {
			if (!(a.getEntity() instanceof MoradorEntity m)) {
				return;
			}
			CompoundTag t = a.getServerData();
			MutableComponent oficio = m.titulo().copy().withStyle(ChatFormatting.GOLD);
			if (!texto(t, "vila").isEmpty()) {
				oficio.append(Component.literal(" · " + texto(t, "vila")).withStyle(ChatFormatting.GRAY));
			}
			tip.add(oficio);
			a.decodeFromNbt(ComponentSerialization.TRUSTED_STREAM_CODEC, t.get("status")).ifPresent(c -> {
				if (!c.getString().isEmpty()) {
					tip.add(c.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
				}
			});
			if (m.crianca()) {
				tip.add(Component.translatable("arraial.jade.crianca", texto(t, "pai").isEmpty() ? "?" : texto(t, "pai"),
						texto(t, "mae").isEmpty() ? "?" : texto(t, "mae")).withStyle(ChatFormatting.LIGHT_PURPLE));
			} else if (!texto(t, "conjuge").isEmpty() || t.getIntOr("filhos", 0) > 0) {
				MutableComponent f = Component.translatable("arraial.jade.familia", texto(t, "sobrenome"));
				if (!texto(t, "conjuge").isEmpty()) {
					f.append(" · ").append(Component.translatable("arraial.jade.casado", texto(t, "conjuge")));
				}
				if (t.getIntOr("filhos", 0) > 0) {
					f.append(" · ").append(Component.translatable("arraial.jade.filhos", t.getIntOr("filhos", 0)));
				}
				tip.add(f.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			if (t.contains("humor")) {
				int h = t.getIntOr("humor", 50);
				MutableComponent estado = Component.translatable("arraial.jade.humor", h)
						.withStyle(h >= 65 ? ChatFormatting.GREEN : h >= 40 ? ChatFormatting.YELLOW : ChatFormatting.RED);
				if (t.getBooleanOr("fome", false)) {
					estado.append(Component.literal(" · ").append(Component.translatable("arraial.jade.fome")).withStyle(ChatFormatting.RED));
				}
				if (t.getBooleanOr("doente", false)) {
					estado.append(Component.literal(" · ").append(Component.translatable("arraial.jade.doente")).withStyle(ChatFormatting.RED));
				}
				tip.add(estado);
			}
		}

		@Override
		public Identifier getUid() {
			return MORADOR;
		}
	}

	enum MoradorDados implements IServerDataProvider<EntityAccessor> {
		INSTANCE;

		@Override
		public void appendServerData(CompoundTag t, EntityAccessor a) {
			if (!(a.getEntity() instanceof MoradorEntity m)) {
				return;
			}
			ArraialBlockEntity vila = m.arraial();
			if (vila != null) {
				t.putString("vila", vila.nomeCompleto());
			}
			t.put("status", a.encodeAsNbt(ComponentSerialization.TRUSTED_STREAM_CODEC, m.status()));
			t.putBoolean("fome", m.comFome());
			t.putBoolean("doente", m.doente());
			t.putInt("humor", m.pessoa().humor(m));
			t.putString("sobrenome", m.sobrenome());
			if (vila != null && a.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
				var todos = vila.moradores(level);
				for (MoradorEntity o : todos) {
					if (o.getUUID().equals(m.conjuge())) {
						t.putString("conjuge", o.nome());
					}
					if (m.crianca() && o.getUUID().equals(m.pai())) {
						t.putString("pai", o.nome());
					}
					if (m.crianca() && o.getUUID().equals(m.mae())) {
						t.putString("mae", o.nome());
					}
				}
				t.putInt("filhos", (int) todos.stream().filter(o -> o.filhoDe(m.getUUID())).count());
			}
		}

		@Override
		public Identifier getUid() {
			return MORADOR;
		}
	}

	// ================================================================ the Cruzeiro

	enum Cruzeiro implements IBlockComponentProvider {
		INSTANCE;


		@Override
		public void appendTooltip(ITooltip tip, BlockAccessor a, IPluginConfig config) {
			CompoundTag t = a.getServerData();
			if (texto(t, "nome").isEmpty()) {
				return;
			}
			tip.add(Component.literal(texto(t, "nome")).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
			if (t.getBooleanOr("missao", false)) {
				tip.add(Component.translatable("arraial.mapa.missao").withStyle(ChatFormatting.GOLD));
			}
			tip.add(Component.translatable("arraial.jade.vila", Estagio.de(t.getIntOr("estagio", 0)).nome(), Era.de(texto(t, "era")).nome(),
					t.getIntOr("populacao", 0)).withStyle(ChatFormatting.GRAY));
			int s = t.getIntOr("satisfacao", 60);
			tip.add(Component.translatable("arraial.jade.satisfacao", s, t.getIntOr("tesouro", 0))
					.withStyle(s >= 60 ? ChatFormatting.GREEN : s >= 35 ? ChatFormatting.YELLOW : ChatFormatting.RED));
			if (!texto(t, "prefeito").isEmpty()) {
				tip.add(Component.translatable("arraial.jade.prefeito", texto(t, "prefeito")).withStyle(ChatFormatting.AQUA));
			}
			a.decodeFromNbt(ComponentSerialization.TRUSTED_STREAM_CODEC, t.get("crise")).ifPresent(c ->
					tip.add(Component.translatable("arraial.jade.crise", c).withStyle(ChatFormatting.RED)));
		}

		@Override
		public Identifier getUid() {
			return CRUZEIRO;
		}
	}

	enum CruzeiroDados implements IServerDataProvider<BlockAccessor> {
		INSTANCE;

		@Override
		public void appendServerData(CompoundTag t, BlockAccessor a) {
			if (!(a.getBlockEntity() instanceof ArraialBlockEntity v)) {
				return;
			}
			t.putString("nome", v.nomeCompleto());
			t.putInt("estagio", v.estagio().ordinal());
			t.putString("era", v.era().id());
			t.putInt("populacao", v.populacao());
			t.putInt("satisfacao", v.satisfacao());
			t.putInt("tesouro", v.tesouro());
			t.putBoolean("missao", v.hispano());
			if (v.crise().tipo() != null) {
				t.put("crise", a.encodeAsNbt(ComponentSerialization.TRUSTED_STREAM_CODEC, v.crise().tipo().nome()));
			}
			if (v.prefeito() != null && a.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
				v.moradores(level).stream().filter(m -> m.getUUID().equals(v.prefeito())).findFirst()
						.ifPresent(m -> t.putString("prefeito", m.nome() + " " + m.sobrenome()));
			}
		}

		@Override
		public Identifier getUid() {
			return CRUZEIRO;
		}
	}

	// ================================================================ workstations

	enum Posto implements IBlockComponentProvider {
		INSTANCE;



		@Override
		public void appendTooltip(ITooltip tip, BlockAccessor a, IPluginConfig config) {
			Profissao p = Profissao.doPosto(a.getBlock());
			if (p == null) {
				return;
			}
			tip.add(Component.translatable("arraial.jade.posto", p.titulo(false)).withStyle(ChatFormatting.GOLD));
			String quem = texto(a.getServerData(), "trabalhador");
			tip.add((quem.isEmpty() ? Component.translatable("arraial.jade.posto_livre") : Component.translatable("arraial.jade.trabalhador", quem))
					.withStyle(ChatFormatting.GRAY));
		}

		@Override
		public Identifier getUid() {
			return POSTO;
		}
	}

	enum PostoDados implements IServerDataProvider<BlockAccessor> {
		INSTANCE;

		@Override
		public boolean shouldRequestData(BlockAccessor a) {
			return Profissao.doPosto(a.getBlock()) != null;
		}

		@Override
		public void appendServerData(CompoundTag t, BlockAccessor a) {
			var pos = a.getPosition();
			a.getLevel().getEntitiesOfClass(MoradorEntity.class, new AABB(pos).inflate(64), m -> pos.equals(m.posto())).stream().findFirst()
					.ifPresent(m -> t.putString("trabalhador", m.nome() + " " + m.sobrenome()));
		}

		@Override
		public Identifier getUid() {
			return POSTO;
		}
	}
}
