@file:JvmName("ValkyrienSkies")
package org.valkyrienskies.mod.api

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

val vsApi: VsApi @JvmName("getApi") get() = ValkyrienSkiesMod.api

fun Level?.getShipManagingBlock(pos: BlockPos?) = vsApi.getShipManagingBlock(this, pos)
fun Level?.getShipManagingBlock(x: Int, y: Int, z: Int) = getShipManagingChunk(x shr 4, z shr 4)

fun Level?.getLoadedShipManagingBlock(pos: BlockPos?): LoadedShip? = getShipManagingBlock(pos) as? LoadedShip

fun Level?.getDeadShipManagingBlock(pos: BlockPos?): Ship? =
    getShipManagingBlock(pos)?.takeUnless { it is LoadedShip }

fun Level?.getShipManagingChunk(pos: ChunkPos?) = vsApi.getShipManagingChunk(this, pos)
fun Level?.getShipManagingChunk(chunkX: Int, chunkZ: Int) = vsApi.getShipManagingChunk(this, chunkX, chunkZ)
