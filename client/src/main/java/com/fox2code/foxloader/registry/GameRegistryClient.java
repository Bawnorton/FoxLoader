package com.fox2code.foxloader.registry;

import static com.fox2code.foxloader.loader.ClientMod.*;

import com.fox2code.foxloader.client.CreativeItems;
import com.fox2code.foxloader.client.mixins.AccessorEntityList;
import com.fox2code.foxloader.client.registry.RegisteredBlockImpl;
import com.fox2code.foxloader.loader.ModLoader;
import com.fox2code.foxloader.loader.packet.ServerHello;
import com.fox2code.foxloader.network.SidedMetadataAPI;
import net.minecraft.src.client.gui.StringTranslate;
import net.minecraft.src.game.block.*;
import net.minecraft.src.game.item.*;
import net.minecraft.src.game.recipe.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

public class GameRegistryClient extends GameRegistry {
    public static final GameRegistryClient INSTANCE = new GameRegistryClient();
    // Common entries start
    public static final int[] chanceToEncourageFire = new int[MAXIMUM_BLOCK_ID];
    public static final int[] abilityToCatchFire = new int[MAXIMUM_BLOCK_ID];
    public static final EnumReflectTranslator<BuiltInMaterial, Material> MATERIAL =
            new EnumReflectTranslator<>(BuiltInMaterial.class, Material.class);
    public static final EnumReflectTranslator<BuiltInStepSounds, StepSound> STEP_SOUND =
            new EnumReflectTranslator<>(BuiltInStepSounds.class, StepSound.class, Block.class);
    // Common entries end
    public static final short[] itemIdMappingIn = new short[MAXIMUM_ITEM_ID];
    public static final short[] itemIdMappingOut = new short[MAXIMUM_ITEM_ID];
    public static final short[] blockIdMappingIn = new short[MAXIMUM_BLOCK_ID];
    public static final short[] blockIdMappingOut = new short[MAXIMUM_BLOCK_ID];
    public static final String[] itemIdMappingInNames = new String[MAXIMUM_ITEM_ID];

    public static final int[] entityTypeIdMappingIn = new int[MAXIMUM_ENTITY_TYPE_ID];
    public static final int[] entityTypeIdMappingOut = new int[MAXIMUM_ENTITY_TYPE_ID];
    public static final String[] entityTypeIdMappingNames = new String[MAXIMUM_ENTITY_TYPE_ID];

    private static MappingState idMappingState = MappingState.CLIENT;
    private enum MappingState {
        CLIENT, SERVER, CUSTOM
    }

    static {
        for (short i = 0; i < MAXIMUM_ITEM_ID; i++) {
            itemIdMappingIn[i] = i;
            itemIdMappingOut[i] = i;
        }
        for (short i = 0; i < MAXIMUM_BLOCK_ID; i++) {
            blockIdMappingIn[i] = i;
            blockIdMappingOut[i] = i;
        }
        for (short i = 0; i < MAXIMUM_ENTITY_TYPE_ID; i++) {
            entityTypeIdMappingIn[i] = i;
            entityTypeIdMappingOut[i] = i;
        }
    }

    public static void initialize() {
        // The check is actually for initializing both Item and Block
        if (Block.blocksList[0].blockID != Item.itemsList[0].itemID) {
            throw new IllegalStateException("Air block is not air?");
        }
    }

    public static void freeze() {
        if (!ModLoader.areAllModsLoaded())
            throw new IllegalArgumentException("Mods didn't finished to load!");
        final Block stoneBlock = Block.blocksList[1];
        for (int i = 0; i < Block.blocksList.length; i++) {
            if (Block.blocksList[i] == null) {
                Block.blocksList[i] = stoneBlock;
            }
        }
        final Item airItem = Item.itemsList[0];
        for (int i = 0; i < Item.itemsList.length; i++) {
            if (Item.itemsList[i] == null) {
                Item.itemsList[i] = airItem;
            }
        }
    }

    private int nextBlockId = INITIAL_BLOCK_ID;
    private int nextItemId = INITIAL_ITEM_ID;
    private int nextEntityTypeId = INITIAL_ENTITY_TYPE_ID;

    private GameRegistryClient() {}

    // START Common code //
    @Override
    public int getMaxBlockId() {
        return nextBlockId - 1;
    }

    @Override
    public RegisteredItem getRegisteredItem(int id) {
        return (RegisteredItem) Item.itemsList[id];
    }

    @Override
    public RegisteredBlock getRegisteredBlock(int id) {
        return (RegisteredBlock) Block.blocksList[id];
    }

    @Override
    public String translateKey(String translationKey) {
        return StringTranslate.getInstance().translateKey(translationKey);
    }

    @Override
    public String translateKeyFormat(String translationKey, String... args) {
        return StringTranslate.getInstance().translateKeyFormat(translationKey, (Object[]) args);
    }

    @Override
    public int generateNewBlockId(String name, int fallbackId) {
        if (registryEntries.containsKey(name)) {
            throw new RuntimeException("Duplicate item/block string id: " + name);
        }
        if (fallbackId < 0 || fallbackId > 255) {
            throw new IllegalArgumentException("Invalid fallback id: " + fallbackId);
        }
        int blockId = nextBlockId++;
        if (blockId > MAXIMUM_BLOCK_ID) {
            throw new RuntimeException("Maximum block count registered! (Too many mods?)");
        }
        registryEntries.put(name, new RegistryEntry(
                (short) convertBlockIdToItemId(blockId), (short) fallbackId, name,
                StringTranslate.getInstance().translateKey("tile." + name.replace(':', '.'))));
        return blockId;
    }

    @Override
    public int generateNewItemId(String name, int fallbackId) {
        if (registryEntries.containsKey(name)) {
            throw new RuntimeException("Duplicate item/block string id: " + name);
        }

        if (fallbackId < 0 || fallbackId > 255) {
            throw new IllegalArgumentException("Invalid fallback id: " + fallbackId);
        }

        int itemId = nextItemId++;

        if (itemId > MAXIMUM_ITEM_ID) {
            throw new RuntimeException("Maximum block count registered! (Too many mods?)");
        }

        registryEntries.put(name, new RegistryEntry((short) itemId, (short) fallbackId, name,
                StringTranslate.getInstance().translateKey("item." + name.replace(':', '.'))));
        return itemId;
    }

    @Override
    public int generateNewEntityTypeId(String name, int fallbackId) {
        if (registryEntries.containsKey(name)) {
            throw new RuntimeException("Duplicate entity string id: " + name);
        }

        if (fallbackId < 0 || fallbackId > 202) {
            throw new IllegalArgumentException("Invalid fallback id: " + fallbackId);
        }

        int entityTypeId = nextEntityTypeId++;

        if (entityTypeId > MAXIMUM_ENTITY_TYPE_ID) {
            // This is extremely unlikely
            throw new RuntimeException("Maximum entity type count registered! (Too many mods?)");
        }

        entityTypeEntries.put(name, new EntityTypeRegistryEntry(entityTypeId, fallbackId, name));
        return entityTypeId;
    }

    @Override
    public RegisteredBlock registerNewBlock(String name, BlockBuilder blockBuilder, int fallbackId) {
        name = validateAndFixRegistryName(name);
        if (blockBuilder == null) blockBuilder = DEFAULT_BLOCK_BUILDER;
        String secondaryExt = blockBuilder.builtInBlockType.secRegistryExt;
        Block mainBlock = this.registerNewBlock0(name, "", blockBuilder, fallbackId, true);
        Block secondaryBlock = null;
        if (secondaryExt != null) {
            secondaryBlock = this.registerNewBlock0(name + secondaryExt, secondaryExt, blockBuilder, fallbackId, false);
        }
        this.registerNewItem0(name, blockBuilder.itemBuilder, mainBlock, secondaryBlock, -1, true);
        if (secondaryExt != null) {
            this.registerNewItem0(name + secondaryExt, blockBuilder.itemBuilder, mainBlock, secondaryBlock, -1, false);
        }
        return (RegisteredBlock) mainBlock;
    }

    public Block registerNewBlock0(String name, String ext, BlockBuilder blockBuilder, int fallbackId, boolean primary) {
        int blockId = generateNewBlockId(name, fallbackId);
        Block block;
        Material material = MATERIAL.translate(blockBuilder.builtInMaterial);
        Block blockSource = (Block) blockBuilder.blockSource;
        boolean selfNotify = false;
        switch (blockBuilder.getBuiltInBlockTypeForConstructor()) {
            default:
                throw new IllegalArgumentException("Invalid block type " + blockBuilder.builtInBlockType);
            case CUSTOM:
                try {
                    block = (Block) blockBuilder.gameBlockProvider.provide(blockId, blockBuilder, ext);
                    if (block.blockID != blockId) {
                        throw new RuntimeException("Block didn't ended up with id it was given to " +
                                "(given " + blockId + " got " + block.blockID + ")");
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to instantiate Custom block", e);
                }
                break;
            case BLOCK:
                block = new Block(blockId, material) {};
                break;
            case GLASS:
                block = new BlockGlass(blockId) {};
                break;
            case WORKBENCH:
                block = new BlockWorkbench(blockId) {};
                break;
            case FALLING:
                block = new BlockFalling(blockId, material);
                break;
            case SLAB:
                EnumSlab slabType = EnumSlab.BRICK;
                for (EnumSlab enumSlabCandidate : EnumSlab.values()) {
                    if (enumSlabCandidate.hasBottomSide && enumSlabCandidate.hasTopSide &&
                            enumSlabCandidate.material == material) {
                        slabType = enumSlabCandidate;
                        break;
                    }
                }

                block = new BlockSlab(blockId, !primary, slabType);
                selfNotify = true;
                break;
            case STAIRS:
                block = new BlockStairs(blockId, Objects.requireNonNull(blockSource, "blockSource")) {};
                selfNotify = true;
                break;
        }
        if (selfNotify) {
            Block.selfNotify.set(blockId, true);
        }
        block.stepSound = STEP_SOUND.translate(blockBuilder.builtInStepSounds);
        if (blockBuilder.blockHardness != 0f) {
            ((RegisteredBlockImpl) block).setRegisteredHardness(blockBuilder.blockHardness);
        }
        if (blockBuilder.blockResistance != 0f) {
            ((RegisteredBlockImpl) block).setRegisteredResistance(blockBuilder.blockResistance);
        }
        if (blockBuilder.blockBurnType != 0 && blockBuilder.blockBurnTime != 0) {
            block.setBurnTime(blockBuilder.blockBurnTime, blockBuilder.blockBurnType);
        }
        if (blockBuilder.chanceToEncourageFire != 0) {
            chanceToEncourageFire[blockId] = blockBuilder.chanceToEncourageFire;
        }
        if (blockBuilder.abilityToCatchFire != 0) {
            abilityToCatchFire[blockId] = blockBuilder.abilityToCatchFire;
        }
        byte effectiveToolBit = blockBuilder.effectiveToolBit;
        for (EnumTools enumTool : EnumTools.values()) {
            if ((effectiveToolBit & (1 << enumTool.ordinal())) != 0) {
                block.setEffectiveTool(enumTool);
            }
        }
        if (blockBuilder.tooltipColor != 0) {
            block.setTooltipColor(blockBuilder.tooltipColor);
        }
        block.setBlockName(blockBuilder.blockName == null ?
                name.replace(':', '.') : blockBuilder.blockName);
        return block;
    }

    @Override
    public RegisteredItem registerNewItem(String name, ItemBuilder itemBuilder, int fallbackId) {
        name = validateAndFixRegistryName(name);
        return this.registerNewItem0(name, itemBuilder, null, null, this.generateNewItemId(name, fallbackId), true);
    }

    private RegisteredItem registerNewItem0(String name, ItemBuilder itemBuilder,
                                            Block blockPrimary, Block blockSecondary,
                                            int itemId, boolean primary) {
        if (itemBuilder == null) itemBuilder = DEFAULT_ITEM_BUILDER;
        Item item;
        Block block = primary ? blockPrimary : blockSecondary;
        if (block != null) {
            itemId = convertBlockIdToItemId(block.blockID);
        }
        final int pItemId = itemId - PARAM_ITEM_ID_DIFF;
        if (itemBuilder.gameItemProvider != null) {
            try {
                item = (Item) itemBuilder.gameItemProvider.provide(itemId, itemBuilder, (RegisteredBlock) block);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to instantiate Item", e);
            }
            if (item.itemID != itemId) {
                throw new RuntimeException("Item didn't ended up with id it was given to " +
                        "(given " + itemId + " got " + item.itemID + ")");
            }
        } else if (blockPrimary != null &&
                blockSecondary != null) {
            item = new ItemBlockSlab(pItemId, block.blockID, blockPrimary, blockSecondary, !primary);
        } else if (block != null) {
            item = new ItemBlock(pItemId, block.blockID) {};
        } else {
            item = new Item(pItemId) {};
        }
        item.setMaxStackSize(itemBuilder.maxStackSize);
        Item containerItem = (Item) itemBuilder.containerItem;
        if (containerItem != null) {
            item.setContainerItem(containerItem);
        }
        if (block == null) {
            item.setItemName(itemBuilder.itemName == null ?
                    name.replace(':', '.') : itemBuilder.itemName);
        }
        if (itemBuilder.itemBurnType != 0 && itemBuilder.itemBurnTime != 0) {
            item.setBurnTime(itemBuilder.itemBurnTime, itemBuilder.itemBurnType);
        }
        if (itemBuilder.tooltipColor != 0) {
            item.setTooltipColor(itemBuilder.tooltipColor);
        }
        if (itemBuilder.worldItemScale != 0F) {
            ((RegisteredItem) item).setWorldItemScale(itemBuilder.worldItemScale);
        }
        if (!itemBuilder.hideFromCreativeInventory) {
            CreativeItems.addToCreativeInventory(new ItemStack(item));
        }
        return (RegisteredItem) item;
    }

	@Override
	public void registerNewEntityType(String name, Class<? extends RegisteredEntity> entityClass, int fallbackId) {
		AccessorEntityList.invokeAddMapping(entityClass, name, generateNewEntityTypeId(name, fallbackId));
	}

    @Override
    public void registerRecipe(RegisteredItemStack result, Object... recipe) {
        if (recipeFrozen) throw new UnsupportedOperationException(LATE_RECIPE_MESSAGE);
        CraftingManager.getInstance().addRecipe(toItemStack(result), recipe);
    }

    @Override
    public void registerShapelessRecipe(RegisteredItemStack result, Ingredient... ingredients) {
        if (recipeFrozen) throw new UnsupportedOperationException(LATE_RECIPE_MESSAGE);
        CraftingManager.getInstance().addShapelessRecipe(toItemStack(result), (Object[]) ingredients);
    }

    @Override
    public void registerFurnaceRecipe(RegisteredItem input, RegisteredItemStack output) {
        if (recipeFrozen) throw new UnsupportedOperationException(LATE_RECIPE_MESSAGE);
        FurnaceRecipes.instance.addSmelting(input.getRegisteredItemId(), toItemStack(output));
    }

    @Override
    public void registerBlastFurnaceRecipe(RegisteredItem input, RegisteredItemStack output) {
        if (recipeFrozen) throw new UnsupportedOperationException(LATE_RECIPE_MESSAGE);
        BlastFurnaceRecipes.instance.addSmelting(input.getRegisteredItemId(), toItemStack(output));
    }

    @Override
    public void registerFreezerRecipe(RegisteredItem input, RegisteredItemStack output) {
        if (recipeFrozen) throw new UnsupportedOperationException(LATE_RECIPE_MESSAGE);
        RefridgifreezerRecipes.instance.addSmelting(input.getRegisteredItemId(), toItemStack(output));
    }

    @ApiStatus.Internal
    public static void freezeRecipes() {
        if (recipeFrozen) return;
        recipeFrozen = true;
        CraftingManager.getInstance().getRecipeList().sort(new RecipeSorter());
    }

    private static class RecipeSorter implements Comparator<Object> {
        public int compareRecipes(IRecipe var1, IRecipe var2) {
            if (var1 instanceof ShapelessRecipes && var2 instanceof ShapedRecipes) {
                return 1;
            } else if (var2 instanceof ShapelessRecipes && var1 instanceof ShapedRecipes) {
                return -1;
            } else if (var2.getRecipeSize() < var1.getRecipeSize()) {
                return -1;
            } else {
                return var2.getRecipeSize() > var1.getRecipeSize() ? 1 : 0;
            }
        }

        public int compare(Object var1, Object var2) {
            return this.compareRecipes((IRecipe)var1, (IRecipe)var2);
        }
    }
    // END Common code //

    public static void resetMappings(boolean singlePlayer) {
        if (singlePlayer) {
            if (idMappingState == MappingState.CLIENT) return;
            idMappingState = MappingState.CLIENT;
            for (short i = INITIAL_ITEM_ID; i < MAXIMUM_ITEM_ID; i++) {
                itemIdMappingIn[i] = i;
                itemIdMappingOut[i] = i;
            }
            for (short i = (short) INITIAL_TRANSLATED_BLOCK_ID; i < MAXIMUM_TRANSLATED_BLOCK_ID; i++) {
                itemIdMappingIn[i] = i;
                itemIdMappingOut[i] = i;
            }
            for (short i = INITIAL_BLOCK_ID; i < MAXIMUM_BLOCK_ID; i++) {
                blockIdMappingIn[i] = i;
                blockIdMappingOut[i] = i;
            }
            for (short i = INITIAL_ENTITY_TYPE_ID; i < MAXIMUM_ENTITY_TYPE_ID; i++) {
                entityTypeIdMappingIn[i] = i;
                entityTypeIdMappingOut[i] = i;
            }
        } else {
            if (idMappingState == MappingState.SERVER) return;
            idMappingState = MappingState.SERVER;
            for (int i = INITIAL_TRANSLATED_BLOCK_ID; i < MAXIMUM_TRANSLATED_BLOCK_ID; i++) {
                itemIdMappingIn[i] = DEFAULT_FALLBACK_BLOCK_ID;
                itemIdMappingOut[i] = DEFAULT_FALLBACK_BLOCK_ID;
            }
            for (short i = INITIAL_BLOCK_ID; i < MAXIMUM_BLOCK_ID; i++) {
                blockIdMappingIn[i] = DEFAULT_FALLBACK_BLOCK_ID;
                blockIdMappingOut[i] = DEFAULT_FALLBACK_BLOCK_ID;
            }
            for (short i = INITIAL_ITEM_ID; i < MAXIMUM_ITEM_ID; i++) {
                itemIdMappingIn[i] = DEFAULT_FALLBACK_ITEM_ID;
                itemIdMappingOut[i] = DEFAULT_FALLBACK_ITEM_ID;
            }
            for (short i = INITIAL_ENTITY_TYPE_ID; i < MAXIMUM_ENTITY_TYPE_ID; i++) {
                entityTypeIdMappingIn[i] = DEFAULT_FALLBACK_ENTITY_TYPE_ID;
                entityTypeIdMappingOut[i] = DEFAULT_FALLBACK_ENTITY_TYPE_ID;
            }
        }
        Arrays.fill(itemIdMappingInNames, null);
        Arrays.fill(entityTypeIdMappingNames, null);
    }

    public static void initializeMappings(ServerHello serverHello) {
        if (idMappingState != MappingState.SERVER) {
            resetMappings(false);
        }
        if (serverHello.metadata != null && !serverHello.metadata.isEmpty()) {
            SidedMetadataAPI.Internal.setActiveMetaData(
                    Collections.unmodifiableMap(serverHello.metadata));
        }
        if (serverHello.registryEntries.isEmpty()) {
            return;
        }
        idMappingState = MappingState.CUSTOM;
        for (RegistryEntry entry : serverHello.registryEntries.values()) {
            final short remoteId = entry.realId;
            if (isLoaderReservedItemId(remoteId)) {
                RegistryEntry local = registryEntries.get(itemIdMappingInNames[remoteId] = entry.name);
                if (local == null) {
                    itemIdMappingIn[remoteId] = entry.fallbackId;
                    if (remoteId >= INITIAL_TRANSLATED_BLOCK_ID &&
                            remoteId < MAXIMUM_TRANSLATED_BLOCK_ID) {
                        blockIdMappingIn[convertItemIdToBlockId(remoteId)] = entry.fallbackId;
                    }
                } else {
                    itemIdMappingIn[remoteId] = local.realId;
                    itemIdMappingOut[local.realId] = remoteId;
                    if (remoteId >= INITIAL_TRANSLATED_BLOCK_ID && remoteId < MAXIMUM_TRANSLATED_BLOCK_ID) {
                        if (local.realId >= INITIAL_TRANSLATED_BLOCK_ID && local.realId < MAXIMUM_TRANSLATED_BLOCK_ID) {
                            final short remoteBlockId = (short) convertItemIdToBlockId(remoteId);
                            final short localBlockId = (short) convertItemIdToBlockId(local.realId);
                            blockIdMappingIn[remoteBlockId] = localBlockId;
                            blockIdMappingOut[localBlockId] = remoteBlockId;
                        } else {
                            // We should never reach here, but let still "support" this extreme case.
                            blockIdMappingIn[convertItemIdToBlockId(remoteId)] = entry.fallbackId;
                        }
                    }
                }
            }
        }

        for (EntityTypeRegistryEntry entry : serverHello.entityTypeRegistryEntries.values()) {
            final int remoteId = entry.realId;
            if (isLoaderReservedEntityTypeId(remoteId)) {
                EntityTypeRegistryEntry local = entityTypeEntries.get(entityTypeIdMappingNames[remoteId] = entry.name);
                if (local == null) {
                    entityTypeIdMappingIn[remoteId] = entry.fallbackId;
                    continue;
                }

				entityTypeIdMappingIn[remoteId] = local.realId;
                entityTypeIdMappingOut[local.realId] = remoteId;
			}
        }
    }
}
