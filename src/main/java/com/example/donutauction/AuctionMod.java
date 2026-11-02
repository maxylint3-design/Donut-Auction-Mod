package com.example.donutauction;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Entry point. Fabric calls onInitializeClient() once when the game starts.
 * This is where we hook everything up.
 */
public class AuctionMod implements ClientModInitializer {
    public static final String MOD_ID = "donutauction";

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        // 1) Key to open the menu (default: Right Shift - change it in Options > Controls)
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "auction"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutauction.open",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RIGHT_SHIFT,
                category));

        // 2) Runs 20 times per second
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.gui.setScreen(new AuctionScreen());
                }
            }
            AuctionManager.tick();
        });

        // 3) Listen to server chat messages (this is how we spot "X paid you $Y")
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                AuctionManager.onChatLine(message.getString());
            }
        });

        // 4) Draw the auction box on screen
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "auction_box"),
                (graphics, tickCounter) -> AuctionHud.render(graphics));

        // 5) Commands. Named /fauction so it does not clash with the server's own commands.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("fauction")
                        // /fauction  -> open the menu
                        .executes(context -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> mc.gui.setScreen(new AuctionScreen()));
                            return 1;
                        })
                        // /fauction cancel
                        .then(ClientCommands.literal("cancel").executes(context -> {
                            AuctionManager.cancel();
                            return 1;
                        }))
                        // /fauction debug  -> shows chat lines containing "paid" that did not match
                        .then(ClientCommands.literal("debug").executes(context -> {
                            AuctionManager.setDebug(!AuctionManager.isDebug());
                            AuctionManager.info("Debug is now " + (AuctionManager.isDebug() ? "ON" : "OFF"));
                            return 1;
                        }))
                        // /fauction test <name> <amount>  -> pretend someone paid you (no real money moves)
                        .then(ClientCommands.literal("test")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .then(ClientCommands.argument("amount", StringArgumentType.word())
                                                .executes(context -> {
                                                    String name = StringArgumentType.getString(context, "name");
                                                    String amountText = StringArgumentType.getString(context, "amount");
                                                    long amount = Format.parseMoney(amountText);
                                                    if (amount <= 0) {
                                                        AuctionManager.info("Could not read the amount '" + amountText + "'. Try 5m or 500k.");
                                                    } else {
                                                        AuctionManager.handlePayment(name, amount);
                                                    }
                                                    return 1;
                                                }))))));
    }
}
