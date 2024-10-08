package com.gmail.goosius.townycultures.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.gmail.goosius.townycultures.TownyCultures;
import com.gmail.goosius.townycultures.metadata.ResidentMetaDataController;
import com.gmail.goosius.townycultures.settings.Settings;
import com.palmergames.adventure.text.Component;
import com.palmergames.bukkit.towny.event.statusscreen.NationStatusScreenEvent;
import com.palmergames.bukkit.towny.event.statusscreen.TownStatusScreenEvent;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.Translatable;
import com.palmergames.util.StringMgmt;

public class StatusScreenListener implements Listener {


	@EventHandler
	public void onTownStatusScreen(TownStatusScreenEvent event) {
		if (!Settings.isTownyCulturesEnabled())
			return;
		String slug = ResidentMetaDataController.hasCulture(event.getTown()) ? TownyCultures.getCulture(event.getTown()) : "/town set culture [culture]";
		event.getStatusScreen().addComponentOf("culture", Component.newline().append(Component.text(Translatable.of("status_town_culture", slug).forLocale(event.getCommandSender()))));
	}

	/*
	 * Adds a cultural breakdown of the Towns in a nation,
	 * using town population to determine culture-strength.
	 */
	@EventHandler
	public void onNationStatus(NationStatusScreenEvent event) {
		if (!Settings.isTownyCulturesEnabled())
			return;

		/*
		 * Create a map of cultures within the nation, from which the town
		 * populations are used to calculate the strength of the culture.
		 */
		Map<String,Integer> cultureStrength = new HashMap<>();
		for (Town town : event.getNation().getTowns())
			assignStrength(town, town.getNumResidents(), cultureStrength);

		/*
		 * Create a double map of culture percentages, filtering out <1% to "Other".
		 */
		Map<String,Double> cultureDoubleMap = new HashMap<>();
		for (Map.Entry<String, Integer> entry: cultureStrength.entrySet())
			assignPercent(entry.getKey(), entry.getValue(), event.getNation().getNumResidents(), cultureDoubleMap);

		/*
		 * Create an int map of culture percentages, adding 0.5 to effectively
		 * round up instead of down if the % ends in a number greater than .5.
		 */
		Map<String,Integer> cultureIntegerMap = new HashMap<>();
		for (Map.Entry<String, Double> entry: cultureDoubleMap.entrySet())
			cultureIntegerMap.put(entry.getKey(), (int) (entry.getValue() + 0.5)); //Round half up

		/*
		 * Check if the percentages add up to 100% at this point. If not:
		 * - assign the remainder to the capital city if it has a culture, or
		 * - prescribe it as an unknown.
		 * Remainder can be positive or negative but will always result in an even 100%.
		 */
		int remainder = 100 - cultureIntegerMap.values().stream().reduce(0, Integer::sum); // 100 - sum of values()
		if (remainder != 0) 
			assignStrength(event.getNation().getCapital(), remainder, cultureIntegerMap);

		/*
		 * Sort the map from strongest culture to weakest culture.
		 */
		if (cultureIntegerMap.size() > 1)
			cultureIntegerMap = sortMap(cultureIntegerMap);

		/*
		 * Turn it into a list of "Culturename ###%" strings.
		 */
		List<String> cultures = new ArrayList<>(cultureIntegerMap.size());
		for (Map.Entry<String, Integer> entry: cultureIntegerMap.entrySet())
			cultures.add(StringMgmt.capitalize(entry.getKey()) + " " + entry.getValue() + "%");

		// Join the lines if need be.
		String output = StringMgmt.join(cultures, ", ");

		// Add our line to the NationStatusScreenEvent.
		event.getStatusScreen().addComponentOf("cultures", Component.newline().append(Component.text(Translatable.of("status_town_culture", output).forLocale(event.getCommandSender()))));
	}

	/*
	 * Assigns the strength to the given Town's culture or to the "Unknown" category.
	 */
	private void assignStrength(Town town, int strength, Map<String, Integer> cultureStrength) {
		String culture = ResidentMetaDataController.getTownCulture(town); // Returns the culture or "Unknown".

		if (cultureStrength.containsKey(culture)) // Culture already exists in the map. Add the strength to it.
			strength += cultureStrength.get(culture);

		cultureStrength.put(culture, strength);
	}

	/*
	 * Assigns the percentage strength for a Nation's culture, filtering <1% to "Other".
	 */
	private void assignPercent(String culture, int value, int nationPop, Map<String, Double> cultureStrength) {
		double percent = ((double)value) / nationPop * 100;
		if (percent < 1)       // Change culture over to 
			culture = "Other"; // "Other" if less than 1%.
		if (cultureStrength.containsKey(culture))    // If culture is already present,
			percent += cultureStrength.get(culture); // add new strength to it.
		
		cultureStrength.put(culture, percent); // Place culture and strength into double map.
	}

	/*
	 * Sorts a nation's cultures into largest -> smallest based on their %.
	 */
	private Map<String, Integer> sortMap(Map<String, Integer> cultureStrength) {
		
        List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>(cultureStrength.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
	}
}
