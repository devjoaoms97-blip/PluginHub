package com.example.skills.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class SkillsGUIListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(SkillsGUI.TITULO)) {
            event.setCancelled(true);
        }
    }
}
