package net.coderbot.iris.compat.dh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.pipeline.PassTiming;
import net.coderbot.iris.shadows.ShadowRenderingState;

@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public class MixinDHShadowPassGuard {
	@Inject(
		method = {"renderLods", "renderDeferredLodsForShaders"},
		at = @At("HEAD"),
		cancellable = true
	)
	private void oculus$skipDhWithoutShadowProgram(CallbackInfo ci) {
		// The pack has no dh_terrain, so nothing can draw LODs in a way it understands. Hide them rather than paste them
		// into one of its buffers (real Oculus 1.8.0 treats such packs as incompatible). Shaders off brings them back.
		if (Iris.isPackInUseQuick() && !DHCompat.isActive()) {
			ci.cancel();
			return;
		}
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered() && !DHCompat.hasShadowProgram()) {
			ci.cancel();
			return;
		}
		PassTiming.begin(ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? PassTiming.Pass.DH_SHADOW : PassTiming.Pass.DH_LODS);
	}

	@Inject(
		method = {"renderLods", "renderDeferredLodsForShaders"},
		at = @At("RETURN")
	)
	private void oculus$endDhTiming(CallbackInfo ci) {
		PassTiming.end(PassTiming.Pass.DH_SHADOW);
		PassTiming.end(PassTiming.Pass.DH_LODS);
	}

	@Inject(
		method = {"renderFadeOpaque", "renderFadeTransparent"},
		at = @At("HEAD"),
		cancellable = true
	)
	private void oculus$skipVanillaFadeRenderer(CallbackInfo ci) {
		if (DHCompat.isActive() || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ci.cancel();
		}
	}
}
