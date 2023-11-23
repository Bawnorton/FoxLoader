package com.fox2code.foxloader.registry;

import com.fox2code.foxloader.network.NetworkPlayer;

import java.util.List;

public interface RegisteredWorld {
    /**
     * @return if the current instance has control over the world
     */
    default boolean hasRegisteredControl() { throw new RuntimeException(); }

    default int getRegisteredBlockId(int xCoord, int yCoord, int zCoord) {
        throw new RuntimeException();
    }

    default int getRegisteredBlockMetadata(int xCoord, int yCoord, int zCoord) {
        throw new RuntimeException();
    }

    /**
     * @throws IllegalStateException if the game doesn't have control over the world
     * @see #hasRegisteredControl()
     */
    default void setRegisteredBlockAndMetadataWithNotify(
            int xCoord, int yCoord, int zCoord, int block, int metadata) {
        throw new RuntimeException();
    }

    /**
     * Same as {@link #setRegisteredBlockAndMetadataWithNotify(int, int, int, int, int)}
     * but won't throw an exception if the game has no control over the world
     */
    default void forceSetRegisteredBlockAndMetadataWithNotify(
            int xCoord, int yCoord, int zCoord, int block, int metadata) {
        throw new RuntimeException();
    }

    /**
     *
     * @param x position
     * @param y position
     * @param z position
     * @param registeredItemStack item of the RegisteredEntityItem
     * @return the spawned RegisteredEntityItem or null if spawning failed.
     */
    default RegisteredEntityItem spawnRegisteredEntityItem(
            double x, double y, double z, RegisteredItemStack registeredItemStack) {
        throw new RuntimeException();
    }

    default List<? extends RegisteredEntity> getRegisteredEntities() {
        throw new RuntimeException();
    }

    default List<? extends RegisteredTileEntity> getRegisteredTileEntities() {
        throw new RuntimeException();
    }

    default List<? extends NetworkPlayer> getRegisteredNetworkPlayers() {
        throw new RuntimeException();
    }

    /**
     * @return the current dimension id
     *
     * Note: Multiples worlds with the same dimension ID can exist at a time
     */
    default int getRegisteredDimensionID() { throw new RuntimeException(); }
}
