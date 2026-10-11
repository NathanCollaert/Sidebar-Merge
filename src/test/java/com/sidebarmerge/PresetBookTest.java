package com.sidebarmerge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.Test;

public class PresetBookTest
{
	private final Gson gson = new Gson();

	private static PresetBook book()
	{
		return PresetBook.migrate(Arrays.asList("a.A", "b.B"), new LinkedHashSet<>(Collections.singletonList("b.B")),
			Collections.singletonMap("a.A", 300));
	}

	@Test
	public void migrationKeepsTheOldLayoutAsDefault()
	{
		PresetBook b = book();
		assertEquals(Collections.singletonList("Default"), b.names());
		assertEquals(Arrays.asList("a.A", "b.B"), b.active().panels);
		assertTrue(b.active().collapsed.contains("b.B"));
		assertEquals(Integer.valueOf(300), b.active().heights.get("a.A"));
	}

	@Test
	public void saveAsNewCopiesTheLayoutAndSwitchesToIt()
	{
		PresetBook b = book();
		assertTrue(b.create("Bossing", true));
		assertEquals("Bossing", b.active().name);
		assertEquals(Arrays.asList("a.A", "b.B"), b.active().panels);
		// It's a copy: changing it leaves Default alone.
		b.active().panels.remove("b.B");
		b.switchTo("Default");
		assertEquals(Arrays.asList("a.A", "b.B"), b.active().panels);
	}

	@Test
	public void newEmptyPresetStartsEmpty()
	{
		PresetBook b = book();
		assertTrue(b.create("Skilling", false));
		assertTrue(b.active().panels.isEmpty() && b.active().heights.isEmpty());
		assertEquals(Arrays.asList("Default", "Skilling"), b.names());
	}

	@Test
	public void namesMustBeUniqueNonBlankAndShort()
	{
		PresetBook b = book();
		assertNotNull(b.nameProblem("  ", false));
		assertNotNull(b.nameProblem("default", false));
		assertNotNull(b.nameProblem("x".repeat(PresetBook.MAX_NAME + 1), false));
		assertNull(b.nameProblem("Bossing", false));
		assertFalse(b.create("DEFAULT", true));
		// Renaming a preset to (a re-cased) itself is fine.
		assertNull(b.nameProblem("DEFAULT", true));
	}

	@Test
	public void renameAndDelete()
	{
		PresetBook b = book();
		b.create("Bossing", true);
		b.create("Skilling", false);
		assertTrue(b.renameActive("Slayer"));
		assertEquals(Arrays.asList("Default", "Bossing", "Slayer"), b.names());
		assertTrue(b.deleteActive());
		assertEquals("Bossing", b.active().name);
		b.deleteActive();
		assertFalse("the last preset stays", b.deleteActive());
		assertEquals(Collections.singletonList("Default"), b.names());
	}

	@Test
	public void jsonRoundTrip()
	{
		PresetBook b = book();
		b.create("Bossing", true);
		PresetBook back = PresetBook.fromJson(gson, b.toJson(gson));
		assertEquals(b.names(), back.names());
		assertEquals("Bossing", back.active().name);
		Map<String, Integer> heights = back.active().heights;
		assertEquals(Integer.valueOf(300), heights.get("a.A"));
		assertTrue(back.active().collapsed.contains("b.B"));
	}

	@Test
	public void brokenJsonIsRepairedOrRejected()
	{
		assertNull(PresetBook.fromJson(gson, "{not json"));
		assertNull(PresetBook.fromJson(gson, ""));
		PresetBook repaired = PresetBook.fromJson(gson, "{\"presets\":[{\"name\":\"A\"},{\"name\":\"a\"},{\"name\":\" \"}],\"active\":\"gone\"}");
		assertEquals(Collections.singletonList("A"), repaired.names());
		assertEquals("A", repaired.active().name);
		assertTrue(repaired.active().panels.isEmpty());
		PresetBook empty = PresetBook.fromJson(gson, "{}");
		assertEquals(Collections.singletonList("Default"), empty.names());
	}
}
