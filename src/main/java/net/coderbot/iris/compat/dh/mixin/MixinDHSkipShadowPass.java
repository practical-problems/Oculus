package net.coderbot.iris.compat.dh.mixin;

import net.coderbot.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculus' shadow pass calls LevelRenderer#renderChunkLayer with the sun's matrices.
 * Distant Horizons hooks that method, so without this it renders (and composites) its LODs
 * during the shadow pass too. Skip every DH render hook while shadows are being rendered.
 *
 * DH 3.3.3: renderLods() and renderDeferredLodsForShaders() both delegate to the private
 * renderLodLayer(boolean); the vanilla-fade passes are renderFadeOpaque() / renderFadeTransparent().
 */
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public class MixinDHSkipShadowPass {
   @Inject(method = {"renderLodLayer(Z)V"}, at = {@At("HEAD")}, cancellable = true, remap = false)
   private void oculus$skipLodLayerInShadowPass(boolean deferred, CallbackInfo ci) {
      if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
         ci.cancel();
      }
   }

   @Inject(method = {"renderFadeOpaque()V"}, at = {@At("HEAD")}, cancellable = true, remap = false)
   private void oculus$skipFadeOpaqueInShadowPass(CallbackInfo ci) {
      if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
         ci.cancel();
      }
   }

   @Inject(method = {"renderFadeTransparent()V"}, at = {@At("HEAD")}, cancellable = true, remap = false)
   private void oculus$skipFadeTransparentInShadowPass(CallbackInfo ci) {
      if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
         ci.cancel();
      }
   }
}
