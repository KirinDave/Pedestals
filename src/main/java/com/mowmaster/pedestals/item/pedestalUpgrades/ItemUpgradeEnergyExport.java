package com.mowmaster.pedestals.item.pedestalUpgrades;

import com.mowmaster.pedestals.tiles.PedestalTileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;

public class ItemUpgradeEnergyExport extends ItemUpgradeBaseEnergy
{
    public ItemUpgradeEnergyExport(Properties builder) {super(builder.group(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptCapacity() {
        return true;
    }

    @Override
    public boolean canSendItem(PedestalTileEntity tile)
    {
        return getStoredInt(tile.getCoinOnPedestal())>0;
    }

    public void updateAction(World world, PedestalTileEntity pedestal)
    {
        if(!world.isRemote)
        {
            ItemStack coinInPedestal = pedestal.getCoinOnPedestal();
            ItemStack itemInPedestal = pedestal.getItemInPedestal();
            BlockPos pedestalPos = pedestal.getPos();

            //Still Needed as we want to limit energy transfer from PEN to world
            int speed = getOperationSpeed(coinInPedestal);

            if(!world.isBlockPowered(pedestalPos))
            {
                if (world.getGameTime()%speed == 0) {
                    //Just receives Energy, then exports it to machines, not other pedestals
                    upgradeItemAction(world,pedestalPos,itemInPedestal,coinInPedestal);
                    upgradeAction(world,pedestalPos,itemInPedestal,coinInPedestal);
                }
            }
        }
    }

    public void upgradeAction(World world, BlockPos posOfPedestal, ItemStack itemInPedestal, ItemStack coinInPedestal)
    {
        int getMaxEnergyValue = getEnergyBuffer(coinInPedestal);
        if(!hasMaxEnergySet(coinInPedestal) || readMaxEnergyFromNBT(coinInPedestal) != getMaxEnergyValue) {setMaxEnergy(coinInPedestal, getMaxEnergyValue);}

        BlockPos posInventory = getPosOfBlockBelow(world,posOfPedestal,1);
        ItemStack itemFromPedestal = ItemStack.EMPTY;

        LazyOptional<IEnergyStorage> cap = findEnergyHandlerAtPos(world,posInventory,getPedestalFacing(world, posOfPedestal),true);

        //Gets inventory TE then makes sure its not a pedestal
        TileEntity invToPushTo = world.getTileEntity(posInventory);
        if(invToPushTo instanceof PedestalTileEntity) {
            itemFromPedestal = ItemStack.EMPTY;
        }
        else {
            if(cap.isPresent())
            {
                IEnergyStorage handler = cap.orElse(null);

                if(handler != null)
                {
                    if(handler.canReceive())
                    {
                        int containerMaxEnergy = handler.getMaxEnergyStored();
                        int containerCurrentEnergy = handler.getEnergyStored();
                        int containerEnergySpace = containerMaxEnergy - containerCurrentEnergy;
                        int getCurrentEnergy = getEnergyStored(coinInPedestal);
                        int transferRate = (containerEnergySpace >= getEnergyTransferRate(coinInPedestal))?(getEnergyTransferRate(coinInPedestal)):(containerEnergySpace);
                        if (getCurrentEnergy < transferRate) {transferRate = getCurrentEnergy;}

                        //transferRate at this point is equal to what we can send.
                        if(handler.receiveEnergy(transferRate,true) > 0)
                        {
                            int energyRemainingInUpgrade = getCurrentEnergy - transferRate;
                            setEnergyStored(coinInPedestal,energyRemainingInUpgrade);
                            handler.receiveEnergy(transferRate,false);
                        }
                    }
                }
            }
        }
    }

    public void upgradeItemAction(World world, BlockPos posOfPedestal, ItemStack itemInPedestal, ItemStack coinInPedestal)
    {
        TileEntity tile = world.getTileEntity(posOfPedestal);
        if(tile instanceof PedestalTileEntity)
        {
            PedestalTileEntity ped = ((PedestalTileEntity)tile);
            if(ped.hasItem())
            {
                if(isEnergyItemInsert(itemInPedestal) && !itemHasMaxEnergy(itemInPedestal))
                {
                    int itemMaxEnergy = getMaxEnergyInStack(itemInPedestal,null);
                    int itemCurrentEnergy = getEnergyInStack(itemInPedestal);
                    int itemEnergySpace = itemMaxEnergy - itemCurrentEnergy;
                    int getCurrentEnergy = getEnergyStored(coinInPedestal);

                    int transferRate = (itemEnergySpace >= getEnergyTransferRate(coinInPedestal))?(getEnergyTransferRate(coinInPedestal)):(itemEnergySpace);
                    if (getCurrentEnergy < transferRate) {transferRate = getCurrentEnergy;}

                    if(insertEnergyIntoStack(itemInPedestal, transferRate, true)>0)
                    {
                        int energyRemainingInUpgrade = getCurrentEnergy - transferRate;
                        setEnergyStored(coinInPedestal,energyRemainingInUpgrade);
                        insertEnergyIntoStack(itemInPedestal, transferRate, false);
                        writeStoredIntToNBT(coinInPedestal,0);
                        ped.update();
                    }
                }
                else
                {
                    writeStoredIntToNBT(coinInPedestal,1);
                    ped.update();
                }
            }
        }

    }

    public static final Item RFEXPORT = new ItemUpgradeEnergyExport(new Properties().maxStackSize(64).group(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/rfexport"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(RFEXPORT);
    }


}
