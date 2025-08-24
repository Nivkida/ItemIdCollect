package Nivkida.Handler;

import Nivkida.Itemidcollect;
import Nivkida.UI.ItemCollectorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Itemidcollect.MODID, value = Dist.CLIENT)
public class KeybindHandler {
    private static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.itemidcollect.open_gui",
            InputConstants.KEY_I,
            "key.categories.misc"
    );

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (OPEN_GUI.consumeClick()) {
            Minecraft.getInstance().setScreen(new ItemCollectorScreen());
        }
    }
}
