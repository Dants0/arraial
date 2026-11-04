package com.guilhermegoes.arraial.obra;

import com.guilhermegoes.arraial.Arraial;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The catalog of buildings the pedreiro knows.
 * Each entry points at a vanilla structure file: data/arraial/structure/<id>.nbt
 * (saved in-game with a Structure Block). Convention: the building's front/door
 * is on the structure's north side (z = 0) and layer y = 0 is the floor.
 */
public final class Catalogo {
	public record Projeto(String id) {
		public Identifier estrutura() {
			return Arraial.id(id);
		}

		public Component nome() {
			return Component.translatable("projeto.arraial." + id);
		}
	}

	public static final List<Projeto> PROJETOS = List.of(
			new Projeto("poco"),
			new Projeto("casebre"));

	public static Optional<Projeto> porId(String id) {
		return PROJETOS.stream().filter(p -> p.id().equals(id)).findFirst();
	}

	/** Next (or previous) project in the catalog; an empty id starts at the first. */
	public static Projeto vizinho(String atual, boolean anterior) {
		int i = -1;
		for (int k = 0; k < PROJETOS.size(); k++) {
			if (PROJETOS.get(k).id().equals(atual)) {
				i = k;
			}
		}
		int n = PROJETOS.size();
		int proximo = i < 0 ? 0 : Math.floorMod(i + (anterior ? -1 : 1), n);
		return PROJETOS.get(proximo);
	}

	private Catalogo() {
	}
}
