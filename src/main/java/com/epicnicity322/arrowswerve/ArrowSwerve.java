/*
 * ArrowSwerve - A bukkit plugin that makes entities dodge projectiles.
 * Copyright (C) 2023 Christiano Rangel
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.epicnicity322.arrowswerve;

import com.epicnicity322.arrowswerve.command.ArrowSwerveReload;
import com.epicnicity322.epicpluginlib.bukkit.lang.MessageSender;
import com.epicnicity322.epicpluginlib.bukkit.logger.Logger;
import com.epicnicity322.epicpluginlib.bukkit.reflection.ReflectionUtil;
import com.epicnicity322.epicpluginlib.core.config.ConfigurationHolder;
import com.epicnicity322.epicpluginlib.core.config.ConfigurationLoader;
import com.epicnicity322.epicpluginlib.core.logger.ConsoleLogger;
import com.epicnicity322.yamlhandler.Configuration;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

public final class ArrowSwerve extends JavaPlugin implements Listener {
    private static final @NotNull Logger logger = new Logger("&0[&9Arrow&6Swerve&0]&7 ");
    private static final @NotNull ConfigurationHolder config = new ConfigurationHolder(Paths.get("plugins", "ArrowSwerve", "config.yml"), "# The version of this configuration. Will reset if the config is outdated enough.\n" +
            "Version: '" + ArrowSwerveVersion.VERSION + "'\n" +
            "\n" +
            "# The language of the messages. Available: [ EN-US, PT-BR ]\n" +
            "Language: 'EN-US'\n" +
            "\n" +
            "# The percentage of times entities will swerve.\n" +
            "Chance: 80.0 #percent\n" +
            "\n" +
            "# Whether the entities must be looking at the arrow to try and avoid it.\n" +
            "Must Be Looking:\n" +
            "  Enabled: true\n" +
            "  # The entity's field of view.\n" +
            "  Field Of View: 72\n" +
            "  # If the projectile source is within this distance, the entity will always swerve no matter where it's looking.\n" +
            "  # AVAILABLE ONLY ON PAPER SERVERS!\n" +
            "  Always Swerve Near Source: 3.0\n" +
            "\n" +
            "# Set so the swerving happens only for specific entities rather than all.\n" +
            "# Use MONSTERS to include all monsters.\n" +
            "# Leave empty to include all entities.\n" +
            "Specific Entities:\n" +
            "  - MONSTERS\n" +
            "\n" +
            "# Set so the swerving happens only for specific projectiles.\n" +
            "# Leave empty to include all projectiles.\n" +
            "Specific Projectiles:\n" +
            "  - ARROW");
    private static final @NotNull ConfigurationHolder langEN_US = new ConfigurationHolder(Paths.get("plugins", "ArrowSwerve", "Language EN-US.yml"), "Version: '" + ArrowSwerveVersion.VERSION + "'\n" +
            "\n" +
            "General:\n" +
            "  Prefix: '&0[&9Arrow&6Swerve&0] '\n" +
            "\n" +
            "Reload:\n" +
            "  Success: '&aReloaded successfully.'\n" +
            "  Error: '&cSomething went wrong while reloading. Please check console for errors.'");
    public static final @NotNull MessageSender lang = new MessageSender(() -> config.getConfiguration().getString("Language").orElse("EN-US"), langEN_US.getDefaultConfiguration());
    private static final @NotNull ConfigurationHolder langPT_BR = new ConfigurationHolder(Paths.get("plugins", "ArrowSwerve", "Language EN-US.yml"), "Version: '" + ArrowSwerveVersion.VERSION + "'\n" +
            "\n" +
            "General:\n" +
            "  Prefix: '&0[&9Arrow&6Swerve&0] '\n" +
            "\n" +
            "Reload:\n" +
            "  Success: '&aRecarregado com sucesso.'\n" +
            "  Error: '&cAlgo de errado ocorreu ao recarregar. Por favor veja o console para erros.'");
    private static final @NotNull ConfigurationLoader loader = new ConfigurationLoader() {{
        registerConfiguration(config, ArrowSwerveVersion.VERSION, ArrowSwerveVersion.VERSION);
        registerConfiguration(langEN_US, ArrowSwerveVersion.VERSION, ArrowSwerveVersion.VERSION);
        registerConfiguration(langPT_BR, ArrowSwerveVersion.VERSION, ArrowSwerveVersion.VERSION);
    }};
    private static final boolean hasGetOrigin = ReflectionUtil.getMethod(Entity.class, "getOrigin") != null;
    private static @Nullable ArrowSwerve instance;

    static {
        lang.addLanguage("EN-US", langEN_US);
        lang.addLanguage("PT-BR", langPT_BR);
    }

    private final @NotNull SecureRandom random = new SecureRandom();
    private final @NotNull HashSet<EntityType> swervingEntities = new HashSet<>();
    private final @NotNull HashSet<EntityType> swervingProjectiles = new HashSet<>();
    private boolean monsters = true;
    private boolean mustBeLooking = true;
    private double nearestSwerve = 3.0;
    private double fov = 72.0;
    private double chance = 80.0;

    public ArrowSwerve() {
        instance = this;
        logger.setLogger(getLogger());
    }

    public static boolean reload() {
        if (instance == null) return false;

        HashMap<ConfigurationHolder, Exception> exceptions = loader.loadConfigurations();
        exceptions.forEach((config, exception) -> {
            logger.log("Something went wrong while loading '" + config.getPath().getFileName() + "' config:");
            exception.printStackTrace();
        });

        Configuration config = ArrowSwerve.config.getConfiguration();

        instance.swervingEntities.clear();
        config.getCollection("Specific Entities", obj -> {
            if (obj.toString().equalsIgnoreCase("monsters")) {
                instance.monsters = true;
            } else {
                try {
                    instance.swervingEntities.add(EntityType.valueOf(obj.toString().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    logger.log("Value '" + obj + "' in Specific Entities list is not a valid entity type.", ConsoleLogger.Level.WARN);
                }
            }
            return null;
        });

        instance.swervingProjectiles.clear();
        config.getCollection("Specific Projectiles", obj -> {
            try {
                instance.swervingProjectiles.add(EntityType.valueOf(obj.toString().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.log("Value '" + obj + "' in Specific Projectiles list is not a valid entity type.", ConsoleLogger.Level.WARN);
            }
            return null;
        });

        instance.chance = config.getNumber("Chance").orElse(80.0).doubleValue();
        instance.mustBeLooking = config.getBoolean("Must Be Looking.Enabled").orElse(false);
        instance.fov = config.getNumber("Must Be Looking.Field Of View").orElse(72.0).doubleValue();
        instance.nearestSwerve = config.getNumber("Must Be Looking.Ignore Near Source").orElse(3.0).doubleValue();
        return exceptions.isEmpty();
    }

    @Override
    public void onEnable() {
        boolean success = reload();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("arrowswervereload")).setExecutor(new ArrowSwerveReload());
        if (success) {
            logger.log("&aArrowSwerve was enabled successfully.");
        } else {
            logger.log("&4Some issues happened while loading. Please check if you edited your configurations properly.");
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity victim = event.getHitEntity();
        Entity projectile = event.getEntity();

        // Checking valid entity types.
        if (victim == null || victim.getType() == EntityType.PLAYER) return;
        // Empty means all.
        if (monsters ? (!(victim instanceof Monster) && !swervingEntities.contains(victim.getType())) : (!swervingEntities.isEmpty() && !swervingEntities.contains(victim.getType()))) {
            return;
        }
        if (!swervingProjectiles.isEmpty() && (!swervingProjectiles.contains(projectile.getType()))) return;

        double chance = this.chance;
        if (chance < 0.0) chance = 0.0;
        if (chance == 0.0 || (chance != 100.0 && chance < random.nextDouble(100.0))) return;

        if (mustBeLooking) {
            Location victimLoc = victim.getLocation(), projectileLoc = projectile.getLocation();
            boolean checkIFLooking = true;

            if (hasGetOrigin && nearestSwerve > 0.0) {
                Location source = event.getEntity().getOrigin();
                if (source != null && source.distanceSquared(victimLoc) <= nearestSwerve * nearestSwerve) {
                    checkIFLooking = false;
                }
            }
            if (checkIFLooking) {
                victimLoc.setPitch(0);
                Vector victimVec = victimLoc.getDirection();
                Vector projectileVec = projectileLoc.toVector().subtract(victimLoc.toVector());

                double angle = victimVec.angle(projectileVec);

                if (angle >= Math.toRadians(fov)) return;
            }
        }

        event.setCancelled(true);

        Vector arrowDirection = projectile.getVelocity().normalize();
        Vector displacement = arrowDirection.crossProduct(new Vector(0, 1, 0)).normalize();
        victim.setVelocity(displacement);
    }
}
