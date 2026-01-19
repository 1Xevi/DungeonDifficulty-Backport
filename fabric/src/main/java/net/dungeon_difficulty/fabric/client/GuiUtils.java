package net.dungeon_difficulty.fabric.client;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.dungeon_difficulty.config.ConfigServer; // Only needed for auto-save hook
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class GuiUtils {
    private static final Map<Object, List<String>> REGISTRY_CACHE = new ConcurrentHashMap<>();
    public static void clearCache() {
        REGISTRY_CACHE.clear();
    }


    @FunctionalInterface
    public interface EditorInjector<T> {
        void inject(BuilderContext<OptionGroup.Builder> ctx, T item);
    }

    public record BuilderContext<B>(
            B builder,
            Screen parent,
            Runnable refreshSelf) {

        public void refresh() {
            if (refreshSelf != null) refreshSelf.run();
        }
    }

    public static <T> OptionEventListener<T> onUpdate(Consumer<T> setter, Runnable refresher) {
        return (option, event) -> {
            if (event == OptionEventListener.Event.STATE_CHANGE) {
                setter.accept(option.pendingValue());
                refresher.run();
            }
        };
    }

    // generic screen builder
    public static <T> Screen createGeneric(
            Screen parent,
            Text title,
            T item,
            Consumer<T> saveConsumer,
            @Nullable Runnable deleteAction,
            Consumer<ConfigCategory.Builder> contentInjector,
            @Nullable Runnable onSaveAndClose
    ) {
        var categoryBuilder = ConfigCategory.createBuilder().name(title);
        contentInjector.accept(categoryBuilder);

        if (deleteAction != null) {
            categoryBuilder.option(ButtonOption.createBuilder()
                    .name(Text.literal("§c[X] Delete Entry"))
                    .description(OptionDescription.of(Text.literal("Permanently remove this entry.")))
                    .action((s, b) -> deleteAction.run())
                    .build());
        }

        return YetAnotherConfigLib.createBuilder()
                .title(title)
                .category(categoryBuilder.build())
                .save(() -> {
                    saveConsumer.accept(item);
                    if (onSaveAndClose != null) onSaveAndClose.run();
                })
                .build()
                .generateScreen(parent);
    }

    // generic list builder
    public static <T> void addGenericList(
            BuilderContext<ConfigCategory.Builder> ctx,
            String listTitle, String itemLabel,
            List<T> list,
            Supplier<T> constructor,
            Function<T, String> nameProvider,
            Function<T, String> descriptionProvider,
            EditorInjector<T> injector
    ) {
        var headerGroup = OptionGroup.createBuilder().name(Text.literal(listTitle)).collapsed(false);
        var category = ctx.builder();
        var parent = ctx.parent();

        headerGroup.option(ButtonOption.createBuilder()
                .name(Text.literal("§a[+] Add New " + itemLabel))
                .action((s, b) -> {
                    list.add(constructor.get());
                    AutoConfig.getConfigHolder(ConfigServer.class).save();
                    MinecraftClient.getInstance().setScreen(GuiBuilder.create(parent));
                })
                .build());

        category.group(headerGroup.build());

        for (int i = 0; i < list.size(); i++) {
            int finalI = i;
            T item = list.get(i);

            var itemGroup = OptionGroup.createBuilder()
                    .name(Text.literal(nameProvider.apply(item)))
                    .description(OptionDescription.of(Text.literal(descriptionProvider.apply(item))))
                    .collapsed(true);

            var itemCtx = new BuilderContext<>(itemGroup, parent, ctx.refreshSelf());

            injector.inject(itemCtx, item);

            itemGroup.option(ButtonOption.createBuilder()
                    .name(Text.literal("§c[X] Delete this entry"))
                    .action((s, b) -> {
                        list.remove(finalI);
                        AutoConfig.getConfigHolder(ConfigServer.class).save();
                        MinecraftClient.getInstance().setScreen(GuiBuilder.create(parent));
                    })
                    .build());

            category.group(itemGroup.build());
        }
    }

    // sublist button
    public static <T> void addSubListButton(
            BuilderContext<OptionGroup.Builder> ctx,
            String title, String label,
            List<T> list,
            Supplier<T> ctor,
            Function<T, String> namer,
            Function<T, String> descriptionProvider,
            EditorInjector<T> injector)
    {
        StringBuilder previewText = new StringBuilder("§7Contents:");
        if (list.isEmpty()) {
            previewText.append("\n§8(Empty)");
        } else {
            int limit = 5;
            for (int i = 0; i < Math.min(list.size(), limit); i++) {
                previewText.append("\n§7- ").append(namer.apply(list.get(i)));
            }
            if (list.size() > limit) {
                previewText.append("\n§8... and ").append(list.size() - limit).append(" more.");
            }
        }

        var builder = ctx.builder();
        var parent = ctx.parent();

        builder.option(ButtonOption.createBuilder()
                .name(Text.literal("§e[>] Edit " + title + " §7(" + list.size() + ")"))
                .description(OptionDescription.of(Text.literal(previewText.toString())))
                .action((s, b) -> MinecraftClient.getInstance().setScreen(
                        createGeneric(
                                s,
                                Text.literal(title),
                                list,
                                (saved) -> AutoConfig.getConfigHolder(ConfigServer.class).save(),
                                null,
                                (catBuilder) -> {
                                    var subCtx = new BuilderContext<>(catBuilder, s, () -> MinecraftClient.getInstance().setScreen(GuiBuilder.create(parent)));

                                    addGenericList(subCtx, title, label, list, ctor, namer, descriptionProvider, injector);
                                },
                                () -> MinecraftClient.getInstance().setScreen(GuiBuilder.create(parent))
                        )
                ))
                .build());
    }

    // helpers

    public static Option<Boolean> buildBool(String name, String desc, boolean def, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder().name(Text.literal(name))
                .description(OptionDescription.of(Text.literal(desc)))
                .binding(def, getter, setter)
                .controller(TickBoxControllerBuilder::create).build();
    }

    public static List<String> getRegistryIds(@Nullable Registry<?> registry) {
        if (registry == null) return List.of("");

        if (REGISTRY_CACHE.containsKey(registry)) {
            return REGISTRY_CACHE.get(registry);
        }

        List<String> ids = new ArrayList<>(registry.getIds().stream()
                .map(Identifier::toString).sorted().toList());
        ids.add(0, "");

        REGISTRY_CACHE.put(registry, ids);
        return ids;
    }

    public static List<String> getRegistryIds(RegistryKey<? extends Registry<?>> key) {
        if (REGISTRY_CACHE.containsKey(key)) {
            return REGISTRY_CACHE.get(key);
        }

        List<String> result;
        var client = MinecraftClient.getInstance();

        if (client.world != null) {
            result = getRegistryIds(client.world.getRegistryManager().get(key));
        } else if (key.equals(RegistryKeys.ENTITY_TYPE)) {
            result = getRegistryIds(Registries.ENTITY_TYPE);
        } else if (key.equals(RegistryKeys.ITEM)) {
            result = getRegistryIds(Registries.ITEM);
        } else if (key.equals(RegistryKeys.BLOCK)) {
            result = getRegistryIds(Registries.BLOCK);
        } else if (key.equals(RegistryKeys.DIMENSION)) {
            List<String> defaults = new ArrayList<>();
            defaults.add("");
            defaults.add("minecraft:overworld");
            defaults.add("minecraft:the_nether");
            defaults.add("minecraft:the_end");
            result = defaults;
        } else {
            try {
                var wrapper = BuiltinRegistries.createWrapperLookup().getWrapperOrThrow(key);
                List<String> ids = new ArrayList<>(wrapper.streamKeys()
                        .map(k -> k.getValue().toString())
                        .sorted()
                        .toList());
                ids.add(0, "");
                result = ids;
            } catch (Exception e) {
                result = new ArrayList<>(List.of(""));
            }
        }

        REGISTRY_CACHE.put(key, result);
        return result;
    }
}