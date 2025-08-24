package Nivkida.Client;

import Nivkida.Itemidcollect;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Itemidcollect.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents {
    public static KeyMapping OPEN_GUI;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_GUI = new KeyMapping(
                "key.itemidcollect.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L, // клавиша по умолчанию: L
                "key.categories.misc"
        );
        event.register(OPEN_GUI);
    }
}