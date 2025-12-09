package net.aaavein.supply;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Demand {
    public static final Demand INSTANCE;
    public static final ModConfigSpec SPEC;

    // settings
    public final ModConfigSpec.BooleanValue playSound;
    public final ModConfigSpec.BooleanValue supplyOffhand;
    public final ModConfigSpec.BooleanValue supplyOnChange;
    public final ModConfigSpec.BooleanValue supplyOnDrop;
    public final ModConfigSpec.BooleanValue supplyThrowables;
    public final ModConfigSpec.BooleanValue supplyLoyalty;

    // rules
    public final ModConfigSpec.BooleanValue supplyCopies;
    public final ModConfigSpec.BooleanValue supplyItems;
    public final ModConfigSpec.BooleanValue supplyFood;
    public final ModConfigSpec.BooleanValue supplyBlocks;

    static {
        final Pair<Demand, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Demand::new);
        SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    private Demand(ModConfigSpec.Builder builder) {
        playSound = builder
                .comment(" Plays a sound when an item is successfully supplied.")
                .define("play_sound", true);

        supplyItems = builder
                .comment(" Supplies items that are the same.", " Ignores Data Components, Enchantments, and Durability.")
                .define("supply_items", true);

        supplyCopies = builder
                .comment(" Supplies items that are exact copies.", " Checks Data Components, Enchantments, and Durability.")
                .define("supply_copies", true);

        supplyFood = builder
                .comment(" Supplies any available food item after eating.", " Only triggers if the player is currently hungry.")
                .define("supply_food", true);

        supplyOffhand = builder
                .comment(" Supplies items to the Offhand slot.", " Useful for Shields, Torches, or Food.")
                .define("supply_offhand", true);

        supplyOnDrop = builder
                .comment(" Supplies after dropping an item.", " Disable to prevent accidental refills while clearing inventory.")
                .define("supply_on_drop", true);

        supplyOnChange = builder
                .comment(" Supplies when the item type changes (e.g., tool breaks).")
                .define("supply_on_change", true);

        supplyThrowables = builder
                .comment(" Supplies items in the \"supply:throwables\" tag (e.g., Eggs or Snowballs).", " Disable to prevent supply on throw, while still allowing refill on manual drop.")
                .define("supply_throwables", true);

        supplyLoyalty = builder
                .comment(" Supplies items with the Loyalty enchantment.", " Disable to wait for the item to return, while still allowing refill on manual drop.")
                .define("supply_loyalty", false);

        supplyBlocks = builder
                .comment(" Supplies any available block item.", " Useful for building with mixed materials.")
                .define("supply_blocks", false);
    }
}