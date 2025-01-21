package net.ippc.nvutils.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NvutilsClient implements ClientModInitializer {

    private static final double MAX_SPEED_BLOCKS_PER_TICK = 1.4;
    private static final long ALTITUDE_CHANGE_COOLDOWN_MS = 100L;

    private boolean isElytraFlyActive = false;
    private long lastAltitudeChangeTime = 0L;

    private long[] tickTimes = new long[100];
    private int tickIndex = 0;
    private long lastTickTime = System.nanoTime();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> trackServerTick());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                handleElytraFly(client.player, client.player.input);
                handleNoFall(client.player);
            }
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> renderModInfo(context));
    }

    private void trackServerTick() {
        long currentTime = System.nanoTime();
        long tickDuration = currentTime - lastTickTime;

        tickTimes[tickIndex] = tickDuration;
        tickIndex = (tickIndex + 1) % tickTimes.length;
        lastTickTime = currentTime;
    }

    private String getServerTPS() {
        long totalTickTime = 0;
        int validTicks = 0;

        for (long tickTime : tickTimes) {
            if (tickTime > 0) {
                totalTickTime += tickTime;
                validTicks++;
            }
        }

        if (validTicks == 0) {
            return "Calculating...";
        }

        double averageTickTime = totalTickTime / (double) validTicks;
        double tps = 1_000_000_000.0 / averageTickTime;

        tps = Math.min(tps, 20.0);

        String tpsColor;
        if (tps >= 18.0) {
            tpsColor = "§a";
        } else if (tps >= 14.0) {
            tpsColor = "§e";
        } else if (tps >= 7.0) {
            tpsColor = "§c";
        } else {
            tpsColor = "§4";
        }

        return tpsColor + String.format("%.1f", tps);
    }

    private void handleElytraFly(ClientPlayerEntity player, Input input) {
        if (player.isFallFlying()) {
            if (!isElytraFlyActive) {
                isElytraFlyActive = true;
                sendChatMessage(player, "§e[NV]§7 Elytraflight is now §aenabled§7.");
            }
            applyElytraFlyLogic(player, input);
        } else {
            if (isElytraFlyActive) {
                isElytraFlyActive = false;
                sendChatMessage(player, "§e[NV]§7 Elytraflight is now §cdisabled§7.");
            }
        }
    }

    private void applyElytraFlyLogic(ClientPlayerEntity player, Input input) {
        long currentTime = System.currentTimeMillis();

        if (input.jumping && currentTime - lastAltitudeChangeTime >= ALTITUDE_CHANGE_COOLDOWN_MS) {
            player.setVelocity(player.getVelocity().add(0, 0.05, 0));
            lastAltitudeChangeTime = currentTime;
        } else if (input.sneaking && currentTime - lastAltitudeChangeTime >= ALTITUDE_CHANGE_COOLDOWN_MS) {
            player.setVelocity(player.getVelocity().add(0, -0.05, 0));
            lastAltitudeChangeTime = currentTime;
        }

        Vec3d movement = calculateHorizontalMovement(player, input);
        player.setVelocity(movement.x, player.getVelocity().y, movement.z);

        player.velocityModified = false;
    }

    private void handleNoFall(ClientPlayerEntity player) {
        if (player.fallDistance >= 2.0f) {
            player.fallDistance = 0.0f;
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }

    private Vec3d calculateHorizontalMovement(ClientPlayerEntity player, Input input) {
        Vec3d movement = Vec3d.ZERO;

        if (input.pressingForward || input.pressingBack || input.pressingLeft || input.pressingRight) {
            float yaw = (float) Math.toRadians(player.getYaw());

            double forward = input.pressingForward ? 1 : (input.pressingBack ? -1 : 0);
            double strafe = input.pressingLeft ? 1 : (input.pressingRight ? -1 : 0);

            movement = new Vec3d(
                    forward * -Math.sin(yaw) + strafe * Math.cos(yaw),
                    0,
                    forward * Math.cos(yaw) + strafe * -Math.sin(yaw)
            ).normalize().multiply(MAX_SPEED_BLOCKS_PER_TICK);
        }

        return movement;
    }

    private void sendChatMessage(ClientPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }

    private void renderModInfo(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            String modName = "§eNV.Utils";
            String tps = "§7TPS: " + getServerTPS();
            String location = "§7Pos:§f " + getPlayerLocation(client.player);

            int x = 2;
            int y = 2;

            context.drawText(client.textRenderer, modName, x, y, 0xFFFFFF, false);
            context.drawText(client.textRenderer, tps, x, y + 10, 0xFFFFFF, false);
            context.drawText(client.textRenderer, location, x, y + 20, 0xFFFFFF, false);
        }
    }

    private String getPlayerLocation(ClientPlayerEntity player) {
        if (player != null) {
            BlockPos pos = player.getBlockPos();
            return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }
        return "Unknown";
    }
}