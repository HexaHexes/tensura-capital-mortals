package org.bitwisemadness.tensuracapitalmortals.gametest;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.tensuracapitalmortals.TensuraCapitalMortals;
import org.bitwisemadness.tensuracapitalmortals.datagen.CapitalContent;
import org.bitwisemadness.tensuracapitalmortals.datagen.CapitalTemplates;

/**
 * Registry-level coverage: the three capitals are registered structures and
 * their central capital tags resolve. Worldgen placement itself is
 * biome-environmental and is verified in game.
 */
@GameTestHolder(TensuraCapitalMortals.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CapitalMortalsGameTests {

    private CapitalMortalsGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /** Humans, Elves and Dwarves capitals are registered and tagged. */
    @GameTest(template = "empty")
    public static void capitals_registered_and_tagged(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String faction : List.of("humans", "elves", "dwarves")) {
            ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> key =
                    ResourceKey.create(Registries.STRUCTURE,
                            ResourceLocation.fromNamespaceAndPath(TensuraCapitalMortals.MOD_ID, faction));
            helper.assertTrue(registry.getHolder(key).isPresent(),
                    "capital structure not registered: " + key.location());

            TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag = TagKey.create(Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath("tensura_faction_spawn", "capital/" + faction));
            var holders = registry.getTag(tag);
            helper.assertTrue(holders.isPresent() && holders.get().size() > 0,
                    "capital tag does not resolve: " + tag.location());
            boolean containsOwn = holders.orElseThrow().stream().anyMatch(h -> containsFaction(h, faction));
            helper.assertTrue(containsOwn, "capital tag " + tag.location() + " is missing the " + faction + " capital");
        }

        // Every datagen-produced file must ship and resolve on the live
        // classpath (issue #19); this also re-runs the table validation.
        CapitalContent.files().keySet().forEach(path -> requireResource(helper, path));
        CapitalTemplates.files().keySet().forEach(path -> requireResource(helper, path));
        helper.succeed();
    }

    private static void requireResource(GameTestHelper helper, String path) {
        // The capital tag anchors live in the tensura_faction_spawn
        // namespace, so the id derives from the path, never from MOD_ID.
        String relative = path.substring("data/".length());
        String namespace = relative.substring(0, relative.indexOf('/'));
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                namespace, relative.substring(relative.indexOf('/') + 1));
        helper.assertTrue(
                helper.getLevel().getServer().getResourceManager().getResource(id).isPresent(),
                "generated datapack file missing at runtime: " + path);
    }

    private static boolean containsFaction(Holder<net.minecraft.world.level.levelgen.structure.Structure> holder,
                                           String faction) {
        return holder.unwrapKey()
                .map(k -> k.location().getNamespace().equals(TensuraCapitalMortals.MOD_ID)
                        && k.location().getPath().equals(faction))
                .orElse(false);
    }
}
