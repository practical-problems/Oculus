package net.coderbot.iris.config;

import net.coderbot.iris.Iris;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Experimental optimizations (mostly ported from the Emberlens fork) and the pass timing tool.
 * Global settings, stored in config/oculus-experimental.properties, not per shader pack.
 * Everything defaults to off, so a fresh install behaves exactly like before.
 *
 * Hot paths read the public static fields; the GUI works on {@link #OPTIONS}.
 */
public final class ExperimentalConfig {
	// Per-frame CPU
	public static boolean samplerUnbind;
	public static boolean memoryBarrierSkip;
	public static boolean matrixInverseCache;
	public static boolean changedOnlyUniforms;
	public static boolean indexedLoops;
	public static boolean cachedUniformLookups;
	public static boolean lazyTimeUniforms;
	public static boolean cachedPipelineLookup;
	public static boolean skipBitangentNormalize;
	// Entities
	public static boolean fastEntityBufferList;
	public static boolean reuseOrderingGraphs;
	public static boolean fastOrderingGraphMap;
	public static boolean noSortWithoutShaders;
	// Timing
	public static boolean timingOverlay;
	public static int timingLogSeconds;

	public enum Risk {
		LOW("Low", 0x55FF55), MEDIUM("Medium", 0xFFFF55), HIGH("High", 0xFF5555);

		public final String label;
		public final int color;

		Risk(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	public static final class Option {
		public final String key;
		public final String group;
		public final String name;
		public final String description;
		public final String benefit;
		public final Risk risk;
		public final String riskNote;
		public final String applies;
		/** Included in "Enable safe optimizations". */
		public final boolean safe;
		private final String[] valueNames;
		private final int[] values;
		private final int defaultIndex;
		private int index;

		private Option(String key, String group, String name, String description, String benefit, Risk risk, String riskNote,
					   String applies, boolean safe, String[] valueNames, int[] values, int defaultIndex) {
			this.key = key;
			this.group = group;
			this.name = name;
			this.description = description;
			this.benefit = benefit;
			this.risk = risk;
			this.riskNote = riskNote;
			this.applies = applies;
			this.safe = safe;
			this.valueNames = valueNames;
			this.values = values;
			this.defaultIndex = defaultIndex;
			this.index = defaultIndex;
		}

		public int value() {
			return values[index];
		}

		public String valueName() {
			return valueNames[index];
		}

		public boolean isOn() {
			return values[index] != 0;
		}

		public void cycle(boolean backwards) {
			index = Math.floorMod(index + (backwards ? -1 : 1), values.length);
		}

		public void reset() {
			index = defaultIndex;
		}

		/** For on/off options: on. */
		public void turnOn() {
			for (int i = 0; i < values.length; i++) {
				if (values[i] != 0) {
					index = i;
					return;
				}
			}
		}

		private void setFromString(String s) {
			for (int i = 0; i < values.length; i++) {
				if (String.valueOf(values[i]).equals(s) || (values.length == 2 && Boolean.toString(values[i] != 0).equals(s))) {
					index = i;
					return;
				}
			}
		}

		private String toStorage() {
			return values.length == 2 && values[0] == 0 && values[1] == 1 ? Boolean.toString(isOn()) : String.valueOf(value());
		}
	}

	private static final String[] OFF_ON = {"OFF", "ON"};
	private static final int[] ZERO_ONE = {0, 1};
	private static final String NOW = "Applies at once";

	public static final String GROUP_CPU = "Per-frame CPU";
	public static final String GROUP_ENTITIES = "Entities";
	public static final String GROUP_TIMING = "Timing";

	public static final List<Option> OPTIONS;

	static {
		List<Option> o = new ArrayList<>();
		o.add(toggle("samplerUnbind", GROUP_CPU, "Sampler unbind",
			"After each shader program, clears only the texture units that were used, with one GL call, instead of checking every unit.",
			"Less CPU time per draw with every pack.", Risk.LOW, "Same GL state as before.", NOW));
		o.add(toggle("memoryBarrierSkip", GROUP_CPU, "Memory barrier skip",
			"Skips the GPU memory barrier issued on every program switch until the pack binds an image or a storage buffer.",
			"Fewer GPU sync points with packs that use neither.", Risk.LOW, "Barriers return as soon as the pack binds one.", NOW));
		o.add(toggle("matrixInverseCache", GROUP_CPU, "Matrix inverse cache",
			"Reuses the inverse projection, inverse model-view and normal matrices until the matrix actually changes.",
			"Fewer matrix inversions per draw.", Risk.LOW, "Exact same values.", NOW));
		o.add(toggle("changedOnlyUniforms", GROUP_CPU, "Changed-only uniforms",
			"Custom uniforms (from shaders.properties) are uploaded to each program only when their value changed.",
			"Less CPU time with packs that define many custom uniforms.", Risk.LOW, "A missed update would show as a stale effect; turn off if a pack effect freezes.", NOW));
		o.add(toggle("indexedLoops", GROUP_CPU, "Indexed uniform loops",
			"Loops over uniform, sampler and image lists by index instead of with iterators.",
			"Tiny saving, no allocations.", Risk.LOW, "Same behaviour.", NOW));
		o.add(toggle("cachedUniformLookups", GROUP_CPU, "Cached uniform lookups",
			"Custom-uniform expressions remember which variable a name refers to instead of looking it up on every evaluation.",
			"Less CPU time with packs that define many custom uniforms.", Risk.LOW, "Same values.", NOW));
		o.add(toggle("lazyTimeUniforms", GROUP_CPU, "Lazy time uniforms",
			"Reads the system clock for the date/time uniforms only when a pack actually uses them.",
			"Saves a clock read every frame.", Risk.LOW, "Same values.", NOW));
		o.add(toggle("cachedPipelineLookup", GROUP_CPU, "Cached pipeline lookup",
			"Returns the current pipeline from a cached Optional instead of creating one on every call.",
			"Fewer short-lived objects (less garbage collection).", Risk.LOW, "Same behaviour.", NOW));
		o.add(toggle("skipBitangentNormalize", GROUP_CPU, "Skip bitangent normalize",
			"Tangent generation stops normalizing the bitangent, because only its sign is used.",
			"Slightly faster chunk building with shaders on.", Risk.LOW, "Identical results.", "Applies to chunks built from now on"));
		o.add(toggle("fastEntityBufferList", GROUP_ENTITIES, "Fast entity buffer list",
			"Entity batching keeps its buffer assignment in a fastutil map instead of a LinkedHashMap with boxed integers.",
			"Less CPU time and garbage per entity draw.", Risk.LOW, "Same batching.", NOW));
		o.add(toggle("reuseOrderingGraphs", GROUP_ENTITIES, "Reuse ordering graphs",
			"Entity draw ordering reuses empty graphs instead of reallocating them every frame, and skips empty ones.",
			"Less garbage per frame.", Risk.LOW, "Same draw order.", NOW));
		o.add(toggle("fastOrderingGraphMap", GROUP_ENTITIES, "Fast ordering graph map",
			"The entity draw-order graph uses a fastutil map instead of a LinkedHashMap.",
			"Less CPU time and garbage with many entity types on screen.", Risk.LOW, "Same draw order.", NOW));
		o.add(toggle("noSortWithoutShaders", GROUP_ENTITIES, "No sort without shaders",
			"Skips sorting the entity list by type while no shader pack is active (the sort only helps shader batching).",
			"Less CPU time with shaders off; no effect with shaders on.", Risk.LOW, "Vanilla draw order without shaders.", NOW));
		o.add(new Option("timingOverlay", GROUP_TIMING, "Timing overlay",
			"Shows CPU and GPU time per render pass in milliseconds in the top-left corner, averaged over about one second.",
			"Measure each option's effect.", Risk.LOW, "Costs a few GPU timestamp queries while on.",
			NOW, false, OFF_ON, ZERO_ONE, 0));
		o.add(new Option("timingLogSeconds", GROUP_TIMING, "Timing log",
			"Writes the same averages to latest.log at the chosen interval.",
			"A record for A/B tests.", Risk.LOW, "Costs a few GPU timestamp queries while on.",
			NOW, false, new String[] {"Off", "10 s", "30 s", "60 s"}, new int[] {0, 10, 30, 60}, 0));
		OPTIONS = Collections.unmodifiableList(o);
	}

	private static Option toggle(String key, String group, String name, String description, String benefit, Risk risk, String riskNote, String applies) {
		return new Option(key, group, name, description, benefit, risk, riskNote, applies, true, OFF_ON, ZERO_ONE, 0);
	}

	private static Path path() {
		return FMLPaths.CONFIGDIR.get().resolve(Iris.MODID + "-experimental.properties");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			Properties properties = new Properties();
			try (InputStream in = Files.newInputStream(path)) {
				properties.load(in);
			} catch (IOException e) {
				Iris.logger.error("Could not read " + path, e);
			}
			for (Option option : OPTIONS) {
				String value = properties.getProperty(option.key);
				if (value != null) {
					option.setFromString(value.trim());
				}
			}
		}
		apply();
	}

	public static void save() {
		Properties properties = new Properties();
		for (Option option : OPTIONS) {
			properties.setProperty(option.key, option.toStorage());
		}
		Path path = path();
		try (OutputStream out = Files.newOutputStream(path)) {
			properties.store(out, "Oculus experimental options (global). Everything off = stock behaviour.");
		} catch (IOException e) {
			Iris.logger.error("Could not write " + path, e);
		}
	}

	public static void resetAll() {
		OPTIONS.forEach(Option::reset);
		apply();
	}

	public static void enableSafe() {
		for (Option option : OPTIONS) {
			if (option.safe) {
				option.turnOn();
			}
		}
		apply();
	}

	private static boolean on(String key) {
		return get(key).isOn();
	}

	public static Option get(String key) {
		for (Option option : OPTIONS) {
			if (option.key.equals(key)) {
				return option;
			}
		}
		throw new IllegalArgumentException(key);
	}

	/** Copies the option values into the static fields read by the render code. */
	public static void apply() {
		samplerUnbind = on("samplerUnbind");
		memoryBarrierSkip = on("memoryBarrierSkip");
		matrixInverseCache = on("matrixInverseCache");
		changedOnlyUniforms = on("changedOnlyUniforms");
		indexedLoops = on("indexedLoops");
		cachedUniformLookups = on("cachedUniformLookups");
		lazyTimeUniforms = on("lazyTimeUniforms");
		cachedPipelineLookup = on("cachedPipelineLookup");
		skipBitangentNormalize = on("skipBitangentNormalize");
		fastEntityBufferList = on("fastEntityBufferList");
		reuseOrderingGraphs = on("reuseOrderingGraphs");
		fastOrderingGraphMap = on("fastOrderingGraphMap");
		noSortWithoutShaders = on("noSortWithoutShaders");
		timingOverlay = on("timingOverlay");
		timingLogSeconds = get("timingLogSeconds").value();
	}

	public static boolean timingActive() {
		return timingOverlay || timingLogSeconds > 0;
	}

	private ExperimentalConfig() {
	}
}
