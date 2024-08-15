package opengrave

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.InventoryBasic
import net.minecraft.inventory.InventoryHelper
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.ITextComponent
import java.util.*

class TileEntityGrave : TileEntity() {

    companion object {
        const val ID = "opengrave.tileentitygrave"
        const val INVENTORY_NBT_KEY = "inventory"
        const val ARMOR_NBT_KEY = "armor"
        const val DEATH_MESSAGE_NBT_KEY = "death_message"
        const val ENTITY_PLAYER_ID_NBT_KEY = "entity_player_id"
        const val BAUBLES_NBT_KEY = "baubles"
    }

    var entityPlayerID: UUID? = null
    var inventory: Array<ItemStack?> = emptyArray()
    var armor: Array<ItemStack?> = emptyArray()
    var offHand: ItemStack? = null
    var baubles: Array<ItemStack?> = emptyArray()
    var deathMessage: ITextComponent? = null

    private val inventoryWrapper: IInventory
        get() = InventoryBasic("throwaway", false, inventory.size + baubles.size + armor.size).apply {
            (inventory + armor + baubles).forEachIndexed { i, stack ->
                if (stack == null) {
                    setInventorySlotContents(i, ItemStack.EMPTY)
                } else {
                    setInventorySlotContents(i, stack)
                }
            }
        }

    fun takeDrops(entityPlayerID: UUID, items: Array<ItemStack?>, armor: Array<ItemStack?>, offHand: ItemStack?, baubles: Array<ItemStack?>, deathMessage: ITextComponent?) {
        this.entityPlayerID = entityPlayerID
        this.inventory = items
        this.armor = armor
        this.offHand = offHand
        this.baubles = baubles
        this.deathMessage = deathMessage
    }

    fun dropItems() = InventoryHelper.dropInventoryItems(world, pos, inventoryWrapper)

    fun returnPlayerItems(player: EntityPlayer) {
        if (player.persistentID != entityPlayerID)
            return

        val playerPos = player.position

        player.inventory.equipArmor(armor, playerPos)
        player.inventory.dispenseItems(inventory, playerPos)
        player.inventory.dispenseOffHand(offHand, playerPos)
        inventory = emptyArray()
        armor = emptyArray()

        val baublesInventory = player.safeGetBaubles()
        if (baublesInventory != null) {
            player.inventory.dispenseBaubles(baubles, player)
            baubles = emptyArray()
        }
    }

    private fun IInventory.dispenseItems(items: Array<ItemStack?>, playerPos: BlockPos) {
        val oldItems: ArrayList<ItemStack> = ArrayList()
        for ((index, itemStack) in items.withIndex()) {
            if (itemStack == null)
                continue

            var dispensed = false
            var possibleIndex = index
            while (possibleIndex < sizeInventory) {
                if (isItemValidForSlot(possibleIndex, itemStack)) {
                    val oldItem = getStackInSlot(possibleIndex)
                    oldItems.add(oldItem)
                    setInventorySlotContents(possibleIndex, itemStack)
                    dispensed = true
                    break
                }
                possibleIndex++
            }
            if (!dispensed) itemStack.dropInWorld(world, playerPos)
        }
        tryInsertStacks(oldItems, playerPos)
    }

    /**
     * Tries to insert an item into inventory, otherwise drops item below the player
     */
    private fun IInventory.tryInsertStacks(items: ArrayList<ItemStack>, playerPos: BlockPos) {
        for ((index, itemStack) in items.withIndex()) {
            var dispensed = false
            var possibleIndex = index
            // Magic number ahead! 36 is the capacity of the *default* inventory
            while (possibleIndex < 36) {
                // isItemBalidForSLot(possibleIndex, itemStack) always returns true....
                if (getStackInSlot(possibleIndex).isEmpty && isItemValidForSlot(possibleIndex, itemStack)) {
                    setInventorySlotContents(possibleIndex, itemStack)
                    dispensed = true
                    break
                }
                possibleIndex++
            }
            if (!dispensed) itemStack.dropInWorld(world, playerPos)
        }
    }

    private fun IInventory.equipArmor(armors: Array<ItemStack?>, playerPos: BlockPos) {
        val oldArmor: ArrayList<ItemStack> = ArrayList()

        for ((index, armor) in armors.withIndex()) {
            if (armor == null || armor.isEmpty) continue

            if (isItemValidForSlot(index + 36, armor)) {
                oldArmor.add(getStackInSlot(index + 36))
                setInventorySlotContents(index + 36, armor)
            }

        }
        tryInsertStacks(oldArmor, playerPos)
    }
    private fun IInventory.dispenseOffHand(offHand: ItemStack?, playerPos: BlockPos) {
        if (offHand == null || offHand.isEmpty) return
        val oldOffHand = getStackInSlot(40)
        setInventorySlotContents(40, offHand)
        if (!oldOffHand.isEmpty) tryInsertStacks(arrayListOf(oldOffHand), playerPos)
    }
    private fun IInventory.dispenseBaubles(baubles: Array<ItemStack?>, player: EntityPlayer) {
        val baublesHandler = player.safeGetBaubles()!!
        val oldBaubles: ArrayList<ItemStack> = ArrayList()

        for ((index, bauble) in baubles.withIndex()) {
            if (bauble == null || bauble.isEmpty) continue

            var possibleIndex = index
            while (possibleIndex < baublesHandler.sizeInventory) {
                if (baublesHandler.isItemValidForSlot(possibleIndex, bauble)) {
                    oldBaubles.add(baublesHandler.getStackInSlot(possibleIndex))
                    baublesHandler.setInventorySlotContents(possibleIndex, bauble)
                    break
                }
                possibleIndex++
            }
        }

        tryDispenseBaubles(oldBaubles, player)
    }
    private fun IInventory.tryDispenseBaubles(baubles: ArrayList<ItemStack>, player: EntityPlayer) {
        val baublesHandler = player.safeGetBaubles()!!
        val unEquipedBaubles: ArrayList<ItemStack> = ArrayList()
        for (bauble in baubles) {
            for (possibleIndex in 0 until baublesHandler.sizeInventory) {
                if (baublesHandler.isItemValidForSlot(possibleIndex, bauble) && baublesHandler.getStackInSlot(possibleIndex).isEmpty) {
                    unEquipedBaubles.add(baublesHandler.getStackInSlot(possibleIndex))
                    baublesHandler.setInventorySlotContents(possibleIndex, bauble)
                    break
                }
            }
        }

        if (unEquipedBaubles.isNotEmpty()) tryInsertStacks(unEquipedBaubles, player.position)
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        val rootTagCompound = compound.getCompoundTag(ID)

        val entityPlayerIDString = rootTagCompound.getString(ENTITY_PLAYER_ID_NBT_KEY)
        if (!entityPlayerIDString.isNullOrBlank()) {
            entityPlayerID = try {
                UUID.fromString(entityPlayerIDString)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        inventory = rootTagCompound.getItemStackArray(INVENTORY_NBT_KEY)
        armor = rootTagCompound.getItemStackArray(ARMOR_NBT_KEY)
        baubles = rootTagCompound.getItemStackArray(BAUBLES_NBT_KEY)

        val json = rootTagCompound.getString(DEATH_MESSAGE_NBT_KEY)
        if (json.isNotBlank()) {
            deathMessage = ITextComponent.Serializer.jsonToComponent(json)
        }
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        val rootTagCompound = NBTTagCompound()

        val entityPlayerIDString = entityPlayerID?.toString() ?: ""
        rootTagCompound.setString(ENTITY_PLAYER_ID_NBT_KEY, entityPlayerIDString)

        rootTagCompound.setTag(INVENTORY_NBT_KEY, inventory.toNBTTag())
        rootTagCompound.setTag(ARMOR_NBT_KEY, armor.toNBTTag())

        rootTagCompound.setTag(BAUBLES_NBT_KEY, baubles.toNBTTag())

        deathMessage?.let {
            val deathMessageJson = ITextComponent.Serializer.componentToJson(it)
            rootTagCompound.setString(DEATH_MESSAGE_NBT_KEY, deathMessageJson)
        }

        compound.setTag(ID, rootTagCompound)

        return compound
    }

    override fun toString() = "TileEntityGrave@$pos"
}
