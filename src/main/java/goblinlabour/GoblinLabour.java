package goblinlabour;

import goblinlabour.block.GoblinBedBlock;
import goblinlabour.block.GoblinBedBlockEntity;
import goblinlabour.block.GoblinScaffoldBlock;
import goblinlabour.block.HomeMarkerBlock;
import goblinlabour.item.GoblinStaffItem;
import goblinlabour.menu.StaffMenu;
import goblinlabour.menu.StaffMenuData;
import goblinlabour.staff.StaffSelection;
import goblinlabour.command.GoblinCommand;
import goblinlabour.dev.DevHooks;
import goblinlabour.entity.GoblinEntity;
import goblinlabour.item.GoblinBlankItem;
import goblinlabour.item.GoblinData;
import goblinlabour.item.GoblinHandbookItem;
import goblinlabour.item.GoblinHeadItem;
import goblinlabour.menu.GoblinBedMenu;
import goblinlabour.menu.GoblinBedMenuData;
import goblinlabour.menu.GoblinInventoryMenu;
import goblinlabour.menu.GoblinMenuData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public final class GoblinLabour implements ModInitializer {
    public static final String MOD_ID = "goblinlabour";
    public static final Logger LOGGER = LoggerFactory.getLogger("Goblin Labour");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Name, hotbar and health of a goblin, carried by the Goblin Blank item. */
    public static final DataComponentType<GoblinData> GOBLIN_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, id("goblin"),
            DataComponentType.<GoblinData>builder()
                    .persistent(GoblinData.CODEC)
                    .networkSynchronized(GoblinData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final Block GOBLIN_STRAW_BED = registerBlock("goblin_straw_bed", props -> new GoblinBedBlock(
            props.mapColor(MapColor.COLOR_YELLOW).strength(0.4f).sound(SoundType.GRASS).noOcclusion()));
    public static final BlockEntityType<GoblinBedBlockEntity> GOBLIN_BED_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("goblin_straw_bed"),
            FabricBlockEntityTypeBuilder.create(GoblinBedBlockEntity::new, GOBLIN_STRAW_BED).build());

    public static final Block GOBLIN_SCAFFOLD = registerBlock("goblin_scaffold", props -> new GoblinScaffoldBlock(
            props.mapColor(MapColor.COLOR_BROWN).strength(0.2f).sound(SoundType.SCAFFOLDING).noOcclusion().noLootTable().dynamicShape()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)));
    public static final Item GOBLIN_SCAFFOLD_ITEM = registerItem("goblin_scaffold",
            props -> new BlockItem(GOBLIN_SCAFFOLD, props.useBlockDescriptionPrefix()));
    /** Technical block, never placed: its particle texture (the emerald item) is what the home markers show. */
    public static final Block HOME_MARKER = registerBlock("home_marker", props -> new HomeMarkerBlock(
            props.noCollision().noLootTable().replaceable().air()));

    public static final Item GOBLIN_STRAW_BED_ITEM = registerItem("goblin_straw_bed",
            props -> new BlockItem(GOBLIN_STRAW_BED, props.useBlockDescriptionPrefix()));
    public static final Item GOBLIN_MEAT_PACK = registerItem("goblin_meat_pack", Item::new);
    public static final Item GOBLIN_BLANK = registerItem("goblin_blank", props -> new GoblinBlankItem(props.stacksTo(16)));
    public static final Item GOBLIN_HEAD = registerItem("goblin_head", props -> new GoblinHeadItem(props.stacksTo(1)));
    public static final Item GOBLIN_STAFF = registerItem("goblin_staff", props -> new GoblinStaffItem(props.stacksTo(1)));
    public static final Item GOBLIN_HANDBOOK = registerItem("goblin_handbook", props -> new GoblinHandbookItem(props.stacksTo(1)));

    public static final EntityType<GoblinEntity> GOBLIN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("goblin"),
            EntityType.Builder.of(GoblinEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.95f)
                    .eyeHeight(0.8f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("goblin"))));

    public static final ExtendedMenuType<GoblinInventoryMenu, GoblinMenuData> GOBLIN_MENU = Registry.register(
            BuiltInRegistries.MENU, id("goblin"),
            new ExtendedMenuType<>(GoblinInventoryMenu::new, GoblinMenuData.STREAM_CODEC));

    public static final ExtendedMenuType<StaffMenu, StaffMenuData> STAFF_MENU = Registry.register(
            BuiltInRegistries.MENU, id("staff"),
            new ExtendedMenuType<>(StaffMenu::new, StaffMenuData.STREAM_CODEC));

    public static final ExtendedMenuType<GoblinBedMenu, GoblinBedMenuData> BED_MENU = Registry.register(
            BuiltInRegistries.MENU, id("bed"),
            new ExtendedMenuType<>(GoblinBedMenu::new, GoblinBedMenuData.STREAM_CODEC));

    public static final CreativeModeTab TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB, id("main"),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.goblinlabour.main"))
                    .icon(() -> new ItemStack(GOBLIN_HEAD))
                    .displayItems((params, out) -> {
                        out.accept(GOBLIN_HANDBOOK);
                        out.accept(GOBLIN_MEAT_PACK);
                        out.accept(GOBLIN_BLANK);
                        out.accept(GOBLIN_STRAW_BED_ITEM);
                        out.accept(GOBLIN_HEAD);
                        out.accept(GOBLIN_STAFF);
                        out.accept(GOBLIN_SCAFFOLD_ITEM);
                    })
                    .build());

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(GOBLIN, GoblinEntity.createAttributes());
        GoblinSounds.init();
        GoblinCommand.init();
        StaffSelection.init();
        DevHooks.initServer();
        LOGGER.info("Goblin Labour loaded");
    }

    private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory) {
        Identifier id = id(name);
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id));
        return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(props));
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> factory) {
        Identifier id = id(name);
        Item.Properties props = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
        return Registry.register(BuiltInRegistries.ITEM, id, factory.apply(props));
    }
}
