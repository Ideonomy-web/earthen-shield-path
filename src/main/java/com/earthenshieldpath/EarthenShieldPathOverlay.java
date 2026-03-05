package com.earthenshieldpath;

import javax.inject.Inject;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Perspective;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class EarthenShieldPathOverlay extends Overlay
{
    private final Client client;
    private final EarthenShieldPathPlugin plugin;
    private final EarthenShieldPathConfig config;

    private static final int TILE_SIZE = 128;

    @Inject
    public EarthenShieldPathOverlay(Client client, EarthenShieldPathPlugin plugin, EarthenShieldPathConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (config.showShieldPath())
        {
            List<LocalPoint> localPath = plugin.getPathLocalPoints();
            Color pathColor = config.pathColor();

            if (localPath != null && !localPath.isEmpty())
            {
                // Draw center 1x1 outlines
                for (LocalPoint lp : localPath)
                {
                    drawLocalTileOutline(graphics, lp, pathColor, 2f);
                }

                // If user enabled full 3x3, draw translucent hull fill only (no outlines)
                if (config.showFull3x3Path())
                {
                    // Adjust transparency?
                    int alpha = 40;
                    Color fillColor = new Color(pathColor.getRed(), pathColor.getGreen(), pathColor.getBlue(), alpha);

                    // Avoid overlap painting
                    Set<Long> drawnTiles = new HashSet<>();

                    for (LocalPoint center : localPath)
                    {
                        fill3x3AtLocal(graphics, center, fillColor, drawnTiles);
                    }
                }
            }
            else
            {
                List<WorldPoint> path = plugin.getPathTiles();
                for (WorldPoint wp : path)
                {
                    LocalPoint lp = LocalPoint.fromWorld(client, wp);
                    if (lp == null)
                        continue;

                    drawLocalTileOutline(graphics, lp, pathColor, 2f);

                    if (config.showFull3x3Path())
                    {
                        int alpha = 40;
                        Color fillColor = new Color(pathColor.getRed(), pathColor.getGreen(), pathColor.getBlue(), alpha);
                        Set<Long> drawnTiles = new HashSet<>();
                        fill3x3AtLocal(graphics, lp, fillColor, drawnTiles);
                    }
                }
            }
        }

        // Floor debris (rocks)
        if (config.markDebris())
        {
            Set<WorldPoint> debris = plugin.getDebrisTiles();
            Color debrisColor = config.debrisColor();

            for (WorldPoint wp : debris)
            {
                if (config.debrisFill())
                {
                    drawTileFill(graphics, wp, debrisColor, config.debrisFillOpacity());
                }

                drawTileOutline(graphics, wp, debrisColor);
            }
        }

        // Shadows from flying rocks
        if (config.markShadows())
        {
            Color shadowColor = config.shadowColor();

            for (GraphicsObject go : client.getGraphicsObjects())
            {
                if (go.getId() != 2380)
                    continue;

                LocalPoint lp = go.getLocation();
                if (lp == null)
                    continue;

                Polygon poly = Perspective.getCanvasTilePoly(client, lp);
                if (poly != null)
                {
                    Color fill = new Color(
                            shadowColor.getRed(),
                            shadowColor.getGreen(),
                            shadowColor.getBlue(),
                            80
                    );

                    graphics.setColor(fill);
                    graphics.fill(poly);
                }
            }
        }

        return null;
    }

    private void fill3x3AtLocal(Graphics2D g, LocalPoint center, Color fillColor, Set<Long> drawnTiles)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                int lx = center.getX() + dx * TILE_SIZE;
                int ly = center.getY() + dy * TILE_SIZE;
                int tileX = lx / TILE_SIZE;
                int tileY = ly / TILE_SIZE;
                long key = (((long) tileX) << 32) | (tileY & 0xffffffffL);

                if (drawnTiles.contains(key))
                    continue;

                LocalPoint lp = new LocalPoint(lx, ly);
                Polygon poly = Perspective.getCanvasTilePoly(client, lp);
                if (poly != null)
                {
                    g.setColor(fillColor);
                    g.fill(poly);

                    drawnTiles.add(key);
                }
            }
        }
    }

    private void drawLocalTileOutline(Graphics2D g, LocalPoint lp, Color color, float strokeWidth)
    {
        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null)
        {
            g.setColor(color);
            g.setStroke(new BasicStroke(strokeWidth));
            g.draw(poly);
        }
    }

    private void drawTileOutline(Graphics2D g, WorldPoint wp, Color color)
    {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null)
            return;

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null)
        {
            g.setColor(color);
            g.setStroke(new BasicStroke(2));
            g.draw(poly);
        }
    }

    private void drawTileFill(Graphics2D g, WorldPoint wp, Color color, int alpha)
    {
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null)
            return;

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null)
        {
            Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
            g.setColor(fill);
            g.fill(poly);
        }
    }
}
