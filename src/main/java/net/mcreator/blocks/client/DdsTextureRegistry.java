package net.mcreator.blocks.client;

import net.mcreator.blocks.BlocksMod;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DdsTextureRegistry {
	private static final Logger LOGGER = LogManager.getLogger("blocks-dds");
	private final Set<ResourceLocation> ddsSprites = new LinkedHashSet<>();
	private boolean initialized;

	public void register() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onTextureStitch(TextureStitchEvent.Pre event) {
		if (!"textures".equals(event.getMap().getBasePath())) {
			return;
		}

		if (!this.initialized) {
			this.initialized = true;
			discoverDdsTextures();
		}

		replaceSprites(event.getMap());
	}

	private void replaceSprites(TextureMap map) {
		Map<String, TextureAtlasSprite> registered = getRegisteredSprites(map);
		if (registered == null) {
			for (ResourceLocation sprite : this.ddsSprites) {
				map.setTextureEntry(new DdsTextureAtlasSprite(sprite));
			}
			return;
		}

		for (ResourceLocation sprite : this.ddsSprites) {
			registered.put(sprite.toString(), new DdsTextureAtlasSprite(sprite));
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, TextureAtlasSprite> getRegisteredSprites(TextureMap map) {
		try {
			return ObfuscationReflectionHelper.getPrivateValue(TextureMap.class, map, "field_110574_e", "mapRegisteredSprites");
		} catch (Exception e) {
			LOGGER.warn("Cannot replace registered sprites directly, fallback to setTextureEntry", e);
			return null;
		}
	}

	private void discoverDdsTextures() {
		ModContainer modContainer = Loader.instance().getIndexedModList().get(BlocksMod.MODID);
		if (modContainer != null) {
			CraftingHelper.findFiles(modContainer, "assets/" + BlocksMod.MODID + "/textures", null, this::collectDds, true, true);
		}

		discoverFromSourceTree();
		LOGGER.info("Registered {} DDS texture(s)", Integer.valueOf(this.ddsSprites.size()));
	}

	private void discoverFromSourceTree() {
		Path sourceRoot = new File("src/main/resources/assets/" + BlocksMod.MODID + "/textures").toPath();
		if (!Files.isDirectory(sourceRoot)) {
			return;
		}

		try {
			Files.walk(sourceRoot).forEach(path -> collectFromPath(sourceRoot, path));
		} catch (Exception e) {
			LOGGER.warn("Failed to scan DDS textures in source tree", e);
		}
	}

	private void collectFromPath(Path root, Path path) {
		if (!Files.isRegularFile(path)) {
			return;
		}

		String relative = root.relativize(path).toString().replace('\\', '/');
		String lower = relative.toLowerCase(java.util.Locale.ROOT);
		if (!lower.endsWith(".dds")) {
			return;
		}

		String texturePath = relative.substring(0, relative.length() - 4);
		this.ddsSprites.add(new ResourceLocation(BlocksMod.MODID, texturePath));
	}

	private Boolean collectDds(Path root, Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return Boolean.TRUE;
		}

		collectFromPath(root, path);
		return Boolean.TRUE;
	}
}
