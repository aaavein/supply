package net.aaavein.supply;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.*;
import java.util.function.Predicate;

@EventBusSubscriber(modid = Supply.MODID, value = Dist.CLIENT)
public class Supplier {

    // supply:throwables
    private static final TagKey<Item> THROWABLE_TAG = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("supply", "throwables"));

    private static final List<Rule> RULES = new ArrayList<>();

    // state tracking
    private static ItemStack lastMainHand = ItemStack.EMPTY;
    private static ItemStack lastOffHand = ItemStack.EMPTY;
    private static int lastSlot = -1;

    private static long lastSupplyTime = 0;
    private static final long COOLDOWN_MS = 100;

    private static class SupplyResult {
        enum Status { SUCCESS, FAILED_NO_MATCH, FAILED_COOLDOWN }
        final Status status;
        final ItemStack targetStack;

        SupplyResult(Status status, ItemStack targetStack) {
            this.status = status;
            this.targetStack = targetStack;
        }
    }

    static {
        RULES.add(new EqualStackRule());
        RULES.add(new EqualItemRule());
        RULES.add(new FoodRule());
        RULES.add(new BlockRule());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        LocalPlayer player = mc.player;

        // inventory is open
        if (mc.screen != null) {
            updateCache(player);
            return;
        }

        int currentSlot = player.getInventory().selected;

        // hotbar slot changed
        if (currentSlot != lastSlot) {
            updateCache(player);
            return;
        }

        boolean isDropping = mc.options.keyDrop.isDown();
        boolean isSwapping = mc.options.keySwapOffhand.isDown(); // Check for 'F' key

        // swap key safety
        if (isSwapping) {
            updateCache(player);
            return;
        }

        // drop key safety
        if (!Demand.INSTANCE.supplyOnDrop.get() && isDropping) {
            updateCache(player);
            return;
        }

        // using item
        if (player.isUsingItem()) {
            updateCache(player);
            return;
        }

        ItemStack currentMain = player.getMainHandItem();
        ItemStack currentOff = player.getOffhandItem();

        boolean mainHandNeedsUpdate = true;
        boolean offHandNeedsUpdate = true;

        // check main hand
        if (shouldSupply(player, lastMainHand, currentMain, isDropping)) {
            SupplyResult result = performSupply(player, InteractionHand.MAIN_HAND, lastMainHand);

            if (result.status == SupplyResult.Status.SUCCESS) {
                lastMainHand = result.targetStack.copy();
                mainHandNeedsUpdate = false;
            } else if (result.status == SupplyResult.Status.FAILED_COOLDOWN) {
                mainHandNeedsUpdate = false;
            }
        }

        // check offhand
        if (Demand.INSTANCE.supplyOffhand.get() && shouldSupply(player, lastOffHand, currentOff, isDropping)) {
            SupplyResult result = performSupply(player, InteractionHand.OFF_HAND, lastOffHand);
            if (result.status == SupplyResult.Status.SUCCESS) {
                lastOffHand = result.targetStack.copy();
                offHandNeedsUpdate = false;
            } else if (result.status == SupplyResult.Status.FAILED_COOLDOWN) {
                offHandNeedsUpdate = false;
            }
        }

        if (mainHandNeedsUpdate) lastMainHand = player.getMainHandItem().copy();
        if (offHandNeedsUpdate) lastOffHand = player.getOffhandItem().copy();
        lastSlot = player.getInventory().selected;
    }

    private static void updateCache(LocalPlayer player) {
        lastMainHand = player.getMainHandItem().copy();
        lastOffHand = player.getOffhandItem().copy();
        lastSlot = player.getInventory().selected;
    }

    private static boolean shouldSupply(LocalPlayer player, ItemStack oldStack, ItemStack newStack, boolean isDropping) {
        if (oldStack.isEmpty()) return false;

        // throwable check
        if (!Demand.INSTANCE.supplyThrowables.get() && oldStack.is(THROWABLE_TAG)) {
            if (!isDropping) {
                return false;
            }
        }

        // loyalty check
        if (!Demand.INSTANCE.supplyLoyalty.get()) {
            var registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var loyalty = registry.getOrThrow(Enchantments.LOYALTY);
            if (oldStack.getEnchantmentLevel(loyalty) > 0) {
                if (!isDropping) {
                    return false;
                }
            }
        }

        // item ran out
        if (newStack.isEmpty()) return true;

        // item changed
        if (Demand.INSTANCE.supplyOnChange.get()) {
            return !ItemStack.isSameItem(oldStack, newStack);
        }

        return false;
    }

    private static SupplyResult performSupply(LocalPlayer player, InteractionHand hand, ItemStack historyStack) {
        if (System.currentTimeMillis() - lastSupplyTime < COOLDOWN_MS) {
            return new SupplyResult(SupplyResult.Status.FAILED_COOLDOWN, ItemStack.EMPTY);
        }

        Inventory inv = player.getInventory();

        for (Rule rule : RULES) {
            if (!rule.isActive() || !rule.matches(player, historyStack)) continue;

            int slot = rule.findMatchingStack(inv, historyStack);
            if (slot != -1) {
                if (hand == InteractionHand.MAIN_HAND && slot == inv.selected) continue;

                ItemStack foundStack = inv.getItem(slot).copy();

                doSwap(player, hand, slot);

                lastSupplyTime = System.currentTimeMillis();
                if (Demand.INSTANCE.playSound.get()) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_INSERT, 1.0F, 0.5F));
                }

                return new SupplyResult(SupplyResult.Status.SUCCESS, foundStack);
            }
        }
        return new SupplyResult(SupplyResult.Status.FAILED_NO_MATCH, ItemStack.EMPTY);
    }

    private static void doSwap(LocalPlayer player, InteractionHand hand, int sourceSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return;

        if (hand == InteractionHand.MAIN_HAND) {
            if (Inventory.isHotbarSlot(sourceSlot)) {
                player.getInventory().selected = sourceSlot;
            } else {
                player.connection.send(new ServerboundPickItemPacket(sourceSlot));
            }
        } else {
            int containerSlot = mapPlayerSlotToContainerSlot(sourceSlot);
            mc.gameMode.handleInventoryMouseClick(0, containerSlot, 0, ClickType.PICKUP, player);
            mc.gameMode.handleInventoryMouseClick(0, InventoryMenu.SHIELD_SLOT, 0, ClickType.PICKUP, player);
            mc.gameMode.handleInventoryMouseClick(0, containerSlot, 0, ClickType.PICKUP, player);
        }
    }

    private static int mapPlayerSlotToContainerSlot(int playerSlot) {
        if (playerSlot >= 0 && playerSlot < 9) return 36 + playerSlot;
        else if (playerSlot >= 9 && playerSlot < 36) return playerSlot;
        return -1;
    }

    // rules
    public abstract static class Rule {
        abstract boolean isActive();
        abstract boolean matches(LocalPlayer player, ItemStack oldStack);
        abstract int findMatchingStack(Inventory playerInventory, ItemStack oldStack);

        protected int iterateInventory(Inventory playerInventory, Predicate<ItemStack> predicate) {
            for (int i = 0; i < 9; i++) if (predicate.test(playerInventory.getItem(i))) return i;
            for (int i = 9; i < 36; i++) if (predicate.test(playerInventory.getItem(i))) return i;
            return -1;
        }
    }

    public static class EqualStackRule extends Rule {
        @Override boolean isActive() { return Demand.INSTANCE.supplyCopies.get(); }
        @Override boolean matches(LocalPlayer player, ItemStack oldStack) { return true; }
        @Override int findMatchingStack(Inventory inv, ItemStack oldStack) {
            return iterateInventory(inv, stack -> !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, oldStack));
        }
    }

    public static class EqualItemRule extends Rule {
        @Override boolean isActive() { return Demand.INSTANCE.supplyItems.get(); }
        @Override boolean matches(LocalPlayer player, ItemStack oldStack) { return true; }
        @Override int findMatchingStack(Inventory inv, ItemStack oldStack) {
            return iterateInventory(inv, stack -> !stack.isEmpty() && stack.is(oldStack.getItem()));
        }
    }

    public static class FoodRule extends Rule {
        @Override boolean isActive() { return Demand.INSTANCE.supplyFood.get(); }
        @Override boolean matches(LocalPlayer player, ItemStack oldStack) {
            return oldStack.has(DataComponents.FOOD) && player.getFoodData().needsFood();
        }
        @Override int findMatchingStack(Inventory inv, ItemStack oldStack) {
            return iterateInventory(inv, stack -> stack.has(DataComponents.FOOD));
        }
    }

    public static class BlockRule extends Rule {
        @Override boolean isActive() { return Demand.INSTANCE.supplyBlocks.get(); }
        @Override boolean matches(LocalPlayer player, ItemStack oldStack) { return oldStack.getItem() instanceof BlockItem; }
        @Override int findMatchingStack(Inventory inv, ItemStack oldStack) {
            return iterateInventory(inv, stack -> stack.getItem() instanceof BlockItem);
        }
    }
}