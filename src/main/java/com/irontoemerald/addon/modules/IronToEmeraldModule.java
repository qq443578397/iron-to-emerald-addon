package com.irontoemerald.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

public class IronToEmeraldModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> searchRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("search-range")
        .description("搜索村民的最大范围（格）")
        .defaultValue(10)
        .min(3)
        .max(30)
        .build()
    );

    private final Setting<Integer> tradeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("trade-delay")
        .description("每次交易后的延迟（刻）")
        .defaultValue(5)
        .min(0)
        .max(20)
        .build()
    );

    private MerchantEntity targetVillager = null;
    private boolean isTrading = false;
    private int cooldown = 0;

    public IronToEmeraldModule() {
        super(Categories.Misc, "iron-to-emerald", "自动用铁锭与铁匠村民换取绿宝石");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // 已在交易界面中
        if (mc.player.currentScreenHandler instanceof MerchantScreenHandler && isTrading) {
            handleTrading();
            return;
        }

        // 退出交易状态
        if (isTrading) {
            isTrading = false;
            targetVillager = null;
        }

        // 寻找目标村民
        if (targetVillager == null || !targetVillager.isAlive()) {
            findNearestBlacksmith();
            return;
        }

        // 与村民交互
        if (targetVillager != null && !isTrading) {
            interactWithVillager();
        }
    }

    private void findNearestBlacksmith() {
        if (mc.player == null) return;

        Box searchBox = mc.player.getBoundingBox().expand(searchRange.get());
        List<MerchantEntity> villagers = mc.world.getEntitiesByClass(
            MerchantEntity.class,
            searchBox,
            this::isBlacksmithVillager
        );

        targetVillager = villagers.stream()
            .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(mc.player)))
            .orElse(null);
    }

    private boolean isBlacksmithVillager(MerchantEntity villager) {
        String name = villager.getDisplayName().getString().toLowerCase();
        return name.contains("工具匠") || name.contains("武器匠") || name.contains("盔甲匠") ||
               name.contains("toolsmith") || name.contains("weaponsmith") || name.contains("armorer");
    }

    private void interactWithVillager() {
        if (targetVillager == null || mc.player == null) return;

        double distanceSq = mc.player.squaredDistanceTo(targetVillager);
        if (distanceSq > 9.0) {
            // 面向村民移动
            lookAtVillager();
            moveTowardsVillager();
            return;
        }

        lookAtVillager();
        mc.interactionManager.interactEntity(mc.player, targetVillager, Hand.MAIN_HAND);
        isTrading = true;
        cooldown = 10;
    }

    private void lookAtVillager() {
        if (targetVillager == null) return;
        double dx = targetVillager.getX() - mc.player.getX();
        double dz = targetVillager.getZ() - mc.player.getZ();
        double dy = targetVillager.getY() + targetVillager.getHeight() / 2 - mc.player.getEyeY();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
        
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private void moveTowardsVillager() {
        if (targetVillager == null) return;
        double dx = targetVillager.getX() - mc.player.getX();
        double dz = targetVillager.getZ() - mc.player.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0) {
            mc.player.setVelocity(dx / length * 0.5, mc.player.getVelocity().y, dz / length * 0.5);
        }
    }

    private void handleTrading() {
        if (!(mc.player.currentScreenHandler instanceof MerchantScreenHandler handler)) return;

        int ironCount = countIronIngots();
        if (ironCount == 0) {
            mc.player.closeHandledScreen();
            isTrading = false;
            targetVillager = null;
            info("铁锭耗尽，停止交易");
            return;
        }

        TradeOfferList offers = handler.getRecipes();
        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            ItemStack firstInput = offer.getAdjustedFirstBuyItem();
            ItemStack output = offer.getSellItem();

            if (firstInput.getItem() == Items.IRON_INGOT && output.getItem() == Items.EMERALD) {
                int requiredIron = firstInput.getCount();
                if (ironCount >= requiredIron) {
                    handler.onButtonClick(mc.player, i);
                    cooldown = tradeDelay.get();
                    info("使用了 %d 个铁锭，获得 %d 个绿宝石", requiredIron, output.getCount());
                    return;
                }
            }
        }

        // 无可用交易（村民需要补货），关闭界面找下一个
        mc.player.closeHandledScreen();
        isTrading = false;
        targetVillager = null;
        cooldown = 20;
    }

    private int countIronIngots() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.IRON_INGOT) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public void onDeactivate() {
        if (isTrading && mc.player != null) {
            mc.player.closeHandledScreen();
        }
        targetVillager = null;
        isTrading = false;
        cooldown = 0;
        super.onDeactivate();
    }
}
