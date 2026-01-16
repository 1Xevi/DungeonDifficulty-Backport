package net.dungeon_difficulty.util;

import net.minecraft.util.Identifier;

public class Compat {
    public static class CIdentifier {
        public static Identifier of(String id) {
            return new Identifier(id);
        }

        public static Identifier of(String namespace, String path) {
            return new Identifier(namespace, path);
        }

        public static Identifier ofVanilla(String path) {
            return new Identifier("minecraft", path);
        }
    }
}