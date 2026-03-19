package net.mcreator.blocks.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Function;

class DdsTextureAtlasSprite extends TextureAtlasSprite {
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
		try (IResource resource = manager.getResource(ddsLocation)) {
			DdsImage.DecodedDds decoded = DdsImage.decode(resource.getInputStream());
			this.setIconWidth(decoded.width);
			this.setIconHeight(decoded.height);
			this.clearFramesTextureData();
			this.framesTextureData.add(decoded.mipmaps);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	public java.util.Collection<ResourceLocation> getDependencies() {
		return Collections.emptyList();
	}
}
