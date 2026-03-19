package net.mcreator.blocks.client;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds support for loading DDS textures as PNG resources at runtime.
 */
public class DdsTextureResourcePack implements IResourcePack {
	private static final String PACK_NAME = "blocks_dds_bridge";
	private static final String PACK_MC_META = "{\"pack\":{\"description\":\"DDS texture bridge\",\"pack_format\":3}}";
	private final Map<ResourceLocation, byte[]> pngCache = new ConcurrentHashMap<>();

	@Override
	public InputStream getInputStream(ResourceLocation location) throws IOException {
		if ("pack.mcmeta".equals(location.getResourcePath())) {
			return new ByteArrayInputStream(PACK_MC_META.getBytes(StandardCharsets.UTF_8));
		}

		if (!shouldHandle(location)) {
			throw new IOException("Unsupported resource: " + location);
		}

		byte[] pngBytes = pngCache.computeIfAbsent(location, this::convertDdsToPng);
		if (pngBytes == null) {
			throw new IOException("DDS resource not found: " + location);
		}
		return new ByteArrayInputStream(pngBytes);
	}

	@Override
	public boolean resourceExists(ResourceLocation location) {
		if ("pack.mcmeta".equals(location.getResourcePath())) {
			return true;
		}

		if (!shouldHandle(location)) {
			return false;
		}

		String ddsPath = toDdsClasspathPath(location);
		return DdsTextureResourcePack.class.getClassLoader().getResource(ddsPath) != null;
	}

	@Override
	public Set<String> getResourceDomains() {
		return ImmutableSet.of("blocks");
	}

	@Nullable
	@Override
	public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer metadataSerializer, String metadataSectionName) {
		return null;
	}

	@Override
	public BufferedImage getPackImage() {
		return null;
	}

	@Override
	public String getPackName() {
		return PACK_NAME;
	}

	private boolean shouldHandle(ResourceLocation location) {
		return location.getResourcePath().endsWith(".png");
	}

	private String toDdsClasspathPath(ResourceLocation location) {
		String ddsPath = location.getResourcePath().substring(0, location.getResourcePath().length() - 4) + ".dds";
		return "assets/" + location.getResourceDomain() + "/" + ddsPath;
	}

	private byte[] convertDdsToPng(ResourceLocation location) {
		String ddsPath = toDdsClasspathPath(location);
		try (InputStream ddsStream = DdsTextureResourcePack.class.getClassLoader().getResourceAsStream(ddsPath)) {
			if (ddsStream == null) {
				return null;
			}

			BufferedImage image = ImageIO.read(ddsStream);
			if (image == null) {
				throw new IOException("Unsupported DDS format: " + ddsPath);
			}

			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "png", output)) {
				throw new IOException("Failed to write PNG bytes for " + ddsPath);
			}
			return output.toByteArray();
		} catch (IOException exception) {
			throw new RuntimeException("Failed to convert DDS texture " + ddsPath, exception);
		}
	}
}
