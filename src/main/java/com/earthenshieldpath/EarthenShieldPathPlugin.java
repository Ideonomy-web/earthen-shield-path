package com.earthenshieldpath;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "Doom QOL"
)
public class EarthenShieldPathPlugin extends Plugin
{
    private static final int VE_ID = 14714;
    private static final int PATH_NPC_ID = 14715;
    private static final Set<Integer> DEBRIS_IDS = Set.of(57286);

    private static final int SHADOW_GFX_ID_1 = 2380;
    private static final int SHADOW_GFX_ID_2 = 3404;

    // Auto-clear path after milliseconds (20 seconds)
    private static final long PATH_EXPIRE_MS = 20_000L;

    @Inject private Client client;
    @Inject private EarthenShieldPathConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private EarthenShieldPathOverlay overlay;

    @Getter private final List<WorldPoint> pathTiles = new ArrayList<>();
    @Getter private final List<LocalPoint> pathLocalPoints = new ArrayList<>();
    @Getter private final Set<WorldPoint> debrisTiles = new HashSet<>();
    @Getter private final Set<WorldPoint> shadowTiles = new HashSet<>();

    // Volatile earth tiles based on click order (world)
    private WorldPoint veEndTile = null;   // first VE clicked
    private WorldPoint veStartTile = null; // second VE clicked

    // Volatile earth tiles based on click order (local)
    private LocalPoint veEndLocal = null;
    private LocalPoint veStartLocal = null;

    private final List<WorldPoint> veClickQueue = new ArrayList<>();
    private final List<LocalPoint> veClickLocalQueue = new ArrayList<>();
    private boolean pendingPathCompute = false;

    // Timestamp when the last path was computed (ms since epoch). 0 = no path.
    private long lastPathComputedAt = 0L;

    @Provides
    EarthenShieldPathConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(EarthenShieldPathConfig.class);
    }

    @Override
    protected void startUp()
    {
        clearAll();
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        clearAll();
    }

    private void clearAll()
    {
        pathTiles.clear();
        pathLocalPoints.clear();
        debrisTiles.clear();
        shadowTiles.clear();
        veClickQueue.clear();
        veClickLocalQueue.clear();
        veEndTile = null;
        veStartTile = null;
        veEndLocal = null;
        veStartLocal = null;
        pendingPathCompute = false;
        lastPathComputedAt = 0L;
    }


    // Volatile earth tracking
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (event.getMenuAction() != MenuAction.NPC_SECOND_OPTION)
            return;

        if (!"Attack".equals(event.getMenuOption()))
            return;

        if (!event.getMenuTarget().contains("Volatile earth"))
            return;

        int npcIndex = event.getId();

        NPC npc = client.getNpcs().stream()
                .filter(n -> n.getIndex() == npcIndex)
                .findFirst()
                .orElse(null);

        if (npc == null || npc.getId() != VE_ID)
            return;

        LocalPoint lp = npc.getLocalLocation();
        if (lp == null)
            return;

        WorldPoint wp = WorldPoint.fromLocalInstance(client, lp);
        if (wp == null)
            return;

        // Potential bug -- quick succession clicks on same target?
        // Prevent duplicate consecutive clicks producing identical points.
        // If the last stored world/local point equals this one, ignore this click.
        if (!veClickQueue.isEmpty())
        {
            WorldPoint lastWp = veClickQueue.get(veClickQueue.size() - 1);
            if (lastWp.getX() == wp.getX() && lastWp.getY() == wp.getY() && lastWp.getPlane() == wp.getPlane())
            {
                return; // duplicate world point, ignore
            }
        }

        if (!veClickLocalQueue.isEmpty())
        {
            LocalPoint lastLp = veClickLocalQueue.get(veClickLocalQueue.size() - 1);
            if (lastLp.getX() == lp.getX() && lastLp.getY() == lp.getY())
            {
                return; // duplicate local point, ignore
            }
        }

        veClickQueue.add(wp);
        veClickLocalQueue.add(lp);

        if (veClickQueue.size() == 2 && veClickLocalQueue.size() == 2)
        {
            veEndTile = veClickQueue.get(0);
            veStartTile = veClickQueue.get(1);

            veEndLocal = veClickLocalQueue.get(0);
            veStartLocal = veClickLocalQueue.get(1);

            pendingPathCompute = true;
        }
    }

    // Tick-delayed path compute + expiry
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (pendingPathCompute)
        {
            if (veStartTile != null && veEndTile != null && veStartLocal != null && veEndLocal != null)
                computeStraightLine();

            // Reset state for next special
            veClickQueue.clear();
            veClickLocalQueue.clear();
            veStartTile = null;
            veEndTile = null;
            veStartLocal = null;
            veEndLocal = null;
            pendingPathCompute = false;
        }

        // Auto-clear path after PATH_EXPIRE_MS since last compute
        if (!pathTiles.isEmpty() || !pathLocalPoints.isEmpty())
        {
            long now = System.currentTimeMillis();
            if (lastPathComputedAt > 0 && now - lastPathComputedAt >= PATH_EXPIRE_MS)
            {
                pathTiles.clear();
                pathLocalPoints.clear();
                lastPathComputedAt = 0L;
            }
        }
    }

    private void computeStraightLine()
    {
        if (veEndTile == null || veStartTile == null || veStartLocal == null || veEndLocal == null)
            return;

        pathTiles.clear();
        pathLocalPoints.clear();

        // WorldPoint path (tile coordinates)
        int x1 = veStartTile.getX();
        int y1 = veStartTile.getY();
        int x2 = veEndTile.getX();
        int y2 = veEndTile.getY();
        int plane = veEndTile.getPlane();

        int dx = Integer.compare(x2, x1);
        int dy = Integer.compare(y2, y1);

        int x = x1;
        int y = y1;

        while (x != x2 || y != y2)
        {
            pathTiles.add(new WorldPoint(x, y, plane));
            if (x != x2) x += dx;
            if (y != y2) y += dy;
        }
        pathTiles.add(veEndTile);

        // LocalPoint path (scene-local coordinates)
        final int TILE_SIZE = 128;

        int lx1 = veStartLocal.getX();
        int ly1 = veStartLocal.getY();
        int lx2 = veEndLocal.getX();
        int ly2 = veEndLocal.getY();

        int ldx = Integer.compare(lx2, lx1);
        int ldy = Integer.compare(ly2, ly1);

        int lx = lx1;
        int ly = ly1;

        // Add start local
        pathLocalPoints.add(new LocalPoint(lx, ly));

        // Move in tile-sized steps until end local
        while ((ldx != 0 && lx != lx2) || (ldy != 0 && ly != ly2))
        {
            if (ldx != 0 && lx != lx2) lx += ldx * TILE_SIZE;
            if (ldy != 0 && ly != ly2) ly += ldy * TILE_SIZE;

            pathLocalPoints.add(new LocalPoint(lx, ly));
        }
        lastPathComputedAt = System.currentTimeMillis();
    }

    // Floor debris (rocks)
    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (!config.markDebris())
            return;

        if (DEBRIS_IDS.contains(event.getGameObject().getId()))
        {
            WorldPoint wp = event.getGameObject().getWorldLocation();
            if (wp != null)
                debrisTiles.add(wp);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        if (!config.markDebris())
            return;

        if (DEBRIS_IDS.contains(event.getGameObject().getId()))
        {
            WorldPoint wp = event.getGameObject().getWorldLocation();
            if (wp != null)
                debrisTiles.remove(wp);
        }
    }

    // Shadows from flying rocks
    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event)
    {
        if (!config.markShadows())
            return;

        int id = event.getGraphicsObject().getId();
        if (id != SHADOW_GFX_ID_1 && id != SHADOW_GFX_ID_2)
            return;

        WorldPoint wp = WorldPoint.fromLocalInstance(client, event.getGraphicsObject().getLocation());
        if (wp != null)
            shadowTiles.add(wp);
    }


    // Earthen shield despawn clear
    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        NPC npc = event.getNpc();

        if (npc.getId() == PATH_NPC_ID)
        {
            // Clear path when the shield despawns (special ended)
            pathTiles.clear();
            pathLocalPoints.clear();

            veClickQueue.clear();
            veClickLocalQueue.clear();
            veStartTile = null;
            veEndTile = null;
            veStartLocal = null;
            veEndLocal = null;
            pendingPathCompute = false;
            lastPathComputedAt = 0L;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOADING
                || event.getGameState() == GameState.HOPPING
                || event.getGameState() == GameState.LOGIN_SCREEN)
        {
            clearAll();
        }
    }
}
