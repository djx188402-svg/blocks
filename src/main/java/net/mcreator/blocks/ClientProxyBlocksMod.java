package net.mcreator.blocks;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.model.obj.OBJLoader;

import net.minecraft.client.Minecraft;

import net.mcreator.blocks.client.DdsTextureResourcePack;

public class ClientProxyBlocksMod implements IProxyBlocksMod {
	@Override
	public void init(FMLInitializationEvent event) {
	}

	private static final DdsTextureResourcePack DDS_TEXTURE_RESOURCE_PACK = new DdsTextureResourcePack();

	@Override
	public void preInit(FMLPreInitializationEvent event) {
		OBJLoader.INSTANCE.addDomain("blocks");
		Minecraft minecraft = Minecraft.getMinecraft();
		if (!minecraft.defaultResourcePacks.contains(DDS_TEXTURE_RESOURCE_PACK)) {
			minecraft.defaultResourcePacks.add(DDS_TEXTURE_RESOURCE_PACK);
		}
	}

	@Override
	public void postInit(FMLPostInitializationEvent event) {
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
	}
}
