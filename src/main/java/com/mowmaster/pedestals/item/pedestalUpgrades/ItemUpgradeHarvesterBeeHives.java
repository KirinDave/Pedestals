package com.mowmaster.pedestals.item.pedestalUpgrades;

import com.mojang.authlib.GameProfile;
import com.mowmaster.pedestals.enchants.EnchantmentArea;
import com.mowmaster.pedestals.enchants.EnchantmentCapacity;
import com.mowmaster.pedestals.enchants.EnchantmentOperationSpeed;
import com.mowmaster.pedestals.enchants.EnchantmentRange;
import com.mowmaster.pedestals.network.PacketHandler;
import com.mowmaster.pedestals.network.PacketParticles;
import com.mowmaster.pedestals.tiles.PedestalTileEntity;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.KelpTopBlock;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.dispenser.BeehiveDispenseBehavior;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.Property;
import net.minecraft.tags.BlockTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;

public class ItemUpgradeHarvesterBeeHives extends ItemUpgradeBase
{
    public ItemUpgradeHarvesterBeeHives(Properties builder) {super(builder.group(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptArea() {
        return true;
    }

    public int getAreaWidth(ItemStack stack)
    {
        int areaWidth = 0;
        int aW = getAreaModifier(stack);
        areaWidth = ((aW)+1);
        return  areaWidth;
    }

    @Override
    public int getWorkAreaX(World world, BlockPos pos, ItemStack coin)
    {
        return getAreaWidth(coin);
    }

    @Override
    public int[] getWorkAreaY(World world, BlockPos pos, ItemStack coin)
    {
        return new int[]{((2*getAreaWidth(coin))+1),0};
    }

    @Override
    public int getWorkAreaZ(World world, BlockPos pos, ItemStack coin)
    {
        return getAreaWidth(coin);
    }

    //https://github.com/Lothrazar/Cyclic/blob/trunk/1.16/src/main/java/com/lothrazar/cyclic/block/harvester/TileHarvester.java#L157
    public static IntegerProperty getBlockPropertyHoney(BlockState blockState) {
        for (Property<?> prop : blockState.getProperties()) {
            if (prop != null && prop.getName() != null && prop instanceof IntegerProperty && prop.getName().equalsIgnoreCase("honey_level")) {
                return (IntegerProperty) prop;
            }
        }
        return null;
    }


    //https://github.com/Lothrazar/Cyclic/blob/trunk/1.16/src/main/java/com/lothrazar/cyclic/block/harvester/TileHarvester.java#L113
    public boolean canHarvest(World world, BlockState state)
    {
        boolean returner = false;
        //BeehiveDispenseBehavior
        if(state.isIn(BlockTags.BEEHIVES))
        {
            IntegerProperty propInt = getBlockPropertyHoney(state);
            if (propInt == null || !(world instanceof ServerWorld)) {
                returner = false;
            }
            else
            {
                int current = state.get(propInt);
                int max = Collections.max(propInt.getAllowedValues());
                //Taked from onBlockActivated method in the BeehiveBlock class
                if(current >= 5)
                {
                    returner = true;
                }
            }
        }

        return returner;
    }

    public int ticked = 0;

    public void updateAction(int tick, World world, ItemStack itemInPedestal, ItemStack coinInPedestal, BlockPos pedestalPos)
    {
        if(!world.isRemote)
        {
            int speed = getOperationSpeed(coinInPedestal);

            int width = getAreaWidth(coinInPedestal);
            int height = (2*width)+1;

            BlockPos negBlockPos = getNegRangePosEntity(world,pedestalPos,width,height);
            BlockPos posBlockPos = getPosRangePosEntity(world,pedestalPos,width,height);

            if(!world.isBlockPowered(pedestalPos)) {
                if (world.getGameTime() % speed == 0) {
                    TileEntity tile = world.getTileEntity(pedestalPos);
                    if(tile instanceof PedestalTileEntity)
                    {
                        PedestalTileEntity pedestal = (PedestalTileEntity) tile;
                        int currentPosition = pedestal.getStoredValueForUpgrades();
                        BlockPos targetPos = getPosOfNextBlock(currentPosition,negBlockPos,posBlockPos);
                        BlockState targetBlock = world.getBlockState(targetPos);
                        upgradeAction(world, itemInPedestal,coinInPedestal, pedestalPos, targetPos, targetBlock);
                        pedestal.setStoredValueForUpgrades(currentPosition+1);
                        if(resetCurrentPosInt(currentPosition,negBlockPos,posBlockPos))
                        {
                            pedestal.setStoredValueForUpgrades(0);
                        }
                    }
                }
            }
        }
    }

    public void upgradeAction(World world, ItemStack itemInPedestal, ItemStack coinInPedestal, BlockPos posOfPedestal, BlockPos posTarget, BlockState target)
    {

        if(canHarvest(world,target) && !target.getBlock().isAir(target,world,posTarget))
        {

            FakePlayer fakePlayer = FakePlayerFactory.get(world.getServer().func_241755_D_(),new GameProfile(getPlayerFromCoin(coinInPedestal),"[Pedestals]"));
            fakePlayer.setPosition(posOfPedestal.getX(),posOfPedestal.getY(),posOfPedestal.getZ());
            ItemStack harvestingHoe = new ItemStack(Items.DIAMOND_HOE,1);
            if (itemInPedestal.getItem() instanceof HoeItem || itemInPedestal.getToolTypes().contains(ToolType.HOE)) {
                fakePlayer.setHeldItem(Hand.MAIN_HAND, itemInPedestal);
            }
            else
            {
                if(EnchantmentHelper.getEnchantments(coinInPedestal).containsKey(Enchantments.SILK_TOUCH))
                {
                    harvestingHoe.addEnchantment(Enchantments.SILK_TOUCH,1);
                    fakePlayer.setHeldItem(Hand.MAIN_HAND,harvestingHoe);
                }
                else if (EnchantmentHelper.getEnchantments(coinInPedestal).containsKey(Enchantments.FORTUNE))
                {
                    int lvl = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE,coinInPedestal);
                    harvestingHoe.addEnchantment(Enchantments.FORTUNE,lvl);
                    fakePlayer.setHeldItem(Hand.MAIN_HAND,harvestingHoe);
                }
                else
                {
                    fakePlayer.setHeldItem(Hand.MAIN_HAND,harvestingHoe);
                }
            }

            if(ForgeEventFactory.doPlayerHarvestCheck(fakePlayer,target,true))
            {
                //if (ForgeEventFactory.doPlayerHarvestCheck(fakePlayer,target,true)) {

                BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(world, posTarget, target, fakePlayer);
                if (!MinecraftForge.EVENT_BUS.post(e)) {
                    target.getBlock().harvestBlock(world, fakePlayer, posTarget, target, null, fakePlayer.getHeldItemMainhand());
                    target.getBlock().onBlockHarvested(world, posTarget, target, fakePlayer);
                    PacketHandler.sendToNearby(world,posOfPedestal,new PacketParticles(PacketParticles.EffectType.HARVESTED,posTarget.getX(),posTarget.getY()-0.5f,posTarget.getZ(),posOfPedestal.getX(),posOfPedestal.getY(),posOfPedestal.getZ(),5));
                    world.removeBlock(posTarget, false);
                }
                //world.setBlockState(posOfBlock, Blocks.AIR.getDefaultState());
                //}
                    /*target.getBlock().harvestBlock(world, fakePlayer, posTarget, target, null, fakePlayer.getHeldItemMainhand());
                    world.setBlockState(posTarget, Blocks.AIR.getDefaultState());*/
            }
        }
    }

    @Override
    public void chatDetails(PlayerEntity player, PedestalTileEntity pedestal)
    {
        ItemStack stack = pedestal.getCoinOnPedestal();

        TranslationTextComponent name = new TranslationTextComponent(getTranslationKey() + ".tooltip_name");
        name.mergeStyle(TextFormatting.GOLD);
        player.sendMessage(name,Util.DUMMY_UUID);

        int s3 = getAreaWidth(stack);
        String tr = "" + (s3+s3+1) + "";
        TranslationTextComponent area = new TranslationTextComponent(getTranslationKey() + ".chat_area");
        TranslationTextComponent areax = new TranslationTextComponent(getTranslationKey() + ".chat_areax");
        area.appendString(tr);
        area.appendString(areax.getString());
        area.appendString(tr);
        area.appendString(areax.getString());
        area.appendString(tr);
        area.mergeStyle(TextFormatting.WHITE);
        player.sendMessage(area,Util.DUMMY_UUID);

        Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(stack);
        if(map.size() > 0)
        {
            TranslationTextComponent enchant = new TranslationTextComponent(getTranslationKey() + ".chat_enchants");
            enchant.mergeStyle(TextFormatting.LIGHT_PURPLE);
            player.sendMessage(enchant,Util.DUMMY_UUID);

            for(Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
                Enchantment enchantment = entry.getKey();
                Integer integer = entry.getValue();
                if(!(enchantment instanceof EnchantmentCapacity) && !(enchantment instanceof EnchantmentRange) && !(enchantment instanceof EnchantmentOperationSpeed) && !(enchantment instanceof EnchantmentArea))
                {
                    TranslationTextComponent enchants = new TranslationTextComponent(" - " + enchantment.getDisplayName(integer).getString());
                    enchants.mergeStyle(TextFormatting.GRAY);
                    player.sendMessage(enchants, Util.DUMMY_UUID);
                }
            }
        }

        //Display Speed Last Like on Tooltips
        TranslationTextComponent speed = new TranslationTextComponent(getTranslationKey() + ".chat_speed");
        speed.appendString(getOperationSpeedString(stack));
        speed.mergeStyle(TextFormatting.RED);
        player.sendMessage(speed,Util.DUMMY_UUID);
    }
    

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        int s3 = getAreaWidth(stack);
        String tr = "" + (s3+s3+1) + "";
        TranslationTextComponent area = new TranslationTextComponent(getTranslationKey() + ".tooltip_area");
        TranslationTextComponent areax = new TranslationTextComponent(getTranslationKey() + ".tooltip_areax");
        area.appendString(tr);
        area.appendString(areax.getString());
        area.appendString(tr);
        area.appendString(areax.getString());
        area.appendString(tr);
        TranslationTextComponent speed = new TranslationTextComponent(getTranslationKey() + ".tooltip_speed");
        speed.appendString(getOperationSpeedString(stack));

        area.mergeStyle(TextFormatting.WHITE);
        speed.mergeStyle(TextFormatting.RED);

        tooltip.add(area);
        tooltip.add(speed);
    }

    public static final Item HARVESTER = new ItemUpgradeHarvesterBeeHives(new Properties().maxStackSize(64).group(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/harvester"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(HARVESTER);
    }


}