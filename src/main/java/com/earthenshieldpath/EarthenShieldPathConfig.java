package com.earthenshieldpath;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("earthenshieldpath")
public interface EarthenShieldPathConfig extends Config
{
    // Floor debris (rocks)
    @ConfigItem(
            position = 1,
            keyName = "markDebris",
            name = "Mark Debris on Ground",
            description = "Highlights debris (rock) tiles on the ground."
    )
    default boolean markDebris()
    {
        return true;
    }

    @ConfigItem(
            position = 2,
            keyName = "debrisColor",
            name = "Debris Color",
            description = "Color used to draw debris tiles."
    )
    default Color debrisColor()
    {
        return new Color(139, 69, 19);
    }

    @ConfigItem(
            position = 3,
            keyName = "debrisFill",
            name = "Fill Debris Tiles",
            description = "If enabled, debris tiles will be filled with the same color as their outline."
    )
    default boolean debrisFill()
    {
        return true;
    }

    @ConfigItem(
            position = 4,
            keyName = "debrisFillOpacity",
            name = "Debris Fill Opacity",
            description = "0 = outline only, 255 = fully filled."
    )
    default int debrisFillOpacity()
    {
        return 80;
    }



    // Shadows from flying rocks
    @ConfigItem(
            position = 5,
            keyName = "markShadows",
            name = "Mark Shadow Tiles",
            description = "Highlights shadow graphics (gfx 2380) on the ground."
    )
    default boolean markShadows()
    {
        return true;
    }

    @ConfigItem(
            position = 6,
            keyName = "shadowColor",
            name = "Shadow Color",
            description = "Color used to highlight shadow tiles."
    )
    default Color shadowColor()
    {
        return new Color(255, 80, 80);
    }


    // SHIELD PATH
    @ConfigItem(
            position = 7,
            keyName = "showShieldPath",
            name = "Show Shield Path",
            description = "If disabled, the shield path will not be drawn."
    )
    default boolean showShieldPath()
    {
        return true;
    }

    @ConfigItem(
            position = 8,
            keyName = "pathColor",
            name = "Shield Path Color",
            description = "Color used to draw the VE1 → VE2 shield path."
    )
    default Color pathColor()
    {
        return Color.GREEN;
    }

    @ConfigItem(
            keyName = "showFull3x3Path",
            name = "Show full 3x3 Orb Path",
            description = "When enabled, draw the full 3x3 orb range",
            position = 9
    )
    default boolean showFull3x3Path()
    {
        return false;
    }

}
