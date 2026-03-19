package net.mcreator.blocks.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.function.Function;

class DdsTextureAtlasSprite extends TextureAtlasSprite {
	private static final Logger LOGGER = LogManager.getLogger("blocks-dds");
	private final ResourceLocation spriteLocation;

	DdsTextureAtlasSprite(ResourceLocation spriteLocation) {
		super(spriteLocation.toString());
		this.spriteLocation = spriteLocation;
	}

	@Override
	public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
		return true;
	}

	@Override
	public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
		ResourceLocation ddsLocation = new ResourceLocation(this.spriteLocation.getNamespace(),
				"textures/" + this.spriteLocation.getPath() + ".dds");
		ResourceLocation ddsUppercaseLocation = new ResourceLocation(this.spriteLocation.getNamespace(),
				"textures/" + this.spriteLocation.getPath() + ".DDS");
		try {
			IResource resource;
			try {
				resource = manager.getResource(ddsLocation);
			} catch (Exception ignored) {
				resource = manager.getResource(ddsUppercaseLocation);
			}

			try (IResource closeableResource = resource) {
				DdsImage.DecodedDds decoded = DdsImage.decode(closeableResource.getInputStream());
				this.setIconWidth(decoded.width);
				this.setIconHeight(decoded.height);
				this.clearFramesTextureData();
				this.framesTextureData.add(decoded.mipmaps);
				return true;
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to load DDS texture {} (or {})", ddsLocation, ddsUppercaseLocation, e);
			return false;
		}
	}

	@Override
	public java.util.Collection<ResourceLocation> getDependencies() {
		return Collections.emptyList();
	}
}
