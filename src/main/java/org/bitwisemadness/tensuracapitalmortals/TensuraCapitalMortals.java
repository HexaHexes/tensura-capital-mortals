package org.bitwisemadness.tensuracapitalmortals;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.bitwisemadness.tensuracapitalmortals.datagen.CapitalContent;
import org.bitwisemadness.tensuracapitalmortals.datagen.CapitalTemplates;
import org.bitwisemadness.tensuracapitalmortals.datagen.JsonFilesProvider;
import org.bitwisemadness.tensuracapitalmortals.datagen.NbtFilesProvider;
import org.slf4j.Logger;

/**
 * Capital cities for the mortal races - Humans, Elves, Dwarves.
 *
 * <p>The cities ship as bundled datapack content ({@code data/} in this jar)
 * and register themselves into the central capital tags owned by the
 * {@code tensura_faction_spawn} mod, which handles arrival and placement. This
 * mod adds no runtime behaviour of its own yet; it exists so the capitals
 * install like any mod and version with the rest of the ecosystem.</p>
 */
@Mod(TensuraCapitalMortals.MOD_ID)
public final class TensuraCapitalMortals {

    public static final String MOD_ID = "tensura_capital_mortals";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraCapitalMortals(IEventBus modBus) {
        modBus.addListener(this::gatherData);
        LOGGER.info("Tensura Capital: Mortals initialized");
    }

    /**
     * Feeds the shipped datapack - the jigsaw structures, structure sets,
     * template pools, capital tag anchors ({@link CapitalContent}) and the
     * twelve template pieces ({@link CapitalTemplates}), ported from the
     * hand-authored originals and the retired generator script - into
     * datagen (issue #19).
     */
    private void gatherData(GatherDataEvent event) {
        if (!event.includeServer()) {
            return;
        }
        event.addProvider(new JsonFilesProvider("capital_json",
                event.getGenerator().getPackOutput(), CapitalContent.files()));
        event.addProvider(new NbtFilesProvider("capital_templates",
                event.getGenerator().getPackOutput(), CapitalTemplates.files()));
    }
}
