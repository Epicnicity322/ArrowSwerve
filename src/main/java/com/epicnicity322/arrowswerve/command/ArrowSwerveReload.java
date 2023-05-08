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

package com.epicnicity322.arrowswerve.command;

import com.epicnicity322.arrowswerve.ArrowSwerve;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class ArrowSwerveReload implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (ArrowSwerve.reload()) {
            ArrowSwerve.lang.send(sender, ArrowSwerve.lang.get("Reload.Success"));
        } else {
            ArrowSwerve.lang.send(sender, ArrowSwerve.lang.get("Reload.Error"));
        }
        return true;
    }
}
