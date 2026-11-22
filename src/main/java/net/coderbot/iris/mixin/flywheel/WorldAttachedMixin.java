package net.coderbot.iris.mixin.flywheel;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flywheel's WorldAttached keeps a strong reference to every level it has seen, which leaks memory
 * when old levels are replaced. Ignore put() calls for any level that is not the current client level.
 *
 * Ported from the Mekalus fork (Oculus 1.19.2 fork by the Mekalus author), which fixed this leak.
 * String target + @Pseudo so Oculus builds and loads without Flywheel present.
 */
@Pseudo
@Mixin(targets = "com.jozufozu.flywheel.util.WorldAttached", remap = false)
public class WorldAttachedMixin<T> {
	@Inject(method = "put", at = @At("HEAD"), cancellable = true, remap = false)
	void atl$stopInjectingOldWorld(LevelAccessor world, T entry, CallbackInfo ci) {
		if (world != Minecraft.getInstance().level) {
			ci.cancel();
		}
	}
}
