package Nivkida;


import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Itemidcollect.MODID)
public class Itemidcollect {
    public static final String MODID = "itemidcollect";

    public Itemidcollect() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
    }
}

