package Nivkida.Client;

import Nivkida.Itemidcollect;
import Nivkida.UI.ItemCollectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Itemidcollect.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (ClientEvents.OPEN_GUI == null) return;
        // consumeClick() - одноразовое срабатывание при нажатии
        if (ClientEvents.OPEN_GUI.consumeClick()) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().setScreen(new ItemCollectorScreen());
                }
            });
        }
    }
}