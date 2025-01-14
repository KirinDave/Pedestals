package com.mowmaster.pedestals.item.pedestalUpgrades;


import com.mowmaster.pedestals.tiles.PedestalTileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.stream.IntStream;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;

public class ItemUpgradeFilterItemStack extends ItemUpgradeBaseFilter
{
    public ItemUpgradeFilterItemStack(Item.Properties builder) {super(builder.group(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptAdvanced() {return true;}

    public void updateAction(World world, PedestalTileEntity pedestal)
    {

    }

    @Override
    public boolean canAcceptItem(World world, BlockPos posPedestal, ItemStack itemStackIn)
    {
        boolean returner = false;

        if(world.getTileEntity(posPedestal) instanceof PedestalTileEntity)
        {
            PedestalTileEntity pedestal = (PedestalTileEntity)world.getTileEntity(posPedestal);
            ItemStack coin = pedestal.getCoinOnPedestal();
            List<ItemStack> stackCurrent = readFilterQueueFromNBT(coin);
            if(!(stackCurrent.size()>0))
            {
                stackCurrent = buildFilterQueue(pedestal);
                writeFilterQueueToNBT(coin,stackCurrent);
            }

            int range = stackCurrent.size();

            ItemStack itemFromInv = ItemStack.EMPTY;
            itemFromInv = IntStream.range(0,range)//Int Range
                    .mapToObj((stackCurrent)::get)//Function being applied to each interval
                    .filter(itemStack -> doItemsMatch(itemStack,itemStackIn))
                    .findFirst().orElse(ItemStack.EMPTY);

            if(!itemFromInv.isEmpty())
            {
                returner = true;
            }
        }

        return returner;
    }

    public void upgradeAction(World world, BlockPos posOfPedestal, ItemStack coinInPedestal)
    {

    }

    @Override
    public void onPedestalNeighborChanged(PedestalTileEntity pedestal) {
        ItemStack coin = pedestal.getCoinOnPedestal();
        List<ItemStack> stackIn = buildFilterQueue(pedestal);
        if(filterQueueSize(coin)>0)
        {
            List<ItemStack> stackCurrent = readFilterQueueFromNBT(coin);
            if(!doesFilterAndQueueMatch(stackIn,stackCurrent))
            {
                writeFilterQueueToNBT(coin,stackIn);
            }
        }
        else
        {
            writeFilterQueueToNBT(coin,stackIn);
        }
    }

    public static final Item ITEMSTACK = new ItemUpgradeFilterItemStack(new Item.Properties().maxStackSize(64).group(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/filteritemstack"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(ITEMSTACK);
    }



}
