package net.mcreator.blocks.client;

import net.mcreator.blocks.BlocksMod;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashSet;
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

		TextureMap map = event.getMap();
		for (ResourceLocation sprite : this.ddsSprites) {
			map.setTextureEntry(new DdsTextureAtlasSprite(sprite));
		}
	}

	private void discoverDdsTextures() {
		ModContainer modContainer = Loader.instance().getIndexedModList().get(BlocksMod.MODID);
		if (modContainer == null) {
			LOGGER.warn("Cannot scan DDS textures: mod container not found");
			return;
		}

		CraftingHelper.findFiles(modContainer, "assets/" + BlocksMod.MODID + "/textures", null, this::collectDds, true, true);
		LOGGER.info("Registered {} DDS texture(s)", Integer.valueOf(this.ddsSprites.size()));
	}

	private Boolean collectDds(Path root, Path path) {
		if (path == null || !java.nio.file.Files.isRegularFile(path)) {
			return Boolean.TRUE;
		}

		String relative = root.relativize(path).toString().replace('\\', '/');
		if (!relative.endsWith(".dds")) {
			return Boolean.TRUE;
		}

		String texturePath = relative.substring(0, relative.length() - 4);
		this.ddsSprites.add(new ResourceLocation(BlocksMod.MODID, texturePath));
		return Boolean.TRUE;
	}
}
