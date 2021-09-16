package melonslise.spacetest.server.util;

import melonslise.mixinInterfaces.server.IChunkMapExtended;
import melonslise.spacetest.SpaceTest;
import melonslise.spacetest.common.util.DimBlockPos;
import melonslise.spacetest.common.util.DimChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fmlserverevents.FMLServerStartedEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Collections.synchronizedMap;

public class ChunkLoaderManager {
    // Chunkloader tracker
    private static Map<ResourceKey<Level>, HashMap<BlockPos, ChunkLoader>> chunkLoaders = synchronizedMap(new HashMap<>());
    private static MinecraftServer server = null;
    private static boolean ready = false;

    /**
     * Attempts to create a chunk loader for a rectangular group of chunks. Creates a "render-only" chunkloader - the chunkloader will
     * only be active while a player is within render distance.
     * @param owner - The dimension block position of the owner of this chunkloader
     * @param startCorner - The lower-left corner (-x, -z) of the chunk region to load, in the destination dimension
     * @param xWidth - The width of the chunkloader along the (+)x-axis
     * @param zWidth - The width of the chunkloader along the (+)z-axis
     * @return true if a new chunkloader was created, false if there was already one for that owner position.
     */
    public static boolean CreateChunkLoader(DimBlockPos owner, DimChunkPos startCorner, int xWidth, int zWidth) {
        // If there's already a chunkloader for this location, don't overwrite it.
        if (LoaderExists(owner)) {
            SpaceTest.LOGGER.warn("Could not create chunkloader for block at '"+owner+"', as there was already one there.");
            return false;
        }

        ChunkPos end = new ChunkPos(startCorner.pos.x+xWidth, startCorner.pos.z+zWidth);
        SpaceTest.LOGGER.info("Creating chunkloader for block at "+owner+
                ".\n\tTarget dimension: "+startCorner.dimension.location()+
                "\n\tStart chunk: "+startCorner+
                "\n\tEnd corner: "+end);

        // Make sure the owner's dimension exists in our map
        if (!chunkLoaders.containsKey(owner.dimension)) {
            SpaceTest.LOGGER.info("Created chunk loader map for dimension "+owner.dimension.location()+".");
            chunkLoaders.put(owner.dimension, new HashMap<BlockPos, ChunkLoader>());
        }

        // Actually create the chunkloader
        ChunkLoader loader = new ChunkLoader(owner, startCorner, xWidth, zWidth);
        chunkLoaders.get(owner.dimension).put(owner.pos, loader);
        SpaceTest.LOGGER.debug("Chunkloader created for owner at "+owner+".");
        UpdateChunkLoader(loader);
        return true;
    }

    // Returns whether or not a loader exists for the specified owner
    public static boolean LoaderExists(DimBlockPos owner) {
        return (chunkLoaders.containsKey(owner.dimension))
                && (chunkLoaders.get(owner.dimension).containsKey(owner.pos));
    }

    /**
     * Attempts to activate the chunkloader for the supplied owner
     * @param owner - The dimension block position of the owner
     * @return - True if the chunk loader was activated, false if it couldn't (already active, didn't exist, unknown error).
     */
    private static boolean ActivateChunkLoader(DimBlockPos owner) {
        if (!ready) {
            SpaceTest.LOGGER.warn("Tried to activate chunkloader before server was ready.");
            return false;
        }

        if (!LoaderExists(owner)) {
            SpaceTest.LOGGER.warn("Tried to activate nonexistent chunkloader at "+ owner +".");
            return false;
        }

        SpaceTest.LOGGER.info("Activating chunk loader at "+ owner + ".");
        ChunkLoader loader = chunkLoaders.get(owner.dimension).get(owner.pos);
        if (!loader.active) {
            ServerLevel level = server.getLevel(owner.dimension);
            if (level != null) {
                loader.active = true;
                for (ChunkPos pos :loader.chunks) {
                    ForgeChunkManager.forceChunk(level, SpaceTest.ID, owner.pos, pos.x, pos.z, true, false);
                }
            }
            else {
                SpaceTest.LOGGER.warn("Level for " +owner.dimension.location() + " was null.");
                return false;
            }
        }
        else {
            SpaceTest.LOGGER.info("Chunkloader at "+ owner + " was already active.");
            return false;
        }
        return true;
    }

    private static boolean DeactivateChunkLoader(DimBlockPos owner) {
        if (!ready) {
            SpaceTest.LOGGER.warn("Tried to deactivate chunkloader before server was ready.");
            return false;
        }

        if (!LoaderExists(owner)) {
            SpaceTest.LOGGER.warn("Tried to deactivate nonexistent chunkloader at "+ owner+".");
            return false;
        }

        SpaceTest.LOGGER.info("Deactivating chunk loader at "+ owner + ".");
        ChunkLoader loader = chunkLoaders.get(owner.dimension).get(owner.pos);
        if (loader.active) {
            ServerLevel level = server.getLevel(owner.dimension);
            if (level != null) {
                for (ChunkPos pos : chunkLoaders.get(owner.dimension).get(owner.pos).chunks) {
                    ForgeChunkManager.forceChunk(level, SpaceTest.ID, owner.pos, pos.x, pos.z, false, false);
                }
                loader.active = false;
            }
            else {
                SpaceTest.LOGGER.warn("Level for " +owner.dimension.location() + " was null.");
            }
        }
        else {
            SpaceTest.LOGGER.info("Chunkloader at "+ owner + " was already inactive.");
            return false;
        }
        return true;
    }

    public static boolean DestroyChunkLoader(DimBlockPos owner) {
        if (!LoaderExists(owner)) {
            SpaceTest.LOGGER.warn("Tried to destroy nonexistent chunk loader at "+ owner + ".");
            return false;
        }
        SpaceTest.LOGGER.info("Destroying chunk loader at "+ owner + ".");
        // Remove all tickets associated with this chunkloader, if they haven't already been removed
        DeactivateChunkLoader(owner);
        chunkLoaders.get(owner.dimension).remove(owner.pos);
        return true;
    }

    private static void UpdateChunkLoader(ChunkLoader loader) {
        if (!ready) {
            SpaceTest.LOGGER.warn("Tried to activate chunkloader before server was ready.");
            return;
        }
        //SpaceTest.LOGGER.info("Updating chunkloader at " + loader.owner);
        if (IsWithinPlayerRender(loader.owner)) {
            if (!loader.active) {
                SpaceTest.LOGGER.info("Player came within render range of chunkloader at "+loader.owner);
                ActivateChunkLoader(loader.owner);
            }
        }
        else {
            if (loader.active) {
                SpaceTest.LOGGER.info("Player left render range of chunkloader at "+loader.owner);
                DeactivateChunkLoader(loader.owner);
            }
        }
    }

    // Is making this synchronized necessary?
    /**
     * Called whenever something that would change the chunkloading occurs - a chunkloader is created/destroyed,
     * is no longer within render-range of any players, etc.
     */
    private static synchronized void UpdateAllChunkLoaders()
    {
        // Only force chunks if the server is ready.
        if (!ready) {
            SpaceTest.LOGGER.warn("Tried to update chunkloaders before server was ready.");
            return;
        }

        //SpaceTest.LOGGER.info("Updating all chunkloaders.");
        for (ResourceKey<Level> dimension : chunkLoaders.keySet()) {
            for (ChunkLoader loader : chunkLoaders.get(dimension).values()) {
                UpdateChunkLoader(loader);
            }
        }
    }

    /**
     * Check what render-only chunkloaders are within range of a player.
      * @param event - player tick event
     */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.SERVER)
        {
            if (event.phase == TickEvent.Phase.START) {
                if (server.getTickCount()%20 == 0) {
                    UpdateAllChunkLoaders();
                }
            }
        }
    }

    /**
     * When the server starts,
     * @param event - server start event
     */
    @SubscribeEvent
    public static void OnServerStart(FMLServerStartedEvent event) {
        SpaceTest.LOGGER.info("Received server start event");
        if (server != null) {
            SpaceTest.LOGGER.warn("Received FMLServerStartedEvent but server variable already referenced a server.");
        }
        server = event.getServer();
        ready = true;
        UpdateAllChunkLoaders();
    }

    @SubscribeEvent
    public static void OnServerClosing(FMLServerStoppedEvent event) {
        ready = false;
        SpaceTest.LOGGER.info("Received server stopped event");
        server = null;
        chunkLoaders = synchronizedMap(new HashMap<>());
    }

    /**
     * Returns whether the supplied position is within the server's view distance of any players.
     * @param pos - Position to check
     * @return - True if any players are within range of the position, False only if no players are within range.
     */
    private static boolean IsWithinPlayerRender(DimBlockPos pos) {
        // View distance is in chunks, so times 16.
        if (!ready) {
            SpaceTest.LOGGER.warn("Tried to access player list before server was ready.");
            return false;
        }

        int viewDistance = 16*((IChunkMapExtended)(server.getLevel(Level.OVERWORLD).getChunkSource().chunkMap)).getViewDistance();
        for (Player player : server.getPlayerList().getPlayers()) {
            if (player.position().distanceTo(Vec3.atCenterOf(pos.pos)) <= viewDistance) {
                return true;
            }
        }
        return false;
    }

    private static class ChunkLoader
    {
        public final DimBlockPos owner;
        public final ResourceKey<Level> targetDimension;
        public final HashSet<ChunkPos> chunks;
        private boolean active = false;

        private ChunkLoader(DimBlockPos owner, DimChunkPos startCorner, int xWidth, int zWidth) {
            this.owner = owner;
            this.targetDimension = startCorner.dimension;

            chunks = new HashSet<ChunkPos>(xWidth * zWidth);
            ChunkPos endCorner = new ChunkPos(startCorner.pos.x + xWidth, startCorner.pos.z + zWidth);

            // Set of chunks
            for (int x = startCorner.pos.x; x < endCorner.x; x++) {
                for (int z = startCorner.pos.z; z < endCorner.z; z++) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
    }
}