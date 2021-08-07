package dev.tigr.ares.fabric.impl.modules.player;

import dev.tigr.ares.core.feature.module.Category;
import dev.tigr.ares.core.feature.module.Module;
import dev.tigr.ares.core.setting.Setting;
import dev.tigr.ares.core.setting.settings.numerical.DoubleSetting;
import dev.tigr.ares.core.util.render.Color;
import dev.tigr.ares.fabric.event.client.OpenScreenEvent;
import dev.tigr.ares.fabric.mixin.accessors.ConnectScreenAccessor;
import dev.tigr.ares.fabric.mixin.accessors.DisconnectedScreenAccessor;
import dev.tigr.ares.fabric.mixin.accessors.ScreenAccessor;
import dev.tigr.simpleevents.listener.EventHandler;
import dev.tigr.simpleevents.listener.EventListener;
import dev.tigr.simpleevents.listener.Priority;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.math.MatrixStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * @author Tigermouthbear
 */
@Module.Info(name = "AutoReconnect", description = "Automatically reconnect at a specific interval", category = Category.PLAYER, alwaysListening = true)
public class AutoReconnect extends Module {
    private static final Constructor CONNECT_SCREEN_CONSTRUCTOR = Arrays.stream(ConnectScreen.class.getDeclaredConstructors()).findFirst().orElseGet(null);

    private final Setting<Double> delay = register(new DoubleSetting("Delay", 1, 0, 30));
    private ServerInfo serverInfo = null;

    @EventHandler
    public EventListener<OpenScreenEvent> openScreenEvent = new EventListener<>(Priority.HIGHEST, event -> {
        if(getEnabled() && event.getScreen() instanceof DisconnectedScreen && !(event.getScreen() instanceof Gui) && CONNECT_SCREEN_CONSTRUCTOR != null) {
            event.setCancelled(true);
            MC.setScreen(new Gui((DisconnectedScreen) event.getScreen()));
        }
    });

    @Override
    public void onTick() {
        serverInfo = MC.getCurrentServerEntry() != null ? MC.getCurrentServerEntry() : serverInfo;
    }

    public class Gui extends DisconnectedScreen {
        private final long endTime;

        public Gui(DisconnectedScreen disconnectedScreen) {
            super(
                    new MultiplayerScreen(new GameMenuScreen(true)),
                    ((ScreenAccessor) disconnectedScreen).getTitle(),
                    ((DisconnectedScreenAccessor) disconnectedScreen).getReason()
            );

            endTime = (long) (System.currentTimeMillis() + (delay.getValue() * 1000));
        }

        @Override
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
            super.render(matrixStack, mouseX, mouseY, partialTicks);

            long time = endTime - System.currentTimeMillis();
            if(time <= 0) {
                try {
                    ConnectScreen connectScreen = (ConnectScreen) CONNECT_SCREEN_CONSTRUCTOR.newInstance(this);
                    MC.disconnect();
                    MC.setCurrentServerEntry(serverInfo);
                    MC.setScreen(connectScreen);
                    ((ConnectScreenAccessor) connectScreen).connect(MC, ServerAddress.parse(serverInfo.address));
                } catch(InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

            String text = "Reconnecting in " + time + "ms";
            textRenderer.drawWithShadow(matrixStack, text, width / 2f - textRenderer.getWidth(text) / 2f, 2, Color.WHITE.getRGB());
        }
    }
}
