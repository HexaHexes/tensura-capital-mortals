package org.bitwisemadness.tensuracapitalmortals.datagen;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Writes the pre-built jigsaw template bytes from {@link CapitalTemplates},
 * keyed by their path inside the jar. The bytes are already gzip NBT in the
 * exact layout the retired script produced, so they go through the cache as
 * raw bytes - the same HashCache contract the JSON writer uses, which is what
 * keeps the committed templates stable under the CI drift gate.
 */
public final class NbtFilesProvider implements DataProvider {
    private final String name;
    private final PackOutput output;
    private final Map<String, byte[]> files;

    public NbtFilesProvider(String name, PackOutput output, Map<String, byte[]> files) {
        this.name = name;
        this.output = output;
        this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Path root = output.getOutputFolder();
        files.forEach((path, bytes) -> {
            try {
                cache.writeIfNeeded(root.resolve(path), bytes,
                        com.google.common.hash.Hashing.sha1().hashBytes(bytes));
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("cannot write template " + path, ex);
            }
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return name;
    }
}
